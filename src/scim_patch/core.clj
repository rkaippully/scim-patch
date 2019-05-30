(ns scim-patch.core
  (:require [clojure.string :as s]
            [scim-patch.paths :as paths]
            [scim-patch.filter :as fltr]))

(defn handle-attr-path-levels
  [schema resource update-fn [attr & attrs]]
  (let [attr-key (keyword attr)
        schema'  (get-in schema [:attributes attr-key])]
    (when (nil? schema')
      (throw (ex-info (str "Invalid path element: " attr)
               {:status   400
                :scimType :invalidPath})))
    (if (empty? attrs)
      (update-fn resource attr schema')
      (do
        (when (:multi-valued schema')
          (throw (ex-info (str "Unexpected multivalued path element: " attr)
                   {:status   400
                    :scimType :invalidPath})))
        (update resource attr-key #(handle-attr-path-levels (:type schema') (or % {}) update-fn attrs))))))

(defn handle-attr-path
  [schema resource uri attr subattr update-fn]
  (as-> [attr] $
    (if (s/blank? uri) $ (cons uri $))
    (if (s/blank? subattr) $ (concat $ [subattr]))
    (handle-attr-path-levels schema resource update-fn $)))

(defn handle-operation
  [schema resource {:keys [path value]} attr-path-fn value-path-fn]
  (if (s/blank? path)
    (merge resource value)
    (let [[_ xs] (paths/parse path)]
      (case (first xs)
        :attrPath
        (let [[uri attr subattr] (paths/extract-attr-path xs)]
          (handle-attr-path schema resource uri attr subattr
            (partial attr-path-fn value)))
        :valuePath
        (let [[_ attr-path value-filter subattr2] xs
              [uri attr subattr]                  (paths/extract-attr-path attr-path)]
          (handle-attr-path schema resource uri attr subattr
            (partial value-path-fn value value-filter subattr2)))))))

(defn add-value
  [schema old-val new-val]
  (if (:multi-valued schema)
    (if (and (sequential? new-val)
          (or (nil? old-val) (sequential? old-val)))
      (concat old-val new-val)
      (throw (ex-info "Invalid value for multivalued attribute"
               {:status   400
                :scimType :invalidValue})))
    new-val))

(defn filter-and-add
  [schema new-val value-filter subattr]
  (fn [old-val]
    (if (fltr/match-filter? schema value-filter old-val)
      (if (s/blank? subattr)
        new-val
        (let [subattr-key (keyword subattr)
              schema'     (get-in schema [:attributes subattr-key])]
          (when (nil? schema')
            (throw (ex-info (str "Invalid path element: " subattr)
                     {:status   400
                      :scimType :invalidPath})))
          (update old-val subattr-key #(add-value schema' % new-val))))
      old-val)))

(defn op-add
  [schema resource opr]
  (letfn [(add-attr-path
            [value res attr sch]
            (update res (keyword attr) #(add-value sch % value)))

          (add-value-path
            [value value-filter subattr res attr sch]
            (when-not (:multi-valued sch)
              (throw (ex-info "Value filter can only be applied on multivalued attributes"
                       {:status   400
                        :scimType :invalidFilter})))
            (update res (keyword attr)
              #(doall (map (filter-and-add sch value value-filter subattr) %))))]

    (handle-operation schema resource opr add-attr-path add-value-path)))

(defn filter-and-remove
  [schema value-filter subattr]
  (fn [acc old-val]
    (if (fltr/match-filter? schema value-filter old-val)
      (if (s/blank? subattr)
        acc
        (let [subattr-key (keyword subattr)
              schema'     (get-in schema [:attributes subattr-key])]
          (when (nil? schema')
            (throw (ex-info (str "Invalid path element: " subattr)
                     {:status   400
                      :scimType :invalidPath})))
          (conj acc (dissoc old-val subattr-key))))
      (conj acc old-val))))

(defn op-remove
  [schema resource opr]
  (when (s/blank? (:path opr))
    (throw (ex-info "Missing path for remove operation"
             {:status   400
              :scimType :noTarget})))
  (letfn [(remove-attr-path
            [value res attr sch]
            (dissoc res (keyword attr)))

          (remove-value-path
            [value value-filter subattr res attr sch]
            (when-not (:multi-valued sch)
              (throw (ex-info "Value filter can only be applied on multivalued attributes"
                       {:status   400
                        :scimType :invalidFilter})))
            (let [attr-key (keyword attr)
                  new-val  (reduce (filter-and-remove sch value-filter subattr) [] (get res attr-key))]
              (if (empty? new-val)
                (dissoc res attr-key)
                (assoc res attr-key new-val))))]

    (handle-operation schema resource opr remove-attr-path remove-value-path)))

(defn patch
  [schema resource op]
  (cond
    ;; single patch operation
    (map? op)
    (case (:op op)
      "add"    (op-add schema resource op)
      "remove" (op-remove schema resource op))

    ;; sequence of operations
    (sequential? op)
    (reduce #(patch schema %1 %2) resource op)))


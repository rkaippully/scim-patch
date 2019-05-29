(ns scim-patch.core
  (:require [clojure.string :as s]
            [scim-patch.paths :as paths]
            [scim-patch.filter :as fltr]))

(defn add-attr-path-levels
  [schema resource update-fn [attr & attrs]]
  (let [attr-key (keyword attr)
        schema'  (get-in schema [:attributes attr-key])]
    (when (nil? schema')
      (throw (ex-info (str "Invalid path element: " attr)
               {:status   400
                :scimType :invalidPath})))
    (if (empty? attrs)
      (update resource attr-key #(update-fn schema' %))
      (do
        (when (:multi-valued schema')
          (throw (ex-info (str "Unexpected multivalued path element: " attr)
                   {:status   400
                    :scimType :invalidPath})))
        (update resource attr-key #(add-attr-path-levels (:type schema') (or % {}) update-fn attrs))))))

(defn add-attr-path
  [schema resource uri attr subattr update-fn]
  (as-> [attr] $
    (if (s/blank? uri) $ (cons uri $))
    (if (s/blank? subattr) $ (concat $ [subattr]))
    (add-attr-path-levels schema resource update-fn $)))

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
  [schema old-val new-val value-filter subattr]
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
    old-val))

(defn handle-value-filter
  [schema vals new-val value-filter subattr]
  (when-not (:multi-valued schema)
    (throw (ex-info "Value filter can only be applied on multivalued attributes"
             {:status   400
              :scimType :invalidFilter})))
  (doall (map #(filter-and-add schema %1 new-val value-filter subattr) vals)))

(defn add-value-path
  [schema resource value uri attr subattr value-filter subattr2]
  (add-attr-path schema resource uri attr subattr
    #(handle-value-filter %1 %2 value value-filter subattr2)))

(defn op-add
  [schema resource {:keys [path value]}]
  (if (s/blank? path)
    (merge resource value)
    (let [[_ xs] (paths/parse path)]
      (case (first xs)
        :attrPath
        (let [[uri attr subattr] (paths/extract-attr-path xs)]
          (add-attr-path schema resource uri attr subattr #(add-value %1 %2 value)))
        :valuePath
        (let [[_ attr-path value-filter subattr2] xs
              [uri attr subattr]                  (paths/extract-attr-path attr-path)]
          (add-value-path schema resource value uri attr subattr value-filter subattr2))))))

(defn patch
  [schema resource op]
  (cond
    ;; single patch operation
    (map? op)
    (case (:op op)
      "add" (op-add schema resource op))

    ;; sequence of operations
    (sequential? op)
    (reduce #(patch schema %1 %2) resource op)))


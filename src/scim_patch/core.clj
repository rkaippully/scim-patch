(ns scim-patch.core
  (:require [clojure.string :as s]
            [scim-patch.paths :as paths]
            [scim-patch.filter :as fltr])
  (:import (clojure.lang ExceptionInfo)))

(defn handle-attr-path-levels
  [schema resource update-fn [attr & attrs]]
  (let [attr-key (keyword attr)
        schema'  (get-in schema [:attributes attr-key])]
    (when (nil? schema')
      (throw (ex-info (str "Invalid path element")
               {:status   400
                :scimType :invalidPath
                :path attr})))
    (if (empty? attrs)
      (update-fn resource attr schema')
      (do
        (when (:multi-valued schema')
          (throw (ex-info (str "Unexpected multivalued path element")
                   {:status   400
                    :scimType :invalidPath
                    :path attr})))
        (update resource attr-key #(handle-attr-path-levels (:type schema') (or % {}) update-fn attrs))))))

(defn handle-attr-path
  [schema resource uri attr subattr skip-unknown? update-fn]
  (let [patching-schema   (if (s/blank? uri)
                            (:id schema)
                            uri)
        patchable-schemas (:schemas schema)]
    (if (or (not skip-unknown?)
            (nil? patching-schema)
            (some #{patching-schema} patchable-schemas))
      ;; The schema is patchable
      (as-> [attr] $
        (if (s/blank? uri) $ (cons uri $))
        (if (s/blank? subattr) $ (concat $ [subattr]))
        (handle-attr-path-levels schema resource update-fn $))
      ;; The schema isn't patchable, ignore it
      resource)))

(defn handle-operation
  [schema resource {:keys [path value]} skip-unknown? attr-path-fn value-path-fn]
  (try
    (if (s/blank? path)
      ;; no path, so handle each attribute separately
      (reduce (fn [r [k v]]
                (handle-operation schema r {:path (name k) :value v} skip-unknown? attr-path-fn value-path-fn))
              resource value)
      ;; path provided
      (let [[_ xs] (paths/parse path)]
        (case (first xs)
          :attrPath
          (let [[uri attr subattr] (paths/extract-attr-path xs)]
            (handle-attr-path schema resource uri attr subattr skip-unknown?
                              (partial attr-path-fn value)))
          :valuePath
          (let [[_ attr-path value-filter subattr2] xs
                [uri attr subattr]                  (paths/extract-attr-path attr-path)]
            (handle-attr-path schema resource uri attr subattr skip-unknown?
                              (partial value-path-fn value value-filter subattr2))))))
    (catch ExceptionInfo e
      (throw (ex-info (.getMessage e)
                      (assoc (ex-data e) :path path))))))

(defn value-for-add
  [schema old-val new-val]
  (if (:multi-valued schema)
    (if (and (sequential? new-val)
          (or (nil? old-val) (sequential? old-val)))
      (if (and (vector? new-val) (or (nil? old-val) (vector? old-val)))
        (vec (concat old-val new-val))
        (concat old-val new-val))
      (throw (ex-info "Invalid value for multivalued attribute"
               {:status   400
                :scimType :invalidValue})))
    (if-let [attributes (get-in schema [:type :attributes])]
      ;; complex attribute
      (reduce (fn [obj [k v]]
                (update obj k #(value-for-add (k attributes) % v)))
              old-val new-val)
      ;; simple attribute
      new-val)))

(defn filter-and-add
  [schema new-val value-filter subattr]
  (fn [old-val]
    (if (fltr/match-filter? schema value-filter old-val)
      (if (s/blank? subattr)
        new-val
        (let [subattr-key (keyword subattr)
              schema'     (get-in schema [:type :attributes subattr-key])]
          (when (nil? schema')
            (throw (ex-info (str "Invalid path element")
                     {:status   400
                      :scimType :invalidPath
                      :path subattr})))
          (update old-val subattr-key #(value-for-add schema' % new-val))))
      old-val)))

(defn op-add
  [schema resource opr skip-unknown?]
  (when-not (contains? opr :value)
    (throw (ex-info "Invalid patch keys" {:status 400
                                          :scimType :invalidSyntax})))
  (letfn [(add-attr-path
            [value res attr sch]
            (try
              (update res (keyword attr) #(value-for-add sch % value))
              (catch ExceptionInfo e
                (throw (ex-info (.getMessage e)
                                (assoc (ex-data e) :path (:path opr)))))))

          (add-value-path
            [value value-filter subattr res attr sch]
            (when-not (:multi-valued sch)
              (throw (ex-info "Value filter can only be applied on multivalued attributes"
                       {:status   400
                        :scimType :invalidFilter
                        :path (:path opr)})))
            (try
              (update res (keyword attr)
                #(let [mapfn (if (vector? %) mapv map)]
                  (doall (mapfn (filter-and-add sch value value-filter subattr) %))))
              (catch ExceptionInfo e
                (throw (ex-info (.getMessage e)
                                (assoc (ex-data e) :path (:path opr)))))))]
    (handle-operation schema resource opr skip-unknown? add-attr-path add-value-path)))

(defn filter-and-remove
  [schema value-filter subattr]
  (fn [acc old-val]
    (if (fltr/match-filter? schema value-filter old-val)
      (if (s/blank? subattr)
        acc
        (let [subattr-key (keyword subattr)
              schema'     (get-in schema [:type :attributes subattr-key])]
          (when (nil? schema')
            (throw (ex-info (str "Invalid path element")
                     {:status   400
                      :scimType :invalidPath
                      :path subattr})))
          (conj acc (dissoc old-val subattr-key))))
      (conj acc old-val))))

(defn op-remove
  [schema resource opr skip-unknown?]
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

    (handle-operation schema resource opr skip-unknown? remove-attr-path remove-value-path)))

(defn value-for-replace
  [schema value]
  (when (and (:multi-valued schema) (not (sequential? value)))
    (throw (ex-info "Invalid value for multivalued attribute"
             {:status   400
              :scimType :invalidValue})))
  value)

(defn filter-and-replace
  [schema new-val value-filter subattr]
  (fn [{:keys [value replaced?]} old-val]
    (if (fltr/match-filter? schema value-filter old-val)
      {:replaced? true
       :value     (conj value
                        (if (s/blank? subattr)
                          new-val
                          (let [subattr-key (keyword subattr)
                                schema'     (get-in schema [:type :attributes subattr-key])]
                            (when (nil? schema')
                              (throw (ex-info (str "Invalid path element")
                                       {:status   400
                                        :scimType :invalidPath
                                        :path subattr})))
                            (assoc old-val subattr-key (value-for-replace schema' new-val)))))}

      ;; Filter did not match
      {:replaced? replaced? :value (conj value old-val)})))

(defn op-replace
  [schema resource opr skip-unknown?]
  (when (not-any? #(contains? opr %) [:value :path])
    (throw (ex-info "Invalid patch keys" {:status 400
                                          :scimType :invalidSyntax})))
  (letfn [(replace-attr-path
            [value res attr sch]
            (assoc res (keyword attr) (value-for-replace sch value)))

          (replace-value-path
            [value value-filter subattr res attr sch]
            (when-not (:multi-valued sch)
              (throw (ex-info "Value filter can only be applied on multivalued attributes"
                       {:status   400
                        :scimType :invalidFilter})))
            (update res (keyword attr)
              #(let [result (reduce (filter-and-replace sch value value-filter subattr)
                              {:value [] :replaced? false} %)]
                 (if (:replaced? result)
                   (:value result)
                   (throw (ex-info "No match for replace operation with value filter"
                            {:status   400
                             :scimType :noTarget}))))))]

    (handle-operation schema resource opr skip-unknown? replace-attr-path replace-value-path)))

(defn patch
  [schema resource op & {:keys [skip-unknown-schemas]}]
  (if (and skip-unknown-schemas (not (:schemas schema)))
    (throw (ex-info "Option 'skip-unknown-schemas' requires :schemas in schema"
                    {:status 400 :scimType :invalidSyntax})))
  (cond
    ;; single patch operation
    (map? op)
    (case (:op op)
      "add"     (op-add schema resource op skip-unknown-schemas)
      "remove"  (op-remove schema resource op skip-unknown-schemas)
      "replace" (op-replace schema resource op skip-unknown-schemas)
      (throw (ex-info (str "Invalid operation") {:status 400
                                                 :scimType :invalidSyntax
                                                 :op (:op op)})))

    ;; sequence of operations
    (sequential? op)
    (reduce #(patch schema %1 %2 :skip-unknown-schemas skip-unknown-schemas) resource op)))

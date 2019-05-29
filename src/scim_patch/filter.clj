(ns scim-patch.filter
  (:require [clojure.string :as s]
            [scim-patch.paths :as paths])
  (:import [com.fasterxml.jackson.core JsonFactory JsonParser]))

(defn has-attr-path?
  [schema attr-path obj]
  (not= ::not-found (paths/traverse schema attr-path obj identity (constantly ::not-found))))

(defn get-schema-and-value
  [schema attr-path obj]
  (paths/traverse schema attr-path obj identity
    #(throw (ex-info "Invalid filter path" {:status   400
                                            :scimType :invalidFilter}))))

(defn scim-compare
  [schema v1 v2 f]
  (let [typ (:type schema)]
    (if (or (= typ :boolean)
          (= typ :binary))
      (throw (ex-info "Invalid data type in filter" {:status   400
                                                     :scimType :invalidFilter}))
      (try
        (f (compare v1 v2))
        (catch ClassCastException _
          false)))))

(defn do-compare
  [schema v1 oper v2]
  (let [v1 (if (number? v1) (bigdec v1) v1)]
    (case (second oper)
      "eq" (= v1 v2)
      "ne" (not= v1 v2)
      "co" (and (string? v1) (string? v2) (s/includes? v1 v2))
      "sw" (and (string? v1) (string? v2) (s/starts-with? v1 v2))
      "ew" (and (string? v1) (string? v2) (s/ends-with? v1 v2))
      "gt" (scim-compare schema v1 v2 pos?)
      "ge" (scim-compare schema v1 v2 #(>= % 0))
      "lt" (scim-compare schema v1 v2 neg?)
      "le" (scim-compare schema v1 v2 #(<= % 0)))))

(def ^JsonFactory json-factory
  (JsonFactory.))

(defn ^JsonParser parse-json
  [^String s]
  (let [jp (.createParser json-factory s)]
    (.nextToken jp)
    jp))

(defn to-value
  [comp-value]
  (let [x (second comp-value)]
    (cond
      (= x "false")         false
      (= x "true")          true
      (= x "null")          nil
      (= (first x) :number) (-> (second x)
                                (parse-json)
                                (.getDecimalValue))
      (= (first x) :string) (-> (second x)
                                (parse-json)
                                (.getText)))))

(defn match-filter-attr-exp?
  [schema attr-exp obj]
  (let [[attr-path oper comp-value] attr-exp]
    (if (= oper [:presenceOp])
      (has-attr-path? schema attr-path obj)
      (let [[schema' value] (get-schema-and-value schema attr-path obj)]
        (do-compare schema' value oper (to-value comp-value))))))

(defn match-filter?
  [schema fltr obj]
  (let [expr (second fltr)]
    (case (first expr)
      :attrExp (match-filter-attr-exp? schema (rest expr) obj)
      :andExp  (and (match-filter? schema (second expr) obj)
                 (match-filter? schema (nth expr 2) obj))
      :orExp   (or (match-filter? schema (second expr) obj)
                 (match-filter? schema (nth expr 2) obj))
      :notExp  (not (match-filter? schema (second expr) obj))
      ;; the default case is a  value filter in parentheses
      (match-filter? schema expr obj))))

(ns scim-patch.paths
  (:require [clojure.java.io :as io]
            [clojure.string :as s]
            [instaparse.core :as p :refer [defparser]]))

(defparser path-parser
  (io/resource "paths.bnf"))

(defn parse
  [path]
  (try
    (let [result (path-parser path)]
      (if (p/failure? result)
        (throw (ex-info (str "Invalid path") {:status      400
                                              :scimType    :invalidPath
                                              :parse-error (p/get-failure
                                                            result)
                                              :path path}))
        result))
    (catch Exception e
      (throw (ex-info (str "Invalid path") {:status 400
                                            :scimType :invalidPath
                                            :path path} e)))))

(defn extract-attr-path
  "Parse attr-path with the following form and extract [uri attr subattr] from it:
  [:attrPath
   [:uri \"urn:ietf:params:scim:schemas:extension:enterprise:2.0:User:\"]
   \"manager\"
   \"displayName\"]"
  [[_ & xs]]
  (let [[uri attr subattr] (case (count xs)
                             3 (cons (second (first xs)) (rest xs))
                             2 (if (= :uri (ffirst xs))
                                 [(second (first xs)) (second xs) nil]
                                 (cons nil xs))
                             1 [nil (first xs) nil])
        ;; remove the tailing ':' char from uri
        uri'               (if (s/blank? uri)
                             uri
                             (subs uri 0 (dec (count uri))))]
    [uri' attr subattr]))

(defn traverse
  [schema attr-path value result-fn not-found-fn]
  (let [attrs (filter some? (extract-attr-path attr-path))]
    (loop [sch   schema
           v     value
           attrs attrs]
      (if (empty? attrs)
        (result-fn [sch v])
        (let [attr-key (keyword (first attrs))
              sch'     (get-in sch [:type :attributes attr-key])
              v'       (get v attr-key ::not-found)]
          (if (or (nil? sch') (= v' ::not-found))
            (not-found-fn)
            (recur sch' v' (rest attrs))))))))

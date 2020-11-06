(ns scim-patch.core-test
  (:require [clojure.test :refer :all]
            [scim-patch.core :as sut])
  (:import [clojure.lang ExceptionInfo]))

(def schema
  {:attributes
   {:userName
    {:type :string}

    :name
    {:type
     {:attributes
      {:formatted
       {:type :string}
       :honorificPrefix
       {:type         :string
        :multi-valued true}}}}

    :phoneNumbers
    {:multi-valued true
     :type
     {:attributes
      {:value
       {:type :string}
       :display
       {:type :string}
       :type
       {:type :string}
       :primary
       {:type :boolean}
       :index
       {:type :integer}}}}

    :x509Certificates
    {:multi-valued true
     :type
     {:attributes
      {:value
       {:type :binary}
       :display
       {:type :string}
       :primary
       {:type :boolean}}}}

    :urn:ietf:params:scim:schemas:extension:enterprise:2.0:User
    {:type
     {:attributes
      {:employeeNumber
       {:type :string}
       :emails
       {:type         :string
        :multi-valued true}
       :manager
       {:type
        {:attributes
         {:displayName
          {:type :string}
          :emails
          {:type         :string
           :multi-valued true}}}}}}}}})

(defmacro get-ex-data
  [body]
  `(try
     ~body
     (catch ExceptionInfo e#
       (ex-data e#))))

(deftest multiple-ops
  (testing "multiple operations in one patch"
    (is (= {:userName "foo"
            :name     {:formatted "bar"}}
          (sut/patch schema {} [{:op    "add"
                                 :path  "userName"
                                 :value "foo"}
                                {:op    "add"
                                 :path  "name.formatted"
                                 :value "bar"}])))))

;;
;; Add operation
;;

(deftest op-add-attr-path-level-1
  (testing "add operation, no filter, single valued, level 1"
    (is (= {:userName "bar"}
          (sut/patch schema {} {:op    "add"
                                :path  "userName"
                                :value "bar"}))))

  (testing "add operation, no filter, multivalued, level 1"
    (is (= {:phoneNumbers [{:value "555-555-5555"
                            :type  "work"}
                           {:value "555-555-4444"
                            :type  "mobile"}]}
          (sut/patch schema {:phoneNumbers [{:value "555-555-5555"
                                             :type  "work"}]}
            {:op    "add"
             :path  "phoneNumbers"
             :value [{:value "555-555-4444"
                      :type  "mobile"}]})))))

(deftest op-add-attr-path-level-2
  (testing "add operation, no filter, single valued, subattribute, level 2"
    (is (= {:name {:formatted "bar"}}
          (sut/patch schema {} {:op    "add"
                                :path  "name.formatted"
                                :value "bar"}))))

  (testing "add operation, no filter, single valued, uri, level 2"
    (is (= {:urn:ietf:params:scim:schemas:extension:enterprise:2.0:User {:employeeNumber "12345"}}
          (sut/patch schema {:urn:ietf:params:scim:schemas:extension:enterprise:2.0:User {}}
            {:op    "add"
             :path  "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User:employeeNumber"
             :value "12345"}))))

  (testing "add operation, no filter, multivalued, subattribute, level 2"
    (is (= {:name {:honorificPrefix ["Mr." "Dr."]}}
          (sut/patch schema {:name {:honorificPrefix ["Mr."]}}
            {:op    "add"
             :path  "name.honorificPrefix"
             :value ["Dr."]}))))

  (testing "add operation, no filter, multivalued, uri, level 2"
    (is (= {:urn:ietf:params:scim:schemas:extension:enterprise:2.0:User {:emails ["test1@example.com" "test2@example.com"]}}
          (sut/patch schema {:urn:ietf:params:scim:schemas:extension:enterprise:2.0:User {:emails ["test1@example.com"]}}
            {:op    "add"
             :path  "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User:emails"
             :value ["test2@example.com"]})))))

(deftest op-add-attr-path-level-3
  (testing "add operation, no filter, single valued, level 3"
    (is (= {:urn:ietf:params:scim:schemas:extension:enterprise:2.0:User {:manager {:displayName "Eddie Brock"}}}
          (sut/patch schema {}
            {:op    "add"
             :path  "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User:manager.displayName"
             :value "Eddie Brock"}))))

  (testing "add operation, no filter, multivalued, level 3"
    (is (= {:urn:ietf:params:scim:schemas:extension:enterprise:2.0:User {:manager {:emails ["test1@example.com" "test2@example.com"]}}}
          (sut/patch schema {}
            {:op    "add"
             :path  "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User:manager.emails"
             :value ["test1@example.com" "test2@example.com"]})))))

(deftest op-add-extension
  (testing "add operation, no filter, extension"
    (let [resource {:userName "foo"
                    :urn:ietf:params:scim:schemas:extension:enterprise:2.0:User {:employeeNumber "12345"}
                    :schemas ["urn:ietf:params:scim:schemas:core:2.0:User"
                              "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User"]}]
      (is (= resource
            (sut/patch schema (dissoc resource :urn:ietf:params:scim:schemas:extension:enterprise:2.0:User)
              {:op    "add"
               :path  "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User"
               :value (:urn:ietf:params:scim:schemas:extension:enterprise:2.0:User resource)}))))))

(deftest op-add-no-path
  (testing "add operation, no path"
    (is (= {:userName "foo"
            :name     {:formatted "bar"}}
          (sut/patch schema {} {:op    "add"
                                :value {:userName "foo"
                                        :name     {:formatted "bar"}}})))))

(deftest op-add-no-path-multivalued
  (testing "add operation, no path, multivalued"
    (is (= {:phoneNumbers [{:value "555-555-5555" :type  "work"}
                           {:value "555-555-4444" :type  "mobile"}]}
           (sut/patch schema
                      {:phoneNumbers [{:value "555-555-5555" :type  "work"}]}
                      {:op "add"
                       :value {:phoneNumbers [{:value "555-555-4444" :type  "mobile"}]}})))))

(deftest op-add-no-path-nested
  (testing "add operation, no path, nested"
    (is (= {:name {:honorificPrefix ["Mr." "Dr."]}}
           (sut/patch schema
                      {:name {:honorificPrefix ["Mr."]}}
                      {:op "add"
                       :value {:name {:honorificPrefix ["Dr."]}}})))))

(deftest ops-add-replace-missing-keys
  (testing "add operation, no path or value"
    (are [x] (= (get-ex-data (sut/patch schema {} x)) {:status 400 :scimType :invalidSyntax})
      {:op "add"}
      {:op "add" :somePath "value"}
      {:op "add" :path "value"}
      {:op "replace"}
      {:op "replace" :something "asdf"})))

(deftest op-add-nonexisting-target-location
  (testing "add operation: If the target location does not exist, the attribute and value are added"
    (is (= {:userName "foo"
            :name     {:formatted "bar"}}
          (sut/patch schema {:userName "foo"} {:op    "add"
                                               :path  "name"
                                               :value {:formatted "bar"}})))))

(deftest op-add-attrpath-filter
  (testing "add operation: simple attrpath filter"
    (is (= {:phoneNumbers [{:type "Work" :value "1112223333"}
                           {:type "Home" :value "3334445555"}]}
          (sut/patch schema {:phoneNumbers [{:type "Work" :value "1112223333"}
                                            {:type "Home" :value "2223334444"}]}
            {:op    "add"
             :path  "phoneNumbers[type eq \"Home\"]"
             :value {:type "Home" :value "3334445555"}}))))

  (testing "add operation: bad attr path"
    (is (= {:status 400 :scimType :invalidPath :path "telephoneNumbers"}
          (get-ex-data
            (sut/patch schema {:phoneNumbers [{:type "Work" :value "1112223333"}]}
              {:op    "add"
               :path  "telephoneNumbers"
               :value [{:type "Home" :value "3334445555"}]})))))

  (testing "add operation: bad filter path"
    (is (= {:status 400 :scimType :invalidFilter :path "phoneNumbers[number eq 1]"}
          (get-ex-data
            (sut/patch schema {:phoneNumbers [{:type "Work" :value "1112223333"}]}
              {:op    "add"
               :path  "phoneNumbers[number eq 1]"
               :value [{:type "Home" :value "3334445555"}]})))))

  (testing "add operation: filter on scalar attribute"
    (is (= {:status 400 :scimType :invalidFilter :path "userName[number eq 1]"}
          (get-ex-data
            (sut/patch schema {:userName "foo"}
              {:op    "add"
               :path  "userName[number eq 1]"
               :value "bar"})))))

  (testing "add operation: attrpath filter with subattr"
    (is (= {:phoneNumbers [{:type "Work" :value "1112223333"}
                           {:type "Home" :value "3334445555" :display "333-444-5555"}]}
          (sut/patch schema {:phoneNumbers [{:type "Work" :value "1112223333"}
                                            {:type "Home" :value "3334445555"}]}
            {:op    "add"
             :path  "phoneNumbers[type eq \"Home\"].display"
             :value "333-444-5555"}))))

  (testing "add operation: attrpath filter with bad subattr"
    (is (= {:status 400 :scimType :invalidPath :path "phoneNumbers[type eq \"Work\"].display1"}
          (get-ex-data
            (sut/patch schema {:phoneNumbers [{:type "Work" :value "1112223333"}]}
              {:op    "add"
               :path  "phoneNumbers[type eq \"Work\"].display1"
               :value "333-444-5555"}))))))

(deftest op-add-attrpath-filter-operators
  (testing "add operation: pr operator"
    (is (= {:phoneNumbers [{:type "Cell" :value "3334445555" :display "333-444-5555"}
                           {:type "Home" :value "2223334444"}]}
          (sut/patch schema {:phoneNumbers [{:type "Work" :value "1112223333" :display "111-222-3333"}
                                            {:type "Home" :value "2223334444"}]}
            {:op    "add"
             :path  "phoneNumbers[display pr]"
             :value {:type "Cell" :value "3334445555" :display "333-444-5555"}}))))

  (testing "add operation: ne operator"
    (is (= {:phoneNumbers [{:type "Work" :value "3334445555"}
                           {:type "Home" :value "2223334444"}]}
          (sut/patch schema {:phoneNumbers [{:type "Work" :value "1112223333"}
                                             {:type "Home" :value "2223334444"}]}
            {:op    "add"
             :path  "phoneNumbers[type ne \"Home\"]"
             :value {:type "Work" :value "3334445555"}}))))

  (testing "add operation: co operator"
    (is (= {:phoneNumbers [{:type "Work" :value "3334445555"}
                           {:type "Home" :value "2223334444"}]}
          (sut/patch schema {:phoneNumbers [{:type "Work" :value "1112223333"}
                                             {:type "Home" :value "2223334444"}]}
            {:op    "add"
             :path  "phoneNumbers[type co \"or\"]"
             :value {:type "Work" :value "3334445555"}}))))

  (testing "add operation: sw operator"
    (is (= {:phoneNumbers [{:type "Work" :value "3334445555"}
                           {:type "Home" :value "2223334444"}]}
          (sut/patch schema {:phoneNumbers [{:type "Work" :value "1112223333"}
                                            {:type "Home" :value "2223334444"}]}
            {:op    "add"
             :path  "phoneNumbers[type sw \"Wo\"]"
             :value {:type "Work" :value "3334445555"}}))))

  (testing "add operation: ew operator"
    (is (= {:phoneNumbers [{:type "Work" :value "3334445555"}
                           {:type "Home" :value "2223334444"}]}
          (sut/patch schema {:phoneNumbers [{:type "Work" :value "1112223333"}
                                            {:type "Home" :value "2223334444"}]}
            {:op    "add"
             :path  "phoneNumbers[type ew \"rk\"]"
             :value {:type "Work" :value "3334445555"}}))))

  (testing "add operation: string operation on non-string value"
    (is (= {:status 400 :scimType :invalidFilter :path "x509Certificates[primary co \"true\"]"}
          (get-ex-data
            (sut/patch schema {:x509Certificates [{:primary true}]}
              {:op   "add"
               :path "x509Certificates[primary co \"true\"]"
               :value ""})))))

  (testing "add operation: gt operator"
    (is (= {:phoneNumbers [{:index 1} {:index 2 :display "111-222-3333"}]}
          (sut/patch schema {:phoneNumbers [{:index 1} {:index 2}]}
            {:op    "add"
             :path  "phoneNumbers[index gt 1].display"
             :value "111-222-3333"}))))

  (testing "add operation: ge operator"
    (is (= {:phoneNumbers [{:index 0}
                           {:index 1 :display "111-222-3333"}
                           {:index 2 :display "111-222-3333"}]}
          (sut/patch schema {:phoneNumbers [{:index 0} {:index 1} {:index 2}]}
            {:op    "add"
             :path  "phoneNumbers[index ge 1].display"
             :value "111-222-3333"}))))

  (testing "add operation: lt operator"
    (is (= {:phoneNumbers [{:index 1 :display "111-222-3333"} {:index 2}]}
          (sut/patch schema {:phoneNumbers [{:index 1} {:index 2}]}
            {:op    "add"
             :path  "phoneNumbers[index lt 2].display"
             :value "111-222-3333"}))))

  (testing "add operation: gt operator"
    (is (= {:phoneNumbers [{:index 1 :display "111-222-3333"}
                           {:index 2 :display "111-222-3333"}
                           {:index 3}]}
          (sut/patch schema {:phoneNumbers [{:index 1} {:index 2} {:index 3}]}
            {:op    "add"
             :path  "phoneNumbers[index le 2].display"
             :value "111-222-3333"})))))


;;
;; Remove operation
;;

(deftest op-remove-missing-path
  (testing "remove operation: missing path"
    (is (= {:status 400 :scimType :noTarget}
          (get-ex-data
            (sut/patch schema {} {:op "remove"})))))

  (testing "remove operation: blank path"
    (is (= {:status 400 :scimType :noTarget}
          (get-ex-data
            (sut/patch schema {} {:op "remove" :path "     "}))))))

(deftest op-remove-single-valued-attribute
  (testing "remove operation: single valued attribute, level 1"
    (is (= {:name {:formatted "bar"}}
          (sut/patch schema {:userName "foo"
                             :name     {:formatted "bar"}}
            {:op   "remove"
             :path "userName"}))))

  (testing "remove operation: single valued attribute, level 2"
    (is (= {:userName "foo" :name {}}
          (sut/patch schema {:userName "foo"
                             :name     {:formatted "bar"}}
            {:op   "remove"
             :path "name.formatted"}))))

  (testing "remove operation: single valued attribute, level 3"
    (is (= {:userName "foo" :urn:ietf:params:scim:schemas:extension:enterprise:2.0:User {:manager {}}}
          (sut/patch schema {:userName "foo"
                             :urn:ietf:params:scim:schemas:extension:enterprise:2.0:User {:manager {:displayName "Eddie Brock"}}}
            {:op   "remove"
             :path "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User:manager.displayName"})))))

(deftest op-remove-extension
  (testing "remove operation: extension"
    (let [resource {:userName "foo"
                    :urn:ietf:params:scim:schemas:extension:enterprise:2.0:User {:employeeNumber "12345"}
                    :schemas ["urn:ietf:params:scim:schemas:core:2.0:User"
                              "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User"]}]
      (is (= (dissoc resource :urn:ietf:params:scim:schemas:extension:enterprise:2.0:User)
             (sut/patch schema resource
               {:op   "remove"
                :path "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User"}))))))

(deftest op-remove-multi-valued-no-filter
  (testing "remove operation: multi valued attribute, level 1"
    (is (= {:userName "foo"}
          (sut/patch schema {:userName     "foo"
                             :phoneNumbers [{:value "555-555-5555"
                                             :type  "work"}
                                            {:value "555-555-4444"
                                             :type  "mobile"}]}
            {:op    "remove"
             :path  "phoneNumbers"}))))

  (testing "remove operation: multi valued attribute, level 2"
    (is (= {:userName "foo" :name {}}
          (sut/patch schema {:userName "foo"
                             :name {:honorificPrefix ["Mr." "Dr."]}}
            {:op    "remove"
             :path  "name.honorificPrefix"}))))

  (testing "remove operation: multi valued attribute, level 3"
    (is (= {:userName "foo" :urn:ietf:params:scim:schemas:extension:enterprise:2.0:User {:manager {}}}
          (sut/patch schema {:userName "foo"
                             :urn:ietf:params:scim:schemas:extension:enterprise:2.0:User {:manager {:emails ["test1@example.com" "test2@example.com"]}}}
            {:op    "remove"
             :path  "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User:manager.emails"})))))

(deftest op-remove-multi-valued-with-filter

  (testing "remove operation: multi valued attribute, value filter"
    (is (= {:userName     "foo"
            :phoneNumbers [{:value "555-555-4444"
                            :type  "mobile"}]}
          (sut/patch schema {:userName     "foo"
                             :phoneNumbers [{:value "555-555-5555"
                                             :type  "work"}
                                            {:value "555-555-4444"
                                             :type  "mobile"}]}
            {:op   "remove"
             :path "phoneNumbers[type eq \"work\"]"}))))

  (testing "remove operation: multi valued attribute, value filter, complex conditions"
    (is (= {:userName     "foo"
            :phoneNumbers [{:value "555-555-4444"
                            :type  "mobile"}]}
          (sut/patch schema {:userName     "foo"
                             :phoneNumbers [{:value "555-555-5555"
                                             :type  "work"}
                                            {:value "555-555-4444"
                                             :type  "mobile"}
                                            {:value "111-222-3333"
                                             :type  "other"}]}
            {:op   "remove"
             :path "phoneNumbers[type eq \"work\" or not (value ew \"444\") and (value pr)]"}))))

  (testing "remove operation: multi valued attribute, value filter, all values removed"
    (is (= {:userName "foo"}
          (sut/patch schema {:userName     "foo"
                             :phoneNumbers [{:value "555-555-5555"
                                             :type  "work"}
                                            {:value "555-555-4444"
                                             :type  "mobile"}
                                            {:value "111-555-3333"
                                             :type  "other"}]}
            {:op   "remove"
             :path "phoneNumbers[value co \"-555-\"]"}))))

  (testing "remove operation: filter on scalar attribute"
    (is (= {:status 400 :scimType :invalidFilter :path "userName[number eq 1]"}
          (get-ex-data
            (sut/patch schema {:userName "foo"}
              {:op    "remove"
               :path  "userName[number eq 1]"})))))

  (testing "remove operation: attrpath filter with subattr"
    (is (= {:phoneNumbers [{:type "Work" :value "1112223333"}
                           {:type "Home" :value "3334445555" :display "333-444-5555"}]}
          (sut/patch schema {:phoneNumbers [{:type "Work" :value "1112223333" :display "111-222-3333"}
                                            {:type "Home" :value "3334445555" :display "333-444-5555"}]}
            {:op    "remove"
             :path  "phoneNumbers[type eq \"Work\"].display"}))))

  (testing "remove operation: attrpath filter with bad subattr"
    (is (= {:status 400 :scimType :invalidPath :path "phoneNumbers[type eq \"Work\"].display1"}
          (get-ex-data
            (sut/patch schema {:phoneNumbers [{:type "Work" :value "1112223333"}]}
              {:op    "remove"
               :path  "phoneNumbers[type eq \"Work\"].display1"}))))))

;;
;; Replace operation
;;

(deftest op-replace-attr-path-level-1
  (testing "replace operation, no filter, single valued, level 1"
    (is (= {:userName "bar"}
          (sut/patch schema {:userName "foo"} {:op    "replace"
                                               :path  "userName"
                                               :value "bar"}))))

  (testing "replace operation, no filter, multivalued, level 1"
    (is (= {:phoneNumbers [{:value "555-555-5555"
                            :type  "work"}
                           {:value "555-555-4444"
                            :type  "mobile"}]}
          (sut/patch schema {:phoneNumbers [{:value "111-222-3333"
                                             :type  "other"}]}
            {:op    "replace"
             :path  "phoneNumbers"
             :value [{:value "555-555-5555"
                      :type  "work"}
                     {:value "555-555-4444"
                      :type  "mobile"}]})))))

(deftest op-replace-attr-path-level-2
  (testing "replace operation, no filter, single valued, subattribute, level 2"
    (is (= {:name {:formatted "bar"}}
          (sut/patch schema {:name {:formatted "foo"}}
            {:op    "replace"
             :path  "name.formatted"
             :value "bar"}))))

  (testing "replace operation, no filter, single valued, uri, level 2"
    (is (= {:urn:ietf:params:scim:schemas:extension:enterprise:2.0:User {:employeeNumber "12345"}}
          (sut/patch schema {:urn:ietf:params:scim:schemas:extension:enterprise:2.0:User {:employeeNumber "99999"}}
            {:op    "replace"
             :path  "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User:employeeNumber"
             :value "12345"}))))

  (testing "replace operation, no filter, multi-valued, subattribute, level 2"
    (is (= {:name {:honorificPrefix ["Mr." "Dr."]}}
          (sut/patch schema {:name {:honorificPrefix ["Ms."]}}
            {:op    "replace"
             :path  "name.honorificPrefix"
             :value ["Mr." "Dr."]}))))

  (testing "replace operation, no filter, multi-valued, uri, level 2"
    (is (= {:urn:ietf:params:scim:schemas:extension:enterprise:2.0:User {:emails ["test1@example.com" "test2@example.com"]}}
          (sut/patch schema {:urn:ietf:params:scim:schemas:extension:enterprise:2.0:User {:emails ["test3@example.com"]}}
            {:op    "replace"
             :path  "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User:emails"
             :value ["test1@example.com" "test2@example.com"]})))))

(deftest op-replace-attr-path-level-3
  (testing "replace operation, no filter, single valued, level 3"
    (is (= {:urn:ietf:params:scim:schemas:extension:enterprise:2.0:User {:manager {:displayName "Eddie Brock"}}}
          (sut/patch schema {:urn:ietf:params:scim:schemas:extension:enterprise:2.0:User {:manager {:displayName "Peter Parker"}}}
            {:op    "replace"
             :path  "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User:manager.displayName"
             :value "Eddie Brock"}))))

  (testing "replace operation, no filter, multi-valued, level 3"
    (is (= {:urn:ietf:params:scim:schemas:extension:enterprise:2.0:User {:manager {:emails ["test1@example.com" "test2@example.com"]}}}
          (sut/patch schema {:urn:ietf:params:scim:schemas:extension:enterprise:2.0:User {:manager {:emails ["test3@example.com"]}}}
            {:op    "replace"
             :path  "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User:manager.emails"
             :value ["test1@example.com" "test2@example.com"]})))))

(deftest op-replace-extension
  (testing "replace operation, no filter, extension"
    (let [resource {:userName "foo"
                    :urn:ietf:params:scim:schemas:extension:enterprise:2.0:User {:employeeNumber "12345"}
                    :schemas ["urn:ietf:params:scim:schemas:core:2.0:User"
                              "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User"]}]
      (is (= (assoc resource :urn:ietf:params:scim:schemas:extension:enterprise:2.0:User {:employeeNumber "99999"})
             (sut/patch schema resource
               {:op    "replace"
                :path  "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User"
                :value {:employeeNumber "99999"}}))))))

(deftest op-replace-no-path
  (testing "replace operation, no path"
    (is (= {:userName     "foo"
            :name         {:formatted "bar"}
            :phoneNumbers [{:value "555-555-5555"
                            :type  "work"}
                           {:value "555-555-4444"
                            :type  "mobile"}]}
          (sut/patch schema {:userName     "baz"
                             :name         {:formatted "qux" :honorificPrefix "Mr."}
                             :phoneNumbers [{:value "555-555-5555"
                                             :type  "work"}
                                            {:value "555-555-4444"
                                             :type  "mobile"}]}
            {:op    "replace"
             :value {:userName "foo"
                     :name     {:formatted "bar"}}})))))

(deftest op-replace-nonexisting-target-location
  (testing "replace operation: If the target location does not exist, the attribute and value are added"
    (is (= {:userName "foo"
            :name     {:formatted "bar"}}
          (sut/patch schema {:userName "foo"}
            {:op    "replace"
             :path  "name"
             :value {:formatted "bar"}})))))

(deftest op-replace-attrpath-filter
  (testing "replace operation: simple attrpath filter"
    (is (= {:phoneNumbers [{:type "Work" :value "1112223333"}
                           {:type "Home" :value "3334445555"}]}
          (sut/patch schema {:phoneNumbers [{:type "Work" :value "1112223333"}
                                            {:type "Home" :value "2223334444"}]}
            {:op    "replace"
             :path  "phoneNumbers[type eq \"Home\"]"
             :value {:type "Home" :value "3334445555"}}))))

  (testing "replace operation: bad attr path"
    (is (= {:status 400 :scimType :invalidPath :path "telephoneNumbers"}
          (get-ex-data
            (sut/patch schema {:phoneNumbers [{:type "Work" :value "1112223333"}]}
              {:op    "replace"
               :path  "telephoneNumbers"
               :value [{:type "Home" :value "3334445555"}]})))))

  (testing "replace operation: bad filter path"
    (is (= {:status 400 :scimType :invalidFilter :path "phoneNumbers[number eq 1]"}
          (get-ex-data
            (sut/patch schema {:phoneNumbers [{:type "Work" :value "1112223333"}]}
              {:op    "replace"
               :path  "phoneNumbers[number eq 1]"
               :value [{:type "Home" :value "3334445555"}]})))))

  (testing "replace operation: filter on scalar attribute"
    (is (= {:status 400 :scimType :invalidFilter :path "userName[number eq 1]"}
          (get-ex-data
            (sut/patch schema {:userName "foo"}
              {:op    "replace"
               :path  "userName[number eq 1]"
               :value "bar"})))))

  (testing "replace operation: attrpath filter with subattr"
    (is (= {:phoneNumbers [{:type "Work" :value "1112223333"}
                           {:type "Home" :value "3334445555" :display "333-444-5555"}]}
          (sut/patch schema {:phoneNumbers [{:type "Work" :value "1112223333"}
                                            {:type "Home" :value "3334445555" :display "3334445555"}]}
            {:op    "replace"
             :path  "phoneNumbers[type eq \"Home\"].display"
             :value "333-444-5555"}))))

  (testing "replace operation: attrpath filter with bad subattr"
    (is (= {:status 400 :scimType :invalidPath :path "phoneNumbers[type eq \"Work\"].display1"}
          (get-ex-data
            (sut/patch schema {:phoneNumbers [{:type "Work" :value "1112223333"}]}
              {:op    "replace"
               :path  "phoneNumbers[type eq \"Work\"].display1"
               :value "333-444-5555"})))))

  (testing "replace operation: filter with no matches"
    (is (= {:status 400 :scimType :noTarget :path "phoneNumbers[type eq \"Work\"]"}
          (get-ex-data
            (sut/patch schema {}
              {:op    "replace"
               :path  "phoneNumbers[type eq \"Work\"]"
               :value {}}))))))

(deftest op-replace-attrpath-filter-operators
  (testing "replace operation: pr operator"
    (is (= {:phoneNumbers [{:type "Cell" :value "3334445555" :display "333-444-5555"}
                           {:type "Home" :value "2223334444"}]}
          (sut/patch schema {:phoneNumbers [{:type "Work" :value "1112223333" :display "111-222-3333"}
                                            {:type "Home" :value "2223334444"}]}
            {:op    "replace"
             :path  "phoneNumbers[display pr]"
             :value {:type "Cell" :value "3334445555" :display "333-444-5555"}}))))

  (testing "replace operation: ne operator"
    (is (= {:phoneNumbers [{:type "Work" :value "3334445555"}
                           {:type "Home" :value "2223334444"}]}
          (sut/patch schema {:phoneNumbers [{:type "Work" :value "1112223333"}
                                            {:type "Home" :value "2223334444"}]}
            {:op    "replace"
             :path  "phoneNumbers[type ne \"Home\"]"
             :value {:type "Work" :value "3334445555"}}))))

  (testing "replace operation: co operator"
    (is (= {:phoneNumbers [{:type "Work" :value "3334445555"}
                           {:type "Home" :value "2223334444"}]}
           (sut/patch schema {:phoneNumbers [{:type "Work" :value "1112223333"}
                                             {:type "Home" :value "2223334444"}]}
                      {:op    "replace"
                       :path  "phoneNumbers[type co \"or\"]"
                       :value {:type "Work" :value "3334445555"}}))))

  (testing "replace operation: sw operator"
    (is (= {:phoneNumbers [{:type "Work" :value "3334445555"}
                           {:type "Home" :value "2223334444"}]}
          (sut/patch schema {:phoneNumbers [{:type "Work" :value "1112223333"}
                                            {:type "Home" :value "2223334444"}]}
            {:op    "replace"
             :path  "phoneNumbers[type sw \"Wo\"]"
             :value {:type "Work" :value "3334445555"}}))))

  (testing "replace operation: ew operator"
    (is (= {:phoneNumbers [{:type "Work" :value "3334445555"}
                           {:type "Home" :value "2223334444"}]}
          (sut/patch schema {:phoneNumbers [{:type "Work" :value "1112223333"}
                                            {:type "Home" :value "2223334444"}]}
            {:op    "replace"
             :path  "phoneNumbers[type ew \"rk\"]"
             :value {:type "Work" :value "3334445555"}}))))

  (testing "replace operation: string operation on non-string value"
    (is (= {:status 400 :scimType :invalidFilter :path "x509Certificates[primary co \"true\"]"}
          (get-ex-data
            (sut/patch schema {:x509Certificates [{:primary true}]}
              {:op   "replace"
               :path "x509Certificates[primary co \"true\"]"})))))

  (testing "replace operation: gt operator"
    (is (= {:phoneNumbers [{:index 1} {:index 2 :display "111-222-3333"}]}
          (sut/patch schema {:phoneNumbers [{:index 1} {:index 2 :display "1112223333"}]}
            {:op    "replace"
             :path  "phoneNumbers[index gt 1].display"
             :value "111-222-3333"}))))

  (testing "replace operation: ge operator"
    (is (= {:phoneNumbers [{:index 0}
                           {:index 1 :display "111-222-3333"}
                           {:index 2 :display "111-222-3333"}]}
          (sut/patch schema {:phoneNumbers [{:index 0}
                                            {:index 1 :display "1112223333"}
                                            {:index 2 :display "1112223333"}]}
            {:op    "replace"
             :path  "phoneNumbers[index ge 1].display"
             :value "111-222-3333"}))))

  (testing "replace operation: lt operator"
    (is (= {:phoneNumbers [{:index 1 :display "111-222-3333"} {:index 2}]}
          (sut/patch schema {:phoneNumbers [{:index 1 :display "1112223333"}
                                            {:index 2}]}
            {:op    "replace"
             :path  "phoneNumbers[index lt 2].display"
             :value "111-222-3333"}))))

  (testing "replace operation: gt operator"
    (is (= {:phoneNumbers [{:index 1 :display "111-222-3333"}
                           {:index 2 :display "111-222-3333"}
                           {:index 3}]}
          (sut/patch schema {:phoneNumbers [{:index 1 :display "1112223333"}
                                            {:index 2 :display "1112223333"}
                                            {:index 3}]}
            {:op    "replace"
             :path  "phoneNumbers[index le 2].display"
             :value "111-222-3333"})))))


;;
;; negative test cases
;;

(deftest invalid-operation
  (testing "unknown operation"
    (is (= {:status 400 :scimType :invalidSyntax :op "blah"}
          (get-ex-data
            (sut/patch schema {} {:op "blah"}))))))

(deftest filter-parse-failure
  (testing "syntax error in value filter"
    (is (= {:status 400 :scimType :invalidPath :path "phoneNumbers[type or value]"}
          (get-ex-data
            (sut/patch schema {} {:op "add" :path "phoneNumbers[type or value]" :value ""}))))))

(deftest multi-valued-attr-in-attr-path
  (testing "multi-valued attribute in attr path"
    (is (= {:status 400 :scimType :invalidPath :path "phoneNumbers.type"}
          (get-ex-data
            (sut/patch schema {} {:op "remove" :path "phoneNumbers.type"}))))))

(deftest scalar-value-for-multi-valued-attr
  (testing "add operation: scalar value for multi-valued attribute"
    (is (= {:status 400 :scimType :invalidValue :path "phoneNumbers"}
          (get-ex-data
            (sut/patch schema {} {:op    "add"
                                  :path  "phoneNumbers"
                                  :value "blah"}))))
    (is (= {:status 400 :scimType :invalidValue :path "phoneNumbers"}
          (get-ex-data
            (sut/patch schema {} {:op    "replace"
                                  :path  "phoneNumbers"
                                  :value "blah"}))))))

(deftest filter-unsupported-comparisons
  (testing "unsupported compare operations"
    (is (= {:status 400 :scimType :invalidFilter :path "x509Certificates[value gt \"foo\"]"}
          (get-ex-data
            (sut/patch schema {:x509Certificates [{:value "foo" :display "bar"}]}
              {:op   "remove"
               :path "x509Certificates[value gt \"foo\"]"}))))
    (is (= {:status 400 :scimType :invalidFilter :path "x509Certificates[primary gt false]"}
          (get-ex-data
            (sut/patch schema {:x509Certificates [{:value "foo" :primary true}]}
              {:op   "remove"
               :path "x509Certificates[primary gt false]"}))))))


;;
;; schema filtering
;;

(deftest schema-filtering
  (let [user    {:userName "foo"
                 :name     {:formatted "bar"}}
        patch   [{:op    "replace"
                  :path  "userName"
                  :value "bar"}
                 {:op    "replace"
                  :path  "urn:ietf:params:scim:schemas:core:2.0:Group:displayName"
                  :value "Administrators"}]
        schema' (assoc schema
                  :id "urn:ietf:params:scim:schemas:core:2.0:User"
                  :schemas ["urn:ietf:params:scim:schemas:core:2.0:User"
                            "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User"])]
    (testing "throws an exception if unknown schema is used"
      (is (= {:status 400 :scimType :invalidPath
              :path "urn:ietf:params:scim:schemas:core:2.0:Group:displayName"}
             (get-ex-data (sut/patch schema user patch)))))
    (testing "throws an exception if told to filter but not given a filter list"
      (is (= {:status 400 :scimType :invalidSyntax}
             (get-ex-data (sut/patch schema user patch :skip-unknown-schemas true)))))
    (testing "ignores unknown schema if there is a schema filter"
      (is (= (assoc user :userName "bar")
             (sut/patch schema' user patch :skip-unknown-schemas true))))))

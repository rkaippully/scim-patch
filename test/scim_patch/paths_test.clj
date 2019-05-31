(ns scim-patch.paths-test
  (:require [clojure.test :refer :all]
            [scim-patch.paths :as sut]))

(deftest test-parse-simple-attribute
  (testing "parse-simple-attribute"
    (is (= (sut/path-parser "userName")
          [:path [:attrPath "userName"]]))))

(deftest test-parse-complex-attribute
  (testing "parse-complex-attribute"
    (is (= (sut/path-parser "name.familyName")
          [:path
           [:attrPath "name" "familyName"]]))))

(deftest test-parse-extension-simple-attribute
  (testing "parse-extension-simple-attribute"
    (is (= (sut/path-parser "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User:employeeNumber")
          [:path
           [:attrPath
            [:uri "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User:"]
            "employeeNumber"]]))))

(deftest test-parse-extension-complex-attribute
  (testing "parse-extension-complex-attribute"
    (is (= (sut/path-parser "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User:manager.displayName")
          [:path
           [:attrPath
            [:uri "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User:"]
            "manager"
            "displayName"]]))))

(deftest test-parse-value-filter
  (testing "parse-value-filter"
    (is (= (sut/path-parser "addresses[type eq \"work\"]")
          [:path
           [:valuePath
            [:attrPath "addresses"]
            [:valFilter
             [:attrExp
              [:attrPath "type"]
              [:compareOp "eq"]
              [:compValue [:string "\"work\""]]]]]]))))

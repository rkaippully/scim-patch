[![License: MPL 2.0](https://img.shields.io/badge/License-MPL%202.0-brightgreen.svg)](LICENSE)
[![Build Status](https://travis-ci.org/rkaippully/scim-patch.svg?branch=master)](https://travis-ci.org/rkaippully/scim-patch)
[![codecov](https://codecov.io/gh/rkaippully/scim-patch/branch/master/graph/badge.svg)](https://codecov.io/gh/rkaippully/scim-patch)
[![Clojars Project](https://img.shields.io/clojars/v/org.clojars.rkaippully/scim-patch.svg)](https://clojars.org/org.clojars.rkaippully/scim-patch)

```clj
[org.clojars.rkaippully/scim-patch "0.0.0"]
```

A Clojure library that implements patch operations as specified by [SCIM RFC](https://tools.ietf.org/html/rfc7644#section-3.5.2).

## Usage

It is straightforward to patch a SCIM resource given its schema and patch operations.

First you need to define the schema like in the example below. Refer [RFC7643](https://tools.ietf.org/html/rfc7643) for more
information about the SCIM schema.

``` clj
(def schema {:attributes
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
                 {:type :boolean}}}}

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
                 :manager
                 {:type
                  {:attributes
                   {:displayName
                    {:type :string}}}}}}}}})
```

Once you have such a schema, you can perform patch operations as shown below:

``` clj
user=> (require '[scim-patch.core :as p])
user=> (def resource {:userName "foo"
                      :phoneNumbers [{:type  "work"
                                      :value "555-555-1111"}
                                     {:type  "home"
                                      :value "555-555-2222"}]})

user=> (p/patch schema resource {:op    "replace"
                                 :path  "userName"
                                 :value "bar"})
{:userName "bar",
 :phoneNumbers
 [{:type "work", :value "555-555-1111"}
  {:type "home", :value "555-555-2222"}]}
```

You can also pass multiple operations in a single invocation of `patch` function:

``` clj
user=> (p/patch schema resource [{:op    "replace"
                                  :path  "userName"
                                  :value "bar"}
                                 {:op    "add"
                                  :path  "phoneNumbers[type eq \"work\"].display"
                                  :value "+1-555-555-1111"}])
{:userName "bar",
 :phoneNumbers
 ({:type "work", :value "555-555-1111", :display "+1-555-555-1111"}
  {:type "home", :value "555-555-2222"})}
```

Errors are reported as exceptions generated via `ex-info`.

``` clj
user=> (try
         (p/patch schema resource {:op    "replace"
                                   :path  "userNames"
                                   :value "bar"})
         (catch Exception e
           (ex-data e)))
{:status 400, :scimType :invalidPath}
```

## License

Copyright © 2019 Raghu Kaippully <rkaippully@gmail.com> 

Distributed under the Mozilla Public License version 2.0.

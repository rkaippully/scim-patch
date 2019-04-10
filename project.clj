(defproject org.clojars.rkaippully/clj-annotations "0.0.0"
  :description "A Clojure library that implements patch operations as specified by RFC7644"
  :url "https://github.com/rkaippully/scim-patch"
  :license {:name "Mozilla Public License v2.0"
            :url  "https://www.mozilla.org/en-US/MPL/2.0/"}
  :dependencies []
  :plugins [[lein-cloverage "1.0.13"]
            [lein-shell "0.5.0"]
            [lein-ancient "0.6.15"]
            [lein-changelog "0.3.2"]
            [lein-codox "0.10.6"]
            [lein-kibit "0.1.6"]]
  :profiles {:dev   [:clj10]
             :clj08 {:dependencies [[org.clojure/clojure "1.8.0"]]}
             :clj09 {:dependencies [[org.clojure/clojure "1.9.0"]]}
             :clj10 {:dependencies [[org.clojure/clojure "1.10.0"]]}}
  :deploy-repositories [["releases" :clojars]]
  :aliases {"update-readme-version" ["shell" "sed" "-i" "" "s/org.clojars.rkaippully\\\\/scim-patch \"[0-9.]*\"/org.clojars.rkaippully\\\\/scim-patch \"${:version}\"/" "README.md"]}
  :release-tasks [["kibit"]
                  ["vcs" "assert-committed"]
                  ["shell" "git" "diff" "--exit-code"]
                  ["change" "version" "leiningen.release/bump-version"]
                  ["change" "version" "leiningen.release/bump-version" "release"]
                  ["changelog" "release"]
                  ["update-readme-version"]
                  ["vcs" "commit"]
                  ["vcs" "tag"]
                  ["deploy"]
                  ["vcs" "push"]])

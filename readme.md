# Clojure Document Extractor
Proof of concept of a tool to extract namespace/functions documentation from Clojure projects.

## How to use
Given a list of projects and it's versions, this tool will download the jars from maven
using `tools.deps` and extract all the analysis data with `clj-kondo`.

```clojure
(extract-all! [{:library 'org.clojure/clojure
                :git {:git/url "https://github.com/clojure/clojure"
                      :git/tag "clojure-1.11.1"
                      :git/sha "ce55092f2b2f5481d25cff6205470c1335760ef6"}}
               {:library 'org.clojure/clojurescript
                :git {:git/url "https://github.com/clojure/clojurescript"
                      :git/tag "r1.11.60"
                      :git/sha "e7cdc70d0371a26e07e394ea9cd72d5c43e5e363"}}
               {:library 'lilactown/helix
                :git {:git/url "https://github.com/lilactown/helix"
                      :git/tag "0.1.10"
                      :git/sha "cc88c8ccfd73fa8e4ac803dd2dcf9115ac943a89"}}]
              :edn)
```

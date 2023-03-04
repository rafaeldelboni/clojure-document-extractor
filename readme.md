# Clojure Document Extractor
Proof of concept of a tool to extract namespace/functions documentation from Clojure projects.

## How to use
Given a list of projects and it's versions this download the jars from maven
using `tools.deps` and extract data with `clj-kondo` to the documentation.

```clojure
(extract-all! [{:project 'org.clojure/clojure
                :version "1.11.1"}
               {:project 'org.clojure/clojurescript
                :version "1.11.60"}])
```

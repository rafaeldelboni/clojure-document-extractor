(ns extractor
  (:require [cheshire.core :as cheshire]
            [clj-kondo.core :as kondo]
            [clojure.edn :as edn]
            [clojure.java.io :as io :refer [make-parents]]
            [clojure.tools.deps :as deps]))

(defn get-jar
  [project version]
  (-> (deps/resolve-deps
       {:deps {project {:mvn/version version}}
        :mvn/repos {"central" {:url "https://repo1.maven.org/maven2/"},
                    "clojars" {:url "https://repo.clojars.org/"}}}
       nil)
      (get project)
      :paths
      first))

(defn kondo-run!
  [paths]
  (-> {:lint paths
       :config {:output {:format :edn}
                :analysis {:arglists true}}}
      kondo/run!
      :analysis
      (dissoc :namespace-usages
              :var-usages)))

(defn kondo-analysis->analysis
  [kondo-analysis]
  (-> kondo-analysis
      (update :name str)
      (update :ns #(some-> % str))))

(defn var-defs->file-name
  [project namespace output]
  (str project "/" namespace "." (name output)))

(defn extract-analysis!
  [project version]
  (let [{:keys [var-definitions namespace-definitions]}
        (kondo-run! [(get-jar project version)])]
    {:vars (->> var-definitions
                (map kondo-analysis->analysis)
                (group-by :ns))
     :nss (mapv (fn [namespace]
                  (-> namespace
                      kondo-analysis->analysis
                      (as-> adapted-ns
                            (assoc adapted-ns
                                   :var-definitions (str project "/" (:name adapted-ns))))))
                namespace-definitions)}))

(defn analysis->file!
  [analysis file-name output]
  (make-parents file-name)
  (->> (case output
         :json (cheshire/generate-string analysis)
         analysis)
       (spit file-name)))

(defn extract-all!
  [projects output]
  (doseq [{:keys [project version]} projects]
    (println "starting extract " project ":" version)
    (let [{:keys [vars nss]} (extract-analysis! project version)]
      (analysis->file! nss (str project "." (name output)) output)
      (doseq [[k v] vars]
        (analysis->file! v (var-defs->file-name project k output) output)))
    (println "finished " project ":" version)))

(defn read-edn [file-name]
  (-> (slurp file-name)
      (edn/read-string)))

(defn read-json [file-name]
  (-> (slurp file-name)
      (cheshire/parse-string true)))

(comment
  ;; downloads and process the projects listed
  (extract-all! [{:project 'org.clojure/clojure
                  :version "1.11.1"}
                 {:project 'org.clojure/clojurescript
                  :version "1.11.60"}]
                :json) ; or :edn

  ;; reading the produced files
  (read-json "org.clojure/clojure.json")
  (read-edn "org.clojure/clojure.edn")
  (let [namespace (->> (read-edn "org.clojure/clojurescript.edn")
                       (filter #(= (:name %) "cljs.core"))
                       first)
        function (->> (read-edn "org.clojure/clojurescript/cljs.core.edn")
                      (filter #(= (:name %) "memoize"))
                      first)]
    {:ns namespace :fn function}))

(ns extractor
  (:require [cheshire.core :as cheshire]
            [clj-kondo.core :as kondo]
            [clojure.edn :as edn]
            [clojure.java.io :as io :refer [make-parents]]
            [clojure.string :as str]
            [clojure.tools.deps :as deps]))

(defn get-project
  [project git]
  (-> (deps/resolve-deps
       {:deps {project git}
        :mvn/repos {"central" {:url "https://repo1.maven.org/maven2/"},
                    "clojars" {:url "https://repo.clojars.org/"}}}
       nil)
      (get project)))

(defn kondo-run!
  [paths]
  (-> {:lint paths
       :config {:output {:format :edn}
                :analysis {:arglists true
                           :var-definitions {:meta [:no-doc
                                                    :skip-wiki
                                                    :arglists]}
                           :namespace-definitions {:meta [:no-doc
                                                          :skip-wiki]}}}}
      kondo/run!
      :analysis
      (dissoc :namespace-usages
              :var-usages)))

(defn kondo-analysis->analysis
  [{:keys [filename row] :as kondo-analysis}
   {:git/keys [url tag] :deps/keys [root]}]
  (let [trim-filename (str/replace filename root "")]
    (-> kondo-analysis
        (assoc :git-source (str url "/blob/" tag trim-filename "#L" row)
               :filename trim-filename)
        (update :name str)
        (update :ns #(some-> % str)))))

(defn var-defs->file-name
  [project namespace output]
  (str project "/" namespace "." (name output)))

(defn extract-analysis!
  [project git]
  (let [{:keys [paths] :as project-meta} (get-project project git)
        {:keys [var-definitions namespace-definitions]} (kondo-run! paths)]
    {:vars (->> var-definitions
                (map #(kondo-analysis->analysis % project-meta))
                (group-by :ns))
     :nss (mapv (fn [namespace]
                  (-> namespace
                      (kondo-analysis->analysis project-meta)
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
  (doseq [{:keys [project git]} projects]
    (println "starting extract " project ":" (:git/tag git))
    (let [{:keys [vars nss]} (extract-analysis! project git)]
      (analysis->file! nss (str project "." (name output)) output)
      (doseq [[k v] vars]
        (analysis->file! v (var-defs->file-name project k output) output)))
    (println "finished " project ":" (:git/tag git))))

(defn read-edn [file-name]
  (-> (slurp file-name)
      (edn/read-string)))

(defn read-json [file-name]
  (-> (slurp file-name)
      (cheshire/parse-string true)))

(comment
  ;; downloads and process the projects listed
  (extract-all! [{:project 'org.clojure/clojure
                  :git {:git/url "https://github.com/clojure/clojure"
                        :git/tag "clojure-1.11.1"
                        :git/sha "ce55092f2b2f5481d25cff6205470c1335760ef6"}}
                 {:project 'org.clojure/clojurescript
                  :git {:git/url "https://github.com/clojure/clojurescript"
                        :git/tag "r1.11.60"
                        :git/sha "e7cdc70d0371a26e07e394ea9cd72d5c43e5e363"}}]
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

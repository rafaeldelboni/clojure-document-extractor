(ns extractor.core
  (:require [extractor.adapters :as adpt]
            [cheshire.core :as cheshire]
            [clj-kondo.core :as kondo]
            [clojure.java.io :as io :refer [make-parents]]
            [clojure.tools.deps :as deps])
  (:gen-class))

(defn download-project!
  [project git]
  (-> (deps/resolve-deps
       {:deps {project git}
        :mvn/repos {"central" {:url "https://repo1.maven.org/maven2/"},
                    "clojars" {:url "https://repo.clojars.org/"}}}
       nil)
      (get project)
      (assoc :project-name (str project))))

(defn kondo-run!
  [paths]
  (-> {:lint paths
       :config {:output {:format :edn}
                :analysis {:arglists true
                           :var-definitions {:meta [:no-doc :skip-wiki :arglists]}
                           :namespace-definitions {:meta [:no-doc :skip-wiki]}}}}
      kondo/run!
      :analysis
      (dissoc :namespace-usages
              :var-usages)))

(defn extract-analysis!
  [{:keys [library git]}]
  (println "extracting" library ":" (:git/tag git))
  (let [{:keys [paths] :as project-meta} (download-project! library git)
        {:keys [var-definitions namespace-definitions]} (kondo-run! paths)]
    {:project project-meta
     :definitions (adpt/kondo-analysis->definitions var-definitions project-meta)
     :libraries (adpt/kondo-analysis->libraries namespace-definitions project-meta)}))

(defn analysis->file!
  [analysis file-name output]
  (let [root-file-name (str "resources/" file-name "." (name output))]
    (make-parents root-file-name)
    (->> (case output
           :json (cheshire/generate-string analysis)
           analysis)
         (spit root-file-name))))

(defn extract-all!
  [projects output]
  (let [projects-analysis (mapv extract-analysis! projects)]
    (doseq [{:keys [definitions libraries project]} projects-analysis]
      (let [{:keys [project-name]} project]
        (println "generating files" project-name)
        (analysis->file! libraries project-name output)
        (doseq [[k v] definitions]
          (analysis->file! v (str project-name "/" k) output))))
    (analysis->file! (adpt/projects-meta->root-file projects-analysis) "root" output)))

#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn exec! [{:keys [projects output]}]
  (extract-all! projects output))

(comment
  ;; testing deps-resolve
  (-> (deps/resolve-deps
       {:deps {'lilactown/helix {:git/url "https://github.com/lilactown/helix"
                                 :git/tag "0.1.10"
                                 :git/sha "cc88c8ccfd73fa8e4ac803dd2dcf9115ac943a89"}}
        :mvn/repos {"central" {:url "https://repo1.maven.org/maven2/"},
                    "clojars" {:url "https://repo.clojars.org/"}}}
       nil)))

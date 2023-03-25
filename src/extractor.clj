(ns extractor
  (:require [cheshire.core :as cheshire]
            [clj-kondo.core :as kondo]
            [clojure.edn :as edn]
            [clojure.java.io :as io :refer [make-parents]]
            [clojure.string :as str]
            [clojure.tools.deps :as deps]
            [clojure.walk :as walk]))

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
                           :var-definitions {:meta [:no-doc
                                                    :skip-wiki
                                                    :arglists]}
                           :namespace-definitions {:meta [:no-doc
                                                          :skip-wiki]}}}}
      kondo/run!
      :analysis
      (dissoc :namespace-usages
              :var-usages)))

(defn kondo-analysis->definition
  [{:keys [filename row] :as kondo-analysis}
   {:git/keys [url tag] :deps/keys [root]}]
  (let [trim-filename (str/replace filename root "")]
    (-> kondo-analysis
        (assoc :git-source (str url "/blob/" tag trim-filename "#L" row)
               :filename trim-filename)
        (update :name str)
        (update :ns #(some-> % str)))))

(defn kondo-analysis->definitions
  [var-definitions project-meta]
  (->> var-definitions
       (mapv #(kondo-analysis->definition % project-meta))
       (group-by :ns)))

(defn kondo-analysis->library
  [{:keys [filename row] :as kondo-analysis}
   {:keys [project-name] :git/keys [url tag] :deps/keys [root]}]
  (let [trim-filename (str/replace filename root "")]
    (-> kondo-analysis
        (assoc :git-source (str url "/blob/" tag trim-filename "#L" row)
               :filename trim-filename
               :definitions (str project-name "/" (:name kondo-analysis)))
        (update :name str)
        (update :ns #(some-> % str)))))

(defn kondo-analysis->libraries
  [namespace-definitions project-meta]
  (mapv #(kondo-analysis->library % project-meta) namespace-definitions))

(defn unnamespace [map]
  (walk/postwalk (fn [x] (if (keyword? x) (keyword (name x)) x)) map))

(defn projects-meta->root-file
  [projects-analysis]
  (mapv (fn [{:keys [project]}]
          (let [[organization library] (-> project :project-name (str/split #"/"))]
            (-> project
                (assoc :paths (mapv #(str/replace % (:deps/root project) "") (:paths project))
                       :project (:project-name project)
                       :organization organization
                       :library library)
                (dissoc :project-name :parents :deps/root)
                unnamespace)))
        projects-analysis))

(defn extract-analysis!
  [{:keys [library git]}]
  (println "extracting" library ":" (:git/tag git))
  (let [{:keys [paths] :as project-meta} (download-project! library git)
        {:keys [var-definitions namespace-definitions]} (kondo-run! paths)]
    {:project project-meta
     :definitions (kondo-analysis->definitions var-definitions project-meta)
     :libraries (kondo-analysis->libraries namespace-definitions project-meta)}))

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
    (analysis->file! (projects-meta->root-file projects-analysis) "root" output)))

(defn read-edn [file-name]
  (-> (slurp file-name)
      (edn/read-string)))

(defn read-json [file-name]
  (-> (slurp file-name)
      (cheshire/parse-string true)))

(comment
  ;; downloads and process the projects listed
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
    {:ns namespace :fn function})
  ;; testing deps-resolve
  (-> (deps/resolve-deps
       {:deps {'lilactown/helix {:git/url "https://github.com/lilactown/helix"
                                 :git/tag "0.1.10"
                                 :git/sha "cc88c8ccfd73fa8e4ac803dd2dcf9115ac943a89"}}
        :mvn/repos {"central" {:url "https://repo1.maven.org/maven2/"},
                    "clojars" {:url "https://repo.clojars.org/"}}}
       nil)))

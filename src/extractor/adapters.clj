(ns extractor.adapters
  (:require [clojure.string :as str]
            [clojure.walk :as walk]))

(defn ^:private unnamespace [map]
  (walk/postwalk (fn [x] (if (keyword? x) (keyword (name x)) x)) map))

(defn ^:private group-by-name-row
  [items]
  (->> items
       (group-by
        (juxt :name :row))
       (map (fn [[_k v]]
              (reduce
               (fn [acc cur]
                 (let [langs (->> (into (or [(:lang acc)] []) [(:lang cur)])
                                  (remove nil?)
                                  flatten
                                  vec)]
                   (-> (merge acc cur)
                       (assoc :lang langs))))
               {}
               v)))
       flatten))

(defn ^:private inrelevant-definitions [{:keys [defined-by]}]
  (contains? #{"clojure.core/declare"} defined-by))

(defn ^:private kondo-analysis->definition
  [{:keys [filename row] :as kondo-analysis}
   {:git/keys [url tag] :deps/keys [root]}]
  (let [trim-filename (str/replace filename root "")]
    (-> kondo-analysis
        (assoc :git-source (str url "/blob/" tag trim-filename "#L" row)
               :filename trim-filename)
        (update :name str)
        (update :ns #(some-> % str)))))

(defn ^:private kondo-analysis->library
  [{:keys [filename row] :as kondo-analysis}
   {:keys [project-name] :git/keys [url tag] :deps/keys [root]}]
  (let [trim-filename (str/replace filename root "")]
    (-> kondo-analysis
        (assoc :git-source (str url "/blob/" tag trim-filename "#L" row)
               :filename trim-filename
               :definitions (str project-name "/" (:name kondo-analysis)))
        (update :name str)
        (update :ns #(some-> % str)))))

(defn kondo-analysis->definitions
  [var-definitions project-meta]
  (->> var-definitions
       (mapv #(kondo-analysis->definition % project-meta))
       (remove inrelevant-definitions)
       group-by-name-row
       (group-by :ns)
       (sort-by :name)))

(defn kondo-analysis->libraries
  [namespace-definitions project-meta]
  (->> namespace-definitions
       (mapv #(kondo-analysis->library % project-meta))
       group-by-name-row
       (sort-by :name)))

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

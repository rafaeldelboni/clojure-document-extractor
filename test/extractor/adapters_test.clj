(ns extractor.adapters-test
  (:require [extractor.adapters :as adpt]
            [clojure.test :refer [deftest is testing]]))

(deftest kondo-analysis->definitions-test
  (testing "kondo-analysis->definitions"
    (is (= [["a"
             [{:ns "a",
               :name "a",
               :filename "a",
               :row 2,
               :lang ["a"],
               :git-source "http://git.url/name/blob/v1a#L2"}
              {:ns "a",
               :name "a",
               :filename "a",
               :row 1,
               :lang ["a" "b"],
               :git-source "http://git.url/name/blob/v1a#L1"}]]]
           (adpt/kondo-analysis->definitions
            [{:ns "a" :name "a" :filename "/home/dir/a" :row 2 :lang "a"}
             {:ns "a" :name "a" :filename "/home/dir/a" :row 1 :lang "a"}
             {:ns "a" :name "a" :filename "/home/dir/a" :row 1 :lang "b"}]
            {:project-name "lib/name"
             :git/url "http://git.url/name"
             :git/tag "v1"
             :deps/root "/home/dir/"})))))

(deftest kondo-analysis->libraries-test
  (testing "kondo-analysis->libraries"
    (is (= [{:ns "a",
             :name "a",
             :filename "a",
             :row 2,
             :lang ["a"],
             :git-source "http://git.url/name/blob/v1a#L2",
             :definitions "lib/name/a"}
            {:ns "a",
             :name "a",
             :filename "a",
             :row 1,
             :lang ["a" "b"],
             :git-source "http://git.url/name/blob/v1a#L1",
             :definitions "lib/name/a"}]
           (adpt/kondo-analysis->libraries
            [{:ns "a" :name "a" :filename "/home/dir/a" :row 2 :lang "a"}
             {:ns "a" :name "a" :filename "/home/dir/a" :row 1 :lang "a"}
             {:ns "a" :name "a" :filename "/home/dir/a" :row 1 :lang "b"}]
            {:project-name "lib/name"
             :git/url "http://git.url/name"
             :git/tag "v1"
             :deps/root "/home/dir/"})))))

(deftest projects-meta->root-file-test
  (testing "projects-meta->root-file"
    (is (= [{:paths ["a" "b"],
             :organization "lib",
             :library "name",
             :project "lib/name",
             :url "http://git.url/name",
             :tag "v1"}]
           (adpt/projects-meta->root-file
            [{:project {:parents [:a :b]
                        :paths ["/home/dir/a" "/home/dir/b"]
                        :project-name "lib/name"
                        :git/url "http://git.url/name"
                        :git/tag "v1"
                        :deps/root "/home/dir/"}}])))))

(ns manetu.lib.registry
  (:require [clojure.string :as string]
            [taoensso.timbre :as log]
            [clojure.java.io :as io]
            [babashka.process :refer [process]]))

(defn crane-ls [registry]
  (with-open [rdr (io/reader (:out (process "crane ls" registry)))]
    (doall (line-seq rdr))))

(def semver-pattern #"([0-9]+)\.([0-9]+)\.([0-9]+)-v([0-9]+)\.([0-9]+)\.([0-9]+).b([0-9]+).([0-9]+)")

(defn semantic-value [version]
  (log/debug "semantic-value:" version)
  (->> version
       (re-find semver-pattern)
       (rest)
       (mapv parse-long)))

(defn semantic-compare
  [a b]
  (log/debug "compare: " a b)
  (compare (semantic-value a) (semantic-value b)))

(defn find-all
  "Given a registry URL, e.g. 'registry.gitlab.com/manetu/manetu-charts/manetu-platform' and a filter regex, e.g. 'v2.0.0', find all tags and sort by semanatic value"
  [registry filter]
  (->> (crane-ls registry)
       (clojure.core/filter (fn [version]
                              (some? (re-find (re-pattern filter) version))))
       (sort semantic-compare)))

(defn find-latest [registry filter]
  (last (find-all registry filter)))

(ns manetu.lib.env
  (:require [clojure.string :as string]
            [environ.core :refer [env]]))

(defn kw->env [k]
  (-> k name (string/replace "-" "_")string/upper-case))

(defn env->kw [v]
  (-> v (string/replace #"_" "-") string/lower-case keyword))

(defn load [k v]
  (if (nil? v)
    (or (env k)
        (throw (ex-info (str "required envvar " (kw->env k) " not set") {:name k})))
    (env k v)))

(defn load-all [spec]
  (reduce (fn [acc [k v]]
            (assoc acc k (load k v)))
            {}
            spec))

(defn generate-dotenv [spec]
  (->> spec
       (map (fn [k]
              (let [v (env k)]
                (str (kw->env k) "=" v))))
       (string/join "\n")))

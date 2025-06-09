(ns manetu.lib.logging
  (:require [clojure.string :as string]
            [taoensso.timbre :as log]))

(defn- ->level [x]
  (-> x name string/upper-case))

(defn- custom-output [{:keys [level msg_ instant] :as args}]
  (string/join " " [instant (->level level) "-" (force msg_)]))

(log/merge-config! {:output-fn custom-output
                    :appenders {:println (log/println-appender {:stream *err*})}})

(def loglevels #{:trace
                 :debug
                 :info
                 :warn
                 :error
                 :fatal
                 :report})

(defn print-loglevels []
      (str "[" (string/join ", " (map name loglevels)) "]"))

(def loglevels-description
  (str "Select the logging levels from " (print-loglevels)))


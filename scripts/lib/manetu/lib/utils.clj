(ns manetu.lib.utils
  (:require [clojure.string :as string]
            [taoensso.timbre :as log]
            [babashka.process :refer [shell]]
            [babashka.fs :as fs]
            [clj-yaml.core :as yaml]))

(defn exit [status msg & args]
      (log/debug "exit:" status "msg:" msg)
      (binding [*out* *err*]
               (apply println msg args))
      (System/exit status))

(defn prep-usage [msg] (->> msg flatten (string/join \newline)))

(defn to-yaml
  ([path data]
   (spit path (to-yaml data)))
  ([data]
   (yaml/generate-string data :dumper-options {:flow-style :block})))

(defn from-yaml
  [path]
  (yaml/parse-string (slurp path)))

(defn merge-yaml
  [a b]
  (:out (shell {:out :string} "spruce merge" a b)))

(defn merge-yaml-files [a b]
  (let [tmp (str (fs/create-temp-file))]
    (->> (merge-yaml a b)
         (spit tmp))
    (fs/delete b)
    (fs/move tmp b)))

(defn fileref [path]
  (str "@" path))

(defn sh [args]
  (shell (string/join " " args)))

(defn maybe-sh [{:keys [dry-run] :as options} command]
  (log/info (str (if-not dry-run "executing" "skipping") ": " (string/join " " command)))
  (when-not dry-run
    (sh command)))

(def not-blank? (complement string/blank?))

(ns manetu.lib.common
  (:require [clojure.string :as string]
            [taoensso.timbre :as log]
            [babashka.fs :as fs]
            [me.raynes.fs :refer [glob]]
            [babashka.process :refer [shell process exec check]]
            [manetu.lib.utils :refer [to-yaml merge-yaml-files fileref sh not-blank?] :as utils]
            [manetu.gitlab.download.generic-package :as generic-package]))

(defn docker-login! []
  (shell "docker-login"))

(defn registry-login! [{:keys [ci-registry gitlab-token-read-registry-user gitlab-token-read-registry]}]
  (shell "helm registry login"
         "--username" gitlab-token-read-registry-user
         "--password" gitlab-token-read-registry
         ci-registry))

(defn login! [options]
  (log/info "Initiating login")
  (docker-login!)
  (registry-login! options))

(defn mk-playbook-root [{:keys [tmpdir]}]
  (let [playbook-root (fs/path tmpdir "playbooks")]
    (fs/create-dir playbook-root)
    (str playbook-root)))

(defn download-playbooks [options project-id release-stream]
  (let [playbook-root (mk-playbook-root options)
        stream (generic-package/download! {:version release-stream :project-id project-id :package-name "manetu-playbooks"})]
    (log/info "downloading playbooks-> project:" (str project-id ":" release-stream) "to" playbook-root)
    (check (process {:in stream :dir playbook-root} "tar -zxv"))
    playbook-root))

(defn resolve-playbooks [{:keys [playbooks-ref] :as options}]
  (let [[_ fileref] (re-find #"file:\/\/(.*)" playbooks-ref)
        [_ project-id release-stream] (re-find #"project:\/\/([^:]*):(.*)" playbooks-ref)]
    (cond
      (some? fileref)
      (do
        (log/info "using playbooks from fileref:" fileref)
        (assoc options :playbook-root fileref))

      (some? project-id)
      (let [playbook-root (download-playbooks options project-id release-stream)]
        (assoc options :playbook-root playbook-root)))))

(defn ansible-playbook [{:keys [ansible-extra-args playbook-subpath] :as options} {:keys [path playbook inventory profile values] :or {profile "dev"}}]
  (sh ["ansible-playbook" (str path "/" playbook)
       "-i" inventory
       "--extra-vars" (fileref values)
       "--extra-vars" (fileref (str path "/" playbook-subpath "/profiles/" profile ".yml"))
       "--connection=local"
       ansible-extra-args]))

;; Helm functions
(defn helm-install [{:keys [helm-wait-timeout] :as options} {:keys [namespace name chartref version config] :or {namespace "manetu-platform"} :as params}]
  (log/debug "helm-install-> options:" options "params:" params)
  (log/info "installing: " chartref "version:" version)
  (sh (-> ["helm upgrade"
           "--install"
           "--wait"
           "--timeout" helm-wait-timeout
           "-n" namespace
           "-f" config]
          (cond-> (some? version) (concat [ "--version" version]))
          (concat [name chartref]))))

(defn helm-uninstall [{:keys [helm-wait-timeout] :as options} {:keys [namespace name] :or {namespace "manetu-platform"} :as params}]
  (log/debug "helm-uninstall-> options:" options "params:" params)
  (log/info "uninstalling: " name)
  (sh ["helm uninstall"
       "--wait"
       "--timeout" helm-wait-timeout
       "-n" namespace
       name]))

;; Chart reference functions
(defn chart-ref-valid? [ref]
  (try
    (sh ["helm show chart" ref])
    true
    (catch Exception e false)))

(defn resolve-glob [pattern]
  (some-> pattern glob first str))

(defn oci-ify [ref]
  (let [[uri version] (string/split ref #":")]
    [(str "oci://" uri) version]))

(defn resolve-chart-ref [chartref]
  (let [[oci-ref oci-version] (oci-ify chartref)]
    (cond
      (and (not-blank? oci-version) (chart-ref-valid? (str oci-ref " --version " oci-version)))
      [oci-ref oci-version]

      (chart-ref-valid? chartref)
      [chartref nil]

      :default
      (throw (ex-info "invalid chartref" {:name chartref})))))

(defn generate-passwords [required-passwords]
  (reduce (fn [acc p] (assoc acc p "password")) {} required-passwords))

(defn install-chart [{:keys [login upgrade tmpdir helm-values-yaml helm-upgradefrom-chart-ref helm-upgrade-style release-name] :as options} chart-values resolve-candidate-fn]
  (when login
    (login! options))

  (let [values-path (str tmpdir "/chart-values.yml")
        [candidate-ref candidate-version :as candidate] (resolve-chart-ref (resolve-candidate-fn options))
        helm-name (or release-name "default-release-name")]

    (to-yaml values-path chart-values)

    (when upgrade
      (let [[lkg-ref lkg-version :as lkg] (resolve-chart-ref helm-upgradefrom-chart-ref)]
        (log/info "Installing UPGRADEFROM release:" lkg)
        (log/info "using config:" (slurp values-path))
        (helm-install options {:name helm-name :chartref lkg-ref :version lkg-version :config values-path}))
      (when (= helm-upgrade-style "stop")
        (log/info "STOP THE WORLD: Uninstalling previous chart before installing CANDIDATE")
        (helm-uninstall options {:name helm-name})))

    (when (not-blank? helm-values-yaml)
      (log/info "merging" helm-values-yaml)
      (merge-yaml-files helm-values-yaml values-path))

    (log/info "Installing CANDIDATE release:" candidate)
    (log/info "using config:" (slurp values-path))
    (helm-install options {:name helm-name :chartref candidate-ref :version candidate-version :config values-path})))
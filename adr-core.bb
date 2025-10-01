#!/usr/bin/env bb

(ns ^:clj-kondo/ignore adr-core
  "Core ADR utilities for config and path resolution.
   Provides config discovery, loading, and merging across global and project configs."
  (:require [babashka.fs :as fs]
            [clojure.edn :as edn]
            [clojure.string :as str]))

;; ============================================================================
;; Configuration Paths
;; ============================================================================

(def global-config-path
  "Path to global ADR configuration"
  (fs/expand-home "~/.adr/config.edn"))

(def project-config-name
  "Name of project-specific config file"
  ".adr.edn")

;; ============================================================================
;; Project Discovery
;; ============================================================================

(defn find-git-root
  "Find git root by searching upward from start-path.
   Returns nil if not in a git repository."
  [start-path]
  (loop [current (fs/absolutize start-path)]
    (cond
      (fs/exists? (fs/path current ".git"))
      (str current)

      (= current (fs/parent current))
      nil

      :else
      (recur (fs/parent current)))))

(defn discover-project-root
  "Discover project root starting from cwd.
   Strategy:
   1. Try to find git root
   2. If not in git repo, use cwd
   Returns absolute path as string."
  []
  (let [cwd (str (fs/cwd))
        git-root (find-git-root cwd)]
    (or git-root cwd)))

;; ============================================================================
;; Config Loading
;; ============================================================================

(defn load-edn-file
  "Load EDN from file path. Returns nil if file doesn't exist.
   Throws on parse errors."
  [path]
  (when (fs/exists? path)
    (try
      (edn/read-string (slurp (str path)))
      (catch Exception e
        (throw (ex-info (str "Failed to parse EDN config: " path)
                        {:path path
                         :error (.getMessage e)}
                        e))))))

(defn load-global-config
  "Load global ADR configuration from ~/.adr/config.edn"
  []
  (load-edn-file global-config-path))

(defn load-project-config
  "Load project-specific config from project-root/.adr.edn"
  [project-root]
  (when project-root
    (load-edn-file (fs/path project-root project-config-name))))

(defn deep-merge
  "Recursively merge maps. Later maps override earlier maps.
   For non-map values, later values override earlier values."
  [& maps]
  (apply merge-with
         (fn [v1 v2]
           (if (and (map? v1) (map? v2))
             (deep-merge v1 v2)
             v2))
         maps))

(defn merge-configs
  "Merge global and project configs.
   Project config overrides global config.
   Returns merged config map."
  [global-config project-config]
  (if project-config
    (deep-merge global-config project-config)
    global-config))

;; ============================================================================
;; Config Resolution
;; ============================================================================

(defn load-config
  "Load and merge ADR configuration.
   1. Discover project root (git root or cwd)
   2. Load global config (~/.adr/config.edn)
   3. Load project config if exists (<project>/.adr.edn)
   4. Merge configs (project overrides global)

   Returns:
   {:config {...}           ; Merged configuration
    :project-root \"...\"    ; Absolute path to project root
    :sources {:global \"...\" ; Path to global config (or nil)
              :project \"...\"}  ; Path to project config (or nil)
    }"
  ([]
   (load-config nil))
  ([project-root-override]
   (let [project-root (or project-root-override (discover-project-root))
         global-cfg (load-global-config)
         project-cfg (load-project-config project-root)
         merged-cfg (merge-configs global-cfg project-cfg)]
     {:config merged-cfg
      :project-root project-root
      :sources {:global (when global-cfg (str global-config-path))
                :project (when project-cfg
                          (str (fs/path project-root project-config-name)))}})))

;; ============================================================================
;; Path Resolution
;; ============================================================================

(defn expand-path
  "Expand path with ~ and make absolute relative to base-dir.
   If path is already absolute, return as-is."
  [path base-dir]
  (let [expanded (fs/expand-home path)]
    (if (fs/absolute? expanded)
      (str expanded)
      (str (fs/path base-dir expanded)))))

(defn resolve-adr-path
  "Resolve ADR directory path from config.
   Makes path absolute relative to project root.

   Args:
     config-result: Result from load-config

   Returns:
     Absolute path to ADR directory as string"
  [config-result]
  (let [adr-path (get-in config-result [:config :adr :path])
        project-root (:project-root config-result)]
    (when-not adr-path
      (throw (ex-info "No :adr :path found in config"
                      {:config (:config config-result)})))
    (expand-path adr-path project-root)))

(defn resolve-template-path
  "Resolve ADR template path from config.
   Makes path absolute, expanding ~ if present.

   Args:
     config-result: Result from load-config

   Returns:
     Absolute path to template file as string"
  [config-result]
  (let [template-path (get-in config-result [:config :adr :template])
        project-root (:project-root config-result)]
    (when-not template-path
      (throw (ex-info "No :adr :template found in config"
                      {:config (:config config-result)})))
    (expand-path template-path project-root)))

;; ============================================================================
;; Config Accessors
;; ============================================================================

(defn get-config-value
  "Get a configuration value from merged config.

   Args:
     config-result: Result from load-config
     path: Vector path into config (e.g., [:adr :require-runnotes])

   Returns:
     Value at path, or nil if not found"
  [config-result path]
  (get-in config-result (cons :config path)))

(defn adr-config
  "Get the :adr section of the config.
   Convenience accessor for (get-config-value config-result [:adr])"
  [config-result]
  (get-config-value config-result [:adr]))

;; ============================================================================
;; Export for use by other scripts
;; ============================================================================

(def exports
  {:discover-project-root discover-project-root
   :load-config load-config
   :resolve-adr-path resolve-adr-path
   :resolve-template-path resolve-template-path
   :get-config-value get-config-value
   :adr-config adr-config
   :expand-path expand-path})

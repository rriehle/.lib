#!/usr/bin/env bb

(ns ^:clj-kondo/ignore config-core
  "Core configuration utilities for ADR and RunNotes systems.
   Provides config discovery, loading, and merging across global and project configs.
   Shared library used by both ~/.adr and ~/.runnote tooling."
  (:require [babashka.fs :as fs]
            [clojure.edn :as edn]
            [clojure.string :as str]))

;; ============================================================================
;; System Type Configuration
;; ============================================================================

(def system-paths
  "Configuration paths for supported systems"
  {:adr {:global "~/.adr/config.edn"
         :project ".adr.edn"
         :root-key :adr}
   :runnote {:global "~/.runnote/config.edn"
             :project ".runnote.edn"
             :root-key :runnote}
   :req {:global "~/.req/config.edn"
         :project ".req.edn"
         :root-key :req}})

(defn get-system-paths
  "Get configuration paths for a system type.

   Args:
     system-type: :adr, :runnote, or :req

   Returns:
     Map with :global, :project, and :root-key paths"
  [system-type]
  (or (get system-paths system-type)
      (throw (ex-info "Unknown system type"
                      {:system-type system-type
                       :valid-types (keys system-paths)}))))

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

      (nil? (fs/parent current))
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
  "Load global configuration for specified system type.

   Args:
     system-type: :adr, :runnote, or :req

   Returns:
     Config map or nil if file doesn't exist"
  [system-type]
  (let [paths (get-system-paths system-type)
        global-path (fs/expand-home (:global paths))]
    (load-edn-file global-path)))

(defn load-project-config
  "Load project-specific config for specified system type.

   Args:
     system-type: :adr, :runnote, or :req
     project-root: Absolute path to project root

   Returns:
     Config map or nil if file doesn't exist"
  [system-type project-root]
  (when project-root
    (let [paths (get-system-paths system-type)
          project-path (fs/path project-root (:project paths))]
      (load-edn-file project-path))))

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
  "Load and merge configuration for specified system type.

   Steps:
   1. Discover project root (git root or cwd)
   2. Load global config (~/.{system}/config.edn)
   3. Load project config if exists (<project>/.{system}.edn)
   4. Merge configs (project overrides global)

   Args:
     system-type: :adr, :runnote, or :req
     project-root-override: (optional) Override project root discovery

   Returns:
   {:config {...}           ; Merged configuration
    :system-type :adr|:runnote|:req ; System type used
    :project-root \"...\"    ; Absolute path to project root
    :sources {:global \"...\" ; Path to global config (or nil)
              :project \"...\"}  ; Path to project config (or nil)
    }"
  ([system-type]
   (load-config system-type nil))
  ([system-type project-root-override]
   (let [paths (get-system-paths system-type)
         project-root (or project-root-override (discover-project-root))
         global-cfg (load-global-config system-type)
         project-cfg (load-project-config system-type project-root)
         merged-cfg (merge-configs global-cfg project-cfg)
         global-path (fs/expand-home (:global paths))
         project-path (when project-cfg
                        (str (fs/path project-root (:project paths))))]
     {:config merged-cfg
      :system-type system-type
      :project-root project-root
      :sources {:global (when global-cfg (str global-path))
                :project project-path}})))

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

(defn resolve-path
  "Resolve a path from config using a path key.
   Makes path absolute relative to project root.

   Args:
     config-result: Result from load-config
     path-keys: Vector of keys to navigate config (e.g., [:adr :path])

   Returns:
     Absolute path as string

   Throws:
     ex-info if path not found in config"
  [config-result path-keys]
  (let [path (get-in config-result (cons :config path-keys))
        project-root (:project-root config-result)]
    (when-not path
      (throw (ex-info (str "No path found in config at " (pr-str path-keys))
                      {:path-keys path-keys
                       :config (:config config-result)})))
    (expand-path path project-root)))

;; ============================================================================
;; System-Specific Path Resolvers
;; ============================================================================

(defn resolve-adr-path
  "Resolve ADR directory path from config.
   Makes path absolute relative to project root.

   Args:
     config-result: Result from load-config

   Returns:
     Absolute path to ADR directory as string"
  [config-result]
  (resolve-path config-result [:adr :path]))

(defn resolve-adr-template-path
  "Resolve ADR template path from config.
   Makes path absolute, expanding ~ if present.

   Args:
     config-result: Result from load-config

   Returns:
     Absolute path to template file as string"
  [config-result]
  (resolve-path config-result [:adr :template]))

(defn resolve-runnote-dir
  "Resolve RunNotes directory path from config.
   Makes path absolute relative to project root.

   Args:
     config-result: Result from load-config

   Returns:
     Absolute path to RunNotes directory as string"
  [config-result]
  (resolve-path config-result [:runnote :dir]))

(defn resolve-runnote-template-dir
  "Resolve RunNotes template directory from config.
   Makes path absolute, expanding ~ if present.

   Args:
     config-result: Result from load-config

   Returns:
     Absolute path to template directory as string"
  [config-result]
  (resolve-path config-result [:runnote :template-dir]))

(defn resolve-req-path
  "Resolve requirements directory path from config.
   Makes path absolute relative to project root.

   Args:
     config-result: Result from load-config

   Returns:
     Absolute path to requirements directory as string"
  [config-result]
  (resolve-path config-result [:req :path]))

(defn resolve-req-template-path
  "Resolve requirements template path from config.
   Makes path absolute, expanding ~ if present.

   Args:
     config-result: Result from load-config

   Returns:
     Absolute path to template file as string"
  [config-result]
  (resolve-path config-result [:req :template]))

(defn resolve-req-template-dir
  "Resolve requirements template directory from config.
   Makes path absolute, expanding ~ if present.

   Args:
     config-result: Result from load-config

   Returns:
     Absolute path to template directory as string"
  [config-result]
  (resolve-path config-result [:req :template-dir]))

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

(defn get-system-config
  "Get the system-specific section of the config.
   Uses :system-type from config-result to determine which section.

   Args:
     config-result: Result from load-config

   Returns:
     System-specific config map (e.g., :adr or :runnote section)"
  [config-result]
  (let [system-type (:system-type config-result)
        paths (get-system-paths system-type)
        root-key (:root-key paths)]
    (get-config-value config-result [root-key])))

;; Backward compatibility aliases for ADR scripts
(defn adr-config
  "Get the :adr section of the config.
   Convenience accessor for ADR scripts.
   Equivalent to (get-config-value config-result [:adr])"
  [config-result]
  (get-config-value config-result [:adr]))

(defn runnote-config
  "Get the :runnote section of the config.
   Convenience accessor for RunNotes scripts.
   Equivalent to (get-config-value config-result [:runnote])"
  [config-result]
  (get-config-value config-result [:runnote]))

(defn req-config
  "Get the :req section of the config.
   Convenience accessor for requirements scripts.
   Equivalent to (get-config-value config-result [:req])"
  [config-result]
  (get-config-value config-result [:req]))

;; ============================================================================
;; Export for use by other scripts
;; ============================================================================

(def exports
  "Exported functions for use by ADR and RunNotes scripts"
  {;; Project discovery
   :discover-project-root discover-project-root
   :find-git-root find-git-root

   ;; Config loading
   :load-config load-config
   :load-global-config load-global-config
   :load-project-config load-project-config

   ;; Path resolution (generic)
   :expand-path expand-path
   :resolve-path resolve-path

   ;; ADR-specific path resolvers (backward compatibility)
   :resolve-adr-path resolve-adr-path
   :resolve-template-path resolve-adr-template-path  ; Alias for backward compat
   :resolve-adr-template-path resolve-adr-template-path

   ;; RunNotes-specific path resolvers
   :resolve-runnote-dir resolve-runnote-dir
   :resolve-runnote-template-dir resolve-runnote-template-dir

   ;; Requirements-specific path resolvers
   :resolve-req-path resolve-req-path
   :resolve-req-template-path resolve-req-template-path
   :resolve-req-template-dir resolve-req-template-dir

   ;; Config accessors
   :get-config-value get-config-value
   :get-system-config get-system-config
   :adr-config adr-config
   :runnote-config runnote-config
   :req-config req-config})

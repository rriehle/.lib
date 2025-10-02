#!/usr/bin/env bb

(ns ^:clj-kondo/ignore req-core
  "Core utilities for requirements management system.
   Delegates to config-core.bb with :req system type."
  (:require [babashka.fs :as fs]))

;; Load the generalized config-core library
(def lib-dir (str (System/getenv "HOME") "/.lib"))
(load-file (str lib-dir "/config-core.bb"))

;; Import functions from config-core
(def discover-project-root config-core/discover-project-root)
(def find-git-root config-core/find-git-root)
(def load-edn-file config-core/load-edn-file)
(def deep-merge config-core/deep-merge)
(def merge-configs config-core/merge-configs)
(def expand-path config-core/expand-path)
(def get-config-value config-core/get-config-value)
(def req-config config-core/req-config)

;; ============================================================================
;; Requirements-Specific Configuration
;; ============================================================================

(def global-config-path
  "Path to global requirements configuration"
  (fs/expand-home "~/.req/config.edn"))

(def project-config-name
  "Name of project-specific config file"
  ".req.edn")

;; ============================================================================
;; Requirements-Specific Wrappers
;; ============================================================================

(defn load-global-config
  "Load global requirements configuration from ~/.req/config.edn"
  []
  (config-core/load-global-config :req))

(defn load-project-config
  "Load project-specific config from project-root/.req.edn"
  [project-root]
  (config-core/load-project-config :req project-root))

(defn load-config
  "Load and merge requirements configuration.

   Returns:
   {:config {...}           ; Merged configuration
    :system-type :req       ; System type
    :project-root \"...\"    ; Absolute path to project root
    :sources {:global \"...\" ; Path to global config (or nil)
              :project \"...\"}  ; Path to project config (or nil)
    }"
  ([]
   (load-config nil))
  ([project-root-override]
   (config-core/load-config :req project-root-override)))

(defn resolve-req-path
  "Resolve requirements directory path from config."
  [config-result]
  (config-core/resolve-req-path config-result))

(defn resolve-template-dir
  "Resolve requirements template directory from config."
  [config-result]
  (config-core/resolve-req-template-dir config-result))

;; ============================================================================
;; Export for use by other scripts
;; ============================================================================

(def exports
  "Exported functions for requirements scripts"
  {:discover-project-root discover-project-root
   :load-config load-config
   :resolve-req-path resolve-req-path
   :resolve-template-dir resolve-template-dir
   :get-config-value get-config-value
   :req-config req-config
   :expand-path expand-path})

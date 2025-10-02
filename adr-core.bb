#!/usr/bin/env bb

(ns ^:clj-kondo/ignore adr-core
  "Backward compatibility wrapper for ADR scripts.
   Delegates to config-core.bb with :adr system type.

   DEPRECATED: ADR scripts should migrate to use config-core.bb directly.
   This wrapper maintains compatibility with existing ADR scripts."
  (:require [babashka.fs :as fs]
            [clojure.edn :as edn]
            [clojure.string :as str]))

;; Load the generalized config-core library
(def lib-dir (str (System/getenv "HOME") "/.lib"))
(load-file (str lib-dir "/config-core.bb"))

;; Import all functions from config-core
(def discover-project-root config-core/discover-project-root)
(def find-git-root config-core/find-git-root)
(def load-edn-file config-core/load-edn-file)
(def deep-merge config-core/deep-merge)
(def merge-configs config-core/merge-configs)
(def expand-path config-core/expand-path)
(def get-config-value config-core/get-config-value)
(def adr-config config-core/adr-config)

;; ============================================================================
;; Backward Compatibility Configuration Paths
;; ============================================================================

(def global-config-path
  "Path to global ADR configuration"
  (fs/expand-home "~/.adr/config.edn"))

(def project-config-name
  "Name of project-specific config file"
  ".adr.edn")

;; ============================================================================
;; Backward Compatibility Wrappers
;; ============================================================================

(defn load-global-config
  "Load global ADR configuration from ~/.adr/config.edn

   DEPRECATED: Use (config-core/load-global-config :adr) instead"
  []
  (config-core/load-global-config :adr))

(defn load-project-config
  "Load project-specific config from project-root/.adr.edn

   DEPRECATED: Use (config-core/load-project-config :adr project-root) instead"
  [project-root]
  (config-core/load-project-config :adr project-root))

(defn load-config
  "Load and merge ADR configuration.

   DEPRECATED: Use (config-core/load-config :adr) instead

   Returns same format as config-core/load-config for backward compatibility"
  ([]
   (load-config nil))
  ([project-root-override]
   (config-core/load-config :adr project-root-override)))

(defn resolve-adr-path
  "Resolve ADR directory path from config.

   DEPRECATED: Use config-core/resolve-adr-path instead"
  [config-result]
  (config-core/resolve-adr-path config-result))

(defn resolve-template-path
  "Resolve ADR template path from config.

   DEPRECATED: Use config-core/resolve-adr-template-path instead"
  [config-result]
  (config-core/resolve-adr-template-path config-result))

;; ============================================================================
;; Export for use by other scripts (backward compatibility)
;; ============================================================================

(def exports
  "Exported functions for backward compatibility with existing ADR scripts"
  {:discover-project-root discover-project-root
   :load-config load-config
   :resolve-adr-path resolve-adr-path
   :resolve-template-path resolve-template-path
   :get-config-value get-config-value
   :adr-config adr-config
   :expand-path expand-path})

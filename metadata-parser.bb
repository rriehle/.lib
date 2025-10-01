#!/usr/bin/env bb

(ns ^:clj-kondo/ignore metadata-parser
  "Shared library for parsing EDN metadata from RunNotes and ADR files.
   Supports extensible metadata schemas with project-specific validation.
   Follows ADR-00035 naming conventions (singular forms)."
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.spec.alpha :as s]))

;; ============================================================================
;; Spec Definitions - Standard Schema
;; ============================================================================

;; Common specs
(s/def ::date-string (s/and string? #(re-matches #"\d{4}-\d{2}-\d{2}" %)))
(s/def ::created ::date-string)
(s/def ::modified ::date-string)
(s/def ::updated ::date-string)

;; Tag must be a set of keywords (singular per ADR-00035)
(s/def ::tag (s/coll-of keyword? :kind set? :min-count 1))

;; Date can be string or nested map (singular per ADR-00035)
(s/def ::date (s/or :simple ::date-string
                    :nested (s/keys :opt-un [::created ::modified])))

;; RunNotes specific specs
(s/def ::phase #{"research" "planning" "implementation" "review" "code review"
                 "debug" "hotfix" "performance" "security" "testing"})
(s/def ::runnotes-status #{:active :blocked :completed :investigating})
(s/def ::thinking-mode #{"think" "think hard" "think harder" "ultrathink"})
(s/def ::complexity #{:low :medium :high :extreme})

;; ADR specific specs
(s/def ::adr-status #{:proposed :accepted :deprecated :superseded})

;; RunNotes references - set of strings with RunNotes filename format
(s/def ::runnotes-filename
  (s/and string?
         #(re-matches #"RunNotes-\d{4}-\d{2}-\d{2}-[A-Za-z]+(-[a-z]+)?\.md" %)))
(s/def ::runnotes-set (s/coll-of ::runnotes-filename :kind set? :min-count 1))

;; Define separate namespace-qualified specs for status to avoid conflicts
(s/def :runnotes/status ::runnotes-status)
(s/def :adr/status ::adr-status)
(s/def :adr/runnotes ::runnotes-set)

;; RunNotes metadata (unchanged)
(s/def ::runnotes-metadata
  (s/keys :req-un [::phase ::tag]
          :opt-un [:runnotes/status ::thinking-mode ::date ::complexity]))

;; ADR metadata - ENHANCED: runnotes is now optional
(s/def ::adr-metadata
  (s/keys :req-un [::date :adr/status ::tag]
          :opt-un [:adr/runnotes ::updated]))

;; ============================================================================
;; Parser Functions
;; ============================================================================

(defn extract-edn-metadata
  "Extract EDN metadata block from markdown content.
   Looks for code blocks tagged with :metadata."
  [content]
  (let [pattern #"```edn :metadata\n([\s\S]*?)\n```"
        match (re-find pattern content)]
    (when match
      (try
        (edn/read-string (second match))
        (catch Exception e
          {:error :parse-failed
           :message (.getMessage e)})))))

;; ============================================================================
;; Extensible Validation
;; ============================================================================

(defn check-required-extension-fields
  "Check if required extension fields are present.
   Returns vector of missing field names, or empty vector if all present.

   Args:
     metadata: The metadata map
     extensions: Map of extension field definitions from config
                 e.g., {:runnotes {:spec :adr/runnotes :required true}}

   Returns:
     Vector of missing required field names"
  [metadata extensions]
  (if-not extensions
    []
    (for [[field-key field-spec] extensions
          :when (and (:required field-spec)
                    (not (contains? metadata field-key)))]
      field-key)))

(defn find-unknown-fields
  "Find fields in metadata that aren't in the standard schema.
   Returns set of unknown field keywords.

   Args:
     metadata: The metadata map
     type: :adr or :runnotes
     known-extensions: Set of known extension field keys from config

   Returns:
     Set of unknown field keywords"
  [metadata type known-extensions]
  (let [standard-fields (case type
                          :adr #{:date :status :tag :runnotes :updated}
                          :runnotes #{:phase :tag :status :thinking-mode :date :complexity})
        all-known (into standard-fields known-extensions)
        metadata-keys (set (keys metadata))]
    (into #{} (remove all-known metadata-keys))))

(defn validate-metadata
  "Validate metadata against appropriate spec with extension support.

   Args:
     metadata: The metadata map to validate
     type: :adr or :runnotes
     config: Optional ADR config map with :metadata-extensions

   Returns:
     {:valid true :data metadata} or
     {:valid false :errors [...] :warnings [...]}"
  ([metadata type]
   (validate-metadata metadata type nil))
  ([metadata type config]
   (let [spec (case type
                :adr ::adr-metadata
                :runnotes ::runnotes-metadata
                (throw (ex-info "Unknown metadata type" {:type type})))
         extensions (get-in config [:adr :metadata-extensions])
         known-extension-keys (set (keys extensions))

         ;; Check standard schema
         base-valid? (s/valid? spec metadata)
         base-errors (when-not base-valid?
                      (s/explain-data spec metadata))

         ;; Check required extensions
         missing-extensions (check-required-extension-fields metadata extensions)

         ;; Find unknown fields (potential typos or undeclared extensions)
         unknown-fields (find-unknown-fields metadata type known-extension-keys)

         ;; Compile validation result
         all-valid? (and base-valid? (empty? missing-extensions))
         errors (cond-> []
                  (not base-valid?)
                  (conj {:type :invalid-standard-fields
                         :spec-explain base-errors
                         :explanation (s/explain-str spec metadata)})

                  (seq missing-extensions)
                  (conj {:type :missing-required-extensions
                         :fields missing-extensions}))

         warnings (when (seq unknown-fields)
                    [{:type :unknown-fields
                      :fields unknown-fields
                      :message (str "Unknown metadata fields (possible typos or "
                                   "undeclared extensions): "
                                   (str/join ", " (map name unknown-fields)))}])]

     (if all-valid?
       {:valid true
        :data metadata
        :warnings warnings}
       {:valid false
        :errors errors
        :warnings warnings
        :explanation (when-not base-valid?
                      (s/explain-str spec metadata))}))))

(defn parse-metadata
  "Main entry point for parsing and validating metadata.
   Extracts EDN from content and validates against appropriate spec.

   Args:
     content: Markdown content containing metadata block
     type: :adr or :runnotes
     config: Optional ADR config map for extension validation

   Returns:
     {:valid true :data metadata :warnings [...]} or
     {:valid false :errors [...] :warnings [...]}"
  ([content type]
   (parse-metadata content type nil))
  ([content type config]
   (if-let [metadata (extract-edn-metadata content)]
     (if (:error metadata)
       {:valid false :errors [metadata]}
       (validate-metadata metadata type config))
     {:valid false :errors [{:error :no-metadata
                             :message "No EDN metadata block found"}]})))

;; ============================================================================
;; Migration Functions
;; ============================================================================

(defn extract-keyword-headers
  "Extract current keyword-based headers from markdown."
  [content]
  (let [lines (str/split-lines content)
        extract-field (fn [field]
                        (some #(when (str/includes? % (str "> **" field "**:"))
                                 (let [parts (str/split % #":" 2)]
                                   (when (> (count parts) 1)
                                     (str/trim (second parts)))))
                              lines))]
    {:phase (extract-field "Phase")
     :tag-string (extract-field "Tags")
     :status (extract-field "Status")
     :thinking-mode (extract-field "Thinking Mode")
     :date (extract-field "Date")}))

(defn parse-tag-string
  "Convert tag string to set of keywords."
  [tag-string]
  (when tag-string
    (into #{}
          (comp (map str/trim)
                (filter #(str/starts-with? % ":"))
                (map #(keyword (subs % 1))))
          (str/split tag-string #" "))))

(defn keyword-headers->edn
  "Convert keyword headers to EDN format."
  [headers]
  (let [phase (some-> (:phase headers)
                      (str/split #" ")
                      first
                      str/lower-case)
        status (when-let [s (:status headers)]
                 (keyword (str/lower-case s)))
        tag (parse-tag-string (:tag-string headers))]
    (cond-> {}
      phase (assoc :phase phase)
      tag (assoc :tag tag)
      status (assoc :status status)
      (:thinking-mode headers) (assoc :thinking-mode (:thinking-mode headers))
      (:date headers) (assoc :date (:date headers)))))

(defn migrate-header
  "Convert keyword headers in markdown to EDN metadata block.
   Returns updated markdown content."
  [content]
  (let [headers (extract-keyword-headers content)
        edn-data (keyword-headers->edn headers)
        edn-block (str "```edn :metadata\n"
                       (pr-str edn-data)
                       "\n```")
        ;; Find title line
        lines (str/split-lines content)
        title-idx (first (keep-indexed #(when (str/starts-with? %2 "#") %1) lines))]
    (if title-idx
      ;; Insert EDN block after title
      (let [before-title (take (inc title-idx) lines)
            after-title (drop (inc title-idx) lines)
            ;; Skip old header lines (those starting with > or empty lines after them)
            content-lines (drop-while #(or (str/starts-with? % ">")
                                           (str/blank? %))
                                      after-title)]
        (str/join "\n"
                  (concat before-title
                          ["" edn-block ""]
                          content-lines)))
      ;; Fallback: just prepend
      (str edn-block "\n\n" content))))

;; ============================================================================
;; Utility Functions
;; ============================================================================

(defn summarize-metadata
  "Generate human-readable summary of metadata."
  [metadata]
  (str "Phase: " (:phase metadata)
       "\nTags: " (str/join " " (map #(str ":" (name %)) (:tag metadata)))
       (when (:status metadata)
         (str "\nStatus: " (name (:status metadata))))
       (when (:thinking-mode metadata)
         (str "\nThinking Mode: " (:thinking-mode metadata)))
       (when (:date metadata)
         (str "\nDate: " (if (string? (:date metadata))
                           (:date metadata)
                           (str (:created (:date metadata))))))))

;; ============================================================================
;; Export for use by other scripts
;; ============================================================================

(def exports
  {:parse-metadata parse-metadata
   :extract-edn-metadata extract-edn-metadata
   :validate-metadata validate-metadata
   :migrate-header migrate-header
   :extract-keyword-headers extract-keyword-headers
   :keyword-headers->edn keyword-headers->edn
   :summarize-metadata summarize-metadata})

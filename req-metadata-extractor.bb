#!/usr/bin/env bb

(ns ^:clj-kondo/ignore req-metadata-extractor
  "Requirements metadata extraction and validation.
   Supports EDN metadata blocks with requirements-specific schema."
  (:require [clojure.string :as str]
            [clojure.spec.alpha :as s]))

;; Load shared metadata parser
(def lib-dir (str (System/getenv "HOME") "/.lib"))
(load-file (str lib-dir "/metadata-parser.bb"))

;; ============================================================================
;; Requirements-Specific Specs
;; ============================================================================

;; Requirement ID format: REQ-[CATEGORY]-[NUMBER]
(s/def ::req-id (s/and string? #(re-matches #"REQ-[A-Z]+-\d{3,5}" %)))

;; Requirement types
(s/def ::type #{:functional :non-functional :constraint})

;; Priority levels (RFC 2119 semantics)
(s/def ::priority #{:must :shall :should :may})

;; Requirement statuses
(s/def ::status #{:proposed :accepted :deprecated :deferred :implemented})

;; Category - project-specific string identifier
(s/def ::category keyword?)

;; Tags - set of keywords
(s/def ::tag (s/coll-of keyword? :kind set? :min-count 1))

;; Traceability links
(s/def ::adr (s/coll-of string? :kind set?))
(s/def ::runnote (s/coll-of string? :kind set?))
(s/def ::code (s/coll-of string? :kind set?))
(s/def ::tests (s/coll-of string? :kind set?))

(s/def ::trace
  (s/keys :opt-un [::adr ::runnote ::code ::tests]))

;; ISO 25010 taxonomy
(s/def ::iso-25010-refs (s/coll-of keyword? :kind set?))

;; FURPS+ taxonomy
(s/def ::furps-refs (s/coll-of keyword? :kind set?))

(s/def ::nfr-taxonomy
  (s/keys :opt-un [::iso-25010-refs ::furps-refs]))

;; Acceptance criteria
(s/def ::ac-id string?)
(s/def ::description string?)
(s/def ::testable boolean?)
(s/def ::test-ref (s/nilable string?))

(s/def ::acceptance-criterion
  (s/keys :req-un [::ac-id ::description ::testable]
          :opt-un [::test-ref]))

(s/def ::acceptance-criteria
  (s/coll-of ::acceptance-criterion :kind vector?))

;; Date format
(s/def ::date-string (s/and string? #(re-matches #"\d{4}-\d{2}-\d{2}" %)))
(s/def ::updated ::date-string)

;; Complete requirements metadata schema
(s/def ::req-metadata
  (s/keys :req-un [::req-id ::type ::category ::priority ::status ::tag]
          :opt-un [::updated ::trace ::nfr-taxonomy ::acceptance-criteria]))

;; ============================================================================
;; Extraction Functions
;; ============================================================================

(defn extract-req-metadata
  "Extract EDN metadata from requirements markdown.
   Uses shared metadata-parser for EDN extraction."
  [content]
  (metadata-parser/extract-edn-metadata content))

(defn validate-req-metadata
  "Validate requirements metadata against spec.

   Args:
     metadata: The metadata map to validate

   Returns:
     {:valid true :data metadata} or
     {:valid false :errors [...] :warnings [...]}"
  [metadata]
  (let [valid? (s/valid? ::req-metadata metadata)
        errors (when-not valid?
                 (s/explain-data ::req-metadata metadata))
        warnings []]
    (if valid?
      {:valid true
       :data metadata
       :warnings warnings}
      {:valid false
       :errors [{:type :invalid-metadata
                 :spec-explain errors
                 :explanation (s/explain-str ::req-metadata metadata)}]
       :warnings warnings})))

(defn parse-req-metadata
  "Main entry point for parsing and validating requirements metadata.

   Args:
     content: Markdown content containing metadata block

   Returns:
     {:valid true :data metadata :warnings [...]} or
     {:valid false :errors [...] :warnings [...]}"
  [content]
  (if-let [metadata (extract-req-metadata content)]
    (if (:error metadata)
      {:valid false :errors [metadata]}
      (validate-req-metadata metadata))
    {:valid false :errors [{:error :no-metadata
                            :message "No EDN metadata block found"}]}))

;; ============================================================================
;; Traceability Analysis
;; ============================================================================

(defn has-adr-trace?
  "Check if requirement has ADR traceability links"
  [metadata]
  (seq (get-in metadata [:trace :adr])))

(defn has-code-trace?
  "Check if requirement has code traceability links"
  [metadata]
  (seq (get-in metadata [:trace :code])))

(defn has-test-trace?
  "Check if requirement has test traceability links"
  [metadata]
  (seq (get-in metadata [:trace :tests])))

(defn has-runnote-trace?
  "Check if requirement has RunNotes traceability links"
  [metadata]
  (seq (get-in metadata [:trace :runnote])))

(defn traceability-coverage
  "Calculate traceability coverage for a requirement.

   Returns:
     {:adr boolean, :code boolean, :tests boolean, :runnote boolean
      :score number}  ; 0-100 percentage"
  [metadata]
  (let [has-adr (has-adr-trace? metadata)
        has-code (has-code-trace? metadata)
        has-tests (has-test-trace? metadata)
        has-runnote (has-runnote-trace? metadata)
        total (count [has-adr has-code has-tests has-runnote])
        present (count (filter identity [has-adr has-code has-tests has-runnote]))
        score (if (pos? total) (* 100 (/ present total)) 0)]
    {:adr has-adr
     :code has-code
     :tests has-tests
     :runnote has-runnote
     :score score}))

;; ============================================================================
;; Requirement Analysis
;; ============================================================================

(defn is-functional?
  "Check if requirement is functional"
  [metadata]
  (= (:type metadata) :functional))

(defn is-non-functional?
  "Check if requirement is non-functional"
  [metadata]
  (= (:type metadata) :non-functional))

(defn priority-level
  "Get priority level (:must, :shall, :should, :may)"
  [metadata]
  (:priority metadata))

(defn is-high-priority?
  "Check if requirement is high priority (must or shall)"
  [metadata]
  (contains? #{:must :shall} (:priority metadata)))

(defn is-implemented?
  "Check if requirement is implemented"
  [metadata]
  (= (:status metadata) :implemented))

(defn is-accepted?
  "Check if requirement is accepted"
  [metadata]
  (= (:status metadata) :accepted))

;; ============================================================================
;; Summarization
;; ============================================================================

(defn summarize-requirement
  "Generate human-readable summary of requirement metadata."
  [metadata]
  (str "ID: " (:req-id metadata)
       "\nType: " (name (:type metadata))
       "\nCategory: " (name (:category metadata))
       "\nPriority: " (name (:priority metadata))
       "\nStatus: " (name (:status metadata))
       "\nTags: " (str/join " " (map #(str ":" (name %)) (:tag metadata)))
       (when (:updated metadata)
         (str "\nUpdated: " (:updated metadata)))
       (when-let [trace (:trace metadata)]
         (str "\nTraceability:"
              (when (seq (:adr trace))
                (str "\n  ADRs: " (str/join ", " (:adr trace))))
              (when (seq (:runnote trace))
                (str "\n  RunNotes: " (str/join ", " (:runnote trace))))
              (when (seq (:code trace))
                (str "\n  Code: " (str/join ", " (:code trace))))
              (when (seq (:tests trace))
                (str "\n  Tests: " (str/join ", " (:tests trace))))))))

;; ============================================================================
;; Export for use by other scripts
;; ============================================================================

(def exports
  {:parse-req-metadata parse-req-metadata
   :extract-req-metadata extract-req-metadata
   :validate-req-metadata validate-req-metadata
   :has-adr-trace? has-adr-trace?
   :has-code-trace? has-code-trace?
   :has-test-trace? has-test-trace?
   :has-runnote-trace? has-runnote-trace?
   :traceability-coverage traceability-coverage
   :is-functional? is-functional?
   :is-non-functional? is-non-functional?
   :priority-level priority-level
   :is-high-priority? is-high-priority?
   :is-implemented? is-implemented?
   :is-accepted? is-accepted?
   :summarize-requirement summarize-requirement})

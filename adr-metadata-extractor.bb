#!/usr/bin/env bb

(ns ^:clj-kondo/ignore adr-metadata-extractor
  (:require [clojure.string :as str]))

(defn extract-adr-date
  "Extract date from ADR content in 'Date: YYYY-MM-DD' format"
  [content]
  (let [date-line (first (filter #(str/starts-with? % "Date:")
                                 (str/split-lines content)))]
    (when date-line
      (str/trim (subs date-line 5)))))

(defn extract-section-value
  "Extract value from markdown section (## Header followed by content)"
  [content section-name]
  (let [lines (str/split-lines content)
        section-pattern (re-pattern (str "^##\\s+" section-name "$"))
        section-idx (first (keep-indexed
                            #(when (re-matches section-pattern %2) %1)
                            lines))]
    (when section-idx
      (let [next-section-idx (or (first (keep-indexed
                                         #(when (and (> %1 section-idx)
                                                     (str/starts-with? %2 "##"))
                                            %1)
                                         lines))
                                 (count lines))
            section-lines (subvec (vec lines) (inc section-idx) next-section-idx)
            ;; Skip blank lines at start
            content-lines (drop-while str/blank? section-lines)]
        (when (seq content-lines)
          (str/trim (str/join " " content-lines)))))))

(defn extract-colon-wrapped-field
  "Extract field with :key: format from content"
  [content field-name]
  (let [pattern (re-pattern (str ":" field-name ":\\s*(.+)"))
        matches (re-find pattern content)]
    (when matches
      (str/trim (second matches)))))

(defn parse-tags-from-string
  "Parse tags from a string like ':ui :architecture :performance'"
  [tag-string]
  (when tag-string
    (into #{}
          (comp (map str/trim)
                (filter #(str/starts-with? % ":"))
                (map #(keyword (subs % 1))))
          (str/split tag-string #"\s+"))))

(defn extract-adr-metadata
  "Extract metadata from ADR using all known patterns"
  [content]
  (let [;; Try simple Date: format
        date (extract-adr-date content)

        ;; Try ## Status section
        status-text (extract-section-value content "Status")
        status (when status-text
                 (keyword (str/lower-case (str/trim status-text))))

        ;; Try ## Tags section
        tags-text (extract-section-value content "Tags")
        tags (parse-tags-from-string tags-text)

        ;; Also try :tags: format
        alt-tags-text (extract-colon-wrapped-field content "tags")
        alt-tags (when (and (not tags) alt-tags-text)
                   (parse-tags-from-string alt-tags-text))

        ;; Try :status: format if ## Status didn't work
        alt-status-text (extract-colon-wrapped-field content "status")
        alt-status (when (and (not status) alt-status-text)
                     (keyword (str/lower-case alt-status-text)))

        ;; Try :date: format if Date: didn't work
        alt-date (extract-colon-wrapped-field content "date")]

    (cond-> {}
      (or date alt-date) (assoc :date (or date alt-date))
      (or status alt-status) (assoc :status (or status alt-status))
      (or tags alt-tags) (assoc :tag (or tags alt-tags)))))

(defn migrate-adr-header
  "Convert ADR metadata to EDN block format"
  [content]
  (let [metadata (extract-adr-metadata content)
        edn-block (str "```edn :metadata\n"
                       (pr-str metadata)
                       "\n```")
        lines (str/split-lines content)
        title-idx (first (keep-indexed #(when (str/starts-with? %2 "#") %1) lines))]

    (if (and title-idx (seq metadata))
      ;; Insert EDN block after title
      (let [before-title (take (inc title-idx) lines)
            after-title (drop (inc title-idx) lines)]
        (str/join "\n"
                  (concat before-title
                          ["" edn-block ""]
                          after-title)))
      ;; No metadata found or no title, return unchanged
      content)))

;; Export functions for use by other scripts
(def exports
  {:extract-adr-metadata extract-adr-metadata
   :migrate-adr-header migrate-adr-header
   :extract-adr-date extract-adr-date
   :extract-section-value extract-section-value
   :parse-tags-from-string parse-tags-from-string})
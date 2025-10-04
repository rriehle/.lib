#!/usr/bin/env bb

(ns ^:clj-kondo/ignore test-framework
  "Reusable test framework for Babashka-based testing.
   Provides utilities for assertions, test running, and result reporting."
  (:require [babashka.fs :as fs]
            [babashka.process :as p]
            [clojure.string :as str]))

;; ============================================================================
;; Test State
;; ============================================================================

(def test-results
  "Global test results atom"
  (atom {:passed 0 :failed 0 :tests []}))

(defn reset-test-results!
  "Reset test results to initial state"
  []
  (reset! test-results {:passed 0 :failed 0 :tests []}))

;; ============================================================================
;; Assertions
;; ============================================================================

(defn assert-true
  "Assert that condition is true, throw ex-info with message if false"
  [condition message]
  (when-not condition
    (throw (ex-info message {:condition condition :assertion :true}))))

(defn assert-false
  "Assert that condition is false"
  [condition message]
  (when condition
    (throw (ex-info message {:condition condition :assertion :false}))))

(defn assert-equal
  "Assert that two values are equal"
  [expected actual message]
  (when-not (= expected actual)
    (throw (ex-info message
                    {:expected expected
                     :actual actual
                     :assertion :equal}))))

(defn assert-not-equal
  "Assert that two values are not equal"
  [expected actual message]
  (when (= expected actual)
    (throw (ex-info message
                    {:expected-not expected
                     :actual actual
                     :assertion :not-equal}))))

(defn assert-contains
  "Assert that collection contains element"
  [coll element message]
  (when-not (some #(= % element) coll)
    (throw (ex-info message
                    {:collection coll
                     :element element
                     :assertion :contains}))))

(defn assert-string-contains
  "Assert that string contains substring"
  [s substring message]
  (when-not (str/includes? (str s) (str substring))
    (throw (ex-info message
                    {:string s
                     :substring substring
                     :assertion :string-contains}))))

(defn assert-throws
  "Assert that function throws an exception"
  [f message]
  (try
    (f)
    (throw (ex-info message {:assertion :throws :threw false}))
    (catch Exception e
      ;; Expected - test passes
      nil)))

(defn assert-not-nil
  "Assert that value is not nil"
  [value message]
  (when (nil? value)
    (throw (ex-info message {:value value :assertion :not-nil}))))

(defn assert-nil
  "Assert that value is nil"
  [value message]
  (when-not (nil? value)
    (throw (ex-info message {:value value :assertion :nil}))))

(defn assert-count
  "Assert collection has expected count"
  [expected coll message]
  (let [actual (count coll)]
    (when-not (= expected actual)
      (throw (ex-info message
                      {:expected-count expected
                       :actual-count actual
                       :collection coll
                       :assertion :count})))))

(defn assert-seq
  "Assert that value is sequential"
  [value message]
  (when-not (sequential? value)
    (throw (ex-info message {:value value :assertion :seq}))))

(defn assert-file-exists
  "Assert that file exists"
  [path message]
  (when-not (fs/exists? path)
    (throw (ex-info message {:path path :assertion :file-exists}))))

(defn assert-file-not-exists
  "Assert that file does not exist"
  [path message]
  (when (fs/exists? path)
    (throw (ex-info message {:path path :assertion :file-not-exists}))))

;; ============================================================================
;; Property-Based Testing Utilities
;; ============================================================================

(defn gen-string
  "Generate random string of length n"
  [n]
  (apply str (repeatedly n #(rand-nth "abcdefghijklmnopqrstuvwxyz"))))

(defn gen-int
  "Generate random integer between min and max (inclusive)"
  [min max]
  (+ min (rand-int (- max min -1))))

(defn gen-seq
  "Generate sequence by repeatedly calling generator n times"
  [n generator]
  (repeatedly n generator))

(defn check-property
  "Run property test n times, return true if all pass"
  [n property-fn]
  (every? identity (repeatedly n property-fn)))

;; ============================================================================
;; Test Execution
;; ============================================================================

(defn test-case
  "Run a single test case and record results"
  [name test-fn]
  (print (str "  " name "... "))
  (flush)
  (try
    (test-fn)
    (println "✅ PASS")
    (swap! test-results update :passed inc)
    (swap! test-results update :tests conj {:name name :status :pass})
    true
    (catch Exception e
      (println (str "❌ FAIL: " (.getMessage e)))
      (when-let [data (ex-data e)]
        (when (:expected data)
          (println (str "    Expected: " (:expected data)))
          (println (str "    Actual:   " (:actual data)))))
      (swap! test-results update :failed inc)
      (swap! test-results update :tests conj
             {:name name
              :status :fail
              :error (.getMessage e)
              :data (ex-data e)})
      false)))

(defn test-suite
  "Run a suite of tests (vector of [name test-fn] pairs)"
  [suite-name tests]
  (println (str "\n" suite-name))
  (println (str/join (repeat (count suite-name) "=")))
  (doseq [[name test-fn] tests]
    (test-case name test-fn)))

;; ============================================================================
;; Test Fixtures
;; ============================================================================

(defn with-temp-dir
  "Execute function with a temporary directory, cleanup after"
  [f]
  (let [temp-dir (fs/create-temp-dir {:prefix "test-"})]
    (try
      (f temp-dir)
      (finally
        (fs/delete-tree temp-dir)))))

(defn create-test-file
  "Create a test file with content in directory"
  [dir filename content]
  (let [path (fs/path dir filename)]
    (spit (str path) content)
    path))

(defn create-test-files
  "Create multiple test files from map of filename -> content"
  [dir file-map]
  (doseq [[filename content] file-map]
    (create-test-file dir filename content)))

;; ============================================================================
;; Command Execution
;; ============================================================================

(defn run-command
  "Run command and return result map"
  [& args]
  (let [result (apply p/shell {:out :string :err :string :continue true} args)]
    {:exit (:exit result)
     :out (:out result)
     :err (:err result)}))

(defn run-script
  "Run a Babashka script and return result"
  [script-path & args]
  (apply run-command "bb" script-path args))

;; ============================================================================
;; Test Reporting
;; ============================================================================

(defn print-test-summary
  "Print test results summary"
  []
  (println "\n" (str/join (repeat 60 "=")))
  (println "Test Results Summary:")
  (println (str "  ✅ Passed: " (:passed @test-results)))
  (println (str "  ❌ Failed: " (:failed @test-results)))
  (println (str "  📊 Total:  " (+ (:passed @test-results) (:failed @test-results))))
  (println (str/join (repeat 60 "="))))

(defn exit-with-results
  "Exit with code 0 if all tests passed, 1 otherwise"
  []
  (System/exit (if (zero? (:failed @test-results)) 0 1)))

(defn run-tests
  "Run tests and return results without exiting"
  [test-cases]
  (reset-test-results!)
  (doseq [tc test-cases]
    (tc))
  {:all-passed (zero? (:failed @test-results))
   :passed (:passed @test-results)
   :failed (:failed @test-results)
   :tests (:tests @test-results)})

(defn run-tests-and-exit
  "Print summary and exit with appropriate code"
  []
  (print-test-summary)
  (exit-with-results))

;; ============================================================================
;; Export for use by other test scripts
;; ============================================================================

(def exports
  {:test-case test-case
   :test-suite test-suite
   :reset-test-results! reset-test-results!
   :assert-true assert-true
   :assert-false assert-false
   :assert-equal assert-equal
   :assert-not-equal assert-not-equal
   :assert-contains assert-contains
   :assert-string-contains assert-string-contains
   :assert-throws assert-throws
   :assert-not-nil assert-not-nil
   :assert-nil assert-nil
   :assert-count assert-count
   :assert-seq assert-seq
   :assert-file-exists assert-file-exists
   :assert-file-not-exists assert-file-not-exists
   :check-property check-property
   :gen-string gen-string
   :gen-int gen-int
   :gen-seq gen-seq
   :with-temp-dir with-temp-dir
   :create-test-file create-test-file
   :create-test-files create-test-files
   :run-command run-command
   :run-script run-script
   :print-test-summary print-test-summary
   :exit-with-results exit-with-results
   :run-tests run-tests
   :run-tests-and-exit run-tests-and-exit})

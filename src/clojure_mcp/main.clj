(ns clojure-mcp.main
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [clojure-mcp.core :as core]
            [clojure-mcp.nrepl :as nrepl]
            [clojure-mcp.prompts :as prompts]
            [clojure-mcp.tools.project.core :as project]
            [clojure-mcp.resources :as resources]
            [clojure-mcp.config :as config]
            [clojure-mcp.tools :as tools]))

;; Delegate to resources namespace
;; Note: working-dir param kept for compatibility with core API but unused
(defn make-resources [nrepl-client-atom working-dir]
  (resources/make-resources nrepl-client-atom))

;; Delegate to prompts namespace
;; Note: working-dir param kept for compatibility with core API but unused
(defn make-prompts [nrepl-client-atom working-dir]
  (prompts/make-prompts nrepl-client-atom))

(defn make-tools [nrepl-client-atom working-directory]
  ;; Use the refactored tools builder
  ;; Note: working-directory param kept for compatibility with core API but unused
  (tools/build-all-tools nrepl-client-atom))

;; DEPRECATED but maintained for backward compatability
(defn ^:deprecated my-prompts
  ([working-dir]
   (my-prompts working-dir core/nrepl-client-atom))
  ([working-dir nrepl-client-atom]
   (make-prompts nrepl-client-atom working-dir)))

(defn ^:deprecated my-resources [nrepl-client-atom working-dir]
  (resources/make-resources nrepl-client-atom))

(defn ^:deprecated my-tools [nrepl-client-atom]
  (tools/build-all-tools nrepl-client-atom))

;;; (clojure-mcp.main/start-mcp-server {:port 7888})
(defn start-mcp-server [opts]
  (core/build-and-start-mcp-server
   opts
   {:make-tools-fn make-tools
    :make-prompts-fn make-prompts
    :make-resources-fn make-resources}))

;; not sure if this is even needed

;; start the server

;; Example parameterized prompt
(defn code-review-prompt-example []
  {:name "code-review-prompt"
   :description "Generate a code review prompt for a specific file or namespace"
   :arguments [{:name "file-path"
                :description "The file path to review"
                :required? true}
               {:name "focus-areas"
                :description "Specific areas to focus on (e.g., 'performance,style,testing')"
                :required? false}]
   :prompt-fn (fn [_ request-args clj-result-k]
                (let [file-path (get request-args "file-path")
                      focus-areas (get request-args "focus-areas" "general code quality")]
                  (clj-result-k
                   {:description (str "Code review for: " file-path)
                    :messages
                    [{:role :user
                      :content
                      (str "Please perform a thorough code review of the file at: "
                           file-path "\n\n"
                           "Focus areas: " focus-areas "\n\n"
                           "Consider:\n"
                           "1. Code style and Clojure idioms\n"
                           "2. Performance implications\n"
                           "3. Error handling\n"
                           "4. Function complexity and readability\n"
                           "5. Missing tests or edge cases\n\n"
                           "Please use the read_file tool to examine the code, "
                           "then provide detailed feedback.")}]})))})


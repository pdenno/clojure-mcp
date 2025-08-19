(ns clojure-mcp.tools.code-critique.tool
  "Implementation of the code critique tool using the tool-system multimethod approach."
  (:require
   [clojure-mcp.tool-system :as tool-system]
   [clojure-mcp.linting :as linting]
   [clojure-mcp.sexp.paren-utils :as paren-utils]
   [clojure-mcp.config :as config]
   [clojure-mcp.agent.langchain :as chain]
   [clojure-mcp.agent.langchain.model :as model]
   [clojure-mcp.agent.general-agent :as general-agent]
   [clojure.java.io :as io]
   [clojure.string :as string]))

(def code-critique-system-message-template
  "The system message template for the code critique tool, loaded from resources"
  (slurp (io/resource "clojure_mcp/tools/code_critique/system_message.md")))

(def code-critique-description
  "The default description for the code critique tool, loaded from resources"
  (slurp (io/resource "clojure_mcp/tools/code_critique/description.md")))

(defn format-system-message
  "Formats the system message template with the improvement count.
   
   Args:
   - improvement-count: Number of improvements to suggest (default: 2)"
  [improvement-count]
  (let [n (or improvement-count 2)
        nstr (if (= 1 n) "single" (str n))
        improvement-label (if (> n 1) "improvements" "improvement")]
    (-> code-critique-system-message-template
        (string/replace "{improvement_count}" nstr)
        (string/replace "{improvement_label}" improvement-label))))

(defn validate-code-critique-inputs
  "Validates inputs for the code critique function"
  [inputs]
  (let [{:keys [code context]} inputs]
    (when-not code
      (throw (ex-info "Missing required parameter: code"
                      {:inputs inputs})))

    (when (and context (not (string? context)))
      (throw (ex-info "Parameter 'context' must be a string when provided"
                      {:inputs inputs
                       :error-details [(str "Got: " (type context))]})))

    ;; First, try to repair code with delimiter errors
    (let [linted (linting/lint code)]
      (if (and linted (:error? linted))
        ;; Check if these are delimiter errors that might be repairable
        (if (paren-utils/has-delimiter-errors? linted)
          ;; Try to repair the code
          (if-let [repaired-code (paren-utils/parinfer-repair code)]
            ;; Use the repaired code
            (assoc inputs :code repaired-code)
            ;; Repair failed, check if original code still has errors
            (let [lint-result linted]
              (throw (ex-info (str "Syntax errors detected in Clojure code:\n"
                                   (:report lint-result)
                                   "\nPlease fix the syntax errors before critiquing.")
                              {:inputs inputs
                               :error-details (:report lint-result)}))))
          ;; Not delimiter errors, report the syntax error
          (throw (ex-info (str "Syntax errors detected in Clojure code:\n"
                               (:report linted)
                               "\nPlease fix the syntax errors before critiquing.")
                          {:inputs inputs
                           :error-details (:report linted)})))
        ;; No lint errors, return inputs with original code
        inputs))))

;; Factory function to create the tool configuration
(defn create-code-critique-tool
  "Creates the code critique tool configuration.
   Checks for :tools-config {:code_critique {:model ...}} in the nrepl-client-atom
   and automatically creates the model if configured.
   
   Args:
   - nrepl-client-atom: Required nREPL client atom
   - model: Optional pre-built langchain model to use instead of auto-detection or config"
  ([nrepl-client-atom]
   (create-code-critique-tool nrepl-client-atom nil))
  ([nrepl-client-atom model]
   (let [working-directory (config/get-nrepl-user-dir @nrepl-client-atom)
         ;; Get tool-specific config
         tool-config (config/get-tool-config @nrepl-client-atom :code_critique)

         ;; Get improvement count from config or default to 2
         improvement-count (or (:improvement-count tool-config) 2)

         ;; Get context from tool config
         context-config (:context tool-config)

         ;; Get system message from tool config or fall back to default
         system-message (or (:system-message tool-config)
                            (format-system-message improvement-count))

         ;; Get description from tool config or fall back to default
         description (or (:description tool-config)
                         code-critique-description)

         ;; Check for tool-specific config if no model provided
         final-model (or model
                         ;; Try to get model from config
                         (model/get-tool-model @nrepl-client-atom :code_critique)
                         ;; default to reasoning model
                         (some-> (chain/reasoning-agent-model)
                                 (.build)))]
     {:tool-type :code-critique
      :nrepl-client-atom nrepl-client-atom
      :model final-model
      :system-message system-message
      :description description
      :context (when context-config
                 (general-agent/build-context-strings nrepl-client-atom working-directory context-config))
      :working-directory working-directory
      :context-config context-config
      :improvement-count improvement-count})))

;; Implement the required multimethods for the code critique tool
(defmethod tool-system/tool-name :code-critique [_]
  "code_critique")

(defmethod tool-system/tool-description :code-critique [tool]
  (or (:description tool) code-critique-description))

;; TODO file-path and optional symbol 
(defmethod tool-system/tool-schema :code-critique [_]
  {:type :object
   :properties {:code {:type :string
                       :description "The Clojure code to analyze and critique"}
                :context {:type :string
                          :description "Optional context from previous conversation or system state"}}
   :required [:code]})

(defmethod tool-system/validate-inputs :code-critique [_ inputs]
  (validate-code-critique-inputs inputs))

(defmethod tool-system/execute-tool :code-critique [tool {:keys [code context]}]
  (let [{:keys [nrepl-client-atom model system-message
                working-directory context-config]} tool
        ;; Try to get cached agent or create new one
        cache-key ::code-critique-service
        cached-agent (get @nrepl-client-atom cache-key)
        agent (or cached-agent
                  (let [new-agent (general-agent/create-general-agent
                                   {:system-prompt system-message
                                    :context (:context tool)
                                    :tools [] ; Code critique doesn't need tools
                                    :model model
                                    :memory-size 35})] ; Smaller memory for focused critique
                    ;; Cache the agent
                    (swap! nrepl-client-atom assoc cache-key new-agent)
                    new-agent))]

    ;; Update context if config changed
    (when (and cached-agent context-config)
      (let [fresh-context (general-agent/build-context-strings nrepl-client-atom working-directory context-config)]
        (when (not= (:context tool) fresh-context)
          (general-agent/update-agent-context agent fresh-context))))

    ;; Chat with the agent, including the optional context parameter
    (let [message (cond-> code
                    (not (string/blank? context))
                    (str "\n\n```context\n" context "\n```\n"))
          result (general-agent/chat-with-agent agent message)]
      ;; Return in the expected format for code critique
      {:critique (:result result)
       :error (:error result)})))

(defmethod tool-system/format-results :code-critique [_ {:keys [critique error]}]
  {:result [critique] :error error})

;; Function that returns the registration map
(defn code-critique-tool
  "Returns a tool registration for the code-critique tool compatible with the MCP system.
   
   Usage:
   
   Basic usage with auto-detected model:
   (code-critique-tool nrepl-client-atom)
   
   With custom model configuration:
   (code-critique-tool nrepl-client-atom {:model my-custom-model})
   
   Where:
   - nrepl-client-atom: Required nREPL client atom
   - config: Optional config map with keys:
     - :model - Pre-built langchain model to use instead of auto-detection
   
   Examples:
   ;; Default model (uses reasoning-agent-model)
   (def my-critic (code-critique-tool nrepl-client-atom))
   
   ;; Custom Anthropic model
   (def custom-model (-> (chain/create-anthropic-model \"claude-3-opus-20240229\") (.build)))
   (def custom-critic (code-critique-tool nrepl-client-atom {:model custom-model}))
   
   ;; Custom OpenAI reasoning model
   (def reasoning-model (-> (chain/create-openai-model \"o1-preview\") (.build)))
   (def reasoning-critic (code-critique-tool nrepl-client-atom {:model reasoning-model}))"
  ([nrepl-client-atom]
   (code-critique-tool nrepl-client-atom nil))
  ([nrepl-client-atom {:keys [model]}]
   (tool-system/registration-map (create-code-critique-tool nrepl-client-atom model))))

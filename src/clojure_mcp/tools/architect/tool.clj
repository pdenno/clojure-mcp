(ns clojure-mcp.tools.architect.tool
  (:require [clojure-mcp.tool-system :as tool-system]
            [clojure-mcp.config :as config]
            [clojure-mcp.agent.langchain :as chain]
            [clojure-mcp.agent.langchain.model :as model]
            [clojure-mcp.agent.general-agent :as general-agent]
            [clojure.java.io :as io]
            ;; Tools for the architect
            [clojure-mcp.tools.unified-read-file.tool :as read-file-tool]
            [clojure-mcp.tools.directory-tree.tool :as directory-tree-tool]
            [clojure-mcp.tools.grep.tool :as grep-tool]
            [clojure-mcp.tools.glob-files.tool :as glob-files-tool]
            [clojure-mcp.tools.project.tool :as project-tool]
            [clojure-mcp.tools.think.tool :as think-tool]))

(def architect-system-message
  "The system message for the architect, loaded from resources"
  (slurp (io/resource "clojure_mcp/tools/architect/system_message.md")))

(def architect-description
  "The default description for the architect, loaded from resources"
  (slurp (io/resource "clojure_mcp/tools/architect/description.md")))

(defn validate-architect-inputs
  "Validates inputs for the architect function"
  [inputs]
  (cond
    (nil? inputs)
    (throw (ex-info "Missing inputs" {:error-details ["No input parameters provided"]}))

    (nil? (:prompt inputs))
    (throw (ex-info "Missing required parameter: prompt"
                    {:error-details ["The 'prompt' parameter is required"]}))

    (not (string? (:prompt inputs)))
    (throw (ex-info "Parameter 'prompt' must be a string"
                    {:error-details [(str "Got: " (type (:prompt inputs)))]}))

    (and (:context inputs) (not (string? (:context inputs))))
    (throw (ex-info "Parameter 'context' must be a string when provided"
                    {:error-details [(str "Got: " (type (:context inputs)))]}))

    :else
    inputs))

(defn build-architect-tools
  "Builds the tools for the architect agent.
   These tools are safe for exploration and analysis.
   
   Args:
   - nrepl-client-atom: The nREPL client atom
   
   Returns: A vector of tools"
  [nrepl-client-atom]
  (mapv
   #(% nrepl-client-atom)
   [read-file-tool/unified-read-file-tool
    directory-tree-tool/directory-tree-tool
    grep-tool/grep-tool
    glob-files-tool/glob-files-tool
    project-tool/inspect-project-tool
    think-tool/think-tool]))

(defn create-architect-tool
  "Creates the architect tool configuration.
   Checks for :tools-config {:architect {:model ...}} in the nrepl-client-atom
   and automatically creates the model if configured.
   
   Args:
   - nrepl-client-atom: Required nREPL client atom
   - model: Optional pre-built langchain model to use instead of auto-detection or config"
  ([nrepl-client-atom]
   (create-architect-tool nrepl-client-atom nil))
  ([nrepl-client-atom model]
   (let [working-directory (config/get-nrepl-user-dir @nrepl-client-atom)
         ;; Get tool-specific config
         tool-config (config/get-tool-config @nrepl-client-atom :architect)

         ;; Get context from tool config
         context-config (:context tool-config)

         ;; Get system message from tool config or fall back to default
         system-message (or (:system-message tool-config)
                            architect-system-message)

         ;; Get description from tool config or fall back to default
         description (or (:description tool-config)
                         architect-description)

         ;; Check for tool-specific config if no model provided
         final-model (or model
                         ;; Try to get model from config
                         (model/get-tool-model @nrepl-client-atom :architect)
                         ;; default to reasoning model
                         (some-> (chain/reasoning-agent-model)
                                 (.build)))]
     {:tool-type :architect
      :nrepl-client-atom nrepl-client-atom
      :model final-model
      :system-message system-message
      :description description
      :context (general-agent/build-context-strings nrepl-client-atom working-directory context-config)
      :tools (build-architect-tools nrepl-client-atom)
      :working-directory working-directory
      :context-config context-config})))

(defn architect-tool
  "Returns a tool registration for the architect tool compatible with the MCP system.
   
   Usage:
   
   Basic usage with auto-detected reasoning model:
   (architect-tool nrepl-client-atom)
   
   With custom model configuration:
   (architect-tool nrepl-client-atom {:model my-custom-model})
   
   Where:
   - nrepl-client-atom: Required nREPL client atom
   - config: Optional config map with keys:
     - :model - Pre-built langchain model to use instead of auto-detection
   
   Examples:
   ;; Default reasoning model (with medium reasoning effort)
   (def my-architect (architect-tool nrepl-client-atom))
   
   ;; Custom Anthropic model
   (def fast-model (-> (chain/create-anthropic-model \"claude-3-haiku-20240307\") (.build)))
   (def fast-architect (architect-tool nrepl-client-atom {:model fast-model}))
   
   ;; Custom OpenAI reasoning model with high effort
   (def reasoning-model (-> (chain/create-openai-model \"o1-preview\")
                            (chain/default-request-parameters #(chain/reasoning-effort % :high))
                            (.build)))
   (def reasoning-architect (architect-tool nrepl-client-atom {:model reasoning-model}))"
  ([nrepl-client-atom]
   (architect-tool nrepl-client-atom nil))
  ([nrepl-client-atom {:keys [model]}]
   (tool-system/registration-map (create-architect-tool nrepl-client-atom model))))

(defmethod tool-system/tool-name :architect [_]
  "architect")

(defmethod tool-system/tool-description :architect [tool]
  (or (:description tool) architect-description))

(defmethod tool-system/tool-schema :architect [_]
  {:type :object
   :properties {:prompt {:type :string
                         :description "The technical request or coding task to analyze"}
                :context {:type :string
                          :description "Optional context from previous conversation or system state"}}
   :required [:prompt]})

(defmethod tool-system/validate-inputs :architect [_ inputs]
  (validate-architect-inputs inputs))

(defmethod tool-system/execute-tool :architect [tool {:keys [prompt context]}]
  (let [{:keys [nrepl-client-atom model system-message
                tools working-directory context-config]} tool
        ;; Try to get cached agent or create new one
        cache-key ::architect-service
        cached-agent (get @nrepl-client-atom cache-key)
        agent (or cached-agent
                  (let [new-agent (general-agent/create-general-agent
                                   {:system-prompt system-message
                                    :context (:context tool)
                                    :tools tools
                                    :model model
                                    :memory-size general-agent/DEFAULT-MEMORY-SIZE})]
                    ;; Cache the agent
                    (swap! nrepl-client-atom assoc cache-key new-agent)
                    new-agent))]

    ;; Update context if config changed
    (when (and cached-agent context-config)
      (let [fresh-context (general-agent/build-context-strings nrepl-client-atom working-directory context-config)]
        (when (not= (:context tool) fresh-context)
          (general-agent/update-agent-context agent fresh-context))))

    ;; Chat with the agent, including the optional context parameter
    (general-agent/chat-with-agent agent
                                   (cond-> prompt
                                     (not (clojure.string/blank? context))
                                     (str "\n\n```context\n" context "\n```\n")))))

(defmethod tool-system/format-results :architect [_ {:keys [result error] :as results}]
  {:result [result]
   :error error})
(ns clojure-mcp.tools.dispatch-agent.tool
  (:require [clojure-mcp.tool-system :as tool-system]
            [clojure-mcp.config :as config]
            [clojure-mcp.agent.langchain :as chain]
            [clojure-mcp.agent.langchain.model :as model]
            [clojure-mcp.agent.general-agent :as general-agent]
            [clojure.java.io :as io]))

(def dispatch-agent-system-message
  "The system message for the dispatch agent, loaded from resources"
  (slurp (io/resource "clojure_mcp/tools/dispatch_agent/system_message.md")))

(def dispatch-agent-description
  "The default description for the dispatch agent, loaded from resources"
  (slurp (io/resource "clojure_mcp/tools/dispatch_agent/description.md")))

(defn validate-dispatch-agent-inputs
  "Validates inputs for the dispatch-agent function"
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

    :else
    inputs))

(defn create-dispatch-agent-tool
  "Creates the dispatch agent tool configuration.
   Checks for :tools-config {:dispatch_agent {:model ...}} in the nrepl-client-atom
   and automatically creates the model if configured.
   
   Args:
   - nrepl-client-atom: Required nREPL client atom
   - model: Optional pre-built langchain model to use instead of auto-detection or config"
  ([nrepl-client-atom]
   (create-dispatch-agent-tool nrepl-client-atom nil))
  ([nrepl-client-atom model]
   (let [working-directory (config/get-nrepl-user-dir @nrepl-client-atom)
         ;; Get tool-specific config
         tool-config (config/get-tool-config @nrepl-client-atom :dispatch_agent)

         ;; Get context from tool config or fall back to legacy config
         context-config (or (:context tool-config)
                            (config/get-dispatch-agent-context @nrepl-client-atom))

         ;; Get system message from tool config or fall back to default
         system-message (or (:system-message tool-config)
                            dispatch-agent-system-message)

         ;; Get description from tool config or fall back to default
         description (or (:description tool-config)
                         dispatch-agent-description)

         ;; Check for tool-specific config if no model provided
         final-model (or model
                         ;; Try to get model from config
                         (model/get-tool-model @nrepl-client-atom :dispatch_agent)
                         ;; default to available models
                         (some-> (chain/agent-model)
                                 (.build)))]
     {:tool-type :dispatch-agent
      :nrepl-client-atom nrepl-client-atom
      :model final-model
      :system-message system-message
      :description description
      :context (general-agent/build-context-strings nrepl-client-atom working-directory context-config)
      :tools (general-agent/build-read-only-tools nrepl-client-atom)
      :working-directory working-directory
      :context-config context-config})))

(defn dispatch-agent-tool
  "Returns a tool registration for the dispatch-agent tool compatible with the MCP system.
   
   Usage:
   
   Basic usage with auto-detected model:
   (dispatch-agent-tool nrepl-client-atom)
   
   With custom model configuration:
   (dispatch-agent-tool nrepl-client-atom {:model my-custom-model})
   
   Where:
   - nrepl-client-atom: Required nREPL client atom
   - config: Optional config map with keys:
     - :model - Pre-built langchain model to use instead of auto-detection
   
   Examples:
   ;; Default model
   (def my-agent (dispatch-agent-tool nrepl-client-atom))
   
   ;; Custom Anthropic model
   (def fast-model (-> (chain/create-anthropic-model \"claude-3-haiku-20240307\") (.build)))
   (def fast-agent (dispatch-agent-tool nrepl-client-atom {:model fast-model}))
   
   ;; Custom OpenAI model
   (def reasoning-model (-> (chain/create-openai-model \"o1-preview\") (.build)))
   (def reasoning-agent (dispatch-agent-tool nrepl-client-atom {:model reasoning-model}))"
  ([nrepl-client-atom]
   (dispatch-agent-tool nrepl-client-atom nil))
  ([nrepl-client-atom {:keys [model]}]
   (tool-system/registration-map (create-dispatch-agent-tool nrepl-client-atom model))))

(defmethod tool-system/tool-name :dispatch-agent [_]
  "dispatch_agent")

(defmethod tool-system/tool-description :dispatch-agent [tool]
  (or (:description tool) dispatch-agent-description))

(defmethod tool-system/tool-schema :dispatch-agent [_]
  {:type :object
   :properties {:prompt {:type :string
                         :description "The prompt to send to the agent"}}
   :required [:prompt]})

(defmethod tool-system/validate-inputs :dispatch-agent [_ inputs]
  (validate-dispatch-agent-inputs inputs))

(defmethod tool-system/execute-tool :dispatch-agent [tool {:keys [prompt]}]
  (let [{:keys [nrepl-client-atom model system-message context
                tools working-directory context-config]} tool
        ;; Try to get cached agent or create new one
        cache-key ::dispatch-agent-service
        cached-agent (get @nrepl-client-atom cache-key)
        agent (or cached-agent
                  (let [new-agent (general-agent/create-general-agent
                                   {:system-prompt system-message
                                    :context context
                                    :tools tools
                                    :model model
                                    :memory-size general-agent/DEFAULT-MEMORY-SIZE})]
                    ;; Cache the agent
                    (swap! nrepl-client-atom assoc cache-key new-agent)
                    new-agent))]

    ;; Update context if config changed
    (when (and cached-agent context-config)
      (let [fresh-context (general-agent/build-context-strings nrepl-client-atom working-directory context-config)]
        (when (not= context fresh-context)
          (general-agent/update-agent-context agent fresh-context))))

    ;; Chat with the agent
    (general-agent/chat-with-agent agent prompt)))

(defmethod tool-system/format-results :dispatch-agent [_ {:keys [result error] :as results}]
  {:result [result]
   :error error})

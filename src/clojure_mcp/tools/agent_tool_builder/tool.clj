(ns clojure-mcp.tools.agent-tool-builder.tool
  "MCP tool interface for dynamically creating agent tools from configuration"
  (:require
   [clojure-mcp.config :as config]
   [clojure-mcp.tool-system :as tool-system]
   [clojure-mcp.tools.agent-tool-builder.core :as core]
   [clojure.tools.logging :as log]))

(defn create-single-agent-tool
  "Creates a tool registration for a single agent configuration.
   
   Args:
   - nrepl-client-atom: The nREPL client atom
   - agent-config: Map with agent configuration
   
   Returns: Tool registration map"
  [nrepl-client-atom agent-config]
  (let [{:keys [id name description]} agent-config
        ;; Create a unique tool type for this agent
        tool-type (keyword (str "configured-agent-" (clojure.core/name id)))]

    ;; Define the multimethods for this specific agent tool
    (defmethod tool-system/tool-name tool-type [_]
      name)

    (defmethod tool-system/tool-description tool-type [_]
      description)

    (defmethod tool-system/tool-schema tool-type [_]
      {:type :object
       :properties {:prompt {:type :string
                             :description "The prompt to send to the agent"}}
       :required [:prompt]})

    (defmethod tool-system/validate-inputs tool-type [_ inputs]
      (cond
        (nil? inputs)
        (throw (ex-info "Missing inputs"
                        {:error-details ["No input parameters provided"]}))

        (nil? (:prompt inputs))
        (throw (ex-info "Missing required parameter: prompt"
                        {:error-details ["The 'prompt' parameter is required"]}))

        (not (string? (:prompt inputs)))
        (throw (ex-info "Parameter 'prompt' must be a string"
                        {:error-details [(str "Got: " (type (:prompt inputs)))]}))

        :else
        inputs))

    (defmethod tool-system/execute-tool tool-type [tool {:keys [prompt]}]
      (let [{:keys [nrepl-client-atom agent-config]} tool]
        (core/chat-with-configured-agent nrepl-client-atom agent-config prompt)))

    (defmethod tool-system/format-results tool-type [_ {:keys [result error] :as results}]
      {:result [result]
       :error error})

    ;; Return the registration map
    (tool-system/registration-map
     {:tool-type tool-type
      :nrepl-client-atom nrepl-client-atom
      :agent-config agent-config})))

(defn create-agent-tools
  "Creates tool registrations for all configured agents.
   
   This function reads the :agents configuration from config.edn and
   creates a separate tool for each configured agent. Each agent becomes
   its own tool with its configured name, description, and behavior.
   
   Args:
   - nrepl-client-atom: The nREPL client atom
   
   Returns: Vector of tool registration maps (one per configured agent)"
  [nrepl-client-atom]
  (let [agents-config (config/get-agents-config @nrepl-client-atom)]
    (if (empty? agents-config)
      (do
        (log/debug "No agents configured in :agents config")
        [])
      (do
        (log/info (str "Creating " (count agents-config) " configured agent tools"))
        (mapv #(create-single-agent-tool nrepl-client-atom %) agents-config)))))

(defn agent-tool-builder
  "Convenience function for creating agent tools.
   Same as create-agent-tools but follows naming convention of other tools.
   
   Args:
   - nrepl-client-atom: The nREPL client atom
   
   Returns: Vector of tool registration maps"
  [nrepl-client-atom]
  (create-agent-tools nrepl-client-atom))
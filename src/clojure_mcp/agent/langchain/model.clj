(ns clojure-mcp.agent.langchain.model
  (:require
   [clojure.string :as string])
  (:import
   [dev.langchain4j.model.anthropic
    AnthropicChatModel
    AnthropicChatModelName]
   [dev.langchain4j.model.googleai
    GoogleAiGeminiChatModel
    GeminiThinkingConfig]
   [dev.langchain4j.model.openai
    OpenAiChatModel
    OpenAiChatRequestParameters]))

(def model-base
  "Base configuration for standard models"
  {:temperature 1
   :max-tokens 4096
   :max-retries 3
   :timeout 60000})

(def reasoning-model-base
  "Base configuration for reasoning models"
  {:temperature 1
   :max-tokens 8192
   :max-retries 3
   :timeout 120000})

(def thinking-base
  "Common thinking configuration for reasoning models"
  {:enabled true
   :return true
   :send true
   :effort :medium})

(def default-configs
  {:openai/o4-mini
   (merge model-base
          {:model-name "o4-mini"})

   :openai/o4-mini-reasoning
   (merge reasoning-model-base
          {:model-name "o4-mini"
           :thinking {:effort :medium}})

   :google/gemini-2-5-flash
   (merge model-base
          {:model-name "gemini-2.5-flash"})

   :google/gemini-2-5-flash-reasoning
   (merge reasoning-model-base
          {:model-name "gemini-2.5-flash"
           :thinking thinking-base})

   :anthropic/claude-sonnet-4
   (merge model-base
          {:model-name AnthropicChatModelName/CLAUDE_SONNET_4_20250514})

   :anthropic/claude-sonnet-4-reasoning
   (merge reasoning-model-base
          {:model-name AnthropicChatModelName/CLAUDE_SONNET_4_20250514
           :thinking (merge thinking-base
                            {:budget-tokens 4096})})})

(defn get-provider [model-key]
  (-> model-key namespace keyword))

(defn merge-with-defaults [model-key config-overrides]
  (let [defaults (get default-configs model-key {})]
    (merge defaults config-overrides)))

(defn- ensure-api-key
  "Ensures API key is present, getting from environment if needed"
  [config provider]
  (if (:api-key config)
    config
    (let [env-key-mapping {:openai "OPENAI_API_KEY"
                           :google "GEMINI_API_KEY"
                           :anthropic "ANTHROPIC_API_KEY"}
          env-key (get env-key-mapping provider)]
      (if-let [api-key (and env-key (System/getenv env-key))]
        (assoc config :api-key api-key)
        config))))

;; Multimethod for creating model builders based on provider
(defmulti create-builder
  "Creates a model builder for the given provider and config.
   Returns the builder object without calling .build()
   
   Dispatches on provider keyword (:anthropic, :google, :openai, etc.)"
  (fn [provider config] provider))

;; Helper function for applying common parameters
(defn- apply-common-params [builder config]
  (cond-> builder
    (:api-key config) (.apiKey (:api-key config))
    (:base-url config) (.baseUrl (:base-url config))
    (:temperature config) (.temperature (double (:temperature config)))
    (:top-p config) (.topP (double (:top-p config)))
    ;; Note: max-tokens handled separately per provider
    ;; Anthropic/OpenAI use maxTokens, Gemini uses maxOutputTokens
    (:stop-sequences config) (.stopSequences (:stop-sequences config))
    (:max-retries config) (.maxRetries (int (:max-retries config)))
    (:log-requests config) (.logRequests (:log-requests config))
    (:log-responses config) (.logResponses (:log-responses config))))

;; Anthropic implementation
(defmethod create-builder :anthropic
  [_ config]
  (let [builder (AnthropicChatModel/builder)]
    (-> builder
        (apply-common-params config)
        (cond->
         (:model-name config) (.modelName (:model-name config))
         (:max-tokens config) (.maxTokens (int (:max-tokens config)))
         (:top-k config) (.topK (int (:top-k config)))
          ;; Thinking configuration
         (get-in config [:thinking :enabled]) (.thinkingType "enabled")
         (get-in config [:thinking :return]) (.returnThinking true)
         (get-in config [:thinking :send]) (.sendThinking true)
         (get-in config [:thinking :budget-tokens])
         (.thinkingBudgetTokens (int (get-in config [:thinking :budget-tokens])))
          ;; Anthropic-specific
         (get-in config [:anthropic :version]) (.version (get-in config [:anthropic :version]))
         (get-in config [:anthropic :beta]) (.beta (get-in config [:anthropic :beta]))
         (get-in config [:anthropic :cache-system-messages])
         (.cacheSystemMessages (get-in config [:anthropic :cache-system-messages]))
         (get-in config [:anthropic :cache-tools])
         (.cacheTools (get-in config [:anthropic :cache-tools]))))))

;; Google Gemini implementation
(defn- build-thinking-config [thinking-params]
  (when thinking-params
    (let [effort (:effort thinking-params :medium)
          budget (or (:budget-tokens thinking-params)
                     (get {:low 1024 :medium 4096 :high 8192} effort))]
      (-> (GeminiThinkingConfig/builder)
          (.includeThoughts true)
          (.thinkingBudget (int budget))
          (.build)))))

(defmethod create-builder :google
  [_ config]
  (let [builder (GoogleAiGeminiChatModel/builder)]
    (-> builder
        (apply-common-params config)
        (cond->
         (:model-name config) (.modelName (:model-name config))
         (:max-tokens config) (.maxOutputTokens (int (:max-tokens config)))
         (:top-k config) (.topK (int (:top-k config)))
         (:seed config) (.seed (int (:seed config)))
         (:frequency-penalty config) (.frequencyPenalty (double (:frequency-penalty config)))
         (:presence-penalty config) (.presencePenalty (double (:presence-penalty config)))
          ;; Thinking configuration
         (get-in config [:thinking :enabled])
         (.thinkingConfig (build-thinking-config (:thinking config)))
         (get-in config [:thinking :return]) (.returnThinking true)
         (get-in config [:thinking :send]) (.sendThinking true)
          ;; Gemini-specific
         (get-in config [:gemini :allow-code-execution])
         (.allowCodeExecution (get-in config [:gemini :allow-code-execution]))
         (get-in config [:gemini :include-code-execution-output])
         (.includeCodeExecutionOutput (get-in config [:gemini :include-code-execution-output]))
         (get-in config [:gemini :response-logprobs])
         (.responseLogprobs (get-in config [:gemini :response-logprobs]))
         (get-in config [:gemini :enable-enhanced-civic-answers])
         (.enableEnhancedCivicAnswers (get-in config [:gemini :enable-enhanced-civic-answers]))
         (get-in config [:gemini :logprobs])
         (.logprobs (int (get-in config [:gemini :logprobs])))))))

;; OpenAI implementation
(defn- apply-openai-reasoning-params [builder config]
  (if-let [effort (get-in config [:thinking :effort])]
    (let [params-builder (OpenAiChatRequestParameters/builder)]
      (.defaultRequestParameters
       builder
       (-> params-builder
           (.reasoningEffort (name effort))
           (.build))))
    builder))

(defmethod create-builder :openai
  [_ config]
  (let [builder (OpenAiChatModel/builder)]
    (-> builder
        (apply-common-params config)
        (cond->
         (:model-name config) (.modelName (:model-name config))
         (:max-tokens config) (.maxTokens (int (:max-tokens config)))
         (:seed config) (.seed (int (:seed config)))
         (:frequency-penalty config) (.frequencyPenalty (double (:frequency-penalty config)))
         (:presence-penalty config) (.presencePenalty (double (:presence-penalty config)))
          ;; Thinking configuration (for reasoning models)
         (get-in config [:thinking :effort]) (apply-openai-reasoning-params config)
         (get-in config [:thinking :return]) (.returnThinking true)
          ;; OpenAI-specific
         (get-in config [:openai :organization-id])
         (.organizationId (get-in config [:openai :organization-id]))
         (get-in config [:openai :project-id])
         (.projectId (get-in config [:openai :project-id]))
         (get-in config [:openai :max-completion-tokens])
         (.maxCompletionTokens (int (get-in config [:openai :max-completion-tokens])))
         (get-in config [:openai :logit-bias])
         (.logitBias (get-in config [:openai :logit-bias]))
         (get-in config [:openai :strict-json-schema])
         (.strictJsonSchema (get-in config [:openai :strict-json-schema]))
         (get-in config [:openai :user])
         (.user (get-in config [:openai :user]))
         (get-in config [:openai :strict-tools])
         (.strictTools (get-in config [:openai :strict-tools]))
         (get-in config [:openai :parallel-tool-calls])
         (.parallelToolCalls (get-in config [:openai :parallel-tool-calls]))
         (get-in config [:openai :store])
         (.store (get-in config [:openai :store]))
         (get-in config [:openai :metadata])
         (.metadata (get-in config [:openai :metadata]))
         (get-in config [:openai :service-tier])
         (.serviceTier (get-in config [:openai :service-tier]))))))

;; Default implementation for unknown providers
(defmethod create-builder :default
  [provider _]
  (throw (ex-info "Unknown provider" {:provider provider})))

;; Public API functions

(defn create-model-builder
  "Creates a model builder from a configuration map.
   
   Usage:
   (create-model-builder :openai/o4-mini {:api-key \"...\"})
   (create-model-builder :anthropic/claude-sonnet-4-reasoning 
                         {:api-key \"...\" 
                          :thinking {:effort :high}})
   
   Returns a builder object. Call .build() on it to get the final model."
  [model-key config-overrides]
  (let [provider (get-provider model-key)
        config (-> (merge-with-defaults model-key config-overrides)
                   (ensure-api-key provider))]
    (create-builder provider config)))

(defn create-builder-from-config
  "Creates a model builder from a complete configuration map without defaults.
   Provider must be specified explicitly.
   
   Returns a builder object. Call .build() on it to get the final model."
  [provider config]
  (let [config (ensure-api-key config provider)]
    (create-builder provider config)))

(defn build-model
  "Convenience function that creates a model builder and builds it immediately.
   
   Usage:
   (build-model :openai/o4-mini {:temperature 0.5})
   
   Returns a fully built model ready for use."
  [model-key config-overrides]
  (.build (create-model-builder model-key config-overrides)))

(defn build-model-from-config
  "Convenience function that creates and builds a model from config.
   
   Returns a fully built model ready for use."
  [provider config]
  (.build (create-builder-from-config provider config)))

(defn available-models
  "Returns a list of available model keys with default configurations."
  []
  (keys default-configs))

(defn extend-provider
  "Helper function to extend the multimethod for custom providers.
   
   Usage:
   (extend-provider :my-provider 
     (fn [_ config]
       (-> (MyProviderModel/builder)
           (apply-common-params config)
           ...)))"
  [provider builder-fn]
  (defmethod create-builder provider
    [p c]
    (builder-fn p c)))

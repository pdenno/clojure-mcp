(ns clojure-mcp.agent.langchain
  (:require
   [clojure.data.json :as json]
   [clojure-mcp.agent.langchain.schema :as schema]
   [clojure.tools.logging :as log]
   [clojure.string :as string]
   [clojure.pprint])
  (:import
   ;; LangChain4j Core and Service classes
   [dev.langchain4j.service AiServices MemoryId]
   [dev.langchain4j.agent.tool ToolSpecification #_ToolParameter]
   [dev.langchain4j.service.tool ToolExecutor ToolExecution]
   [dev.langchain4j.service TokenStream]
   [dev.langchain4j.data.message SystemMessage UserMessage TextContent]
   [dev.langchain4j.agent.tool ToolExecutionRequest]
   [dev.langchain4j.model.chat.request.json JsonObjectSchema]
   [dev.langchain4j.memory ChatMemory]
   [dev.langchain4j.memory.chat MessageWindowChatMemory]
   [dev.langchain4j.model.chat.request ChatRequest ToolChoice]

   ;; LangChain4j Model classes (using Anthropic as an example)
   [dev.langchain4j.model.anthropic
    AnthropicChatModel
    AnthropicStreamingChatModel
    AnthropicChatModelName]
   [dev.langchain4j.model.googleai
    GoogleAiGeminiChatModel
    GeminiThinkingConfig]
   [java.util.function Consumer Function]

   [dev.langchain4j.model.openai
    OpenAiChatModel
    OpenAiChatRequestParameters
    OpenAiChatModelName]

   ;; Java Time API
   [java.time LocalTime LocalDate ZoneId]))

(def default-max-memory 100)

;; simple API as we don't really need more right now

(defn create-gemini-model [model-name]
  (-> (GoogleAiGeminiChatModel/builder)
      (.apiKey (System/getenv "GEMINI_API_KEY"))
      (.maxRetries (int 3))
      (.modelName model-name)))

(defn gemini-reasoning-effort [builder effort]
  (let [budget (get {:low 1024 :medium 4096 :high 8192} (or effort :low))]
    (.thinkingConfig
     builder
     (-> (GeminiThinkingConfig/builder)
         (.includeThoughts true)
         (.thinkingBudget (int budget))
         (.build)))))

(defn create-gemini-reasoning-model [model-name effort]
  (-> (create-gemini-model model-name)
      (.returnThinking true)
      (.sendThinking true)
      (gemini-reasoning-effort effort)))

(defn create-openai-model [model-name]
  (-> (OpenAiChatModel/builder)
      (.apiKey (System/getenv "OPENAI_API_KEY"))
      (.maxRetries (int 3))
      (.modelName model-name)))

(declare default-request-parameters reasoning-effort)

(defn create-openai-reasoning-model [model-name effort]
  (-> (create-openai-model model-name)
      (default-request-parameters
       #(reasoning-effort % effort))))

(defn create-anthropic-model [model-name]
  (-> (AnthropicChatModel/builder)
      (.apiKey (System/getenv "ANTHROPIC_API_KEY"))
      (.maxRetries (int 3))
      (.modelName model-name)))

(defn anthropic-reasoning-effort [builder effort]
  (let [budget (get {:low 1024 :medium 4096 :high 8192} (or effort :low))
        max-tokens (get {:low 4096 :medium (* 6 1024) :high (* 10 1024)}
                        (or effort :low))]
    (-> builder
        (.thinkingBudgetTokens (int budget))
        (.maxTokens (int max-tokens)))))

(defn create-anthropic-reasoning-model [model-name effort]
  (-> (create-anthropic-model model-name)
      (.thinkingType "enabled")
      (anthropic-reasoning-effort effort)
      (.sendThinking true)
      (.returnThinking true)))

(defn default-request-parameters [model-builder configure-fn]
  (.defaultRequestParameters model-builder
                             (.build (configure-fn (OpenAiChatRequestParameters/builder)))))

(defn reasoning-effort [request-params-builder reasoning-effort]
  (assert (#{:low :medium :high} reasoning-effort))
  (.reasoningEffort request-params-builder (name reasoning-effort)))

(defn max-output-tokens [request-params-builder max-output-tokens]
  (.maxOutputTokens request-params-builder (int max-output-tokens)))

(defn reasoning-agent-model []
  (cond
    (System/getenv "ANTHROPIC_API_KEY")
    (create-anthropic-reasoning-model AnthropicChatModelName/CLAUDE_SONNET_4_20250514 :medium)
    (System/getenv "GEMINI_API_KEY")
    (create-gemini-reasoning-model "gemini-2.5-flash" :medium)
    (System/getenv "OPENAI_API_KEY")
    (create-openai-reasoning-model "o4-mini" :medium)
    :else nil))

(defn agent-model []
  (cond
    (System/getenv "ANTHROPIC_API_KEY")
    (create-anthropic-model AnthropicChatModelName/CLAUDE_SONNET_4_20250514)
    (System/getenv "GEMINI_API_KEY")
    (create-gemini-model "gemini-2.5-flash")
    (System/getenv "OPENAI_API_KEY")
    (create-openai-model "o4-mini")
    :else nil))

;; simple API as we don't really need more right now

(defn chat-memory
  ([]
   (chat-memory default-max-memory))
  ([size]
   (MessageWindowChatMemory/withMaxMessages size)))

;; ------------------------------------------------------------
;; converting tools
;; ------------------------------------------------------------

(defn is-well-formed-json? [json-string]
  (try
    {:result (json/read-str json-string)}
    (catch Exception _ false)))

(defn registration-map->tool-executor [{:keys [tool-fn]}]
  (reify ToolExecutor
    (execute [_this request memory-id]
      (let [tool-name (.name request)
            arg-str (.arguments request)]
        (if-let [arg-result (is-well-formed-json? arg-str)]
          (try
            (log/info (str "Calling tool" (pr-str {:tool-name tool-name
                                                   :arg-result (:result arg-result)})))
            (let [callback-result (promise)]
              (tool-fn nil
                       (:result arg-result)
                       (fn [result error]
                         (deliver callback-result
                                  (if error
                                    (str "Tool Error: " (string/join "\n" result))
                                    (if (sequential? result)
                                      (string/join "\n\n" result)
                                      (str result))))))
              @callback-result)
            (catch Exception e
              (str "Error executing tool '" tool-name "': " (.getMessage e)
                   (with-out-str (clojure.pprint/pprint (Throwable->map e))))))
          (str "ERROR: Arguments provided to the tool call were malformed.\n=====\n" arg-str "\n=====\n"))))))

(defn registration-map->tool-specification [{:keys [name description schema]}]
  {:pre [(or (string? schema) (map? schema))
         (string? name) (string? description)]}
  (-> (ToolSpecification/builder)
      (.name name)
      (.description description)
      (.parameters (schema/edn->sch
                    (if (string? schema)
                      (json/read-str schema :key-fn keyword)
                      schema)))
      (.build)))

(defn convert-tools [registration-maps]
  (into {}
        (map (juxt registration-map->tool-specification
                   registration-map->tool-executor)
             registration-maps)))

(defn chat-request [message & {:keys [system-message tools require-tool-choice]}]
  ;; ChatResponse response = model.chat(request);
  ;;AiMessage aiMessage = response.aiMessage();
  (cond-> (ChatRequest/builder)
    system-message (.messages (list (SystemMessage. system-message)
                                    (UserMessage. message)))
    (not system-message) (.messages (list (UserMessage. message)))
    (not-empty tools) (.toolSpecifications (map registration-map->tool-specification tools))
    ;; TODO on next langchain bump
    ;; require-tool-choice (.toolChoice ToolChoice/REQUIRED)
    :else (.build)))

(definterface AiService
  (^String chat [^String userMessage])
  (^String chat [^dev.langchain4j.data.message.UserMessage userMessage]))

(defn create-service [klass {:keys [model memory tools system-message]}]
  (-> (AiServices/builder klass) ; Use the interface defined above
      (.chatModel model)
      (.systemMessageProvider
       (reify Function
         (apply [this mem-id]
           system-message)))
      (cond->
       tools (.tools (convert-tools tools)))
      (.chatMemory memory)))

(comment
  (def test-tool
    {:name "hello"
     :description "says hello"
     :schema
     (json/json-str {:type :object
                     :properties
                     {:nm {:type :string
                           :description "Name to say hi to"}}
                     :required [:nm]})
     :tool-fn (fn [_ {:keys [nm]} callback]
                (callback [(str "Hello " nm "!")] false))})
  #_(create-service AiService {})

  (let [exec (registration-map->tool-executor test-tool)]
    (.execute exec (-> (ToolExecutionRequest/builder)
                       (.name "hello")
                       (.arguments (json/json-str {:nm "Joey"}))
                       (.build))
              "asdf")))

;; keep this example
#_(definterface StreamingAiService
    (^dev.langchain4j.service.TokenStream chat [^String userMessage])
    (^dev.langchain4j.service.TokenStream chat [^dev.langchain4j.data.message.UserMessage userMessage]))


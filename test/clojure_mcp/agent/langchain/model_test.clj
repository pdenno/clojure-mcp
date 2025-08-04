(ns clojure-mcp.agent.langchain.model-test
  (:require
   [clojure.test :refer [deftest testing is]]
   [clojure-mcp.agent.langchain.model :as model]
   [clojure-mcp.config :as config])
  (:import
   [dev.langchain4j.model.anthropic
    AnthropicChatModel$AnthropicChatModelBuilder
    AnthropicChatModelName]
   [dev.langchain4j.model.googleai
    GoogleAiGeminiChatModel$GoogleAiGeminiChatModelBuilder]
   [dev.langchain4j.model.openai
    OpenAiChatModel$OpenAiChatModelBuilder]))

(deftest test-available-models
  (testing "Available models should include all defaults"
    (let [models (model/available-models)]
      ;; OpenAI models
      (is (contains? (set models) :openai/gpt-4o))
      (is (contains? (set models) :openai/gpt-4-1))
      (is (contains? (set models) :openai/gpt-4-1-mini))
      (is (contains? (set models) :openai/gpt-4-1-nano))
      (is (contains? (set models) :openai/o1))
      (is (contains? (set models) :openai/o1-mini))
      (is (contains? (set models) :openai/o3))
      (is (contains? (set models) :openai/o3-mini))
      (is (contains? (set models) :openai/o3-pro))
      (is (contains? (set models) :openai/o4-mini))
      (is (contains? (set models) :openai/o4-mini-reasoning))
      ;; Google models
      (is (contains? (set models) :google/gemini-2-5-flash-lite))
      (is (contains? (set models) :google/gemini-2-5-pro))
      (is (contains? (set models) :google/gemini-2-5-flash))
      (is (contains? (set models) :google/gemini-2-5-flash-reasoning))
      (is (contains? (set models) :google/gemini-2-5-pro-reasoning))
      ;; Anthropic models
      (is (contains? (set models) :anthropic/claude-opus-4))
      (is (contains? (set models) :anthropic/claude-opus-4-reasoning))
      (is (contains? (set models) :anthropic/claude-3-5-haiku))
      (is (contains? (set models) :anthropic/claude-sonnet-4))
      (is (contains? (set models) :anthropic/claude-sonnet-4-reasoning))
      (is (= 21 (count models))))))

(deftest test-get-provider
  (testing "Provider extraction from model keys"
    (is (= :openai (model/get-provider :openai/o4-mini)))
    (is (= :google (model/get-provider :google/gemini-2-5-flash)))
    (is (= :anthropic (model/get-provider :anthropic/claude-sonnet-4)))
    (is (= :custom (model/get-provider :custom/my-model)))))

(deftest test-merge-with-defaults
  (testing "Config merging preserves defaults and applies overrides"
    (let [merged (model/merge-with-defaults :openai/o4-mini {:temperature 0.5})]
      (is (= "o4-mini" (:model-name merged)))
      (is (= 0.5 (:temperature merged)))
      (is (nil? (:top-p merged)) "Default config has no top-p")
      (is (= 4096 (:max-tokens merged)) "Should preserve default max-tokens"))

    (testing "Custom parameters are included"
      (let [merged (model/merge-with-defaults :openai/o4-mini {:custom-param "test"})]
        (is (= "test" (:custom-param merged))))

      (let [merged (model/merge-with-defaults :openai/o4-mini {:top-p 0.95})]
        (is (= 0.95 (:top-p merged)) "Can add top-p when not in defaults")))

    (testing "Unknown model key returns empty defaults"
      (let [merged (model/merge-with-defaults :unknown/model {:temperature 0.5})]
        (is (= {:temperature 0.5} merged))))))

(deftest test-create-model-builder
  (testing "Builder creation for each provider"
    (testing "OpenAI builder"
      (let [builder (model/create-model-builder :openai/o4-mini {})]
        (is (instance? OpenAiChatModel$OpenAiChatModelBuilder builder))))

    (testing "Google builder"
      (let [builder (model/create-model-builder :google/gemini-2-5-flash {})]
        (is (instance? GoogleAiGeminiChatModel$GoogleAiGeminiChatModelBuilder builder))))

    (testing "Anthropic builder"
      (let [builder (model/create-model-builder :anthropic/claude-sonnet-4 {})]
        (is (instance? AnthropicChatModel$AnthropicChatModelBuilder builder))))))

(deftest test-new-model-configurations
  (testing "New OpenAI models"
    (let [gpt4o (model/create-model-builder :openai/gpt-4o {})
          o3 (model/create-model-builder :openai/o3 {})]
      (is (instance? OpenAiChatModel$OpenAiChatModelBuilder gpt4o))
      (is (instance? OpenAiChatModel$OpenAiChatModelBuilder o3))))

  (testing "New Google models"
    (let [lite (model/create-model-builder :google/gemini-2-5-flash-lite {})
          pro (model/create-model-builder :google/gemini-2-5-pro {})]
      (is (instance? GoogleAiGeminiChatModel$GoogleAiGeminiChatModelBuilder lite))
      (is (instance? GoogleAiGeminiChatModel$GoogleAiGeminiChatModelBuilder pro))))

  (testing "New Anthropic models"
    (let [opus (model/create-model-builder :anthropic/claude-opus-4 {})
          haiku (model/create-model-builder :anthropic/claude-3-5-haiku {})]
      (is (instance? AnthropicChatModel$AnthropicChatModelBuilder opus))
      (is (instance? AnthropicChatModel$AnthropicChatModelBuilder haiku))))

  (testing "O3 models have medium reasoning effort"
    (let [o3-config (model/merge-with-defaults :openai/o3 {})
          o3-pro-config (model/merge-with-defaults :openai/o3-pro {})]
      (is (= :medium (get-in o3-config [:thinking :effort])))
      (is (= :medium (get-in o3-pro-config [:thinking :effort])))))

  (testing "Haiku has lower max tokens"
    (let [haiku-config (model/merge-with-defaults :anthropic/claude-3-5-haiku {})]
      (is (= 2048 (:max-tokens haiku-config)))))

  (testing "Opus reasoning has higher budget tokens"
    (let [opus-reasoning (model/merge-with-defaults :anthropic/claude-opus-4-reasoning {})]
      (is (= 8192 (get-in opus-reasoning [:thinking :budget-tokens]))))))

(deftest test-builder-modifications
  (testing "Builders can be modified before building"
    (let [builder (model/create-model-builder :openai/o4-mini {})
          modified (.maxTokens builder (int 2000))]
      (is (identical? builder modified) "Builder should return itself for chaining")
      (is (instance? OpenAiChatModel$OpenAiChatModelBuilder modified)))))

(deftest test-reasoning-model-configs
  (testing "Reasoning models have appropriate thinking configurations"
    (let [config (model/merge-with-defaults :openai/o4-mini-reasoning {})]
      (is (= :medium (get-in config [:thinking :effort]))))

    (let [config (model/merge-with-defaults :google/gemini-2-5-flash-reasoning {})]
      (is (true? (get-in config [:thinking :enabled])))
      (is (true? (get-in config [:thinking :return])))
      (is (true? (get-in config [:thinking :send])))
      (is (= :medium (get-in config [:thinking :effort]))))

    (let [config (model/merge-with-defaults :anthropic/claude-sonnet-4-reasoning {})]
      (is (= 4096 (get-in config [:thinking :budget-tokens]))))))

(deftest test-config-overrides
  (testing "Config overrides work correctly"
    (let [overrides {:temperature 0.2
                     :max-tokens 2048
                     :thinking {:effort :high}}
          builder (model/create-model-builder :anthropic/claude-sonnet-4-reasoning overrides)
          config (model/merge-with-defaults :anthropic/claude-sonnet-4-reasoning overrides)]
      (is (= 0.2 (:temperature config)))
      (is (= 2048 (:max-tokens config)))
      (is (= :high (get-in config [:thinking :effort])))
      ;; Also verify the builder is created
      (is (instance? AnthropicChatModel$AnthropicChatModelBuilder builder)))))

(deftest test-provider-specific-configs
  (testing "Provider-specific configurations"
    (testing "Anthropic-specific"
      (let [config {:anthropic {:cache-system-messages true
                                :cache-tools true
                                :version "2024-01-01"}}
            builder (model/create-model-builder :anthropic/claude-sonnet-4 config)]
        (is (instance? AnthropicChatModel$AnthropicChatModelBuilder builder))))

    (testing "Google-specific"
      (let [config {:google {:allow-code-execution true
                             :include-code-execution-output true}}
            builder (model/create-model-builder :google/gemini-2-5-flash config)]
        (is (instance? GoogleAiGeminiChatModel$GoogleAiGeminiChatModelBuilder builder))))

    (testing "OpenAI-specific"
      (let [config {:openai {:organization-id "org-123"
                             :project-id "proj-456"
                             :strict-tools true}}
            builder (model/create-model-builder :openai/o4-mini config)]
        (is (instance? OpenAiChatModel$OpenAiChatModelBuilder builder))))))

(deftest test-build-model-convenience
  (testing "Convenience build functions"
    (let [model (model/build-model :openai/o4-mini {})]
      (is (= "dev.langchain4j.model.openai.OpenAiChatModel"
             (.getName (.getClass model)))))

    (let [model (model/build-model-from-config :google {:model-name "gemini-2.5-flash"})]
      (is (= "dev.langchain4j.model.googleai.GoogleAiGeminiChatModel"
             (.getName (.getClass model)))))))

(deftest test-multimethod-dispatch
  (testing "Multimethod dispatches correctly"
    (is (model/create-builder :openai {}) "OpenAI dispatch")
    (is (model/create-builder :google {}) "Google dispatch")
    (is (model/create-builder :anthropic {}) "Anthropic dispatch")

    (testing "Unknown provider throws exception"
      (is (thrown-with-msg? Exception #"Unknown provider"
                            (model/create-builder :unknown {}))))))

(deftest test-multimethod-extensibility
  (testing "Multimethod can be extended with new providers"
    ;; Remove any existing :test-provider method first
    (remove-method model/create-builder :test-provider)

    ;; Extend with a test provider
    (model/extend-provider :test-provider
                           (fn [_ config]
        ;; Return a mock builder (using OpenAI as stand-in)
                             (-> (dev.langchain4j.model.openai.OpenAiChatModel/builder)
                                 (.modelName "test-model")
                                 (.apiKey (:api-key config "test-key")))))

    ;; Test the extended provider
    (let [builder (model/create-builder :test-provider {:temperature 0.5})]
      (is (instance? OpenAiChatModel$OpenAiChatModelBuilder builder)))

    ;; Clean up - remove the test method
    (remove-method model/create-builder :test-provider)))

(deftest test-api-key-handling
  (testing "API key from config takes precedence"
    (let [config {:api-key "explicit-key"}
          merged (model/merge-with-defaults :openai/o4-mini config)]
      (is (= "explicit-key" (:api-key merged)))))

  (testing "API key from environment when not in config"
    ;; This test depends on environment variables
    ;; In a real test environment, you might mock System/getenv
    (when (System/getenv "OPENAI_API_KEY")
      (let [builder (model/create-model-builder :openai/o4-mini {})]
        (is (instance? OpenAiChatModel$OpenAiChatModelBuilder builder))))))

(deftest test-thinking-config-variations
  (testing "Different thinking effort levels"
    (doseq [effort [:low :medium :high]]
      (let [config {:thinking {:effort effort}}
            builder (model/create-model-builder :openai/o4-mini-reasoning config)]
        (is (instance? OpenAiChatModel$OpenAiChatModelBuilder builder)))))

  (testing "Custom thinking budget tokens"
    (let [config {:thinking {:budget-tokens 8192}}
          builder (model/create-model-builder :anthropic/claude-sonnet-4-reasoning config)]
      (is (instance? AnthropicChatModel$AnthropicChatModelBuilder builder)))))

(deftest test-model-name-types
  (testing "Model names can be strings or enums"
    (let [string-config {:model-name "custom-model"}
          enum-config {:model-name AnthropicChatModelName/CLAUDE_SONNET_4_20250514}

          builder1 (model/create-model-builder :openai/o4-mini string-config)
          builder2 (model/create-model-builder :anthropic/claude-sonnet-4 enum-config)]

      (is (instance? OpenAiChatModel$OpenAiChatModelBuilder builder1))
      (is (instance? AnthropicChatModel$AnthropicChatModelBuilder builder2)))))

(deftest test-create-builder-from-config
  (testing "Direct builder creation with provider"
    (let [builder (model/create-builder-from-config
                   :google
                   {:model-name "gemini-pro"
                    :temperature 0.3})]
      (is (instance? GoogleAiGeminiChatModel$GoogleAiGeminiChatModelBuilder builder))))

  (testing "Works without default configs"
    (let [builder (model/create-builder-from-config
                   :anthropic
                   {:model-name "claude-3"
                    :max-tokens 1000})]
      (is (instance? AnthropicChatModel$AnthropicChatModelBuilder builder)))))

(deftest test-spec-validation
  (testing "Valid configs pass validation"
    (is (model/create-model-builder :openai/gpt-4o
                                    {:temperature 0.7
                                     :max-tokens 2048})))

  (testing "Invalid configs are rejected with validation enabled"
    (is (thrown-with-msg? Exception #"Invalid configuration"
                          (model/create-model-builder :openai/gpt-4o
                                                      {:temperature 3.0} ; > 2.0
                                                      {:validate? true}))))

  (testing "Invalid configs pass when validation is disabled"
    (let [builder (model/create-model-builder :openai/gpt-4o
                                              {:temperature 3.0}
                                              {:validate? false})]
      (is (instance? OpenAiChatModel$OpenAiChatModelBuilder builder))))

  (testing "Invalid model keys are rejected"
    (is (thrown-with-msg? Exception #"Invalid model key"
                          (model/create-model-builder :invalid/model-key
                                                      {}
                                                      {:validate? true}))))

  (testing "Provider-specific validation"
    ;; Top-k is valid for Anthropic
    (is (model/create-model-builder :anthropic/claude-sonnet-4
                                    {:top-k 50}))

    ;; Invalid thinking effort is rejected
    (is (thrown-with-msg? Exception #"Invalid configuration"
                          (model/create-model-builder :openai/o3
                                                      {:thinking {:effort :extreme}})))))

(deftest test-create-model-builder-from-config
  (testing "User config takes precedence over defaults"
    (let [user-models {:openai/my-custom {:model-name "gpt-4-turbo"
                                          :temperature 0.5
                                          :max-tokens 8192}}
          nrepl-client-map {::config/config {:models user-models}}
          builder (model/create-model-builder-from-config
                   nrepl-client-map
                   :openai/my-custom
                   {})]
      (is (instance? OpenAiChatModel$OpenAiChatModelBuilder builder))))

  (testing "Falls back to defaults when not in user config"
    (let [nrepl-client-map {::config/config {:models {}}}
          builder (model/create-model-builder-from-config
                   nrepl-client-map
                   :openai/gpt-4o
                   {})]
      (is (instance? OpenAiChatModel$OpenAiChatModelBuilder builder))))

  (testing "Config overrides work with user models"
    (let [user-models {:anthropic/my-claude {:model-name "claude-3-opus"
                                             :temperature 0.7}}
          nrepl-client-map {::config/config {:models user-models}}
          builder (model/create-model-builder-from-config
                   nrepl-client-map
                   :anthropic/my-claude
                   {:temperature 0.3})] ; Override temperature
      (is (instance? AnthropicChatModel$AnthropicChatModelBuilder builder))))

  (testing "Unknown model key throws exception"
    (let [nrepl-client-map {::config/config {:models {}}}]
      (is (thrown-with-msg? Exception #"Unknown model key"
                            (model/create-model-builder-from-config
                             nrepl-client-map
                             :unknown/model
                             {})))))

  (testing "Validation works with user models"
    (let [user-models {:openai/bad-model {:temperature 3.0}} ; Invalid temp
          nrepl-client-map {::config/config {:models user-models}}]
      (is (thrown-with-msg? Exception #"Invalid configuration"
                            (model/create-model-builder-from-config
                             nrepl-client-map
                             :openai/bad-model
                             {}
                             {:validate? true})))))

  (testing "Can disable validation"
    (let [user-models {:openai/bad-model {:temperature 3.0}}
          nrepl-client-map {::config/config {:models user-models}}
          builder (model/create-model-builder-from-config
                   nrepl-client-map
                   :openai/bad-model
                   {}
                   {:validate? false})]
      (is (instance? OpenAiChatModel$OpenAiChatModelBuilder builder))))

  (testing "Complex user model with thinking config"
    (let [user-models {:google/my-gemini {:model-name "gemini-2.5-pro"
                                          :max-tokens 4096
                                          :thinking {:enabled true
                                                     :effort :high
                                                     :budget-tokens 8192}}}
          nrepl-client-map {::config/config {:models user-models}}
          builder (model/create-model-builder-from-config
                   nrepl-client-map
                   :google/my-gemini
                   {})]
      (is (instance? GoogleAiGeminiChatModel$GoogleAiGeminiChatModelBuilder builder)))))

(deftest test-create-model-from-config
  (testing "Convenience function builds model directly"
    (let [user-models {:openai/my-custom {:model-name "gpt-4-turbo"
                                          :temperature 0.5}}
          nrepl-client-map {::config/config {:models user-models}}
          model (model/create-model-from-config
                 nrepl-client-map
                 :openai/my-custom)]
      (is (= "dev.langchain4j.model.openai.OpenAiChatModel"
             (.getName (.getClass model))))
      (is (instance? dev.langchain4j.model.openai.OpenAiChatModel model))))

  (testing "Works with default models"
    (let [nrepl-client-map {::config/config {:models {}}}
          model (model/create-model-from-config
                 nrepl-client-map
                 :google/gemini-2-5-flash)]
      (is (= "dev.langchain4j.model.googleai.GoogleAiGeminiChatModel"
             (.getName (.getClass model))))
      (is (instance? dev.langchain4j.model.googleai.GoogleAiGeminiChatModel model))))

  (testing "Accepts config overrides"
    (let [user-models {:anthropic/my-claude {:model-name "claude-3-opus"
                                             :temperature 0.7}}
          nrepl-client-map {::config/config {:models user-models}}
          model (model/create-model-from-config
                 nrepl-client-map
                 :anthropic/my-claude
                 {:max-tokens 2048})]
      (is (= "dev.langchain4j.model.anthropic.AnthropicChatModel"
             (.getName (.getClass model))))
      (is (instance? dev.langchain4j.model.anthropic.AnthropicChatModel model))))

  (testing "Validation can be disabled"
    (let [user-models {:openai/bad-model {:temperature 3.0}}
          nrepl-client-map {::config/config {:models user-models}}
          model (model/create-model-from-config
                 nrepl-client-map
                 :openai/bad-model
                 {}
                 {:validate? false})]
      (is (= "dev.langchain4j.model.openai.OpenAiChatModel"
             (.getName (.getClass model)))))))
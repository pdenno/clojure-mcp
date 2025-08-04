(ns clojure-mcp.agent.langchain.model-spec-test
  (:require
   [clojure.test :refer [deftest testing is]]
   [clojure.spec.alpha :as s]
   [clojure-mcp.agent.langchain.model-spec :as spec]))

(deftest test-temperature-spec
  (testing "Temperature validation"
    (is (s/valid? ::spec/temperature 0.0))
    (is (s/valid? ::spec/temperature 1.0))
    (is (s/valid? ::spec/temperature 2.0))
    (is (not (s/valid? ::spec/temperature -0.1)))
    (is (not (s/valid? ::spec/temperature 2.1)))
    (is (not (s/valid? ::spec/temperature "1.0")))))

(deftest test-top-p-spec
  (testing "Top-p validation"
    (is (s/valid? ::spec/top-p 0.0))
    (is (s/valid? ::spec/top-p 0.5))
    (is (s/valid? ::spec/top-p 1.0))
    (is (not (s/valid? ::spec/top-p -0.1)))
    (is (not (s/valid? ::spec/top-p 1.1)))))

(deftest test-thinking-spec
  (testing "Thinking configuration validation"
    (is (s/valid? ::spec/thinking {:effort :low}))
    (is (s/valid? ::spec/thinking {:effort :medium}))
    (is (s/valid? ::spec/thinking {:effort :high}))
    (is (not (s/valid? ::spec/thinking {:effort :extreme})))
    (is (s/valid? ::spec/thinking {:effort :medium
                                   :enabled true
                                   :return true
                                   :send false
                                   :budget-tokens 4096}))
    (is (not (s/valid? ::spec/thinking {:budget-tokens -100})))))

(deftest test-model-config-spec
  (testing "Complete model config validation"
    (is (s/valid? ::spec/model-config {})) ; Empty config is valid (all optional)
    (is (s/valid? ::spec/model-config {:temperature 0.7
                                       :max-tokens 4096
                                       :thinking {:effort :medium}}))
    (is (s/valid? ::spec/model-config {:api-key "test-key"
                                       :temperature 1.5
                                       :top-p 0.9
                                       :max-tokens 10000
                                       :stop-sequences ["END" "STOP"]}))))

(deftest test-provider-specific-specs
  (testing "Anthropic-specific config"
    (is (s/valid? ::spec/anthropic-config {:top-k 50}))
    (is (s/valid? ::spec/anthropic-config {:anthropic {:cache-system-messages true
                                                       :cache-tools false}})))

  (testing "Google-specific config"
    (is (s/valid? ::spec/google-config {:seed 42
                                        :frequency-penalty 0.5
                                        :presence-penalty -0.5}))
    (is (s/valid? ::spec/google-config {:google {:allow-code-execution true
                                                 :logprobs 5}})))

  (testing "OpenAI-specific config"
    (is (s/valid? ::spec/openai-config {:seed 123
                                        :frequency-penalty 1.0}))
    (is (s/valid? ::spec/openai-config {:openai {:organization-id "org-123"
                                                 :strict-tools true}}))))

(deftest test-validation-functions
  (testing "validate-config function"
    (let [valid-config {:temperature 1.0}
          invalid-config {:temperature 3.0}]
      (is (= valid-config (spec/validate-config valid-config)))
      (is (thrown-with-msg? Exception #"Invalid model configuration"
                            (spec/validate-config invalid-config)))))

  (testing "validate-config-for-provider function"
    (is (spec/validate-config-for-provider :openai {:seed 42}))
    (is (spec/validate-config-for-provider :anthropic {:top-k 100}))
    (is (thrown-with-msg? Exception #"Invalid configuration for provider"
                          (spec/validate-config-for-provider :openai {:temperature 3.0})))))

(deftest test-explain-config
  (testing "Explanation for invalid configs"
    (let [invalid {:temperature 3.0}]
      (is (string? (spec/explain-config invalid)))
      (is (re-find #"failed.*temperature" (spec/explain-config invalid))))

    (let [valid {:temperature 1.0}]
      (is (nil? (spec/explain-config valid))))))

(deftest test-coerce-numeric-params
  (testing "String to number coercion"
    (let [string-config {:temperature "0.7"
                         :top-p "0.9"
                         :top-k "50"
                         :max-tokens "4096"
                         :seed "42"
                         :timeout "30000"
                         :max-retries "3"
                         :frequency-penalty "0.5"
                         :presence-penalty "-0.5"}
          coerced (spec/coerce-numeric-params string-config)]
      (is (= 0.7 (:temperature coerced)))
      (is (instance? Double (:temperature coerced)))
      (is (= 50 (:top-k coerced)))
      (is (instance? Integer (:top-k coerced)))
      (is (= 0.5 (:frequency-penalty coerced)))
      (is (= -0.5 (:presence-penalty coerced))))))

(deftest test-model-key-validation
  (testing "Valid model keys"
    (is (s/valid? ::spec/model-key :openai/gpt-4))
    (is (s/valid? ::spec/model-key :google/gemini-pro))
    (is (s/valid? ::spec/model-key :anthropic/claude-3)))

  (testing "Invalid model keys"
    (is (not (s/valid? ::spec/model-key :invalid/model)))
    (is (not (s/valid? ::spec/model-key :model-without-namespace)))
    (is (not (s/valid? ::spec/model-key "not-a-keyword"))))

  (testing "validate-model-key function"
    (is (= :openai/gpt-4 (spec/validate-model-key :openai/gpt-4)))
    (is (thrown-with-msg? Exception #"Invalid model key"
                          (spec/validate-model-key :unknown/model)))))
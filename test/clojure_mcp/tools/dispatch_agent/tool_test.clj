(ns clojure-mcp.tools.dispatch-agent.tool-test
  (:require [clojure.test :refer :all]
            [clojure-mcp.tools.dispatch-agent.tool :as tool]
            [clojure-mcp.config :as config]
            [clojure-mcp.agent.langchain.model :as model]))

(deftest test-create-dispatch-agent-tool-with-config
  (testing "Tool creation with model configuration"
    (binding [model/*env-overrides* {"ANTHROPIC_API_KEY" "test-key"}]
      (let [nrepl-client-atom (atom {::config/config
                                     {:tools-config {:dispatch_agent {:model :anthropic/claude-3-haiku-20240307}}
                                      :models {:anthropic/claude-3-haiku-20240307
                                               {:model-name "claude-3-haiku-20240307"
                                                :api-key [:env "ANTHROPIC_API_KEY"]}}}})]
        (let [tool-config (tool/create-dispatch-agent-tool nrepl-client-atom)]
          (is (= :dispatch-agent (:tool-type tool-config)))
          (is (some? (:model tool-config)) "Model should be created from config")))))

  (testing "Tool creation without model configuration uses default model"
    (let [nrepl-client-atom (atom {::config/config {}})]
      (let [tool-config (tool/create-dispatch-agent-tool nrepl-client-atom)]
        (is (= :dispatch-agent (:tool-type tool-config)))
        ;; Model should be created from default chain/agent-model
        (is (some? (:model tool-config)) "Model should use default when not configured"))))

  (testing "Tool creation with explicit model overrides config"
    (let [nrepl-client-atom (atom {::config/config
                                   {:tools-config {:dispatch_agent {:model :anthropic/claude-3-haiku-20240307}}}})
          explicit-model "explicit-model-instance"]
      (let [tool-config (tool/create-dispatch-agent-tool nrepl-client-atom explicit-model)]
        (is (= :dispatch-agent (:tool-type tool-config)))
        (is (= explicit-model (:model tool-config)) "Explicit model should override config")))))

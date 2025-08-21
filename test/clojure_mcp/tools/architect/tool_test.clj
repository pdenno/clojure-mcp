(ns clojure-mcp.tools.architect.tool-test
  (:require [clojure.test :refer :all]
            [clojure-mcp.tools.architect.tool :as tool]
            [clojure-mcp.config :as config]
            [clojure-mcp.agent.langchain.model :as model]))

(deftest test-create-architect-tool-with-config
  (testing "Tool creation with model configuration"
    (binding [model/*env-overrides* {"OPENAI_API_KEY" "test-key"}]
      (let [nrepl-client-atom (atom {::config/config
                                     {:tools-config {:architect {:model :openai/gpt-4o}}
                                      :models {:openai/gpt-4o
                                               {:model-name "gpt-4o"
                                                :api-key [:env "OPENAI_API_KEY"]}}}})]
        (let [tool-config (tool/create-architect-tool nrepl-client-atom)]
          (is (= :architect (:tool-type tool-config)))
          (is (some? (:model tool-config)) "Model should be created from config")))))

  (testing "Tool creation without model configuration uses default reasoning model"
    (let [nrepl-client-atom (atom {::config/config {}})]
      (let [tool-config (tool/create-architect-tool nrepl-client-atom)]
        (is (= :architect (:tool-type tool-config))))))

  (testing "Tool creation with explicit model overrides config"
    (binding [model/*env-overrides* {"OPENAI_API_KEY" "test-key"}]
      (let [nrepl-client-atom (atom {::config/config
                                     {:tools-config {:architect {:model :openai/gpt-4o}}}})
            explicit-model "explicit-model-instance"]
        (let [tool-config (tool/create-architect-tool nrepl-client-atom explicit-model)]
          (is (= :architect (:tool-type tool-config)))
          (is (= explicit-model (:model tool-config)) "Explicit model should override config"))))))

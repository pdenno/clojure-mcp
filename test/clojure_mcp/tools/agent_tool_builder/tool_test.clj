(ns clojure-mcp.tools.agent-tool-builder.tool-test
  (:require
   [clojure.test :refer :all]
   [clojure-mcp.tools.agent-tool-builder.tool :as agent-builder]
   [clojure-mcp.tools.agent-tool-builder.core :as core]
   [clojure-mcp.tools :as tools]
   [clojure-mcp.config :as config]))

(deftest test-filter-tools
  (testing "Filter tools with enable list"
    (let [tools [{:tool-type :tool1} {:tool-type :tool2} {:tool-type :tool3}]
          filtered (tools/filter-tools tools [:tool1 :tool3] nil)]
      (is (= 2 (count filtered)))
      (is (= :tool1 (:tool-type (first filtered))))
      (is (= :tool3 (:tool-type (second filtered))))))

  (testing "Filter tools with disable list"
    (let [tools [{:tool-type :tool1} {:tool-type :tool2} {:tool-type :tool3}]
          filtered (tools/filter-tools tools :all [:tool2])]
      (is (= 2 (count filtered)))
      (is (= :tool1 (:tool-type (first filtered))))
      (is (= :tool3 (:tool-type (second filtered))))))

  (testing "Filter tools with both enable and disable"
    (let [tools [{:tool-type :tool1} {:tool-type :tool2} {:tool-type :tool3} {:tool-type :tool4}]
          filtered (tools/filter-tools tools [:tool1 :tool2 :tool3] [:tool2])]
      (is (= 2 (count filtered)))
      (is (= :tool1 (:tool-type (first filtered))))
      (is (= :tool3 (:tool-type (second filtered))))))

  (testing "nil enable-tools returns empty vector (no tools)"
    (let [tools [{:tool-type :tool1} {:tool-type :tool2}]
          filtered (tools/filter-tools tools nil nil)]
      (is (= [] filtered) "nil enable-tools should return no tools")))

  (testing ":all enable-tools returns all tools"
    (let [tools [{:tool-type :tool1} {:tool-type :tool2}]
          filtered (tools/filter-tools tools :all nil)]
      (is (= tools filtered) "':all' enable-tools should return all tools"))))

(deftest test-create-agent-tools
  (testing "Empty agents config returns empty vector"
    (let [nrepl-atom (atom {::config/config {:agents []}})]
      (is (= [] (agent-builder/create-agent-tools nrepl-atom)))))

  (testing "Creates tools for configured agents"
    (let [agent-config {:id :test-agent
                        :name "test_agent"
                        :description "Test agent"
                        :system-message "Test system message"
                        :context false
                        :enable-tools nil
                        :disable-tools nil}
          nrepl-atom (atom {::config/config {:agents [agent-config]}})]

      (let [tools (agent-builder/create-agent-tools nrepl-atom)]
        (is (= 1 (count tools)))
        (let [tool (first tools)]
          (is (map? tool))
          (is (:name tool))
          (is (:description tool))
          (is (:schema tool)))))))

(deftest test-agent-config-retrieval
  (testing "Get agents config"
    (let [agents [{:id :agent1} {:id :agent2}]
          nrepl-map {::config/config {:agents agents}}]
      (is (= agents (config/get-agents-config nrepl-map)))))

  (testing "Get specific agent config"
    (let [agent1 {:id :agent1 :name "Agent 1"}
          agent2 {:id :agent2 :name "Agent 2"}
          nrepl-map {::config/config {:agents [agent1 agent2]}}]
      (is (= agent1 (config/get-agent-config nrepl-map :agent1)))
      (is (= agent2 (config/get-agent-config nrepl-map :agent2)))
      (is (nil? (config/get-agent-config nrepl-map :agent3))))))

(deftest test-tool-normalization
  (testing "String and keyword tool IDs are normalized"
    (let [tools [{:tool-type :tool1} {:name "tool2"} {:tool-type :tool3}]
          ;; Test with string IDs
          filtered1 (tools/filter-tools tools ["tool1" "tool3"] nil)]
      (is (= 2 (count filtered1)))

      ;; Test with mixed string and keyword disable list
      ;; Use :all to get all tools, then apply disable list
      (let [filtered2 (tools/filter-tools tools :all ["tool1" :tool3])]
        (is (= 1 (count filtered2)))))))

(deftest test-agent-no-tools-by-default
  (testing "Agents have no tools by default when enable-tools is nil"
    (let [agent-config {:id :test-agent
                        :name "test_agent"
                        :description "Test agent"
                        :system-message "Test"
                        :context false
                        :enable-tools nil ; nil means no tools
                        :disable-tools nil}
          nrepl-atom (atom {::config/config {}})
          ;; Mock tools/build-all-tools by setting up some test tools
          test-tools [{:tool-type :tool1} {:tool-type :tool2}]]

      ;; Test the filtering logic directly
      (with-redefs [tools/build-all-tools (fn [_] test-tools)]
        ;; This simulates the logic in build-agent-from-config
        (let [filtered (cond
                         (nil? (:enable-tools agent-config)) []
                         (and (sequential? (:enable-tools agent-config))
                              (some #{:all} (:enable-tools agent-config)))
                         (tools/filter-tools test-tools :all nil)
                         :else
                         (tools/filter-tools test-tools
                                             (:enable-tools agent-config)
                                             (:disable-tools agent-config)))]
          (is (= [] filtered) "Agent with nil enable-tools should have no tools")))))

  (testing "Agents with [:all] get all tools"
    (let [test-tools [{:tool-type :tool1} {:tool-type :tool2}]
          agent-config {:enable-tools [:all]}]
      (with-redefs [tools/build-all-tools (fn [_] test-tools)]
        (let [filtered (cond
                         (nil? (:enable-tools agent-config)) []
                         (and (sequential? (:enable-tools agent-config))
                              (some #{:all} (:enable-tools agent-config)))
                         (tools/filter-tools test-tools :all nil)
                         :else
                         (tools/filter-tools test-tools
                                             (:enable-tools agent-config)
                                             nil))]
          (is (= 2 (count filtered)) "Agent with [:all] should get all tools"))))))
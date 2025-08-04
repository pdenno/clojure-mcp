(ns clojure-mcp.config.tool-filter-test
  (:require [clojure.test :refer :all]
            [clojure-mcp.config :as config]))

(deftest test-tool-id-enabled?
  (testing "No configuration - all tools enabled"
    (let [nrepl-map {::config/config {}}]
      (is (true? (config/tool-id-enabled? nrepl-map :clojure-eval)))
      (is (true? (config/tool-id-enabled? nrepl-map :bash)))
      (is (true? (config/tool-id-enabled? nrepl-map :any-tool)))))

  (testing "Empty enable list - no tools enabled"
    (let [nrepl-map {::config/config {:enable-tools []}}]
      (is (false? (config/tool-id-enabled? nrepl-map :clojure-eval)))
      (is (false? (config/tool-id-enabled? nrepl-map :bash)))
      (is (false? (config/tool-id-enabled? nrepl-map :any-tool)))))

  (testing "Enable specific tools"
    (let [nrepl-map {::config/config {:enable-tools [:clojure-eval :read-file :file-write]}}]
      (is (true? (config/tool-id-enabled? nrepl-map :clojure-eval)))
      (is (true? (config/tool-id-enabled? nrepl-map :read-file)))
      (is (true? (config/tool-id-enabled? nrepl-map :file-write)))
      (is (false? (config/tool-id-enabled? nrepl-map :bash)))
      (is (false? (config/tool-id-enabled? nrepl-map :grep)))))

  (testing "Disable specific tools"
    (let [nrepl-map {::config/config {:disable-tools [:bash :dispatch-agent]}}]
      (is (true? (config/tool-id-enabled? nrepl-map :clojure-eval)))
      (is (true? (config/tool-id-enabled? nrepl-map :read-file)))
      (is (false? (config/tool-id-enabled? nrepl-map :bash)))
      (is (false? (config/tool-id-enabled? nrepl-map :dispatch-agent)))))

  (testing "Enable and disable lists - disable takes precedence"
    (let [nrepl-map {::config/config {:enable-tools [:clojure-eval :bash :read-file]
                                      :disable-tools [:bash]}}]
      (is (true? (config/tool-id-enabled? nrepl-map :clojure-eval)))
      (is (false? (config/tool-id-enabled? nrepl-map :bash))) ; disabled even though enabled
      (is (true? (config/tool-id-enabled? nrepl-map :read-file)))
      (is (false? (config/tool-id-enabled? nrepl-map :grep))))) ; not in enable list

  (testing "String tool IDs are converted to keywords"
    (let [nrepl-map {::config/config {:enable-tools ["clojure-eval" "read-file"]
                                      :disable-tools ["bash"]}}]
      (is (true? (config/tool-id-enabled? nrepl-map "clojure-eval")))
      (is (true? (config/tool-id-enabled? nrepl-map :clojure-eval)))
      (is (false? (config/tool-id-enabled? nrepl-map "bash")))
      (is (false? (config/tool-id-enabled? nrepl-map :bash))))))

(deftest test-prompt-name-enabled?
  (testing "No configuration - all prompts enabled"
    (let [nrepl-map {::config/config {}}]
      (is (true? (config/prompt-name-enabled? nrepl-map "clojure_repl_system_prompt")))
      (is (true? (config/prompt-name-enabled? nrepl-map "chat-session-summarize")))
      (is (true? (config/prompt-name-enabled? nrepl-map "any-prompt")))))

  (testing "Empty enable list - no prompts enabled"
    (let [nrepl-map {::config/config {:enable-prompts []}}]
      (is (false? (config/prompt-name-enabled? nrepl-map "clojure_repl_system_prompt")))
      (is (false? (config/prompt-name-enabled? nrepl-map "chat-session-summarize")))
      (is (false? (config/prompt-name-enabled? nrepl-map "any-prompt")))))

  (testing "Enable specific prompts"
    (let [nrepl-map {::config/config {:enable-prompts [:clojure-repl-system-prompt :chat-session-summarize]}}]
      (is (true? (config/prompt-name-enabled? nrepl-map "clojure_repl_system_prompt")))
      (is (true? (config/prompt-name-enabled? nrepl-map "chat-session-summarize")))
      (is (false? (config/prompt-name-enabled? nrepl-map "chat-session-resume")))
      (is (false? (config/prompt-name-enabled? nrepl-map "create-update-project-summary")))))

  (testing "Disable specific prompts"
    (let [nrepl-map {::config/config {:disable-prompts [:chat-session-summarize :chat-session-resume]}}]
      (is (true? (config/prompt-name-enabled? nrepl-map "clojure_repl_system_prompt")))
      (is (false? (config/prompt-name-enabled? nrepl-map "chat-session-summarize")))
      (is (false? (config/prompt-name-enabled? nrepl-map "chat-session-resume")))))

  (testing "Enable and disable lists - disable takes precedence"
    (let [nrepl-map {::config/config {:enable-prompts [:clojure-repl-system-prompt :chat-session-summarize]
                                      :disable-prompts [:chat-session-summarize]}}]
      (is (true? (config/prompt-name-enabled? nrepl-map "clojure_repl_system_prompt")))
      (is (false? (config/prompt-name-enabled? nrepl-map "chat-session-summarize"))) ; disabled even though enabled
      (is (false? (config/prompt-name-enabled? nrepl-map "chat-session-resume"))))) ; not in enable list

  (testing "Prompt names with underscores are converted to hyphens"
    (let [nrepl-map {::config/config {:enable-prompts ["clojure_repl_system_prompt"]
                                      :disable-prompts []}}]
      (is (true? (config/prompt-name-enabled? nrepl-map "clojure_repl_system_prompt")))
      ;; Even if config uses underscores, it still matches
      (is (true? (config/prompt-name-enabled? nrepl-map :clojure-repl-system-prompt)))))

  (testing "Mixed hyphen and underscore names"
    (let [nrepl-map {::config/config {:enable-prompts [:create-update-project-summary :clojure-repl-system-prompt]}}]
      (is (true? (config/prompt-name-enabled? nrepl-map "create-update-project-summary")))
      (is (true? (config/prompt-name-enabled? nrepl-map "clojure_repl_system_prompt"))))))


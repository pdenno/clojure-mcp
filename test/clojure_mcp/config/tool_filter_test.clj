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
    (let [nrepl-map {::config/config {:enable-prompts [:clojure_repl_system_prompt :chat-session-summarize]}}]
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
    (let [nrepl-map {::config/config {:enable-prompts [:clojure_repl_system_prompt :chat-session-summarize]
                                      :disable-prompts [:chat-session-summarize]}}]
      (is (true? (config/prompt-name-enabled? nrepl-map "clojure_repl_system_prompt")))
      (is (false? (config/prompt-name-enabled? nrepl-map "chat-session-summarize"))) ; disabled even though enabled
      (is (false? (config/prompt-name-enabled? nrepl-map "chat-session-resume"))))) ; not in enable list

  (testing "Prompt names are converted to keywords"
    (let [nrepl-map {::config/config {:enable-prompts ["clojure_repl_system_prompt"]
                                      :disable-prompts []}}]
      (is (true? (config/prompt-name-enabled? nrepl-map "clojure_repl_system_prompt")))
      (is (true? (config/prompt-name-enabled? nrepl-map :clojure_repl_system_prompt)))))

  (testing "Mixed string and keyword prompt names"
    (let [nrepl-map {::config/config {:enable-prompts [:create-update-project-summary "clojure_repl_system_prompt"]}}]
      (is (true? (config/prompt-name-enabled? nrepl-map "create-update-project-summary")))
      (is (true? (config/prompt-name-enabled? nrepl-map "clojure_repl_system_prompt"))))))

(deftest test-resource-uri-enabled?
  (testing "No configuration - all resources enabled"
    (let [nrepl-map {::config/config {}}]
      (is (true? (config/resource-uri-enabled? nrepl-map "prompts/clojure-mcp/clojure_repl_system_prompt.md")))
      (is (true? (config/resource-uri-enabled? nrepl-map "any-resource")))
      (is (true? (config/resource-uri-enabled? nrepl-map :some-resource)))))

  (testing "Empty enable list - no resources enabled"
    (let [nrepl-map {::config/config {:enable-resources []}}]
      (is (false? (config/resource-uri-enabled? nrepl-map "prompts/clojure-mcp/clojure_repl_system_prompt.md")))
      (is (false? (config/resource-uri-enabled? nrepl-map "any-resource")))
      (is (false? (config/resource-uri-enabled? nrepl-map :some-resource)))))

  (testing "Enable specific resources"
    (let [nrepl-map {::config/config {:enable-resources ["prompts/clojure-mcp/clojure_repl_system_prompt.md"
                                                         "prompts/clojure-mcp/chat_session_resume.txt"]}}]
      (is (true? (config/resource-uri-enabled? nrepl-map "prompts/clojure-mcp/clojure_repl_system_prompt.md")))
      (is (true? (config/resource-uri-enabled? nrepl-map "prompts/clojure-mcp/chat_session_resume.txt")))
      (is (false? (config/resource-uri-enabled? nrepl-map "prompts/clojure-mcp/create_update_project_summary.md")))
      (is (false? (config/resource-uri-enabled? nrepl-map "other-resource")))))

  (testing "Disable specific resources"
    (let [nrepl-map {::config/config {:disable-resources ["prompts/clojure-mcp/scratch_pad_save_as.md"]}}]
      (is (true? (config/resource-uri-enabled? nrepl-map "prompts/clojure-mcp/clojure_repl_system_prompt.md")))
      (is (false? (config/resource-uri-enabled? nrepl-map "prompts/clojure-mcp/scratch_pad_save_as.md")))
      (is (true? (config/resource-uri-enabled? nrepl-map "other-resource")))))

  (testing "Enable and disable lists - disable takes precedence"
    (let [nrepl-map {::config/config {:enable-resources ["prompts/clojure-mcp/clojure_repl_system_prompt.md"
                                                         "prompts/clojure-mcp/chat_session_resume.txt"]
                                      :disable-resources ["prompts/clojure-mcp/chat_session_resume.txt"]}}]
      (is (true? (config/resource-uri-enabled? nrepl-map "prompts/clojure-mcp/clojure_repl_system_prompt.md")))
      (is (false? (config/resource-uri-enabled? nrepl-map "prompts/clojure-mcp/chat_session_resume.txt"))) ; disabled even though enabled
      (is (false? (config/resource-uri-enabled? nrepl-map "other-resource"))))) ; not in enable list

  (testing "Resource URIs are converted to keywords for comparison"
    (let [nrepl-map {::config/config {:enable-resources ["prompts/clojure-mcp/clojure_repl_system_prompt.md"
                                                         "test-resource"]
                                      :disable-resources []}}]
      (is (true? (config/resource-uri-enabled? nrepl-map "prompts/clojure-mcp/clojure_repl_system_prompt.md")))
      ;; Even when passed as keyword internally
      (is (true? (config/resource-uri-enabled? nrepl-map :test-resource)))))) ; simple keyword without special chars



(ns clojure-mcp.prompts-test
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.java.io :as io]
            [clojure-mcp.prompts :as prompts]
            [clojure-mcp.config :as config]
            [clojure.string :as str])
  (:import [java.io File]))

(defn with-temp-dir
  "Create a temporary directory and execute f with it, cleaning up after."
  [f]
  (let [temp-dir (File/createTempFile "test-prompts" "")
        dir-path (.getAbsolutePath temp-dir)]
    (.delete temp-dir)
    (.mkdir temp-dir)
    (try
      (f dir-path)
      (finally
        ;; Clean up temp dir and contents
        (doseq [file (.listFiles temp-dir)]
          (.delete file))
        (.delete temp-dir)))))

(defn create-test-file
  "Create a test file with content in the given directory."
  [dir-path filename content]
  (let [file (io/file dir-path filename)]
    (spit file content)
    (.getAbsolutePath file)))

(deftest test-create-prompt-from-config-inline
  (testing "Creating prompt from inline content"
    (let [config {:description "Test prompt"
                  :args [{:name "name" :description "User name" :required? true}
                         {:name "age" :description "User age" :required? false}]
                  :content "Hello {{name}}, you are {{age}} years old."}
          test-atom (atom {:clojure-mcp.config/config {:nrepl-user-dir "/tmp"}})
          prompt (prompts/create-prompt-from-config "test-prompt" config "/tmp" test-atom)]

      (is (= "test-prompt" (:name prompt)))
      (is (= "Test prompt" (:description prompt)))
      (is (= 2 (count (:arguments prompt))))

      ;; Test argument structure
      (let [arg1 (first (:arguments prompt))]
        (is (= "name" (:name arg1)))
        (is (= "User name" (:description arg1)))
        (is (true? (:required? arg1))))

      ;; Test rendering
      (let [result (atom nil)]
        ((:prompt-fn prompt) nil {"name" "Alice" "age" "30"}
                             (fn [r] (reset! result r)))
        (is (= "Hello Alice, you are 30 years old."
               (get-in @result [:messages 0 :content])))))))

(deftest test-create-prompt-from-config-file
  (testing "Creating prompt from file"
    (with-temp-dir
      (fn [temp-dir]
        (create-test-file temp-dir "prompt.md" "Welcome {{user}}!")

        (let [config {:description "File-based prompt"
                      :args [{:name "user" :description "Username" :required? true}]
                      :file-path "prompt.md"}
              test-atom (atom {:clojure-mcp.config/config {:nrepl-user-dir temp-dir}})
              prompt (prompts/create-prompt-from-config "file-prompt" config temp-dir test-atom)]

          (is (= "file-prompt" (:name prompt)))
          (is (= "File-based prompt" (:description prompt)))

          ;; Test rendering
          (let [result (atom nil)]
            ((:prompt-fn prompt) nil {"user" "Bob"}
                                 (fn [r] (reset! result r)))
            (is (= "Welcome Bob!"
                   (get-in @result [:messages 0 :content])))))))))

(deftest test-create-prompts-from-config
  (testing "Creating multiple prompts from config"
    (with-temp-dir
      (fn [temp-dir]
        (create-test-file temp-dir "review.md" "Review {{file}}")

        (let [config {"inline-prompt" {:description "Inline test"
                                       :args []
                                       :content "Simple prompt"}
                      "file-prompt" {:description "File test"
                                     :args [{:name "file" :description "File to review"}]
                                     :file-path "review.md"}}
              test-atom (atom {:clojure-mcp.config/config {:nrepl-user-dir temp-dir}})
              prompts (prompts/create-prompts-from-config config temp-dir test-atom)]

          (is (= 2 (count prompts)))
          (is (= #{"inline-prompt" "file-prompt"}
                 (set (map :name prompts)))))))))

(deftest test-make-prompts-with-defaults
  (testing "make-prompts includes default prompts"
    (with-temp-dir
      (fn [temp-dir]
        (let [test-atom (atom {:clojure-mcp.config/config {:nrepl-user-dir temp-dir}})
              prompts (prompts/make-prompts test-atom)
              prompt-names (set (map :name prompts))]

          ;; Should have at least the default prompts
          (is (contains? prompt-names "clojure_repl_system_prompt"))
          (is (contains? prompt-names "create-update-project-summary"))
          (is (contains? prompt-names "chat-session-summarize"))
          (is (contains? prompt-names "chat-session-resume"))
          (is (contains? prompt-names "plan-and-execute")))))))

(deftest test-make-prompts-with-override
  (testing "Config prompts override defaults with same name"
    (with-temp-dir
      (fn [temp-dir]
        (let [config {:prompts {"clojure_repl_system_prompt" {:description "Overridden system prompt"
                                                              :args []
                                                              :content "Custom system prompt"}
                                "new-prompt" {:description "Additional prompt"
                                              :args []
                                              :content "New content"}}
                      :nrepl-user-dir temp-dir}
              test-atom (atom {:clojure-mcp.config/config config})
              prompts (prompts/make-prompts test-atom)
              prompts-by-name (into {} (map (juxt :name identity) prompts))]

          (testing "System prompt is overridden"
            (let [system-prompt (get prompts-by-name "clojure_repl_system_prompt")]
              (is (= "Overridden system prompt" (:description system-prompt)))))

          (testing "Additional prompt is included"
            (is (contains? prompts-by-name "new-prompt"))
            (let [new-prompt (get prompts-by-name "new-prompt")]
              (is (= "Additional prompt" (:description new-prompt)))))

          (testing "Non-overridden defaults still present"
            (is (contains? prompts-by-name "chat-session-summarize"))))))))

(deftest test-prompt-filtering
  (testing "Prompts filtering configuration is respected in core.clj"
    ;; This test documents that filtering happens in core.clj, not in make-prompts
    ;; make-prompts returns all prompts, and core.clj filters based on config
    (with-temp-dir
      (fn [temp-dir]
        (let [config {:prompts {"custom1" {:description "Custom 1" :args [] :content "C1"}
                                "custom2" {:description "Custom 2" :args [] :content "C2"}}
                      :enable-prompts ["clojure_repl_system_prompt" "custom1"]
                      :nrepl-user-dir temp-dir}
              test-atom (atom {:clojure-mcp.config/config config})
              all-prompts (prompts/make-prompts test-atom)
              prompt-names (set (map :name all-prompts))]

          (testing "make-prompts returns all prompts without filtering"
            ;; All prompts should be present - filtering happens in core.clj
            (is (contains? prompt-names "clojure_repl_system_prompt"))
            (is (contains? prompt-names "custom1"))
            (is (contains? prompt-names "custom2"))
            (is (contains? prompt-names "chat-session-summarize"))
            (is (contains? prompt-names "chat-session-resume"))

            ;; Document that config/prompt-name-enabled? would filter these
            (is (config/prompt-name-enabled? @test-atom "clojure_repl_system_prompt"))
            (is (config/prompt-name-enabled? @test-atom "custom1"))
            (is (not (config/prompt-name-enabled? @test-atom "custom2")))
            (is (not (config/prompt-name-enabled? @test-atom "chat-session-summarize")))))))))

(deftest test-prompt-with-complex-template
  (testing "Complex mustache template rendering"
    (let [config {:description "Complex template"
                  :args [{:name "items" :description "List of items"}]
                  :content "Items:\n{{#items}}\n- {{.}}\n{{/items}}"}
          test-atom (atom {:clojure-mcp.config/config {:nrepl-user-dir "/tmp"}})
          prompt (prompts/create-prompt-from-config "complex" config "/tmp" test-atom)
          result (atom nil)]

      ((:prompt-fn prompt) nil {"items" ["apple" "banana" "cherry"]}
                           (fn [r] (reset! result r)))

      (let [content (get-in @result [:messages 0 :content])]
        (is (str/includes? content "- apple"))
        (is (str/includes? content "- banana"))
        (is (str/includes? content "- cherry"))))))

(deftest test-missing-file-handling
  (testing "Non-existent file returns nil prompt"
    (with-temp-dir
      (fn [temp-dir]
        (let [config {:description "Missing file"
                      :args []
                      :file-path "does-not-exist.md"}
              test-atom (atom {:clojure-mcp.config/config {:nrepl-user-dir temp-dir}})
              prompt (prompts/create-prompt-from-config "missing" config temp-dir test-atom)]

          (is (nil? prompt)))))))

(deftest test-absolute-path-handling
  (testing "Absolute paths are handled correctly"
    (with-temp-dir
      (fn [temp-dir]
        (let [abs-path (create-test-file temp-dir "absolute.md" "Absolute {{test}}")
              config {:description "Absolute path prompt"
                      :args [{:name "test" :description "Test param"}]
                      :file-path abs-path}
              test-atom (atom {:clojure-mcp.config/config {:nrepl-user-dir "/different/dir"}})
              prompt (prompts/create-prompt-from-config "absolute" config "/different/dir" test-atom)]

          (is (= "absolute" (:name prompt)))

          ;; Test rendering
          (let [result (atom nil)]
            ((:prompt-fn prompt) nil {"test" "works"}
                                 (fn [r] (reset! result r)))
            (is (= "Absolute works"
                   (get-in @result [:messages 0 :content])))))))))

(deftest test-template-argument-handling
  (testing "Template handles extra and missing arguments gracefully"
    (let [config {:description "Flexible template"
                  :args [{:name "name" :description "User name"}
                         {:name "age" :description "User age"}]
                  :content "Hello {{name}}!"}
          test-atom (atom {:clojure-mcp.config/config {:nrepl-user-dir "/tmp"}})
          prompt (prompts/create-prompt-from-config "flexible" config "/tmp" test-atom)]

      (testing "Extra arguments are ignored"
        (let [result (atom nil)]
          ((:prompt-fn prompt) nil {"name" "Alice" "age" "30" "extra" "ignored"}
                               (fn [r] (reset! result r)))
          (is (= "Hello Alice!"
                 (get-in @result [:messages 0 :content])))))

      (testing "Missing template variables render as empty"
        (let [config-with-missing {:description "Template with optional"
                                   :args [{:name "name" :description "Name"}]
                                   :content "Hello {{name}}, age: {{age}}"}
              prompt-missing (prompts/create-prompt-from-config "missing" config-with-missing "/tmp" test-atom)
              result (atom nil)]
          ((:prompt-fn prompt-missing) nil {"name" "Bob"}
                                       (fn [r] (reset! result r)))
          (is (= "Hello Bob, age: "
                 (get-in @result [:messages 0 :content]))))))))
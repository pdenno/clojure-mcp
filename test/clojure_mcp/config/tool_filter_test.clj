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

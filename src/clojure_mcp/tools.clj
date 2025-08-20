(ns clojure-mcp.tools
  "Tool construction functions for Clojure MCP.
   Uses dynamic symbol resolution to avoid circular dependencies.")

;; Tool creation function symbols organized by category
(def read-only-tool-syms
  "Symbols for read-only tool creation functions"
  ['clojure-mcp.tools.directory-tree.tool/directory-tree-tool
   'clojure-mcp.tools.unified-read-file.tool/unified-read-file-tool
   'clojure-mcp.tools.grep.tool/grep-tool
   'clojure-mcp.tools.glob-files.tool/glob-files-tool
   'clojure-mcp.tools.think.tool/think-tool
   'clojure-mcp.tools.project.tool/inspect-project-tool])

(def eval-tool-syms
  "Symbols for evaluation tool creation functions"
  ['clojure-mcp.tools.eval.tool/eval-code
   'clojure-mcp.tools.bash.tool/bash-tool])

(def editing-tool-syms
  "Symbols for file editing tool creation functions"
  ['clojure-mcp.tools.form-edit.combined-edit-tool/unified-form-edit-tool
   'clojure-mcp.tools.form-edit.tool/sexp-update-tool
   'clojure-mcp.tools.file-edit.tool/file-edit-tool
   'clojure-mcp.tools.file-write.tool/file-write-tool])

(def agent-tool-syms
  "Symbols for agent tool creation functions (require API keys)"
  ['clojure-mcp.tools.dispatch-agent.tool/dispatch-agent-tool
   'clojure-mcp.tools.architect.tool/architect-tool])

(def experimental-tool-syms
  "Symbols for experimental tool creation functions"
  ['clojure-mcp.tools.scratch-pad.tool/scratch-pad-tool
   'clojure-mcp.tools.code-critique.tool/code-critique-tool])

;; Note: introspection tools are already included in read-only-tool-syms
;; This is kept for documentation purposes but not used in all-tool-syms
(def introspection-tool-syms
  "Symbols for introspection tool creation functions"
  ['clojure-mcp.tools.project.tool/inspect-project-tool])

(def all-tool-syms
  "All tool symbols combined"
  (concat read-only-tool-syms
          eval-tool-syms
          editing-tool-syms
          agent-tool-syms
          experimental-tool-syms))

;; Memoized resolution functions
(def resolve-tool-fn
  "Memoized function to resolve a tool creation function symbol"
  (memoize
   (fn [sym]
     (require (symbol (namespace sym)))
     (deref (resolve sym)))))

(defn resolve-tool-fns
  "Resolve a collection of tool function symbols"
  [syms]
  (mapv resolve-tool-fn syms))

;; Public API functions
(defn build-read-only-tools
  "Builds and returns the read-only tools that don't modify files.
   These tools are safe for exploration and analysis."
  [nrepl-client-atom]
  (let [tool-fns (resolve-tool-fns read-only-tool-syms)]
    (mapv #(% nrepl-client-atom) tool-fns)))

(defn build-all-tools
  "Builds and returns all available tools including read-only, eval, editing, and agent tools.
   Note: Agent tools require API keys to be configured."
  [nrepl-client-atom]
  (let [tool-fns (resolve-tool-fns all-tool-syms)]
    (mapv #(% nrepl-client-atom) tool-fns)))

;; Category-specific builders for fine-grained control
(defn build-eval-tools
  "Build just the evaluation tools"
  [nrepl-client-atom]
  (let [tool-fns (resolve-tool-fns eval-tool-syms)]
    (mapv #(% nrepl-client-atom) tool-fns)))

(defn build-editing-tools
  "Build just the editing tools"
  [nrepl-client-atom]
  (let [tool-fns (resolve-tool-fns editing-tool-syms)]
    (mapv #(% nrepl-client-atom) tool-fns)))

(defn build-agent-tools
  "Build just the agent tools (require API keys)"
  [nrepl-client-atom]
  (let [tool-fns (resolve-tool-fns agent-tool-syms)]
    (mapv #(% nrepl-client-atom) tool-fns)))

(defn build-experimental-tools
  "Build just the experimental tools"
  [nrepl-client-atom]
  (let [tool-fns (resolve-tool-fns experimental-tool-syms)]
    (mapv #(% nrepl-client-atom) tool-fns)))

(defn build-custom-tools
  "Build a custom set of tools from provided symbols.
   
   Example:
   (build-custom-tools nrepl-client-atom
     ['clojure-mcp.tools.eval.tool/eval-code
      'clojure-mcp.tools.file-write.tool/file-write-tool])"
  [nrepl-client-atom tool-syms]
  (let [tool-fns (resolve-tool-fns tool-syms)]
    (mapv #(% nrepl-client-atom) tool-fns)))

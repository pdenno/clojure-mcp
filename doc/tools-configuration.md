# Tools Configuration

## Overview

The `:tools-config` key in `.clojure-mcp/config.edn` allows you to provide tool-specific configurations. This is particularly useful for tools that use AI models or have other configurable behavior.

## Configuration Structure

```edn
{:tools-config {<tool-id> {<config-key> <config-value>}}}
```

## Model Configuration for Tools

Many tools can be configured to use specific AI models. The configuration system provides a helper function `get-tool-model` that simplifies this pattern.

### Example Configuration

```edn
{:tools-config {:dispatch_agent {:model :openai/my-o3}
                :architect {:model :anthropic/my-claude-3}
                :code_critique {:model :openai/my-gpt-4o}
                :bash {:default-timeout-ms 60000
                       :working-dir "/opt/project"
                       :bash-over-nrepl false}}
 
 ;; Define the models referenced above
 :models {:openai/my-o3 {:model-name "o3-mini"
                      :temperature 0.2
                      :api-key [:env "OPENAI_API_KEY"]}
          :openai/my-gpt-4o {:model-name "gpt-4o"
                          :temperature 0.3
                          :api-key [:env "OPENAI_API_KEY"]}
          :anthropic/my-claude-3 {:model-name "claude-3-haiku-20240307"
                                  :api-key [:env "ANTHROPIC_API_KEY"]}}}
```

## API Functions

### `get-tools-config`
Returns the entire tools configuration map.

```clojure
(config/get-tools-config nrepl-client-map)
;; => {:dispatch_agent {:model :openai/o3}, :architect {...}}
```

### `get-tool-config`
Returns configuration for a specific tool.

```clojure
(config/get-tool-config nrepl-client-map :dispatch_agent)
;; => {:model :openai/o3}
```

### `get-tool-model`
Creates a model for a tool based on its configuration. This function is located in `clojure-mcp.agent.langchain.model` namespace. It's a convenience function that:
1. Looks up the tool's configuration
2. Extracts the model key (default: `:model`)
3. Creates the model using the models configuration
4. Handles errors gracefully

```clojure
(require '[clojure-mcp.agent.langchain.model :as model])

;; Using default :model key
(model/get-tool-model nrepl-client-map :dispatch_agent)

;; Using custom config key
(model/get-tool-model nrepl-client-map :code_critique :primary-model)
(model/get-tool-model nrepl-client-map :code_critique :fallback-model)
```

## Implementing Tool Configuration

To add configuration support to a tool:

```clojure
(ns my-tool.tool
  (:require [clojure-mcp.config :as config]
            [clojure-mcp.agent.langchain.model :as model]))

(defn create-my-tool
  ([nrepl-client-atom]
   (create-my-tool nrepl-client-atom nil))
  ([nrepl-client-atom model]
   (let [;; Use explicitly provided model or get from config
         final-model (or model
                        (model/get-tool-model @nrepl-client-atom :my_tool))]
     {:tool-type :my-tool
      :nrepl-client-atom nrepl-client-atom
      :model final-model})))
```

## Currently Supported Tools

### AI-Powered Tools

- **dispatch_agent**: Supports `:model` configuration for custom AI models
- **architect**: Supports `:model` configuration for custom AI models
- **code_critique**: Supports `:model` configuration for custom AI models

### Other Configurable Tools

- **bash**: Command execution configuration
  - `:default-timeout-ms` - Default timeout in milliseconds (default: `180000` for 3 minutes)
  - `:working-dir` - Default working directory for commands (uses nrepl-user-dir if not set)
  - `:bash-over-nrepl` - Override global bash-over-nrepl setting (true/false)

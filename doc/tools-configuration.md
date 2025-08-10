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
{:tools-config {:dispatch_agent {:model :openai/o3}
                :architect {:model :anthropic/claude-3-haiku-20240307}
                :code_critique {:primary-model :openai/gpt-4o
                               :fallback-model :anthropic/claude-3-haiku-20240307}}
 
 ;; Define the models referenced above
 :models {:openai/o3 {:model-name "o3-mini"
                      :temperature 0.2
                      :api-key [:env "OPENAI_API_KEY"]}
          :openai/gpt-4o {:model-name "gpt-4o"
                          :temperature 0.3
                          :api-key [:env "OPENAI_API_KEY"]}
          :anthropic/claude-3-haiku-20240307 {:model-name "claude-3-haiku-20240307"
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
Creates a model for a tool based on its configuration. This is a convenience function that:
1. Looks up the tool's configuration
2. Extracts the model key (default: `:model`)
3. Creates the model using the models configuration
4. Handles errors gracefully

```clojure
;; Using default :model key
(config/get-tool-model nrepl-client-map :dispatch_agent)

;; Using custom config key
(config/get-tool-model nrepl-client-map :code_critique :primary-model)
(config/get-tool-model nrepl-client-map :code_critique :fallback-model)
```

## Implementing Tool Configuration

To add configuration support to a tool:

```clojure
(ns my-tool.tool
  (:require [clojure-mcp.config :as config]))

(defn create-my-tool
  ([nrepl-client-atom]
   (create-my-tool nrepl-client-atom nil))
  ([nrepl-client-atom model]
   (let [;; Use explicitly provided model or get from config
         final-model (or model
                        (config/get-tool-model @nrepl-client-atom :my_tool))]
     {:tool-type :my-tool
      :nrepl-client-atom nrepl-client-atom
      :model final-model})))
```

## Currently Supported Tools

- **dispatch_agent**: Supports `:model` configuration for custom AI models

## Extending Configuration

Tools can use any configuration keys they need. Common patterns include:
- `:model` - Primary AI model to use
- `:fallback-model` - Backup model if primary fails
- `:timeout` - Operation timeout values
- `:max-retries` - Retry configuration
- `:enabled-features` - Feature flags

The configuration system is flexible and can be extended as needed for each tool's requirements.

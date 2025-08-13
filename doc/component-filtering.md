# Component Filtering Configuration

ClojureMCP allows fine-grained control over which tools, prompts, and resources are exposed to AI assistants through configuration options in `.clojure-mcp/config.edn`. This is useful for creating focused MCP servers with only the components you need.

## Table of Contents
- [Overview](#overview)
- [Tools Filtering](#tools-filtering)
- [Prompts Filtering](#prompts-filtering)
- [Resources Filtering](#resources-filtering)
- [Examples](#examples)
- [Best Practices](#best-practices)

## Overview

Component filtering uses an allow/deny list pattern:
- **Enable lists** (`enable-*`) - When specified, ONLY these items are enabled
- **Disable lists** (`disable-*`) - Applied after enable filtering to remove specific items
- **Default behavior** - When no filtering is specified, all components are enabled

The filtering logic follows this order:
1. If an enable list is provided and empty (`[]`), nothing is enabled
2. If an enable list is provided with items, only those items are enabled
3. If no enable list is provided (`nil`), all items start enabled
4. The disable list is then applied to remove items from the enabled set

## Tools Filtering

Control which tools are available to the AI assistant.

### Configuration Keys

```edn
{:enable-tools [:clojure-eval :read-file :file-write]  ; Only these tools
 :disable-tools [:dispatch-agent :architect]}          ; Remove these tools
```

### Tool Identifiers

Tools can be specified using keywords or strings:
- `:clojure-eval` or `"clojure-eval"`
- `:read-file` or `"read_file"` 
- `:file-write` or `"file_write"`

Common tool IDs include:
- `:clojure-eval` - Evaluate Clojure code
- `:read-file` - Read file contents
- `:file-edit` - Edit files
- `:file-write` - Write files
- `:bash` - Execute shell commands
- `:grep` - Search file contents
- `:glob-files` - Find files by pattern
- `:dispatch-agent` - Launch sub-agents
- `:architect` - Technical planning
- `:code-critique` - Code review
- `:scratch-pad` - Persistent storage

### Examples

**Minimal REPL-only server:**
```edn
{:enable-tools [:clojure-eval]}
```

**Read-only exploration server:**
```edn
{:enable-tools [:read-file :grep :glob-files :LS :clojure-inspect-project]}
```

**Full access except agents:**
```edn
{:disable-tools [:dispatch-agent :architect :code-critique]}
```

## Prompts Filtering

Control which system prompts are provided to the AI assistant.

### Configuration Keys

```edn
{:enable-prompts ["clojure_repl_system_prompt" "chat-session-summarize"]
 :disable-prompts ["scratch-pad-save-as"]}
```

### Prompt Names

Prompts are identified by their string names (not keywords):
- `"clojure_repl_system_prompt"` - Main REPL interaction prompt
- `"chat-session-summarize"` - Session summarization
- `"scratch-pad-load"` - Loading scratch pad data
- `"scratch-pad-save-as"` - Saving scratch pad snapshots

### Examples

**Essential prompts only:**
```edn
{:enable-prompts ["clojure_repl_system_prompt"]}
```

**Disable scratch pad prompts:**
```edn
{:disable-prompts ["scratch-pad-load" "scratch-pad-save-as"]}
```

## Resources Filtering

Control which resource files are exposed to the AI assistant.

### Configuration Keys

```edn
{:enable-resources ["PROJECT_SUMMARY.md" "README.md"]
 :disable-resources ["CLAUDE.md" "LLM_CODE_STYLE.md"]}
```

### Resource Names

Resources are identified by their filenames (not URIs or paths):
- `"PROJECT_SUMMARY.md"` - Project overview
- `"README.md"` - Main documentation
- `"CLAUDE.md"` - Claude-specific instructions
- `"LLM_CODE_STYLE.md"` - Coding style guide

### Examples

**Only project documentation:**
```edn
{:enable-resources ["PROJECT_SUMMARY.md" "README.md"]}
```

**Remove AI-specific resources:**
```edn
{:disable-resources ["CLAUDE.md" "LLM_CODE_STYLE.md"]}
```

## Complete Examples

### Minimal Configuration

A minimal MCP server for REPL interaction only:

```edn
{:enable-tools [:clojure-eval]
 :enable-prompts ["clojure_repl_system_prompt"]
 :enable-resources []}
```

### Read-Only Configuration

For code review and exploration without modification:

```edn
{:enable-tools [:read-file :grep :glob-files :LS :clojure-inspect-project :think]
 :disable-prompts ["scratch-pad-load" "scratch-pad-save-as"]
 :enable-resources ["PROJECT_SUMMARY.md" "README.md"]}
```

### Development Configuration

Full development capabilities without agent tools:

```edn
{:disable-tools [:dispatch-agent :architect :code-critique]
 :disable-resources ["CLAUDE.md"]}
```

### Custom Focused Server

A server focused on file operations and bash commands:

```edn
{:enable-tools [:read-file :file-edit :file-write :bash :grep :glob-files]
 :enable-prompts ["clojure_repl_system_prompt"]
 :enable-resources ["PROJECT_SUMMARY.md"]}
```

## Best Practices

### 1. Start Restrictive
Begin with a minimal set of enabled components and add more as needed:
```edn
{:enable-tools [:clojure-eval :read-file]
 :enable-prompts ["clojure_repl_system_prompt"]
 :enable-resources ["PROJECT_SUMMARY.md"]}
```

### 2. Use Enable for Security
When security is important, use enable lists to explicitly specify what's allowed:
```edn
{:enable-tools [:read-file :grep]  ; Only reading, no writing
 :enable-resources []}              ; No resource access
```

### 3. Use Disable for Convenience
When you want most features but need to remove a few:
```edn
{:disable-tools [:bash]              ; Everything except shell access
 :disable-resources ["SENSITIVE.md"]} ; All resources except sensitive ones
```

### 4. Document Your Choices
Add comments explaining why certain components are filtered:
```edn
{;; Disable agent tools to reduce API costs
 :disable-tools [:dispatch-agent :architect :code-critique]
 
 ;; Only essential prompts for focused interaction
 :enable-prompts ["clojure_repl_system_prompt"]}
```

### 5. Test Your Configuration
After setting up filtering, verify the configuration works as expected:
1. Start your MCP server
2. Connect with Claude Desktop or your MCP client
3. Check that only the intended tools appear
4. Verify prompts and resources are properly filtered

### 6. Environment-Specific Configs
Consider using different configurations for different environments:

**Development:** `.clojure-mcp/config.edn`
```edn
{:disable-tools [:dispatch-agent]}  ; Save API costs during development
```

**Production:** `.clojure-mcp/config.prod.edn`
```edn
{:enable-tools [:read-file :clojure-eval]  ; Restricted for production
 :disable-tools [:bash :file-write]}
```

## Checking Active Components

To see which components are active after filtering, you can:

1. **Check the logs** - The MCP server logs which tools, prompts, and resources are loaded
2. **Use the MCP client** - Claude Desktop shows available tools in the interface
3. **Query programmatically** - Use the configuration functions:
   ```clojure
   (config/tool-id-enabled? nrepl-client-map :clojure-eval)
   (config/prompt-name-enabled? nrepl-client-map "clojure_repl_system_prompt")
   (config/resource-name-enabled? nrepl-client-map "README.md")
   ```

## Troubleshooting

### Components Not Appearing
- Verify the correct identifier format (keywords for tools, strings for prompts/resources)
- Check that enable lists aren't empty `[]` when you want defaults
- Ensure the config file is in the correct location: `.clojure-mcp/config.edn`

### Unexpected Components Visible
- Check if you're using disable lists without enable lists
- Verify spelling of component identifiers
- Remember that disable is applied after enable

### Configuration Not Loading
- Check file syntax with a Clojure EDN validator
- Look for error messages in the MCP server logs
- Ensure the configuration file has proper EDN format

## See Also

- [Model Configuration](model-configuration.md) - Configure custom LLM models
- [Tools Configuration](tools-configuration.md) - Configure tool-specific settings
- [Creating Custom MCP Servers](custom-mcp-server.md) - Build servers with custom filtering

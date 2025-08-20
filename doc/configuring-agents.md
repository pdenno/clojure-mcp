# Configuring Custom Agents

The Clojure MCP server supports defining custom AI agents through configuration. Each configured agent becomes its own tool in the MCP interface, allowing you to create specialized assistants without writing code.

## Overview

The agent tool builder dynamically creates MCP tools from agent configurations defined in your `.clojure-mcp/config.edn` file. Each agent can have:
- Its own system prompt and personality
- Access to specific tools (read, write, execute, or none)
- Custom context (files, project info)
- Different AI models
- Unique name and description

## ⚠️ Important: Tool Access Changes

**Agents have NO tools by default.** You must explicitly specify which tools an agent can use via `:enable-tools`. This is a safety feature to prevent unintended access to powerful capabilities.

Agents can now access ALL available tools, including:
- Read-only tools (grep, read-file, etc.)
- **File editing tools** (file-write, clojure-edit, etc.)
- **Code execution tools** (clojure-eval, bash)
- Agent tools (dispatch-agent, architect)

Always consider the security implications when granting tool access.

## Basic Configuration

Add an `:agents` key to your `.clojure-mcp/config.edn`:

```clojure
{:agents [{:id :my-agent
           :name "my_agent"
           :description "A custom agent for specific tasks"
           :system-message "You are a helpful assistant specialized in..."
           :context true
           :enable-tools [:read-file :grep]  ; Must explicitly list tools
           :disable-tools nil}]}
```

## Configuration Options

Each agent configuration supports the following keys:

### Required Fields

- **`:id`** - Unique keyword identifier for the agent
- **`:name`** - String name that appears as the tool name in MCP
- **`:description`** - String description shown in the MCP interface
- **`:system-message`** - System prompt that defines the agent's behavior

### Optional Fields

- **`:model`** - AI model to use (keyword reference or model object)
  - References models defined in `:models` config
  - Falls back to default if not specified
- **`:context`** - What context to provide the agent:
  - `true` - Include PROJECT_SUMMARY.md and code index
  - `false` or `nil` - No context
  - `["file1.md", "file2.clj"]` - Specific file paths
- **`:enable-tools`** - Controls which tools the agent can use:
  - `nil` - **No tools** (default if omitted)
  - `[:all]` - All available tools
  - `[:tool1 :tool2]` - Specific tools only
- **`:disable-tools`** - List of tool IDs to disable (applied after enable-tools)

## Available Tools for Agents

Agents can potentially access all MCP tools:

### Read-Only Tools
| Tool ID | Description |
|---------|-------------|
| `:LS` | Directory tree view |
| `:read-file` | Read file contents with pattern matching |
| `:grep` | Search file contents |
| `:glob-files` | Find files by pattern |
| `:think` | Reasoning tool |
| `:clojure-inspect-project` | Project structure analysis |

### Evaluation Tools
| Tool ID | Description |
|---------|-------------|
| `:clojure-eval` | Execute Clojure code in REPL |
| `:bash` | Execute shell commands |

### File Editing Tools
| Tool ID | Description |
|---------|-------------|
| `:file-write` | Create or overwrite files |
| `:file-edit` | Edit files by text replacement |
| `:clojure-edit` | Structure-aware Clojure editing |
| `:clojure-edit-replace-sexp` | S-expression replacement |

### Agent Tools
| Tool ID | Description |
|---------|-------------|
| `:dispatch-agent` | Launch sub-agents |
| `:architect` | Technical planning assistant |
| `:scratch-pad` | Persistent data storage |
| `:code-critique` | Code review feedback |

## Tool Access Patterns

### No Tools (Default)
```clojure
{:id :minimal-agent
 :name "minimal_agent"
 :description "Agent with no tools"
 :system-message "You provide advice based only on your training"
 ;; :enable-tools nil  ; Can omit - nil is default
}
```

### Read-Only Access
```clojure
{:id :research-agent
 :name "research_agent"
 :description "Can read but not modify"
 :system-message "You research and analyze code"
 :enable-tools [:read-file :grep :glob-files :clojure-inspect-project]}
```

### Write Access
```clojure
{:id :code-writer
 :name "code_writer"
 :description "Can create and modify files"
 :system-message "You write and refactor code. Always test before writing."
 :enable-tools [:read-file :grep :clojure-eval :file-write :clojure-edit]}
```

### Full Access
```clojure
{:id :full-access-agent
 :name "full_access"
 :description "Access to all tools - use with caution"
 :system-message "You have full system access. Confirm destructive operations."
 :enable-tools [:all]
 :disable-tools [:dispatch-agent]  ; Can still exclude specific tools
}
```

## Complete Example

Here's a comprehensive configuration with agents of varying capability levels:

```clojure
{:allowed-directories ["." "src" "test" "resources"]
 
 ;; Define custom models (optional)
 :models {:anthropic/fast
          {:model-name "claude-3-haiku-20240307"
           :api-key [:env "ANTHROPIC_API_KEY"]
           :temperature 0.3
           :max-tokens 2048}
          
          :openai/smart
          {:model-name "gpt-4-turbo-preview"
           :api-key [:env "OPENAI_API_KEY"]
           :temperature 0.2
           :max-tokens 4096}}
 
 ;; Agent definitions - from least to most capable
 :agents [;; Minimal agent - no tools
          {:id :advisor
           :name "advisor"
           :description "Provides advice without any tool access"
           :system-message "You are a technical advisor. Provide guidance based on 
                           your knowledge without accessing files or running code."
           :context false
           ;; No :enable-tools means no tools
          }
          
          ;; Read-only research agent
          {:id :research-agent
           :name "research_agent"
           :description "Researches code patterns and finds examples"
           :system-message "You are a research specialist. Find patterns, examples, 
                           and analyze structure. Be thorough and provide specific locations."
           :model :anthropic/fast
           :context true
           :enable-tools [:grep :glob-files :read-file :clojure-inspect-project]
           :disable-tools nil}
          
          ;; Documentation specialist
          {:id :doc-reader
           :name "doc_reader"
           :description "Reads and summarizes documentation"
           :system-message "You are a documentation specialist. Summarize clearly 
                           and focus on practical usage."
           :context ["README.md" "doc/"]
           :enable-tools [:read-file :glob-files]}
          
          ;; Test runner - can execute but not modify
          {:id :test-runner
           :name "test_runner"
           :description "Runs tests and analyzes results"
           :system-message "You run tests and analyze results. You can execute code 
                           but cannot modify files."
           :context ["test/"]
           :enable-tools [:read-file :grep :glob-files :clojure-eval :bash]
           :disable-tools [:file-write :file-edit :clojure-edit]}
          
          ;; Code writer - can modify code
          {:id :code-writer
           :name "code_writer"
           :description "Writes and modifies code files"
           :system-message "You are a code writing assistant. Create and edit files 
                           responsibly. Always test code before writing to files. 
                           Explain changes clearly."
           :model :openai/smart
           :context true
           :enable-tools [:read-file :grep :glob-files 
                          :clojure-eval :bash
                          :file-write :file-edit 
                          :clojure-edit :clojure-edit-replace-sexp]}
          
          ;; Full access agent - use with extreme caution
          {:id :admin-agent
           :name "admin_agent"
           :description "Full system access - use with extreme caution"
           :system-message "You have complete system access. Always confirm destructive 
                           operations. Explain risks before taking actions. Never delete 
                           or overwrite without explicit confirmation."
           :context true
           :enable-tools [:all]  ; Access to everything
           :disable-tools nil}]}
```

## Using Configured Agents

Once configured, your agents appear as individual tools in your MCP client (like Claude Desktop):

1. Each agent shows up with its configured name (e.g., `research_agent`, `code_writer`)
2. Select the appropriate agent tool for your task
3. Send your prompt to the agent
4. The agent responds according to its specialized configuration and tool access

### Example Usage

```
User: research_agent("Find all uses of multimethods in this project")
Research Agent: I'll search for multimethod usage across the project...
[Uses only read-only tools to search and analyze]

User: code_writer("Add a new test for the filter-tools function")
Code Writer: I'll create a test for filter-tools. Let me first examine the existing tests...
[Can read, evaluate, and write files]

User: advisor("What's the best way to structure a Clojure web app?")
Advisor: Based on common patterns and best practices...
[Provides advice without accessing any tools]
```

## Security Considerations

### Principle of Least Privilege

Always give agents the minimum tools necessary for their task:

1. **No tools** for general advice/consultation
2. **Read-only tools** for analysis and research
3. **Execution tools** only when testing is required
4. **Write tools** only when file modification is needed
5. **Full access** only in controlled environments with trusted users

### Risk Levels

| Access Level | Risk | Use Cases |
|-------------|------|-----------|
| No tools | **None** | General advice, explanations |
| Read-only | **Low** | Code review, research, analysis |
| Read + Execute | **Medium** | Testing, debugging, validation |
| Read + Write | **High** | Code generation, refactoring |
| All tools | **Very High** | System administration, complex automation |

### Best Practices

1. **Default to no tools** - Only add what's needed
2. **Use `:disable-tools`** to remove dangerous tools even from `:all`
3. **Clear system messages** - Instruct agents about responsible tool use
4. **Audit configurations** - Regularly review agent capabilities
5. **Test in sandbox** - Try new configurations in safe environments first

## Context Configuration

The `:context` field controls what information the agent has access to:

### Default Context (`:context true`)
Includes:
- `PROJECT_SUMMARY.md` if it exists
- Current project structure from `clojure-inspect-project`
- `.clojure-mcp/code_index.txt` if it exists

### Custom File Context
```clojure
:context ["README.md"           ; Single file
          "doc/"                ; All files in directory
          "src/my_app/core.clj" ; Specific source file
          "../other-project/summary.md"] ; Relative paths work
```

### No Context (`:context false` or `:context nil`)
Agent starts with only its system message, useful for general-purpose agents.

## Model Configuration

Agents can use custom models defined in the `:models` configuration:

```clojure
{:models {:openai/o3-mini
          {:model-name "o3-mini"
           :api-key [:env "OPENAI_API_KEY"]
           :temperature 0.1}}
           
 :agents [{:id :reasoning-agent
           :name "reasoning_agent"
           :model :openai/o3-mini  ; Reference the model
           ;; ... other config
           }]}
```

## Tool Filtering Logic

- If `:enable-tools` is `nil` or omitted: **No tools enabled**
- If `:enable-tools` is `[:all]`: All available tools enabled
- If `:enable-tools` is `[...]`: Only listed tools enabled
- `:disable-tools` is applied after `:enable-tools` to remove specific tools

Examples:
```clojure
;; No tools (default)
{:enable-tools nil}  ; or omit entirely

;; Specific tools only
{:enable-tools [:read-file :grep]}

;; All tools except some
{:enable-tools [:all]
 :disable-tools [:bash :file-write]}
```

## Caching and Performance

- Agents are cached after first creation for performance
- The same agent instance is reused across invocations
- Cache key is based on the agent's `:id`
- Agents maintain conversation memory within a session

## Troubleshooting

### Agent Not Appearing
- Check that `:agents` is properly formatted in `config.edn`
- Ensure all required fields (`:id`, `:name`, `:description`, `:system-message`) are present
- Restart the MCP server after configuration changes

### No Tools Available
- Remember: agents have no tools by default
- Explicitly list tools in `:enable-tools`
- Check tool IDs match exactly (e.g., `:read-file` not `:read_file`)

### Model Not Found
- Verify model is defined in `:models` configuration
- Check environment variables for API keys
- Ensure model name is correct for the provider

### Tools Not Working
- Verify tool IDs in `:enable-tools` match available tools
- Check that tools aren't disabled in `:disable-tools`
- Ensure the agent has the necessary tool combination (e.g., needs `:read-file` before `:clojure-edit`)

## Migration from Previous Versions

If you have existing agent configurations:

### Old Behavior (pre-update)
- Agents had all read-only tools by default
- Could not access write or execution tools

### New Behavior
- Agents have NO tools by default
- Can access any tools when explicitly enabled

### Migration Steps
1. Review existing agent configs
2. Add explicit `:enable-tools` lists
3. For read-only agents, add: `:enable-tools [:read-file :grep :glob-files ...]`
4. Test thoroughly before deploying

## Integration with MCP Server

The agent tool builder is automatically included in the main MCP server. When the server starts:

1. Reads the `:agents` configuration from `.clojure-mcp/config.edn`
2. Creates a separate tool for each configured agent
3. Each agent gets only its specified tools
4. Registers these tools with the MCP protocol
5. Each agent appears as an individual tool in your MCP client

No code changes are required - just add agent configurations to your `config.edn` file and restart the server.

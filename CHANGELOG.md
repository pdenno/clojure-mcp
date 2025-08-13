# Changelog

## [0.1.8-alpha] - 2025-08-13

### What's New

This release brings major configuration enhancements and improved Clojure code editing:

** Key Highlights:**
- **Custom LLM Models** - Define your own model configurations in `.clojure-mcp/config.edn` with environment variable support for API keys
- **Scittle Support** - Connect directly to Scittle nREPL without configuration
- **Component Filtering** - Choose exactly which tools, prompts, and resources your server exposes
- **Improved Code Editing** - Much better handling of preceeding comments and sexp editing
- **Tool Configuration** - Configure individual tools with custom settings including model selection

**Example Model Configuration:**
```edn
:models {:openai/my-fast {:model-name "gpt-4o"
                          :api-key [:env "OPENAI_API_KEY"]
                          :base-url [:env "OPENROUTER_URL"]
                          :temperature 0.3}
         :anthropic/my-claude {:model-name "claude-3-5-sonnet-20241022"
                              :temperature 0.7}}

:tools-config {:dispatch_agent {:model :openai/my-fast}}
```

**Documentation:**

These docs are incomplete but should get you started...

- [Component Filtering Guide](https://github.com/bhauman/clojure-mcp/blob/main/doc/component-filtering.md) - Control which components are exposed
- [Model Configuration Guide](https://github.com/bhauman/clojure-mcp/blob/main/doc/model-configuration.md)
- [Tools Configuration Guide](https://github.com/bhauman/clojure-mcp/blob/main/doc/tools-configuration.md)
- [Default Models Reference](https://github.com/bhauman/clojure-mcp/blob/main/src/clojure_mcp/agent/langchain/model.clj)

**Coming Soon:**
- Editing agent for cleaner context management
- Expanded tool configuration options
- Custom resources, prompts, tools, and agents definable in config

#### Custom Model Configuration System
Complete support for user-defined LLM model configurations via `.clojure-mcp/config.edn`:
- **Named model definitions**: Define custom models under the `:models` key with provider-specific settings
- **Environment variable support**: Secure API key management with `[:env "VAR_NAME"]` syntax
- **Provider flexibility**: Support for OpenAI, Anthropic, Google Gemini
- **Validation with Clojure spec**: Ensures configuration correctness at startup
- **Fallback to defaults**: Built-in models remain available when custom ones aren't defined

#### Tool-Specific Configuration System
New `:tools-config` key for configuring individual tools:
- **Per-tool settings**: Configure tools like `dispatch_agent`, `architect`, and `code_critique` with custom models
- **Example**: `:tools-config {:dispatch_agent {:model :openai/o3}}`

#### Component Filtering System
Fine-grained control over which components are enabled:
- **Tool filtering**: `:enable-tools` and `:disable-tools` configuration options
- **Prompt filtering**: `:enable-prompts` and `:disable-prompts` for controlling AI prompts
- **Resource filtering**: `:enable-resources` and `:disable-resources` for managing exposed files
- **Flexible formats**: Support for both keyword and string identifiers

#### S-Expression Editing Improvements (#77)
Major revamp of how s-expression editing works:
- **Better pattern matching**: More reliable matching of Clojure forms
- **Private function support**: `clojure_edit` now matches private `def-` and `defn-` forms flexibly
- **Comment-aware editing**: Improved handling of comments in top-level forms
- **Scittle support**: Initial support for Scittle (browser Clojure) files
- **Basilisp extension**: `.lpy` files now recognized as Clojure for editing

### Added
- **Claude 4.1 and GPT-5 models**: Support for latest AI models
- **5 additional OpenAI models**: Expanded model selection with simplified names
- **Code index documentation**: Initial documentation for code indexing feature (#76)
- **Example configuration files**:
  - `resources/configs/example-models-with-provider.edn` for model configuration
  - `resources/configs/example-tools-config.edn` for tool-specific settings
  - `resources/configs/example-component-filtering.edn` for component filtering
- **Model configuration documentation**: Comprehensive guide at `doc/model-configuration.md`
- **Bash tool enhancements**:
  - Configurable timeout with `timeout_ms` parameter
  - Additional configuration options for execution control

### Changed
- **Write-file-guard default**: Changed from `:full-read` to `:partial-read` for better usability
- **LangChain4j upgrade**: Updated to version 1.2.0 for improved model support
  - Unified model configuration system
- **Tool ID override**: Tool IDs can now be overridden for better customization
- **Dispatch agent context**: Added project info to dispatch agent context (#78, fixes #71)

### Fixed
- **CI environment compatibility**: Tests now work without API keys in CI environment
- **Test reliability**: Various test fixes for improved stability
- **Comment handling**: Fixed extra spaces being added before comments in edited code
- **Tool call prefixing**: Added note about correctly prefixing tool calls to system prompt

## [0.1.7-alpha] - 2025-07-22

Note the major changes listed in `0.1.7-alpha-pre` below which are part of this release.

Moving out of pre-release with several code fixes. A new configuration option allows the dispatch_agent to load files as context. The `:dispatch-agent-context` setting accepts either:
- `true` - automatically loads `PROJECT_SUMMARY.md` (if available) and `./.clojure-mcp/code_index.txt` (the new default output location for `clojure-mcp.code-indexer`)
- A vector of file paths - loads the specified files into the dispatch_agent context

This enables dispatch_agent to have better project awareness through pre-loaded context files.

### Fixed
- **Race condition in nREPL session creation** - Fixed critical race condition in `clojure-mcp.nrepl/new-session` that could cause session initialization failures
- **Working directory handling** - Shell commands now properly use the working directory when executing

### Added
- **:mcp-client-hint configuration** - New config option for providing hints to MCP clients about tool capabilities, used in scratch_pad tool schema - currently this allows me to experiment with using the more specific scratch_pad schema on claude desktop...
- **:dispatch-agent-context configuration** - New config option for providing context to dispatch agents 

### Changed
- **Updated to latest Clojure version** - Project now uses the most recent Clojure release
- **Code simplification** - Various internal simplifications to improve maintainability

### Documentation
- **Dual Shadow mode documentation** - Added documentation for dual Shadow CLJS mode setup
- **Minor grammar fixes** - Improved clarity in various documentation sections
- **Updated PROJECT_SUMMARY prompt** - Corrected command name in documentation

### Internal
- Fixed nREPL server function invocation
- Moved default code indexer output in preparation for dispatch-agent-content configuration
- Minor deps.edn configuration adjustments

### Contributors
Thanks to Jonathon McKitrick for documenting dual Shadow mode and Thomas Mattacchione for the PROJECT_SUMMARY prompt update.

## [v0.1.7-alpha-pre] - 2025-07-08

### Major Improvements

#### Performance Fix: Startup Speed
Fixed critical issue where ClojureMCP was scanning the home directory, causing extremely slow startup times. The fix includes:
- **Project inspection optimization**: Reduced from multiple glob calls to a single optimized call
- **Path validation**: Fixed bad paths being passed to glob functions that caused excessive filesystem scanning

#### Multi-Dialect Support
Initial support for non-JVM Clojure dialects including Babashka and Basilisp:
- **Reduced Initializing Clojure Evals**: Minimized reliance on Clojure code evaluation on startup
- **Multimethod dispatch**: Added `:nrepl-env-type` based dispatch for dialect-specific behavior
- **Configuration**: Use `:project-dir` command-line option to specify working directory for alternative dialects
- **Note**: Disable `:bash-over-nrepl` in `.clojure-mcp/config.edn` for non-JVM environments

#### Scratch Pad Persistence 
The scratch pad now automatically saves to `.clojure-mcp/scratch_pad.edn` on every change:
- **Explicit loading**: Use `scratch_pad_load` prompt to load saved data
- **Snapshot saving**: Use `scratch_pad_save_as` prompt to save snapshots to named files
- **Configurable filename**: Set `:scratch-pad-file` in config to use different filenames
- **Auto-load on startup**: Enable with `:scratch-pad-load true` in config
Many thanks to Mark Addleman for working on this.

### Added
- **Multi-dialect support infrastructure**:
  - `:nrepl-env-type` configuration parameter for dialect detection
  - Multimethod dispatch system for dialect-specific behavior
  - Support for Babashka, and Basilisp environments
- **Scratch pad persistence** with flexible configuration via `.clojure-mcp/config.edn`:
  - `:scratch-pad-load` boolean flag for auto-loading on startup (default: `false`)
  - `:scratch-pad-file` to specify filename within `.clojure-mcp/` directory (default: `"scratch_pad.edn"`)
  - Automatic saving on every change
- **New prompts for scratch pad file operations**:
  - `ACT/scratch_pad_load` prompt for loading EDN files into scratch pad
  - `ACT/scratch_pad_save_as` prompt for saving scratch pad snapshots to named files
- **Project directory override** with `:project-dir` command-line option
- **Pattern-based collapsed view for text files** in `read_file` tool:
  - Shows only lines matching patterns with 20 lines of context
  - Works with `content_pattern` or `name_pattern` parameters
- **Bash execution isolation**:
  - `:bash-over-nrepl` config option to control execution mode (default: `true`)
  - Separate nREPL session for bash commands to prevent REPL pollution
  - Output truncation for local bash execution

### Changed
- **Project inspection performance**: Optimized glob operations from multiple calls to single call
- **grep and glob_files tools** now properly launch from working directory
- **Bash tool** enhanced with configurable execution modes
- Configuration loading no longer attempts to fetch remote configs

### Documentation
- Added table of contents to README
- Hopefully improved setup instructions
- Added Shadow CLJS section to README
- Updated README with `:project-dir` usage information

### Internal
- Improved string building performance in various tools
- Enhanced configuration handling for cleaner code structure

## [v0.1.6-alpha] - 2025-06-30

### Performance Improvements
- **Optimized `clojure_inspect_project`** with ~3.8x speedup by reducing glob operations from 5 calls to 1 using brace expansion patterns 
- **Added ripgrep recommendation** to README prerequisites for better `grep` and `glob_files` performance

### Fixed
- **Issue #13**: Replaced external diff utility with native Java library (java-diff-utils) to eliminate shell command dependencies (Many thanks to @burinc for this!)
- **Project inspection file discovery** - Fixed issue where the tool was failing to find files in some configurations
- **Grep tool** now properly handles file patterns
- **Scratch pad schema** simplified to resolve issue #42, improving compatibility with tool description overrides

### Internal
- Added comprehensive test suite for diff.clj with 38 assertions covering edge cases
- Removed debug printlns from codebase
- Minor description revert for clarity

## [v0.1.5-alpha] - 2025-06-22

### Major Refactoring: Simplified Custom MCP Server Creation

The project has undergone a significant refactor to make creating custom MCP servers dramatically easier. The new pattern uses factory functions and a single entry point, reducing custom server creation to just a few lines of code.

### Added
- **Factory function pattern** for creating custom MCP servers with `make-tools`, `make-prompts`, and `make-resources` functions
- **Code indexer tool** (`clojure -X:index`) for creating condensed codebase maps showing function signatures
- **Reader conditional support** in collapsed view for `.cljc` files with platform-specific code
- **Multi-tool support** in `glob_files` with intelligent fallback (ripgrep → find → Java NIO)
- **Add-dir prompt** allowing you to add directories to allowed paths

### Changed
- **Project inspection tool** completely refactored for 97% reduction in nREPL payload by moving file operations to local execution
- **Unified read_file tool** replacing legacy implementations with pattern-based code exploration
- **Main entry point** simplified to use `core/build-and-start-mcp-server` with factory functions
- **glob_files** enhanced with better truncation messages and cross-platform compatibility
- File collection in project tool now uses local glob operations instead of remote nREPL
- Documentation updated throughout to reflect new patterns and accurate tool information

### Fixed
- **Issue #43**: grep tool now correctly handles patterns starting with `-` character
- Path existence validation in file operations
- MCP server closing behavior when client atom is not set

### Removed
- Legacy `read_file` tool implementations
- Unused collapsed file view code
- Complex manual setup patterns in favor of simplified factory approach

### Configuration
- Added `:cljfmt` option to `.clojure-mcp/config.edn` to disable code formatting when set to `false`

### Documentation
- PROJECT_SUMMARY.md updated with accurate tool names and descriptions
- Custom server guide rewritten for new simplified pattern
- Added examples for SSE transport and pattern variations

### Internal
- File timestamp behavior improved with better logging
- Code organization enhanced with docstrings for core functions
- Examples updated to use new interface patterns

## [v0.1.4-alpha] - 2025-06-11

### The scratch_pad Tool: Persistent AI Workspace

After a bunch of refinements the scratch_pad tool has matured into a
very interesting tool - a freeform **JSON data structure** shared
across all chat sessions in chat client.

#### What It Does

- **Persistent memory**: Data survives across conversations
- **Flexible storage**: Any JSON data (objects, arrays, strings, numbers)
- **Path operations**: Use `set_path`/`get_path`/`delete_path` for precise data manipulation
- **AI workspace**: Serves as both thinking tool and progress tracker

#### Structured Planning

For complex features, use the new `plan-and-execute` prompt which leverages scratch_pad to:
- Research problems thoroughly
- Break down tasks into manageable subtasks  
- Track progress with structured todo lists
- Maintain context throughout development

### Added
- **Ripgrep (rg) support** in grep tool with intelligent fallback hierarchy (rg > grep > Java)
- **Smart path operations** in scratch_pad tool for automatic string-to-number conversion and data structure initialization

### Changed
- **Tool rename**: `fs_grep` → `grep` for better consistency
- Enhanced scratch_pad with smart path munging for vector indices
- Improved error handling for streaming operations with null parameters

### Fixed
- Streaming errors when receiving null parameters in MCP operations
- Schema validation errors in tool operations

### Internal
- General code linting and cleanup across multiple files
- Improved documentation and README content

## [v0.1.3-alpha] - 2025-06-09

### Added
- **Configurable file timestamp tracking** via `:write-file-guard` option (`:full-read`, `:partial-read`, or `false`)
- File existence validation in `unified_read_file` tool
- FAQ.md documenting file timestamp tracking behavior
- Docker support (`:dkr-nrepl` alias)
- `plan-and-execute` prompt for structured planning workflows
- Test coverage for new features

### Changed
- Enhanced scratch_pad prompt to encourage planning usage
- Clojure files always read as text format
- nREPL aliases now bind to 0.0.0.0 for network access
- Cleaned up unused prompt definitions

### Documentation
- FAQ with table of contents explaining timestamp tracking
- Updated system prompts and README with Wiki link

### Internal
- Added configuration helper functions (`get-write-file-guard`, `write-guard?`)
- File validation utilities (`path-exists?`)

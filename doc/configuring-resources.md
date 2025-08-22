# Configuring Resources in ClojureMCP

Resources in ClojureMCP provide a way to expose files and content to LLMs through the Model Context Protocol. They allow LLMs to access project documentation, configuration files, and other important resources.

## Configuration Structure

Resources are configured under the `:resources` key in your `.clojure-mcp/config.edn` file. Each resource is a map entry with the resource name as the key.

```clojure
:resources {"resource-name" {:description "What this resource provides"
                             :file-path "path/to/file.md"}}
```

## Resource Fields

### Required Fields

- **`:description`** - A clear description of what the resource contains. This helps LLMs understand when to access the resource.

- **`:file-path`** - Path to the file to serve as a resource

### Optional Fields

- **`:url`** - Custom URL for the resource (defaults to `custom://kebab-case-name`)
- **`:mime-type`** - MIME type for the resource (auto-detected if not specified)

## Auto-Detection Features

### MIME Type Detection
If `:mime-type` is not specified, ClojureMCP automatically detects it using Apache Tika based on file extension:
- `.md` → `text/markdown`
- `.clj`, `.cljs`, `.cljc` → `text/x-clojure`
- `.edn` → `application/edn`
- `.json` → `application/json`
- `.txt` → `text/plain`
- And many more...

### URL Generation
If `:url` is not specified, it's auto-generated from the resource name:
- Converts to lowercase
- Replaces non-alphanumeric characters with hyphens
- Example: `"My Resource.txt"` → `"custom://my-resource-txt"`

## Examples

### 1. Simple Resource

```clojure
:resources {"deps.edn" {:description "Project dependencies configuration"
                        :file-path "deps.edn"}}
```

### 2. Multiple Resources

```clojure
:resources {"deps.edn" {:description "Project dependencies"
                        :file-path "deps.edn"}
            
            "build.clj" {:description "Build configuration script"
                         :file-path "build.clj"}
            
            "CHANGELOG.md" {:description "Project changelog and version history"
                            :file-path "CHANGELOG.md"}
            
            "API.md" {:description "API documentation"
                      :file-path "doc/API.md"}}
```

### 3. Custom URL and MIME Type

```clojure
:resources {"custom-data" {:description "Custom data file"
                           :file-path "data/custom.dat"
                           :url "special://my-custom-data"
                           :mime-type "application/octet-stream"}}
```

### 4. Overriding Default Resources

You can override built-in resources by using the same name:

```clojure
:resources {"README.md" {:description "Enhanced project documentation"
                         :file-path "doc/README-extended.md"}
            
            "PROJECT_SUMMARY.md" {:description "Detailed project summary"
                                  :file-path ".clojure-mcp/PROJECT_SUMMARY.md"}}
```

## File Paths

### Relative Paths
Paths are resolved relative to the `nrepl-user-dir` (typically your project root):
```clojure
:file-path "doc/guide.md"        ; Resolves to <project-root>/doc/guide.md
:file-path ".clojure-mcp/info.md" ; Resolves to <project-root>/.clojure-mcp/info.md
```

### Absolute Paths
Absolute paths are used as-is:
```clojure
:file-path "/usr/local/share/doc/myapp/manual.md"
```

## Default Resources

ClojureMCP includes several built-in resources that are automatically available:

1. **`PROJECT_SUMMARY.md`** - Project summary document for LLM context
   - Path: `PROJECT_SUMMARY.md`
   - Description: "A Clojure project summary document for the project hosting the REPL"

2. **`README.md`** - Project README
   - Path: `README.md`
   - Description: "A README document for the current Clojure project"

3. **`CLAUDE.md`** - Claude-specific instructions
   - Path: `CLAUDE.md`
   - Description: "The Claude instructions document for the current project"

4. **`LLM_CODE_STYLE.md`** - Code style guidelines
   - Path: `LLM_CODE_STYLE.md`
   - Description: "Guidelines for writing Clojure code for the current project"

5. **`Clojure Project Info`** - Dynamic resource
   - Generated from project analysis
   - Description: "Information about the current Clojure project structure, REPL environment and dependencies"

## Filtering Resources

Control which resources are available using enable/disable lists:

```clojure
;; Only enable specific resources
:enable-resources ["README.md" "deps.edn" "API.md"]

;; Or disable specific resources
:disable-resources ["LLM_CODE_STYLE.md" "CLAUDE.md"]
```

**Note**: Filtering is applied at the MCP server level in `core.clj`, not in the resource creation.

## Resource Behavior

### Missing Files
Resources pointing to non-existent files are automatically filtered out during creation. They won't appear in the available resources list.

### File Changes
Resources read file content dynamically when accessed, so changes to files are immediately reflected without restarting the server.

### Access Control
Resources respect the `allowed-directories` configuration - files outside allowed directories cannot be served as resources.

## Best Practices

1. **Descriptive Names** - Use clear, descriptive resource names that indicate content type

2. **Helpful Descriptions** - Write descriptions that help LLMs understand when to access each resource

3. **Project Documentation** - Include key project documentation as resources:
   - Architecture diagrams
   - API documentation
   - Configuration guides
   - Development workflows

4. **Keep Files Updated** - Since resources read files dynamically, keep resource files current

5. **Logical Organization** - Group related resources and use consistent naming:
   ```clojure
   "api/rest.md" {:description "REST API documentation" ...}
   "api/graphql.md" {:description "GraphQL schema documentation" ...}
   "api/examples.md" {:description "API usage examples" ...}
   ```

6. **Override Thoughtfully** - When overriding defaults, ensure your replacements provide equivalent or better information

## Use Cases

### Project Documentation
```clojure
:resources {"ARCHITECTURE.md" {:description "System architecture overview"
                               :file-path "doc/ARCHITECTURE.md"}
            "DATABASE.md" {:description "Database schema and migrations"
                          :file-path "doc/DATABASE.md"}}
```

### Configuration Examples
```clojure
:resources {"config-dev.edn" {:description "Development configuration example"
                              :file-path "config/dev.edn"}
            "config-prod.edn" {:description "Production configuration template"
                               :file-path "config/prod.edn"}}
```

### Test Data
```clojure
:resources {"test-fixtures" {:description "Test fixture data"
                             :file-path "test/fixtures/data.edn"}
            "test-schema" {:description "Test database schema"
                          :file-path "test/resources/schema.sql"}}
```

## Troubleshooting

### Resource Not Appearing
- Check that the file exists at the specified path
- Verify the path is within `allowed-directories`
- Check enable/disable resource filters

### Wrong MIME Type
- Explicitly specify `:mime-type` in the configuration
- Check that file extension is recognized by Apache Tika

### Path Resolution Issues
- Use absolute paths for files outside the project
- Remember relative paths are from `nrepl-user-dir`
- Check the working directory with `(System/getProperty "user.dir")`
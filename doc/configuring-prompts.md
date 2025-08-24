# Configuring Prompts in ClojureMCP

Prompts in ClojureMCP allow you to define reusable templates that guide LLM interactions. They support Mustache templating for dynamic content and can be configured through the `.clojure-mcp/config.edn` file.

## Configuration Structure

Prompts are configured under the `:prompts` key in your config file. Each prompt is a map entry with the prompt name as the key.

```clojure
:prompts {"prompt-name" {:description "What this prompt does"
                         :args [{:name "param1" 
                                :description "Description of param1"
                                :required? true}]
                         :content "Template content with {{param1}}"}}
```

## Prompt Fields

### Required Fields

- **`:description`** - A clear description of what the prompt does. This is shown to the LLM when listing available prompts.

### Optional Fields

- **`:args`** - A vector of argument definitions. Each argument has:
  - `:name` - The parameter name (used in templates)
  - `:description` - What this parameter is for
  - `:required?` - Boolean indicating if required (defaults to false)

- **`:content`** - Inline template content (use this OR `:file-path`)
- **`:file-path`** - Path to a template file (use this OR `:content`)

## Template Syntax

Prompts use Mustache templating via the Pogonos library:

### Basic Variable Substitution
```mustache
Hello {{name}}, welcome to {{project}}!
```

### Conditional Sections
```mustache
{{#error}}
Error occurred: {{error}}
{{/error}}

{{^error}}
No errors!
{{/error}}
```

### Lists/Collections
```mustache
Files to review:
{{#files}}
- {{.}}
{{/files}}
```

## Examples

### 1. Simple Inline Prompt

```clojure
:prompts {"greeting" {:description "Generate a personalized greeting"
                     :args [{:name "name" :description "User's name" :required? true}]
                     :content "Hello {{name}}! How can I help you today?"}}
```

### 2. Code Review Prompt

```clojure
:prompts {"code-review" {:description "Generate a code review for specified file"
                         :args [{:name "file" :description "File path to review" :required? true}
                                {:name "focus" :description "Review focus areas" :required? false}]
                         :content "Please perform a thorough code review of: {{file}}
                                   
{{#focus}}
Focus areas: {{focus}}
{{/focus}}

Analyze:
1. Code style and idioms
2. Performance
3. Error handling
4. Testing needs"}}
```

### 3. File-Based Prompt

Create a file at `.clojure-mcp/prompts/debug-help.md`:

```mustache
I need help debugging an issue in {{namespace}}.

{{#error}}
The error message is:
```
{{error}}
```
{{/error}}

{{#context}}
Additional context:
{{context}}
{{/context}}

Please help me:
1. Understand the root cause
2. Suggest a fix
3. Verify the solution
```

Then reference it in config:

```clojure
:prompts {"debug-help" {:description "Help debug issues in Clojure namespaces"
                        :args [{:name "namespace" :description "Namespace with issue" :required? true}
                               {:name "error" :description "Error message" :required? false}
                               {:name "context" :description "Additional context" :required? false}]
                        :file-path ".clojure-mcp/prompts/debug-help.md"}}
```

## Template Behavior

### Missing Variables
If a template references a variable that isn't provided, it renders as an empty string:
- Template: `"Hello {{name}}, age: {{age}}"`
- With only `{:name "Alice"}` → `"Hello Alice, age: "`

### Extra Arguments
Arguments provided but not used in the template are simply ignored - no errors occur.

## Overriding Default Prompts

You can override built-in prompts by using the same name in your configuration:

```clojure
:prompts {"clojure_repl_system_prompt" {:description "Custom system prompt"
                                        :args []
                                        :content "My custom system instructions..."}}
```

## Filtering Prompts

Control which prompts are available using enable/disable lists:

```clojure
;; Only enable specific prompts
:enable-prompts ["code-review" "debug-help"]

;; Or disable specific prompts
:disable-prompts ["chat-session-summarize" "plan-and-execute"]
```

## File Paths

- **Relative paths** are resolved from the `nrepl-user-dir` (typically your project root)
- **Absolute paths** are used as-is
- File paths can reference any readable file in allowed directories

## Best Practices

1. **Clear Descriptions** - Write descriptions that help users understand when to use each prompt

2. **Optional Parameters** - Use `required? false` for parameters that enhance but aren't essential

3. **Conditional Sections** - Use `{{#param}}...{{/param}}` to handle optional parameters gracefully

4. **File-Based for Complex Templates** - Use separate files for long or complex templates to keep config clean

5. **Meaningful Names** - Use descriptive prompt names that indicate their purpose

6. **Documentation in Templates** - Include instructions within the template to guide the LLM

## Default Prompts

ClojureMCP includes several built-in prompts:
- `clojure_repl_system_prompt` - System instructions for Clojure development
- `create-update-project-summary` - Generate project documentation
- `chat-session-summarize` - Summarize conversation for context
- `chat-session-resume` - Resume from previous session
- `plan-and-execute` - Planning with scratch pad
- `ACT/add-dir` - Add directory to allowed paths
- `ACT/scratch_pad_load` - Load scratch pad from file
- `ACT/scratch_pad_save_as` - Save scratch pad to file

These can all be overridden or disabled through configuration.
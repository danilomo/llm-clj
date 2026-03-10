# Server Tools

Anthropic provides built-in server-side tools that execute on Anthropic's infrastructure rather than locally. These include web search, code execution, and full computer control capabilities.

## Overview

Server tools differ from regular tools in important ways:

| Aspect | Regular Tools | Server Tools |
|--------|---------------|--------------|
| Execution | Your code | Anthropic's servers |
| Implementation | You provide the function | Pre-built by Anthropic |
| Results | You return results | Results come directly in response |
| Beta features | Not required | Some require beta features |

**Available Server Tools:**

| Tool | Description | Beta Required |
|------|-------------|---------------|
| `web-search` | Search the web | No |
| `code-execution` | Run Python code | `:code-execution` |
| `bash-tool` | Execute bash commands | `:computer-use` |
| `text-editor` | View and edit files | `:computer-use` |
| `computer` | Full computer control | `:computer-use` |
| `mcp-server` | Model Context Protocol | `:mcp` |

## Web Search

The web search tool allows Claude to search the internet and incorporate results into responses.

### Basic Usage

```clojure
(require '[llm-clj.core :as llm])
(require '[llm-clj.providers.anthropic :as anthropic])
(require '[llm-clj.server-tools :as st])

(def provider (anthropic/create-provider {}))

(llm/chat-completion provider
  [{:role :user :content "What are the latest developments in quantum computing?"}]
  {:model "claude-sonnet-4-20250514"
   :tools [(st/web-search)]
   :max-tokens 1000})
```

### With Domain Restrictions

```clojure
;; Only search specific domains
(st/web-search {:allowed-domains ["arxiv.org" "nature.com" "science.org"]})

;; Block specific domains
(st/web-search {:blocked-domains ["pinterest.com" "facebook.com"]})

;; Limit number of results
(st/web-search {:max-results 5})

;; Combine options
(st/web-search {:max-results 10
                :allowed-domains ["wikipedia.org" "britannica.com"]})
```

### REPL Example: Research Assistant

```clojure
(require '[llm-clj.core :as llm])
(require '[llm-clj.providers.anthropic :as anthropic])
(require '[llm-clj.server-tools :as st])

(def provider (anthropic/create-provider {}))

(defn research [topic & {:keys [sources max-results]
                         :or {max-results 10}}]
  (let [tool-opts (cond-> {:max-results max-results}
                    sources (assoc :allowed-domains sources))]
    (llm/chat-completion provider
      [{:role :system
        :content "You are a research assistant. Search the web for information and provide a well-sourced summary with citations."}
       {:role :user
        :content (str "Research: " topic)}]
      {:model "claude-sonnet-4-20250514"
       :tools [(st/web-search tool-opts)]
       :max-tokens 2000})))

;; General research
(research "CRISPR gene editing recent breakthroughs 2024")

;; Academic sources only
(research "machine learning interpretability"
          :sources ["arxiv.org" "openreview.net" "neurips.cc"])

;; News sources
(research "AI regulation updates"
          :sources ["reuters.com" "bbc.com" "nytimes.com"]
          :max-results 5)
```

## Code Execution

The code execution tool lets Claude write and run Python code in a sandboxed environment.

### Basic Usage

```clojure
(require '[llm-clj.core :as llm])
(require '[llm-clj.providers.anthropic :as anthropic])
(require '[llm-clj.server-tools :as st])

(def provider (anthropic/create-provider {}))

(llm/chat-completion provider
  [{:role :user :content "Calculate the first 20 Fibonacci numbers"}]
  {:model "claude-sonnet-4-20250514"
   :tools [(st/code-execution)]
   :beta-features [:code-execution]
   :max-tokens 1000})
```

### With Timeout

```clojure
;; Set execution timeout (seconds)
(st/code-execution {:timeout 30})
```

### Available Packages

The sandbox includes common Python packages:
- **Data**: numpy, pandas, scipy
- **Visualization**: matplotlib, seaborn, plotly
- **ML/AI**: scikit-learn, tensorflow, pytorch
- **Utilities**: requests, beautifulsoup4, json, etc.

### REPL Example: Data Analysis Assistant

```clojure
(require '[llm-clj.core :as llm])
(require '[llm-clj.providers.anthropic :as anthropic])
(require '[llm-clj.server-tools :as st])

(def provider (anthropic/create-provider {}))

(defn analyze-data [description]
  (llm/chat-completion provider
    [{:role :system
      :content "You are a data analyst. Write and execute Python code to analyze data and provide insights. Show your work."}
     {:role :user
      :content description}]
    {:model "claude-sonnet-4-20250514"
     :tools [(st/code-execution {:timeout 60})]
     :beta-features [:code-execution]
     :max-tokens 2000}))

;; Statistical analysis
(analyze-data "Generate 1000 random samples from a normal distribution,
               calculate descriptive statistics, and test for normality")

;; Data visualization
(analyze-data "Create a visualization showing the relationship between
               hours studied and test scores for a hypothetical class of 50 students")

;; Complex calculation
(analyze-data "Calculate compound interest for a $10,000 investment
               at 7% annual rate over 30 years, compounded monthly.
               Show the growth curve.")
```

### REPL Example: Math Problem Solver

```clojure
(require '[llm-clj.core :as llm])
(require '[llm-clj.providers.anthropic :as anthropic])
(require '[llm-clj.server-tools :as st])

(def provider (anthropic/create-provider {}))

(defn solve-math [problem]
  (llm/chat-completion provider
    [{:role :system
      :content "You are a math tutor. Solve problems step-by-step using Python for calculations. Explain your reasoning."}
     {:role :user
      :content problem}]
    {:model "claude-sonnet-4-20250514"
     :tools [(st/code-execution)]
     :beta-features [:code-execution]
     :max-tokens 1500}))

(solve-math "Find all prime numbers between 1 and 1000 that are also palindromes")

(solve-math "Solve the system of equations: 3x + 2y = 12, 5x - y = 7")

(solve-math "Calculate the eigenvalues of the matrix [[4, 2], [1, 3]]")
```

## Computer Use Tools

Computer use tools allow Claude to interact with a virtual computer environment.

### Bash Tool

Execute shell commands:

```clojure
(require '[llm-clj.server-tools :as st])

;; Basic bash tool
(st/bash-tool)

;; With restart on failure
(st/bash-tool {:restart-on-failure true})
```

### Text Editor

View and edit text files:

```clojure
(st/text-editor)
;; => {:type "text_editor_20250124" :name "str_replace_editor"}
```

### Computer Control

Full virtual computer control (mouse, keyboard, screenshots):

```clojure
;; Default resolution (1024x768)
(st/computer)

;; Custom resolution
(st/computer {:display-width 1920 :display-height 1080})

;; With display number
(st/computer {:display-width 1920
              :display-height 1080
              :display-number 1})
```

### REPL Example: Computer Use Agent

```clojure
(require '[llm-clj.core :as llm])
(require '[llm-clj.providers.anthropic :as anthropic])
(require '[llm-clj.server-tools :as st])

(def provider (anthropic/create-provider {}))

(defn computer-agent [task]
  (llm/chat-completion provider
    [{:role :system
      :content "You have access to a computer. Use the tools to complete tasks."}
     {:role :user
      :content task}]
    {:model "claude-sonnet-4-20250514"
     :tools [(st/computer {:display-width 1920 :display-height 1080})
             (st/bash-tool)
             (st/text-editor)]
     :beta-features [:computer-use]
     :max-tokens 4096}))

;; File operations
(computer-agent "List all Python files in the current directory and show me their sizes")

;; System information
(computer-agent "Check the system's memory usage and running processes")
```

## MCP (Model Context Protocol)

Connect to external MCP servers to extend Claude's capabilities:

```clojure
(require '[llm-clj.server-tools :as st])

;; Connect to an MCP server
(st/mcp-server {:name "my-tools"
                :url "http://localhost:3000"})

;; Limit which tools are exposed
(st/mcp-server {:name "database"
                :url "http://localhost:5000"
                :tools ["query" "insert"]})
```

### REPL Example: Using MCP Server

```clojure
(require '[llm-clj.core :as llm])
(require '[llm-clj.providers.anthropic :as anthropic])
(require '[llm-clj.server-tools :as st])

(def provider (anthropic/create-provider {}))

(defn with-mcp-tools [server-url server-name message]
  (llm/chat-completion provider
    [{:role :user :content message}]
    {:model "claude-sonnet-4-20250514"
     :tools [(st/mcp-server {:name server-name
                             :url server-url})]
     :beta-features [:mcp]
     :max-tokens 1000}))

;; Use tools from your MCP server
(with-mcp-tools "http://localhost:3000" "my-server"
  "Use the available tools to help me")
```

## Beta Feature Management

### Required Beta Features

Different server tools require different beta features:

```clojure
(require '[llm-clj.server-tools :as st])

;; Check what beta features tools need
(st/required-beta-features [(st/web-search)])
;; => #{}  (no beta required)

(st/required-beta-features [(st/code-execution)])
;; => #{:code-execution}

(st/required-beta-features [(st/bash-tool) (st/computer)])
;; => #{:computer-use}

(st/required-beta-features [(st/code-execution) (st/mcp-server {:name "x" :url "y"})])
;; => #{:code-execution :mcp}
```

### Validating Beta Features

Ensure required features are enabled:

```clojure
;; Throws if features are missing
(st/validate-beta-features [(st/code-execution)] [:code-execution])
;; => true

(st/validate-beta-features [(st/code-execution)] [])
;; => throws validation-error
```

### Auto-Adding Beta Features

Automatically add required beta features to options:

```clojure
(def my-tools [(st/code-execution) (st/web-search)])

;; Automatically determine and add required features
(def opts (st/with-required-betas my-tools {:model "claude-sonnet-4-20250514"}))
;; => {:model "claude-sonnet-4-20250514" :beta-features [:code-execution]}

;; Use in completion
(llm/chat-completion provider messages
  (assoc opts :tools my-tools))
```

### REPL Example: Auto-Feature Management

```clojure
(require '[llm-clj.core :as llm])
(require '[llm-clj.providers.anthropic :as anthropic])
(require '[llm-clj.server-tools :as st])

(def provider (anthropic/create-provider {}))

(defn smart-completion
  "Automatically adds required beta features for server tools."
  [provider messages tools opts]
  (let [full-opts (-> opts
                      (st/with-required-betas tools)
                      (assoc :tools tools))]
    (llm/chat-completion provider messages full-opts)))

;; No need to manually specify beta features
(smart-completion provider
  [{:role :user :content "Search for recent AI news and analyze the trends using Python"}]
  [(st/web-search) (st/code-execution)]
  {:model "claude-sonnet-4-20250514"
   :max-tokens 2000})
```

## Extracting Results

### Web Search Results

```clojure
(require '[llm-clj.server-tools :as st])

;; Check if a content block is a web search result
(st/web-search-result? {:type "web_search_tool_result" :results [...]})
;; => true

;; Extract all search results from response content
(let [response (llm/chat-completion provider messages
                 {:tools [(st/web-search)]})
      content-blocks (:content response)]  ;; When content is a vector
  (st/extract-web-search-results content-blocks))
;; => [{:title "..." :url "..." :snippet "..."} ...]
```

### Code Execution Output

```clojure
;; Check if a content block is code execution output
(st/code-execution-result? {:type "code_execution_tool_result" :output "..."})
;; => true

;; Extract code output from response
(let [response (llm/chat-completion provider messages
                 {:tools [(st/code-execution)]
                  :beta-features [:code-execution]})
      content-blocks (:content response)]
  (st/extract-code-output content-blocks))
;; => "stdout/stderr output..."
```

### REPL Example: Processing Results

```clojure
(require '[llm-clj.core :as llm])
(require '[llm-clj.providers.anthropic :as anthropic])
(require '[llm-clj.server-tools :as st])

(def provider (anthropic/create-provider {}))

(defn search-and-extract [query]
  (let [response (llm/chat-completion provider
                   [{:role :user :content query}]
                   {:model "claude-sonnet-4-20250514"
                    :tools [(st/web-search {:max-results 5})]
                    :max-tokens 1000})
        ;; Response content may be a vector of blocks
        content-blocks (if (vector? (:content response))
                         (:content response)
                         [])
        search-results (st/extract-web-search-results content-blocks)]

    {:answer (:content response)
     :sources (map #(select-keys % [:title :url]) search-results)}))

(def result (search-and-extract "What is the population of Tokyo?"))

(println "Answer:" (:answer result))
(println "Sources:")
(doseq [source (:sources result)]
  (println " -" (:title source) "\n   " (:url source)))
```

## Combining Multiple Server Tools

### Web Search + Code Execution

```clojure
(require '[llm-clj.core :as llm])
(require '[llm-clj.providers.anthropic :as anthropic])
(require '[llm-clj.server-tools :as st])

(def provider (anthropic/create-provider {}))

(defn research-and-analyze [topic]
  (let [tools [(st/web-search) (st/code-execution)]
        opts (st/with-required-betas tools
               {:model "claude-sonnet-4-20250514"
                :max-tokens 3000})]
    (llm/chat-completion provider
      [{:role :system
        :content "You are a research analyst. Search for data on the topic, then use Python to analyze and visualize it."}
       {:role :user
        :content topic}]
      (assoc opts :tools tools))))

(research-and-analyze
  "Find the GDP growth rates of G7 countries for the last 5 years and create a comparison chart")
```

### Full Computer Use Suite

```clojure
(require '[llm-clj.core :as llm])
(require '[llm-clj.providers.anthropic :as anthropic])
(require '[llm-clj.server-tools :as st])

(def provider (anthropic/create-provider {}))

(defn full-computer-access [task]
  (let [tools [(st/computer {:display-width 1920 :display-height 1080})
               (st/bash-tool)
               (st/text-editor)]]
    (llm/chat-completion provider
      [{:role :system
        :content "You have full access to a computer. Use the available tools to complete the task."}
       {:role :user
        :content task}]
      {:model "claude-sonnet-4-20250514"
       :tools tools
       :beta-features [:computer-use]
       :max-tokens 4096})))

(full-computer-access "Create a new directory called 'project', initialize a git repo, and create a README.md file")
```

## Tool Structure Reference

### Web Search

```clojure
{:type "web_search"
 :name "web_search"
 :max_results 10                        ; optional
 :allowed_domains ["example.com"]       ; optional
 :blocked_domains ["blocked.com"]}      ; optional
```

### Code Execution

```clojure
{:type "code_execution"
 :name "code_execution"
 :timeout 30}                           ; optional, seconds
```

### Bash

```clojure
{:type "bash_20250124"
 :name "bash"
 :restart_on_failure true}              ; optional
```

### Text Editor

```clojure
{:type "text_editor_20250124"
 :name "str_replace_editor"}
```

### Computer

```clojure
{:type "computer_20250124"
 :name "computer"
 :display_width_px 1920
 :display_height_px 1080
 :display_number 1}                     ; optional
```

### MCP Server

```clojure
{:type "mcp"
 :server_name "my-server"
 :server_url "http://localhost:3000"
 :allowed_tools ["tool1" "tool2"]}      ; optional
```

## Best Practices

1. **Use `with-required-betas`** to automatically manage beta features
2. **Set appropriate timeouts** for code execution to prevent hanging
3. **Restrict domains** for web search when you need specific sources
4. **Extract and process results** for structured data handling
5. **Combine tools thoughtfully** - web search + code execution is powerful for data analysis
6. **Handle errors gracefully** - server tools can fail for various reasons

## Error Handling

```clojure
(require '[llm-clj.server-tools :as st])
(require '[llm-clj.errors :as errors])

(try
  ;; This will throw if beta features are missing
  (st/validate-beta-features [(st/code-execution)] [])
  (catch Exception e
    (when (errors/validation-error? e)
      (let [data (ex-data e)]
        (println "Missing features:" (:missing data))
        (println "Required:" (:required data))))))

;; MCP server validation
(try
  (st/mcp-server {:name "test"})  ; missing :url
  (catch Exception e
    (println "Validation error:" (ex-message e))))
```

## Limitations

- **Anthropic only**: Server tools are not available with OpenAI
- **Beta features**: Some tools require beta access
- **Execution time**: Code execution has timeout limits
- **Network access**: Code execution sandbox may have limited network access
- **Computer use**: Requires specific environment setup


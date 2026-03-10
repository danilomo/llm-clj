(ns llm-clj.server-tools
  "Anthropic server-side tool definitions.

  Server tools are executed by Anthropic's servers, not locally.
  They require specific beta features to be enabled.

  Usage:
  (require '[llm-clj.server-tools :as st])

  (chat-completion provider messages
    {:tools [(st/web-search)]
     :beta-features [:web-search]})"
  (:require [llm-clj.errors :as errors]
            [clojure.set :as set]
            [clojure.string :as str]))

;; Tool-to-beta-feature mapping
(def ^:private tool-beta-requirements
  {:web_search_20250305 nil  ;; No beta required as of 2025
   :code_execution_20250522 nil  ;; No beta required
   :bash_20250124 :computer-use
   :text_editor_20250124 :computer-use
   :computer_20250124 :computer-use
   :mcp :mcp})

;; Web Search Tool

(defn web-search
  "Creates a web_search server tool.

  The web search tool allows Claude to search the web and incorporate
  results into its responses.

  Options:
  - :max-results - Maximum number of results (optional)
  - :allowed-domains - Vector of allowed domains (optional)
  - :blocked-domains - Vector of blocked domains (optional)

  Usage:
  (web-search)
  (web-search {:max-results 5 :allowed-domains [\"wikipedia.org\"]})"
  ([] (web-search {}))
  ([{:keys [max-results allowed-domains blocked-domains]}]
   (cond-> {:type "web_search_20250305"
            :name "web_search"}
     max-results (assoc :max_results max-results)
     allowed-domains (assoc :allowed_domains (vec allowed-domains))
     blocked-domains (assoc :blocked_domains (vec blocked-domains)))))

;; Code Execution Tool

(defn code-execution
  "Creates a code_execution server tool.

  Allows Claude to write and execute Python code in a sandboxed environment.
  Requires the :code-execution beta feature.

  The sandbox includes common packages: numpy, pandas, matplotlib, etc.

  Options:
  - :timeout - Execution timeout in seconds (optional)

  Usage:
  (code-execution)
  (code-execution {:timeout 30})"
  ([] (code-execution {}))
  ([{:keys [timeout]}]
   (cond-> {:type "code_execution_20250522"
            :name "code_execution"}
     timeout (assoc :timeout timeout))))

;; Computer Use Tools

(defn bash-tool
  "Creates a bash server tool for computer use.

  Allows Claude to execute bash commands in a controlled environment.
  Requires the :computer-use beta feature.

  Options:
  - :restart-on-failure - Whether to restart on failure (optional)

  Usage:
  (bash-tool)"
  ([] (bash-tool {}))
  ([{:keys [restart-on-failure]}]
   (cond-> {:type "bash_20250124"
            :name "bash"}
     (some? restart-on-failure) (assoc :restart_on_failure restart-on-failure))))

(defn text-editor
  "Creates a text_editor server tool for computer use.

  Allows Claude to view and edit text files.
  Requires the :computer-use beta feature.

  Usage:
  (text-editor)"
  []
  {:type "text_editor_20250124"
   :name "str_replace_editor"})

(defn computer
  "Creates a computer server tool for full computer control.

  Allows Claude to control a virtual computer: mouse, keyboard, screenshots.
  Requires the :computer-use beta feature.

  Options:
  - :display-width - Screen width in pixels (default: 1024)
  - :display-height - Screen height in pixels (default: 768)
  - :display-number - X display number (optional)

  Usage:
  (computer)
  (computer {:display-width 1920 :display-height 1080})"
  ([] (computer {}))
  ([{:keys [display-width display-height display-number]
     :or {display-width 1024 display-height 768}}]
   (cond-> {:type "computer_20250124"
            :name "computer"
            :display_width_px display-width
            :display_height_px display-height}
     display-number (assoc :display_number display-number))))

;; MCP (Model Context Protocol) Tools

(defn mcp-server
  "Creates an MCP server tool reference.

  Allows Claude to use tools provided by an MCP server.
  Requires the :mcp beta feature.

  Options:
  - :name - Server name
  - :url - Server URL
  - :tools - Vector of tool names to expose (optional, all if not specified)

  Usage:
  (mcp-server {:name \"my-server\" :url \"http://localhost:3000\"})"
  [{:keys [name url tools]}]
  (when-not (and name url)
    (throw (errors/validation-error
            "MCP server requires :name and :url"
            {:name name :url url})))
  (cond-> {:type "mcp"
           :server_name name
           :server_url url}
    tools (assoc :allowed_tools (vec tools))))

;; Server tool type set for identification
(def ^:private server-tool-types
  #{"web_search_20250305" "code_execution_20250522" "bash_20250124"
    "text_editor_20250124" "computer_20250124" "mcp"})

(defn server-tool?
  "Returns true if the tool is a server-side tool."
  [tool]
  (contains? server-tool-types (:type tool)))

;; Helper functions

(defn required-beta-features
  "Returns the set of beta features required for the given tools.

  Usage:
  (required-beta-features [(web-search) (code-execution)])
  ;; => #{:code-execution}"
  [tools]
  (->> tools
       (map #(get tool-beta-requirements (keyword (:type %))))
       (filter some?)
       (into #{})))

(defn validate-beta-features
  "Validates that required beta features are enabled for the given tools.
  Throws validation-error if features are missing.

  Usage:
  (validate-beta-features [(code-execution)] [:code-execution]) ;; ok
  (validate-beta-features [(code-execution)] []) ;; throws"
  [tools enabled-features]
  (let [required (required-beta-features tools)
        enabled-set (set enabled-features)
        missing (set/difference required enabled-set)]
    (when (seq missing)
      (throw (errors/validation-error
              (str "Missing required beta features: " (pr-str missing))
              {:required required
               :enabled enabled-features
               :missing missing})))
    true))

(defn with-required-betas
  "Returns the options map with required beta features added.

  Usage:
  (with-required-betas {:tools [(code-execution)]} options)
  ;; => (update options :beta-features conj :code-execution)"
  [tools options]
  (let [required (required-beta-features tools)
        current (set (:beta-features options))]
    (assoc options :beta-features (vec (into current required)))))

;; Tool result helpers

(defn web-search-result?
  "Returns true if the content block is a web search result."
  [content-block]
  (= "web_search_tool_result" (:type content-block)))

(defn code-execution-result?
  "Returns true if the content block is a code execution result."
  [content-block]
  (= "code_execution_tool_result" (:type content-block)))

(defn extract-web-search-results
  "Extracts web search results from a response's content blocks.

  Returns a vector of search result maps."
  [content-blocks]
  (->> content-blocks
       (filter web-search-result?)
       (mapcat :results)))

(defn extract-code-output
  "Extracts code execution output from a response's content blocks.

  Returns the stdout/stderr output as a string."
  [content-blocks]
  (->> content-blocks
       (filter code-execution-result?)
       (map :output)
       (str/join "\n")))

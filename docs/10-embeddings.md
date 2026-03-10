# Embeddings

Embeddings convert text into numerical vectors that capture semantic meaning. These vectors enable similarity search, clustering, classification, and many other machine learning applications.

## Overview

llm-clj provides access to OpenAI's Embeddings API through a protocol-based abstraction:

```clojure
(require '[llm-clj.embeddings.openai :as embed])

;; Simple embedding
(embed/embed "Hello, world!")
;; => {:embeddings [[0.0023 -0.0091 0.0152 ...]]
;;     :model "text-embedding-3-small"
;;     :usage {:prompt-tokens 3 :total-tokens 3}}
```

## Key Concepts

### What Are Embeddings?

Embeddings are dense vector representations of text where:
- Similar texts have vectors that are close together
- Different texts have vectors that are far apart
- The distance/similarity can be computed mathematically

### Use Cases

| Use Case | Description |
|----------|-------------|
| **Semantic Search** | Find documents similar to a query |
| **Clustering** | Group similar texts together |
| **Classification** | Categorize text based on training examples |
| **Recommendations** | Suggest similar content |
| **Anomaly Detection** | Find unusual or outlier texts |
| **RAG** | Retrieval-Augmented Generation for LLMs |

### Available Models

| Model | Dimensions | Description |
|-------|------------|-------------|
| `text-embedding-3-small` | 1536 (default) | Fast, efficient, good quality |
| `text-embedding-3-large` | 3072 (default) | Higher quality, more dimensions |
| `text-embedding-ada-002` | 1536 | Legacy model |

Both `text-embedding-3-*` models support custom dimensions.

## Creating Embeddings

### Using the Protocol

```clojure
(require '[llm-clj.embeddings.core :as embeddings])
(require '[llm-clj.embeddings.openai :as embed])

;; Create a provider
(def provider (embed/create-provider {}))

;; Single embedding
(embeddings/create-embedding provider "Hello world" {})

;; With options
(embeddings/create-embedding provider "Hello world"
  {:model "text-embedding-3-large"
   :dimensions 1024})
```

### Using the Convenience Function

```clojure
(require '[llm-clj.embeddings.openai :as embed])

;; Simplest usage
(embed/embed "Hello world")

;; With options
(embed/embed "Hello world" {:model "text-embedding-3-large"})

;; With custom dimensions
(embed/embed "Hello world" {:dimensions 512})

;; With explicit API key
(embed/embed "Hello world" {:api-key "sk-..."})
```

### Batch Embeddings

Process multiple texts in a single API call:

```clojure
(embed/embed ["First text" "Second text" "Third text"])
;; => {:embeddings [[...] [...] [...]]
;;     :model "text-embedding-3-small"
;;     :usage {:prompt-tokens 8 :total-tokens 8}}
```

## REPL Examples

### Basic Embedding

Copy and paste this entire block:

```clojure
(require '[llm-clj.embeddings.openai :as embed])

;; Create an embedding
(def result (embed/embed "The quick brown fox jumps over the lazy dog"))

;; Check the result structure
(println "Model:" (:model result))
(println "Dimensions:" (count (first (:embeddings result))))
(println "Tokens used:" (:total-tokens (:usage result)))

;; Look at the first few values
(println "First 5 values:" (take 5 (first (:embeddings result))))
```

### Semantic Similarity

```clojure
(require '[llm-clj.embeddings.openai :as embed])

;; Embed multiple texts
(def texts ["cat" "kitten" "dog" "automobile" "vehicle"])
(def result (embed/embed texts))

;; Get embeddings
(def [cat kitten dog automobile vehicle] (:embeddings result))

;; Calculate similarities
(println "Similarities:")
(println "  cat <-> kitten:" (embed/cosine-similarity cat kitten))
(println "  cat <-> dog:" (embed/cosine-similarity cat dog))
(println "  cat <-> automobile:" (embed/cosine-similarity cat automobile))
(println "  automobile <-> vehicle:" (embed/cosine-similarity automobile vehicle))

;; Expected output shows cat is more similar to kitten than to automobile
```

### Finding Similar Texts

```clojure
(require '[llm-clj.embeddings.openai :as embed])

(def documents
  ["Clojure is a functional programming language"
   "Python is great for data science"
   "JavaScript runs in the browser"
   "Lisp is the second oldest high-level programming language"
   "Machine learning uses statistical techniques"
   "Functional programming avoids mutable state"])

(defn find-similar [query documents n]
  (let [;; Embed query and all documents together
        all-texts (cons query documents)
        result (embed/embed all-texts)
        [query-emb & doc-embs] (:embeddings result)

        ;; Calculate similarities
        similarities (map-indexed
                       (fn [idx doc-emb]
                         {:index idx
                          :text (nth documents idx)
                          :similarity (embed/cosine-similarity query-emb doc-emb)})
                       doc-embs)]

    ;; Return top N
    (->> similarities
         (sort-by :similarity >)
         (take n))))

;; Find documents similar to a query
(find-similar "What is functional programming?" documents 3)
;; => ({:index 0 :text "Clojure is a functional programming language" :similarity 0.82}
;;     {:index 5 :text "Functional programming avoids mutable state" :similarity 0.78}
;;     {:index 3 :text "Lisp is the second oldest..." :similarity 0.65})
```

## Custom Dimensions

The `text-embedding-3-*` models support custom output dimensions:

```clojure
(require '[llm-clj.embeddings.openai :as embed])

;; Default dimensions (1536 for small, 3072 for large)
(def full (embed/embed "Hello" {:model "text-embedding-3-small"}))
(println "Full dimensions:" (count (first (:embeddings full))))
;; => 1536

;; Reduced dimensions (faster, smaller storage)
(def reduced (embed/embed "Hello" {:model "text-embedding-3-small"
                                    :dimensions 512}))
(println "Reduced dimensions:" (count (first (:embeddings reduced))))
;; => 512

;; Minimum useful dimensions
(def minimal (embed/embed "Hello" {:model "text-embedding-3-small"
                                    :dimensions 256}))
```

### Dimension Trade-offs

| Dimensions | Storage | Speed | Quality |
|------------|---------|-------|---------|
| 256 | Smallest | Fastest | Good for simple tasks |
| 512 | Small | Fast | Good balance |
| 1024 | Medium | Medium | High quality |
| 1536+ | Large | Slower | Highest quality |

## Building a Simple Search Engine

```clojure
(require '[llm-clj.embeddings.openai :as embed])

;; === Simple Vector Search Engine ===

(defn create-index
  "Creates a searchable index from documents."
  [documents]
  (let [result (embed/embed documents {:dimensions 512})]
    {:documents documents
     :embeddings (:embeddings result)}))

(defn search
  "Searches the index for documents similar to the query."
  [index query & {:keys [top-k] :or {top-k 5}}]
  (let [{:keys [documents embeddings]} index
        query-result (embed/embed query {:dimensions 512})
        query-emb (first (:embeddings query-result))

        ;; Score all documents
        scored (map-indexed
                 (fn [idx doc-emb]
                   {:document (nth documents idx)
                    :score (embed/cosine-similarity query-emb doc-emb)})
                 embeddings)]

    ;; Return top results
    (->> scored
         (sort-by :score >)
         (take top-k))))

;; === Usage ===

(def knowledge-base
  ["Clojure is a modern Lisp that runs on the JVM"
   "React is a JavaScript library for building user interfaces"
   "PostgreSQL is a powerful relational database"
   "Docker containers package applications with dependencies"
   "Kubernetes orchestrates container deployments"
   "Redis is an in-memory data structure store"
   "GraphQL is a query language for APIs"
   "Terraform manages infrastructure as code"
   "Clojure uses persistent data structures"
   "ClojureScript compiles to JavaScript"])

;; Create index (do this once)
(def index (create-index knowledge-base))

;; Search
(search index "How do I build web UIs?")
;; => ({:document "React is a JavaScript library..." :score 0.78}
;;     {:document "ClojureScript compiles to JavaScript" :score 0.65}
;;     ...)

(search index "What databases can I use?")
;; => ({:document "PostgreSQL is a powerful..." :score 0.82}
;;     {:document "Redis is an in-memory..." :score 0.71}
;;     ...)

(search index "Lisp programming" :top-k 3)
;; => ({:document "Clojure is a modern Lisp..." :score 0.85}
;;     {:document "Clojure uses persistent data structures" :score 0.72}
;;     {:document "ClojureScript compiles to JavaScript" :score 0.68})
```

## RAG (Retrieval-Augmented Generation)

Combine embeddings with chat completion for grounded responses:

```clojure
(require '[llm-clj.embeddings.openai :as embed])
(require '[llm-clj.core :as llm])
(require '[llm-clj.providers.openai :as openai])

(def chat-provider (openai/create-provider {}))

(defn rag-query
  "Answers questions using retrieved context."
  [index question & {:keys [top-k] :or {top-k 3}}]
  (let [;; Retrieve relevant documents
        results (search index question :top-k top-k)
        context (->> results
                     (map :document)
                     (clojure.string/join "\n- "))

        ;; Generate answer with context
        response (llm/chat-completion chat-provider
                   [{:role :system
                     :content (str "Answer questions based on the following context. "
                                   "If the context doesn't contain the answer, say so.\n\n"
                                   "Context:\n- " context)}
                    {:role :user
                     :content question}]
                   {:temperature 0.3
                    :max-tokens 500})]

    {:answer (:content response)
     :sources (map :document results)}))

;; Usage
(def result (rag-query index "What is Clojure and what platform does it run on?"))

(println "Answer:" (:answer result))
(println "\nSources:")
(doseq [source (:sources result)]
  (println " -" source))
```

## Cosine Similarity

The library provides a built-in cosine similarity function:

```clojure
(require '[llm-clj.embeddings.openai :as embed])

;; Identical vectors = 1.0
(embed/cosine-similarity [1 0 0] [1 0 0])
;; => 1.0

;; Orthogonal vectors = 0.0
(embed/cosine-similarity [1 0 0] [0 1 0])
;; => 0.0

;; Opposite vectors = -1.0
(embed/cosine-similarity [1 0 0] [-1 0 0])
;; => -1.0

;; Similar but not identical
(embed/cosine-similarity [1 0.1 0] [1 0 0.1])
;; => ~0.99
```

### Interpreting Similarity Scores

| Score Range | Interpretation |
|-------------|----------------|
| 0.9 - 1.0 | Nearly identical meaning |
| 0.7 - 0.9 | Very similar |
| 0.5 - 0.7 | Somewhat related |
| 0.3 - 0.5 | Loosely related |
| < 0.3 | Likely unrelated |

## Options Reference

### Provider Options

```clojure
(embed/create-provider
  {:api-key "sk-..."})  ; Optional, uses OPENAI_API_KEY env var
```

### Embedding Options

```clojure
{:model "text-embedding-3-small"  ; Model to use
 :dimensions 512                   ; Output dimensions (model-specific)
 :encoding-format :float           ; :float or :base64
 :user "user-123"}                 ; User ID for tracking
```

## Response Structure

```clojure
{:embeddings [[0.001 -0.023 0.045 ...] ...]  ; Vector of embedding vectors
 :model "text-embedding-3-small"              ; Model used
 :usage {:prompt-tokens 5                     ; Input tokens
         :total-tokens 5}}                    ; Total tokens (same for embeddings)
```

## Performance Tips

### Batch Your Requests

```clojure
;; GOOD: Single API call for multiple texts
(embed/embed ["text1" "text2" "text3" "text4" "text5"])

;; BAD: Multiple API calls
(embed/embed "text1")
(embed/embed "text2")
(embed/embed "text3")
```

### Use Appropriate Dimensions

```clojure
;; For simple similarity tasks, smaller is fine
(embed/embed texts {:dimensions 256})

;; For complex semantic tasks, use more
(embed/embed texts {:dimensions 1024})
```

### Cache Embeddings

```clojure
;; Store embeddings for reuse
(def embedding-cache (atom {}))

(defn cached-embed [text]
  (if-let [cached (get @embedding-cache text)]
    cached
    (let [result (first (:embeddings (embed/embed text)))]
      (swap! embedding-cache assoc text result)
      result)))
```

## Error Handling

```clojure
(require '[llm-clj.embeddings.openai :as embed])
(require '[llm-clj.errors :as errors])

(try
  (embed/embed "test text")
  (catch Exception e
    (cond
      (errors/rate-limited? e)
      (do
        (println "Rate limited, waiting...")
        (Thread/sleep (* 1000 (or (errors/retry-after e) 60)))
        (embed/embed "test text"))

      (errors/authentication-error? e)
      (println "Check your API key")

      :else
      (throw e))))
```

## Complete Example: Document Q&A System

```clojure
(require '[llm-clj.embeddings.openai :as embed])
(require '[llm-clj.core :as llm])
(require '[llm-clj.providers.openai :as openai])

;; === Document Q&A System ===

(def chat-provider (openai/create-provider {}))

(defn chunk-text
  "Splits text into chunks of roughly n words."
  [text n]
  (let [words (clojure.string/split text #"\s+")]
    (->> words
         (partition-all n)
         (map #(clojure.string/join " " %)))))

(defn create-qa-index
  "Creates a Q&A index from a document."
  [document & {:keys [chunk-size] :or {chunk-size 100}}]
  (let [chunks (chunk-text document chunk-size)
        result (embed/embed (vec chunks) {:dimensions 512})]
    {:chunks (vec chunks)
     :embeddings (:embeddings result)}))

(defn answer-question
  "Answers a question using the document index."
  [index question]
  (let [;; Find relevant chunks
        query-emb (first (:embeddings (embed/embed question {:dimensions 512})))
        scored (map-indexed
                 (fn [idx chunk-emb]
                   {:chunk (nth (:chunks index) idx)
                    :score (embed/cosine-similarity query-emb chunk-emb)})
                 (:embeddings index))
        top-chunks (->> scored
                        (sort-by :score >)
                        (take 3)
                        (map :chunk))
        context (clojure.string/join "\n\n" top-chunks)

        ;; Generate answer
        response (llm/chat-completion chat-provider
                   [{:role :system
                     :content (str "Answer the question based on the following document excerpts. "
                                   "Be concise and accurate.\n\n"
                                   "Document excerpts:\n" context)}
                    {:role :user
                     :content question}]
                   {:temperature 0.2
                    :max-tokens 300})]

    {:answer (:content response)
     :relevant-chunks top-chunks}))

;; === Usage ===

(def document
  "Clojure is a dynamic, general-purpose programming language that runs on the
   Java Virtual Machine. It was created by Rich Hickey and released in 2007.
   Clojure is a dialect of Lisp with a focus on functional programming.

   One of Clojure's key features is its persistent data structures, which are
   immutable but provide efficient updates through structural sharing. This
   makes it easy to write concurrent programs without worrying about locks.

   ClojureScript is a compiler for Clojure that targets JavaScript. It allows
   developers to use Clojure for frontend web development and Node.js applications.

   The language emphasizes simplicity and provides powerful abstractions like
   sequences, transducers, and core.async for asynchronous programming.")

;; Create index
(def qa-index (create-qa-index document :chunk-size 50))

;; Ask questions
(answer-question qa-index "Who created Clojure?")
;; => {:answer "Clojure was created by Rich Hickey and released in 2007."
;;     :relevant-chunks [...]}

(answer-question qa-index "What are persistent data structures?")
;; => {:answer "Persistent data structures in Clojure are immutable but provide
;;              efficient updates through structural sharing..."
;;     :relevant-chunks [...]}
```


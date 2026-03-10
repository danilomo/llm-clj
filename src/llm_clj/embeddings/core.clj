(ns llm-clj.embeddings.core
  "Protocol definitions for the Embeddings API.")

(defprotocol EmbeddingProvider
  "Protocol for providers implementing text embeddings."

  (create-embedding [this input options]
    "Creates embeddings for the given input.

    Input can be:
    - A string (single text to embed)
    - A vector of strings (batch embedding)

    Options:
    - :model - Embedding model (default: text-embedding-3-small)
    - :dimensions - Output dimension count (optional, model-specific)
    - :encoding-format - :float or :base64 (default: :float)
    - :user - User identifier for abuse tracking

    Returns:
    {:embeddings [[0.1 0.2 ...] [0.3 0.4 ...]]
     :model \"text-embedding-3-small\"
     :usage {:prompt-tokens N :total-tokens N}}"))

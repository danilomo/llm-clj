(ns llm-clj.batch.core
  "Protocol definitions for Batch Processing APIs.")

(defprotocol BatchProvider
  "Protocol for providers implementing batch processing."

  (create-batch [this requests options]
    "Creates a batch of requests for asynchronous processing.

    requests - Vector of request maps, each with:
      :custom-id - Unique identifier for tracking
      :params - Request parameters (same as chat-completion options)
      :messages - Messages for the request

    Options:
    - :completion-window - Time window: \"24h\" (OpenAI default)
    - :metadata - Metadata map for the batch

    Returns:
    {:id \"batch_abc123\"
     :status :validating
     :created-at timestamp
     :request-counts {:total N :completed 0 :failed 0}}")

  (get-batch [this batch-id]
    "Retrieves batch status and results.

    Returns:
    {:id \"batch_abc123\"
     :status :completed  ; :validating, :in_progress, :completed, :failed, :expired, :cancelled
     :created-at timestamp
     :completed-at timestamp
     :request-counts {:total N :completed M :failed F}
     :output-file-id \"file_...\"  ; when completed
     :error-file-id \"file_...\"   ; when has errors}")

  (cancel-batch [this batch-id]
    "Cancels a batch that is in progress.

    Returns updated batch status.")

  (list-batches [this options]
    "Lists batches with optional filtering.

    Options:
    - :limit - Max results (default 20)
    - :after - Cursor for pagination

    Returns:
    {:batches [{...} {...}]
     :has-more boolean
     :first-id \"...\"
     :last-id \"...\"}"))

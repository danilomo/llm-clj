(ns llm-clj.responses.core
  "Protocol definitions for the Responses API.")

(defprotocol ResponsesProvider
  "Protocol for providers implementing the Responses API."

  (create-response [this input options]
    "Creates a model response.
    Input can be:
    - A string (simple text prompt)
    - A vector of content items

    Options:
    - :model - Model to use
    - :instructions - System instructions
    - :previous-response-id - ID of previous response for multi-turn
    - :tools - Vector of tool configurations
    - :tool-choice - Tool selection strategy
    - :temperature - Sampling temperature
    - :max-output-tokens - Maximum tokens in response
    - :top-p - Nucleus sampling
    - :store - Whether to store the response

    Returns a response map with :id, :output, :usage, etc.")

  (create-response-stream [this input options]
    "Streaming version of create-response.
    Returns a core.async channel emitting streaming events.")

  (get-response [this response-id]
    "Retrieves a previously stored response by ID.
    Returns the full response map or throws if not found.")

  (delete-response [this response-id]
    "Deletes a stored response.
    Returns true on success."))

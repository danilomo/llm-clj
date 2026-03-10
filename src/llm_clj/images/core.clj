(ns llm-clj.images.core
  "Protocol definitions for the Images API.")

(defprotocol ImageProvider
  "Protocol for providers implementing image generation."

  (generate-image [this prompt options]
    "Generates images from a text prompt.

    Options:
    - :model - Image model (default: dall-e-3 or gpt-image-1)
    - :n - Number of images (1-10, default: 1)
    - :size - Image size: 1024x1024, 1024x1792, 1792x1024
    - :quality - :standard, :hd, or :high
    - :style - :vivid or :natural (DALL-E 3 only)
    - :response-format - :url or :b64_json (default: :url)
    - :user - User identifier

    Returns:
    {:images [{:url \"...\" :revised-prompt \"...\"}]
     :created timestamp}")

  (edit-image [this image prompt options]
    "Edits an image with a text prompt (DALL-E 2 only).

    image - Path to PNG file or byte array
    prompt - Description of desired edits

    Options:
    - :mask - Path to mask image (transparent areas will be edited)
    - :n - Number of images
    - :size - Image size
    - :response-format - :url or :b64_json

    Returns same format as generate-image.")

  (create-variation [this image options]
    "Creates variations of an image (DALL-E 2 only).

    image - Path to PNG file or byte array

    Options:
    - :n - Number of variations
    - :size - Image size
    - :response-format - :url or :b64_json

    Returns same format as generate-image."))

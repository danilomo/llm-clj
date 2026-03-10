# Images (DALL-E)

OpenAI's Images API allows you to generate, edit, and create variations of images using DALL-E models. llm-clj provides a protocol-based abstraction for these capabilities.

## Overview

The Images API supports three operations:

| Operation | Description | Models |
|-----------|-------------|--------|
| **Generate** | Create images from text prompts | DALL-E 2, DALL-E 3, gpt-image-1 |
| **Edit** | Modify parts of an existing image | DALL-E 2 only |
| **Variation** | Create variations of an image | DALL-E 2 only |

## Available Models

| Model | Sizes | Features |
|-------|-------|----------|
| `gpt-image-1` | 1024x1024, 1024x1792, 1792x1024 | Latest model, high quality |
| `dall-e-3` | 1024x1024, 1024x1792, 1792x1024 | HD quality, style control |
| `dall-e-2` | 256x256, 512x512, 1024x1024 | Edit, variations, multiple images |

## Generating Images

### Using the Protocol

```clojure
(require '[llm-clj.images.core :as images])
(require '[llm-clj.images.openai :as img])

;; Create a provider
(def provider (img/create-provider {}))

;; Generate an image
(images/generate-image provider
  "A serene Japanese garden with a koi pond"
  {:size "1024x1024"})
```

### Using the Convenience Function

```clojure
(require '[llm-clj.images.openai :as img])

;; Simple generation
(img/generate "A sunset over mountains")

;; With options
(img/generate "A futuristic cityscape"
  {:model "dall-e-3"
   :size "1792x1024"
   :quality :hd
   :style :vivid})
```

### Response Structure

```clojure
{:images [{:url "https://..."
           :revised-prompt "A serene Japanese garden..."}]
 :created 1699000000}
```

The `:revised-prompt` shows how DALL-E 3 interpreted your prompt (it often enhances prompts automatically).

## REPL Examples

### Basic Image Generation

Copy and paste this entire block:

```clojure
(require '[llm-clj.images.openai :as img])

;; Generate a simple image
(def result (img/generate "A happy golden retriever playing in autumn leaves"))

;; Check the result
(println "Generated" (count (:images result)) "image(s)")
(println "URL:" (-> result :images first :url))

;; If using DALL-E 3, see the revised prompt
(when-let [revised (-> result :images first :revised-prompt)]
  (println "Revised prompt:" revised))
```

### High Quality Generation

```clojure
(require '[llm-clj.images.openai :as img])

;; Generate HD quality image with DALL-E 3
(def result
  (img/generate "An astronaut riding a horse on Mars, photorealistic"
    {:model "dall-e-3"
     :size "1792x1024"
     :quality :hd
     :style :vivid}))

(println "Image URL:" (-> result :images first :url))
```

### Multiple Images

```clojure
(require '[llm-clj.images.openai :as img])

;; Generate multiple variations (DALL-E 2 only)
(def result
  (img/generate "Abstract geometric art"
    {:model "dall-e-2"
     :n 4
     :size "512x512"}))

(println "Generated" (count (:images result)) "images:")
(doseq [[idx image] (map-indexed vector (:images result))]
  (println (str "  " (inc idx) ": " (:url image))))
```

### Save Generated Image

```clojure
(require '[llm-clj.images.openai :as img])

;; Generate and save to file
(defn generate-and-save [prompt output-path opts]
  (let [result (img/generate prompt opts)
        url (-> result :images first :url)]
    (img/save-image url output-path)
    (println "Saved to:" output-path)
    output-path))

(generate-and-save
  "A minimalist logo for a tech startup"
  "/tmp/logo.png"
  {:size "1024x1024"})
```

### Get Base64 Instead of URL

```clojure
(require '[llm-clj.images.openai :as img])
(require '[clojure.java.io :as io])

;; Get image as base64
(def result
  (img/generate "A simple icon"
    {:model "dall-e-2"
     :size "256x256"
     :response-format :b64_json}))

;; Decode and save
(let [b64 (-> result :images first :b64-json)
      bytes (img/decode-b64-image b64)]
  (with-open [out (io/output-stream "/tmp/icon.png")]
    (.write out bytes))
  (println "Saved" (count bytes) "bytes"))
```

## Generation Options

### Size Options

| Model | Available Sizes |
|-------|-----------------|
| `gpt-image-1` | `"1024x1024"`, `"1024x1792"`, `"1792x1024"` |
| `dall-e-3` | `"1024x1024"`, `"1024x1792"`, `"1792x1024"` |
| `dall-e-2` | `"256x256"`, `"512x512"`, `"1024x1024"` |

### Quality Options

```clojure
;; Standard quality (faster, cheaper)
{:quality :standard}

;; HD quality (DALL-E 3)
{:quality :hd}

;; High quality (gpt-image-1)
{:quality :high}
```

### Style Options (DALL-E 3 only)

```clojure
;; Vivid - hyper-real and dramatic
{:style :vivid}

;; Natural - more realistic, less hyper-real
{:style :natural}
```

### Complete Options Reference

```clojure
{:model "dall-e-3"           ; Model to use
 :n 1                        ; Number of images (1-10, DALL-E 2 only for n>1)
 :size "1024x1024"           ; Image dimensions
 :quality :hd                ; :standard, :hd, or :high
 :style :vivid               ; :vivid or :natural (DALL-E 3)
 :response-format :url       ; :url or :b64_json
 :user "user-123"}           ; User ID for tracking
```

## Editing Images

Edit parts of an existing image using DALL-E 2. Requires a mask indicating which areas to modify.

### Basic Edit

```clojure
(require '[llm-clj.images.core :as images])
(require '[llm-clj.images.openai :as img])

(def provider (img/create-provider {}))

;; Edit an image using a mask
(images/edit-image provider
  "/path/to/original.png"
  "Add a beautiful rainbow in the sky"
  {:mask "/path/to/mask.png"
   :size "1024x1024"})
```

### How Masks Work

The mask is a PNG with transparent areas indicating where edits should be applied:
- **Transparent pixels** = Areas to edit
- **Opaque pixels** = Areas to preserve

```clojure
;; Example: Edit the sky in an image
(images/edit-image provider
  "/path/to/landscape.png"
  "Replace the sky with a dramatic sunset"
  {:mask "/path/to/sky-mask.png"  ; Sky area is transparent
   :n 3                            ; Generate 3 variations
   :size "1024x1024"})
```

### REPL Example: Image Editing

```clojure
(require '[llm-clj.images.core :as images])
(require '[llm-clj.images.openai :as img])

(def provider (img/create-provider {}))

(defn edit-and-save [image-path mask-path prompt output-path]
  (let [result (images/edit-image provider image-path prompt
                 {:mask mask-path
                  :size "1024x1024"})
        url (-> result :images first :url)]
    (img/save-image url output-path)
    (println "Edited image saved to:" output-path)))

;; Usage (requires actual image and mask files)
;; (edit-and-save "photo.png" "mask.png" "Add a cat sitting here" "output.png")
```

## Creating Variations

Generate variations of an existing image using DALL-E 2.

### Basic Variation

```clojure
(require '[llm-clj.images.core :as images])
(require '[llm-clj.images.openai :as img])

(def provider (img/create-provider {}))

;; Create variations
(images/create-variation provider
  "/path/to/image.png"
  {:n 4
   :size "512x512"})
```

### REPL Example: Generate Variations

```clojure
(require '[llm-clj.images.core :as images])
(require '[llm-clj.images.openai :as img])

(def provider (img/create-provider {}))

(defn create-variations [image-path output-dir n]
  (let [result (images/create-variation provider image-path
                 {:n n
                  :size "512x512"})]
    (doseq [[idx image] (map-indexed vector (:images result))]
      (let [output-path (str output-dir "/variation-" (inc idx) ".png")]
        (img/save-image (:url image) output-path)
        (println "Saved:" output-path)))
    (println "Created" (count (:images result)) "variations")))

;; Usage (requires actual image file)
;; (create-variations "original.png" "/tmp/variations" 4)
```

## Utility Functions

### Save Image from URL

```clojure
(require '[llm-clj.images.openai :as img])

;; Download and save image
(img/save-image "https://example.com/image.png" "/tmp/downloaded.png")
```

### Decode Base64 Image

```clojure
(require '[llm-clj.images.openai :as img])
(require '[clojure.java.io :as io])

;; Get base64 image
(def result (img/generate "A test image"
              {:response-format :b64_json
               :model "dall-e-2"
               :size "256x256"}))

;; Decode to bytes
(def image-bytes
  (img/decode-b64-image (-> result :images first :b64-json)))

;; Save to file
(with-open [out (io/output-stream "/tmp/image.png")]
  (.write out image-bytes))
```

## Complete Application: AI Image Generator

```clojure
(require '[llm-clj.images.openai :as img])
(require '[llm-clj.images.core :as images])
(require '[clojure.java.io :as io])

;; === AI Image Generator Application ===

(def provider (img/create-provider {}))

(defn generate-image-set
  "Generates a set of images with consistent style."
  [base-prompt variations output-dir opts]
  (let [style-suffix (or (:style-suffix opts) "")]
    (doseq [[idx variation] (map-indexed vector variations)]
      (let [full-prompt (str base-prompt ", " variation style-suffix)
            result (images/generate-image provider full-prompt
                     (merge {:size "1024x1024"} opts))
            output-path (str output-dir "/" (format "%02d" (inc idx)) "-"
                             (-> variation
                                 (clojure.string/replace #"\s+" "-")
                                 (clojure.string/lower-case))
                             ".png")]
        (io/make-parents output-path)
        (img/save-image (-> result :images first :url) output-path)
        (println "Generated:" output-path)))))

;; Generate a series of icons
(generate-image-set
  "A flat design icon representing"
  ["a house" "a car" "a tree" "a person" "a phone"]
  "/tmp/icons"
  {:model "dall-e-3"
   :style :natural
   :style-suffix ", minimalist, single color on white background"})
```

## Prompt Engineering Tips

### Be Specific

```clojure
;; Too vague
(img/generate "A dog")

;; Better
(img/generate "A golden retriever puppy sitting in a field of sunflowers,
               soft natural lighting, shallow depth of field,
               professional photography")
```

### Specify Style

```clojure
;; Art styles
(img/generate "A mountain landscape, oil painting style, impressionist")
(img/generate "A robot, cyberpunk aesthetic, neon lighting")
(img/generate "A portrait, pencil sketch, detailed crosshatching")
(img/generate "A city street, anime style, Studio Ghibli inspired")
```

### Specify Technical Details

```clojure
;; Photography terms
(img/generate "A coffee cup, product photography, studio lighting,
               white background, 85mm lens, f/2.8")

;; Camera angles
(img/generate "A skyscraper, low angle shot, dramatic perspective,
               wide angle lens")
```

### Use Negative Prompts (in the prompt itself)

```clojure
(img/generate "A realistic portrait photo of a businesswoman,
               professional headshot, NOT cartoon, NOT illustrated,
               photorealistic, natural skin texture")
```

## Error Handling

```clojure
(require '[llm-clj.images.openai :as img])
(require '[llm-clj.errors :as errors])

(defn safe-generate [prompt opts]
  (try
    {:success true
     :result (img/generate prompt opts)}
    (catch Exception e
      (cond
        (errors/rate-limited? e)
        {:success false
         :error :rate-limited
         :retry-after (errors/retry-after e)}

        (errors/validation-error? e)
        {:success false
         :error :invalid-request
         :message (ex-message e)}

        :else
        {:success false
         :error :unknown
         :message (ex-message e)}))))

;; Usage
(let [result (safe-generate "A test image" {:size "1024x1024"})]
  (if (:success result)
    (println "Generated:" (-> result :result :images first :url))
    (println "Error:" (:error result) "-" (:message result))))
```

## Cost Considerations

| Model | Size | Quality | Approximate Cost |
|-------|------|---------|------------------|
| DALL-E 3 | 1024x1024 | Standard | Higher |
| DALL-E 3 | 1024x1024 | HD | Highest |
| DALL-E 2 | 1024x1024 | - | Lower |
| DALL-E 2 | 512x512 | - | Lower |
| DALL-E 2 | 256x256 | - | Lowest |

Tips for managing costs:
- Use DALL-E 2 for prototyping
- Use smaller sizes when high resolution isn't needed
- Use `:response-format :b64_json` to avoid URL expiration issues

## Limitations

- **DALL-E 3**: Only generates 1 image per request
- **DALL-E 2**: Required for editing and variations
- **File formats**: PNG required for editing/variations
- **URL expiration**: Generated URLs expire after some time
- **Content policy**: Some prompts may be rejected

## Next Steps

- [Audio](12-audio.md) - Whisper transcription and TTS
- [Embeddings](10-embeddings.md) - Combine with image descriptions
- [Error Handling](06-errors.md) - Handle image API errors

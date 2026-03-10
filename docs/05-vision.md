# Vision and Image Understanding

Both OpenAI and Anthropic support vision models that can understand and analyze images. llm-clj provides a unified interface for working with images across both providers.

## Image Content Types

The `llm-clj.vision` namespace provides helpers for creating image content blocks:

```clojure
(require '[llm-clj.vision :as vision])

;; From a URL
(vision/image-url "https://example.com/image.jpg")

;; From base64 data
(vision/image-base64 "iVBORw0KGgo..." "image/png")

;; From a local file
(vision/image-file "/path/to/image.jpg")
```

## Supported Formats

Both providers support:
- JPEG (`image/jpeg`)
- PNG (`image/png`)
- GIF (`image/gif`)
- WebP (`image/webp`)

## Basic Image Analysis

### Analyzing an Image from URL

```clojure
(require '[llm-clj.core :as llm])
(require '[llm-clj.providers.openai :as openai])
(require '[llm-clj.vision :as vision])

(def provider (openai/create-provider {:model "gpt-4o"}))

(def response
  (llm/chat-completion provider
    [(vision/vision-message
       [(vision/text-content "What's in this image?")
        (vision/image-url "https://upload.wikimedia.org/wikipedia/commons/thumb/4/47/PNG_transparency_demonstration_1.png/280px-PNG_transparency_demonstration_1.png")])]
    {}))

(:content response)
;; => "This image shows two dice with a checkered background demonstrating PNG transparency..."
```

### Analyzing a Local File

```clojure
(require '[llm-clj.core :as llm])
(require '[llm-clj.providers.openai :as openai])
(require '[llm-clj.vision :as vision])

(def provider (openai/create-provider {:model "gpt-4o"}))

;; Analyze a local image file
(def response
  (llm/chat-completion provider
    [(vision/vision-message
       [(vision/text-content "Describe this image in detail.")
        (vision/image-file "/path/to/your/image.jpg")])]
    {:max-tokens 500}))

(println (:content response))
```

## REPL Examples

### Complete Image Analysis Example

Copy and paste this block (replace the image URL with a valid one):

```clojure
(require '[llm-clj.core :as llm])
(require '[llm-clj.providers.openai :as openai])
(require '[llm-clj.vision :as vision])

(def provider (openai/create-provider {:model "gpt-4o"}))

;; Analyze an image from URL
(defn analyze-image [image-url question]
  (let [response (llm/chat-completion provider
                   [(vision/vision-message
                      [(vision/text-content question)
                       (vision/image-url image-url)])]
                   {:max-tokens 300})]
    (:content response)))

;; Example with a public domain image
(analyze-image
  "https://upload.wikimedia.org/wikipedia/commons/thumb/a/a7/Camponotus_flavomarginatus_ant.jpg/320px-Camponotus_flavomarginatus_ant.jpg"
  "What insect is shown in this image? Describe its features.")
```

### Multiple Images in One Message

```clojure
(require '[llm-clj.core :as llm])
(require '[llm-clj.providers.openai :as openai])
(require '[llm-clj.vision :as vision])

(def provider (openai/create-provider {:model "gpt-4o"}))

;; Compare two images
(defn compare-images [url1 url2 question]
  (llm/chat-completion provider
    [(vision/vision-message
       [(vision/text-content question)
        (vision/image-url url1)
        (vision/image-url url2)])]
    {:max-tokens 500}))

;; Example
(compare-images
  "https://example.com/image1.jpg"
  "https://example.com/image2.jpg"
  "What are the main differences between these two images?")
```

## Image Detail Levels (OpenAI)

OpenAI supports different detail levels for image analysis:

```clojure
;; Low detail - faster, cheaper, good for simple tasks
(vision/image-url "https://example.com/image.jpg" {:detail "low"})

;; High detail - slower, more thorough analysis
(vision/image-url "https://example.com/image.jpg" {:detail "high"})

;; Auto (default) - let the model decide
(vision/image-url "https://example.com/image.jpg" {:detail "auto"})
```

## Vision with Anthropic

Anthropic's Claude also supports vision with the same interface:

```clojure
(require '[llm-clj.core :as llm])
(require '[llm-clj.providers.anthropic :as anthropic])
(require '[llm-clj.vision :as vision])

(def provider (anthropic/create-provider {:model "claude-sonnet-4-20250514"}))

(def response
  (llm/chat-completion provider
    [(vision/vision-message
       [(vision/text-content "What do you see in this image?")
        (vision/image-url "https://example.com/image.jpg")])]
    {:max-tokens 500}))

(:content response)
```

## Building Vision Messages

### The `vision-message` Helper

Creates a user message with mixed text and image content:

```clojure
(vision/vision-message
  [(vision/text-content "First, describe this image:")
   (vision/image-url "https://example.com/first.jpg")
   (vision/text-content "Now compare it to this one:")
   (vision/image-url "https://example.com/second.jpg")
   (vision/text-content "Which one has more detail?")])
```

### Manual Message Construction

You can also build messages manually:

```clojure
{:role :user
 :content [{:type :text :text "What's in this image?"}
           {:type :image-url
            :url "https://example.com/image.jpg"
            :detail "auto"}]}
```

## Base64 Image Encoding

For images that aren't accessible via URL:

```clojure
(require '[llm-clj.vision :as vision])
(require '[clojure.java.io :as io])
(import '[java.util Base64])

;; Read and encode a file
(defn encode-image [file-path]
  (let [bytes (java.nio.file.Files/readAllBytes
                (.toPath (io/file file-path)))]
    (.encodeToString (Base64/getEncoder) bytes)))

;; Use with the API
(def base64-data (encode-image "/path/to/image.png"))

(llm/chat-completion provider
  [(vision/vision-message
     [(vision/text-content "Analyze this image:")
      (vision/image-base64 base64-data "image/png")])]
  {})

;; Or use the convenience function
(llm/chat-completion provider
  [(vision/vision-message
     [(vision/text-content "Analyze this image:")
      (vision/image-file "/path/to/image.png")])]
  {})
```

## Combining Vision with Tools

Vision can be combined with function calling:

```clojure
(require '[llm-clj.core :as llm])
(require '[llm-clj.providers.openai :as openai])
(require '[llm-clj.vision :as vision])
(require '[llm-clj.tools :as tools])

(def provider (openai/create-provider {:model "gpt-4o"}))

;; Tool to save detected objects
(def save-objects-tool
  (tools/define-tool
    "save_detected_objects"
    "Saves the detected objects from an image"
    [:map
     [:objects [:vector :string]]
     [:confidence :double]]
    (fn [{:keys [objects confidence]}]
      {:saved true
       :count (count objects)
       :objects objects})))

;; Analyze image and extract structured data
(def response
  (llm/chat-completion provider
    [(vision/vision-message
       [(vision/text-content "Identify all objects in this image and save them using the tool.")
        (vision/image-url "https://example.com/room.jpg")])]
    {:tools [(tools/format-tool-openai save-objects-tool)]}))

;; Handle tool calls as usual
(when (:tool-calls response)
  (tools/execute-tool-call [save-objects-tool] (first (:tool-calls response))))
```

## Complete Vision Application Example

```clojure
(require '[llm-clj.core :as llm])
(require '[llm-clj.providers.openai :as openai])
(require '[llm-clj.vision :as vision])
(require '[llm-clj.tools :as tools])
(require '[cheshire.core :as json])

(def provider (openai/create-provider {:model "gpt-4o"}))

;; === Image Analysis Assistant ===

(def analyze-tool
  (tools/define-tool
    "analyze_result"
    "Records the analysis results in structured form"
    [:map
     [:objects [:vector :string]]
     [:colors [:vector :string]]
     [:mood :string]
     [:description :string]]
    (fn [result]
      (println "Analysis recorded:" result)
      {:recorded true})))

(defn analyze-image-structured [image-source]
  (let [image-block (cond
                      (clojure.string/starts-with? image-source "http")
                      (vision/image-url image-source)

                      (.exists (clojure.java.io/file image-source))
                      (vision/image-file image-source)

                      :else
                      (throw (ex-info "Invalid image source" {:source image-source})))

        response (llm/chat-completion provider
                   [(vision/vision-message
                      [(vision/text-content
                         "Analyze this image thoroughly. Identify all objects, dominant colors, the overall mood, and provide a description. Then use the analyze_result tool to record your findings.")
                       image-block])]
                   {:tools [(tools/format-tool-openai analyze-tool)]
                    :temperature 0.3})]

    (if (:tool-calls response)
      (let [tool-call (first (:tool-calls response))
            args (json/parse-string (get-in tool-call [:function :arguments]) true)]
        {:success true
         :analysis args})
      {:success false
       :raw-response (:content response)})))

;; Usage
;; (analyze-image-structured "https://example.com/photo.jpg")
;; (analyze-image-structured "/path/to/local/image.png")
```

## Provider Format Differences

The library handles format differences automatically:

### OpenAI Format (internal)

```clojure
{:type "image_url"
 :image_url {:url "https://..." :detail "auto"}}

;; For base64:
{:type "image_url"
 :image_url {:url "data:image/png;base64,iVBOR..." :detail "auto"}}
```

### Anthropic Format (internal)

```clojure
{:type "image"
 :source {:type "url" :url "https://..."}}

;; For base64:
{:type "image"
 :source {:type "base64"
          :media_type "image/png"
          :data "iVBOR..."}}
```

You don't need to worry about these differences - just use the `vision` helpers and the providers handle the formatting.

## Best Practices

1. **Use appropriate detail levels**: Use `"low"` for simple tasks to save tokens
2. **Compress large images**: Large images use many tokens
3. **Be specific in prompts**: Tell the model exactly what to look for
4. **Combine with tools**: Extract structured data using function calling
5. **Handle errors**: Images may fail to load or be unreadable


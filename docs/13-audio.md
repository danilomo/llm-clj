# Audio (Whisper & TTS)

OpenAI's Audio API provides speech-to-text (Whisper) and text-to-speech (TTS) capabilities. llm-clj offers a protocol-based abstraction for transcription, translation, and speech synthesis.

## Overview

The Audio API supports three operations:

| Operation | Description | Model |
|-----------|-------------|-------|
| **Transcribe** | Convert audio to text in the original language | Whisper |
| **Translate** | Convert audio to English text | Whisper |
| **Text-to-Speech** | Convert text to spoken audio | TTS-1, TTS-1-HD |

## Available Models

### Whisper (Speech-to-Text)

| Model | Description |
|-------|-------------|
| `whisper-1` | General-purpose transcription, supports 50+ languages |

### TTS (Text-to-Speech)

| Model | Description |
|-------|-------------|
| `tts-1` | Standard quality, faster, lower latency |
| `tts-1-hd` | High definition, better quality |

### Available Voices

| Voice | Description |
|-------|-------------|
| `:alloy` | Neutral, balanced |
| `:echo` | Warm, conversational |
| `:fable` | Expressive, storytelling |
| `:onyx` | Deep, authoritative |
| `:nova` | Friendly, upbeat |
| `:shimmer` | Soft, gentle |

## The Audio Protocol

```clojure
(defprotocol AudioProvider
  (transcribe [this audio options])
  (translate [this audio options])
  (text-to-speech [this text options]))
```

## Supported Audio Formats

For transcription and translation:
- MP3 (`.mp3`)
- MP4 (`.mp4`)
- MPEG (`.mpeg`)
- MPGA (`.mpga`)
- M4A (`.m4a`)
- WAV (`.wav`)
- WebM (`.webm`)

## Transcribing Audio

### Using the Protocol

```clojure
(require '[llm-clj.audio.core :as audio])
(require '[llm-clj.audio.openai :as whisper])

;; Create a provider
(def provider (whisper/create-provider {}))

;; Transcribe audio file
(audio/transcribe provider "/path/to/audio.mp3" {})
```

### Using the Convenience Function

```clojure
(require '[llm-clj.audio.openai :as whisper])

;; Simple transcription
(whisper/transcribe-file "/path/to/audio.mp3")

;; With options
(whisper/transcribe-file "/path/to/audio.mp3"
  {:language "en"
   :prompt "This is a technical discussion about programming."})
```

### Response Structure

```clojure
{:text "The transcribed text content..."}
```

With `:response-format :verbose_json`:

```clojure
{:text "The transcribed text..."
 :language "en"
 :duration 45.2
 :segments [{:id 0
             :start 0.0
             :end 2.5
             :text "The transcribed..."}
            ...]
 :words [{:word "The"
          :start 0.0
          :end 0.15}
         ...]}
```

## REPL Examples

### Basic Transcription

Copy and paste this entire block:

```clojure
(require '[llm-clj.audio.openai :as whisper])

;; Transcribe an audio file (adjust path to your file)
(def result (whisper/transcribe-file "/path/to/your/audio.mp3"))

;; Check the result
(println "Transcription:" (:text result))
```

### Transcription with Language Hint

```clojure
(require '[llm-clj.audio.openai :as whisper])

;; Specify language for better accuracy
(def result
  (whisper/transcribe-file "/path/to/spanish-audio.mp3"
    {:language "es"
     :prompt "Una conversación en español sobre tecnología."}))

(println "Spanish transcription:" (:text result))
```

### Get Word-Level Timestamps

```clojure
(require '[llm-clj.audio.core :as audio])
(require '[llm-clj.audio.openai :as whisper])

(def provider (whisper/create-provider {}))

;; Get detailed transcription with timestamps
(def result
  (audio/transcribe provider "/path/to/audio.mp3"
    {:response-format :verbose_json
     :timestamp-granularities [:word :segment]}))

;; Print segments
(println "Segments:")
(doseq [segment (:segments result)]
  (println (format "  [%.2f - %.2f] %s"
                   (:start segment)
                   (:end segment)
                   (:text segment))))

;; Print words with timestamps
(println "\nWords:")
(doseq [word (take 10 (:words result))]
  (println (format "  %.2f: %s" (:start word) (:word word))))
```

### Generate Subtitles (SRT/VTT)

```clojure
(require '[llm-clj.audio.core :as audio])
(require '[llm-clj.audio.openai :as whisper])

(def provider (whisper/create-provider {}))

;; Get SRT format subtitles
(def srt-result
  (audio/transcribe provider "/path/to/video.mp4"
    {:response-format :srt}))

(println "SRT Subtitles:")
(println (:text srt-result))
;; => 1
;;    00:00:00,000 --> 00:00:02,500
;;    Hello and welcome to this video.
;;
;;    2
;;    00:00:02,500 --> 00:00:05,000
;;    Today we'll discuss programming.

;; Save to file
(spit "/path/to/subtitles.srt" (:text srt-result))

;; Or get VTT format (for web)
(def vtt-result
  (audio/transcribe provider "/path/to/video.mp4"
    {:response-format :vtt}))

(spit "/path/to/subtitles.vtt" (:text vtt-result))
```

## Translating Audio to English

Translate audio from any supported language to English:

```clojure
(require '[llm-clj.audio.core :as audio])
(require '[llm-clj.audio.openai :as whisper])

(def provider (whisper/create-provider {}))

;; Translate French audio to English
(def result
  (audio/translate provider "/path/to/french-audio.mp3" {}))

(println "English translation:" (:text result))
```

### REPL Example: Translation

```clojure
(require '[llm-clj.audio.core :as audio])
(require '[llm-clj.audio.openai :as whisper])

(def provider (whisper/create-provider {}))

;; Translate with a guiding prompt
(def result
  (audio/translate provider "/path/to/foreign-audio.mp3"
    {:prompt "This is a formal business meeting discussion."
     :temperature 0.2}))

(println "Translation:" (:text result))
```

## Text-to-Speech

### Using the Convenience Function

```clojure
(require '[llm-clj.audio.openai :as tts])

;; Generate speech (returns byte array)
(def audio-bytes (tts/speak "Hello, how are you today?"))

;; Generate and save to file
(tts/speak-to-file "Hello, world!" "/tmp/greeting.mp3")
```

### Using the Protocol

```clojure
(require '[llm-clj.audio.core :as audio])
(require '[llm-clj.audio.openai :as tts])

(def provider (tts/create-provider {}))

;; Generate speech
(def audio-bytes
  (audio/text-to-speech provider "Welcome to our application."
    {:voice :nova
     :model "tts-1-hd"
     :speed 1.0}))

;; Save to file
(with-open [out (clojure.java.io/output-stream "/tmp/welcome.mp3")]
  (.write out audio-bytes))
```

### REPL Examples: Text-to-Speech

#### Basic Speech Generation

```clojure
(require '[llm-clj.audio.openai :as tts])

;; Generate speech with default settings
(tts/speak-to-file
  "Welcome to the llm-clj library. This is a test of the text to speech system."
  "/tmp/test-speech.mp3")

(println "Audio saved to /tmp/test-speech.mp3")
```

#### Compare Voices

```clojure
(require '[llm-clj.audio.openai :as tts])

;; Generate samples of all voices
(def sample-text "The quick brown fox jumps over the lazy dog.")

(doseq [voice [:alloy :echo :fable :onyx :nova :shimmer]]
  (let [output-path (str "/tmp/voice-" (name voice) ".mp3")]
    (tts/speak-to-file sample-text output-path {:voice voice})
    (println "Generated:" output-path)))

(println "All voice samples generated!")
```

#### Adjust Speed

```clojure
(require '[llm-clj.audio.openai :as tts])

(def text "This demonstrates different speech speeds.")

;; Slow (0.25x to 1.0x)
(tts/speak-to-file text "/tmp/slow.mp3" {:speed 0.7})

;; Normal
(tts/speak-to-file text "/tmp/normal.mp3" {:speed 1.0})

;; Fast (1.0x to 4.0x)
(tts/speak-to-file text "/tmp/fast.mp3" {:speed 1.5})

(println "Speed samples generated")
```

#### High Definition Audio

```clojure
(require '[llm-clj.audio.openai :as tts])

;; Standard quality (faster, good for real-time)
(tts/speak-to-file "Standard quality audio."
  "/tmp/standard.mp3"
  {:model "tts-1"})

;; HD quality (better for recorded content)
(tts/speak-to-file "High definition audio."
  "/tmp/hd.mp3"
  {:model "tts-1-hd"})
```

#### Different Output Formats

```clojure
(require '[llm-clj.audio.openai :as tts])

(def text "Testing different audio formats.")

;; MP3 (default, good compression)
(tts/speak-to-file text "/tmp/audio.mp3" {:response-format :mp3})

;; Opus (excellent for streaming)
(tts/speak-to-file text "/tmp/audio.opus" {:response-format :opus})

;; WAV (uncompressed, highest quality)
(tts/speak-to-file text "/tmp/audio.wav" {:response-format :wav})

;; FLAC (lossless compression)
(tts/speak-to-file text "/tmp/audio.flac" {:response-format :flac})

;; AAC (good for Apple devices)
(tts/speak-to-file text "/tmp/audio.aac" {:response-format :aac})
```

## Options Reference

### Transcription Options

```clojure
{:model "whisper-1"              ; Transcription model
 :language "en"                   ; ISO-639-1 language code (auto-detected if omitted)
 :prompt "..."                    ; Optional prompt to guide style/vocabulary
 :response-format :json           ; :json, :text, :srt, :verbose_json, or :vtt
 :temperature 0.2                 ; Sampling temperature (0-1)
 :timestamp-granularities [:word] ; [:word], [:segment], or [:word :segment]
}
```

### Translation Options

Same as transcription except `:language` (output is always English).

### Text-to-Speech Options

```clojure
{:model "tts-1"                 ; tts-1 or tts-1-hd
 :voice :alloy                   ; :alloy, :echo, :fable, :onyx, :nova, :shimmer
 :response-format :mp3           ; :mp3, :opus, :aac, :flac, :wav, :pcm
 :speed 1.0                      ; Speed multiplier (0.25 - 4.0)
}
```

## Response Formats

### Transcription Response Formats

| Format | Description | Use Case |
|--------|-------------|----------|
| `:json` | Simple JSON with text | Default, most common |
| `:text` | Plain text only | Simple extraction |
| `:verbose_json` | JSON with timestamps | Subtitles, alignment |
| `:srt` | SubRip subtitle format | Video subtitles |
| `:vtt` | WebVTT subtitle format | Web video subtitles |

### TTS Output Formats

| Format | Description | Use Case |
|--------|-------------|----------|
| `:mp3` | MPEG Audio Layer 3 | General purpose (default) |
| `:opus` | Opus codec | Streaming, web |
| `:aac` | Advanced Audio Coding | Apple devices |
| `:flac` | Free Lossless Audio Codec | Archival, high quality |
| `:wav` | Waveform Audio | Uncompressed, editing |
| `:pcm` | Raw PCM audio | Audio processing |

## Error Handling

```clojure
(require '[llm-clj.audio.openai :as whisper])
(require '[llm-clj.errors :as errors])

(defn safe-transcribe [path]
  (try
    {:success true
     :result (whisper/transcribe-file path)}
    (catch Exception e
      (cond
        (errors/validation-error? e)
        {:success false
         :error :invalid-input
         :message (ex-message e)}

        (errors/rate-limited? e)
        {:success false
         :error :rate-limited
         :retry-after (errors/retry-after e)}

        (errors/authentication-error? e)
        {:success false
         :error :auth-failed}

        :else
        {:success false
         :error :unknown
         :message (ex-message e)}))))

;; Usage
(let [result (safe-transcribe "/path/to/audio.mp3")]
  (if (:success result)
    (println "Transcription:" (-> result :result :text))
    (println "Error:" (:error result))))
```

## Complete Application: Podcast Transcriber

```clojure
(require '[llm-clj.audio.core :as audio])
(require '[llm-clj.audio.openai :as whisper])
(require '[clojure.java.io :as io])
(require '[clojure.string :as str])

;; === Podcast Transcription Service ===

(def provider (whisper/create-provider {}))

(defn transcribe-podcast
  "Transcribes a podcast episode with timestamps and speaker hints."
  [audio-path output-dir & {:keys [language] :or {language nil}}]
  (let [filename (-> audio-path io/file .getName (str/replace #"\.[^.]+$" ""))

        ;; Get detailed transcription
        result (audio/transcribe provider audio-path
                 (cond-> {:response-format :verbose_json
                          :timestamp-granularities [:segment]}
                   language (assoc :language language)))

        ;; Generate different formats
        plain-text (:text result)

        ;; Generate timestamped transcript
        timestamped (->> (:segments result)
                         (map (fn [{:keys [start end text]}]
                                (format "[%s - %s] %s"
                                        (format-time start)
                                        (format-time end)
                                        (str/trim text))))
                         (str/join "\n\n"))

        ;; Get SRT subtitles
        srt-result (audio/transcribe provider audio-path
                     {:response-format :srt})]

    ;; Create output directory
    (io/make-parents (str output-dir "/" filename ".txt"))

    ;; Save files
    (spit (str output-dir "/" filename ".txt") plain-text)
    (spit (str output-dir "/" filename "-timestamped.txt") timestamped)
    (spit (str output-dir "/" filename ".srt") (:text srt-result))

    {:filename filename
     :duration (:duration result)
     :language (:language result)
     :segments (count (:segments result))
     :output-dir output-dir}))

(defn format-time [seconds]
  (let [mins (int (/ seconds 60))
        secs (mod (int seconds) 60)]
    (format "%02d:%02d" mins secs)))

;; Usage
(def result
  (transcribe-podcast "/path/to/podcast-episode.mp3" "/tmp/transcripts"))

(println "Transcribed:" (:filename result))
(println "Duration:" (format-time (:duration result)))
(println "Language:" (:language result))
(println "Segments:" (:segments result))
(println "Output saved to:" (:output-dir result))
```

## Complete Application: Voice Assistant

```clojure
(require '[llm-clj.audio.openai :as audio])
(require '[llm-clj.core :as llm])
(require '[llm-clj.providers.openai :as openai])

;; === Voice-to-Voice Assistant ===

(def chat-provider (openai/create-provider {}))

(defn voice-assistant
  "Processes voice input and generates voice response."
  [audio-path output-path & {:keys [voice system-prompt]
                              :or {voice :nova
                                   system-prompt "You are a helpful assistant. Be concise."}}]
  (let [;; Transcribe user's speech
        transcription (audio/transcribe-file audio-path)
        user-text (:text transcription)
        _ (println "User said:" user-text)

        ;; Generate response with LLM
        response (llm/chat-completion chat-provider
                   [{:role :system :content system-prompt}
                    {:role :user :content user-text}]
                   {:temperature 0.7
                    :max-tokens 200})
        assistant-text (:content response)
        _ (println "Assistant says:" assistant-text)

        ;; Convert response to speech
        _ (audio/speak-to-file assistant-text output-path
            {:voice voice
             :model "tts-1-hd"})]

    {:user-input user-text
     :assistant-response assistant-text
     :audio-output output-path}))

;; Usage
(voice-assistant
  "/path/to/user-question.mp3"
  "/tmp/assistant-response.mp3"
  :voice :echo
  :system-prompt "You are a friendly tech support agent.")
```

## Complete Application: Meeting Summarizer

```clojure
(require '[llm-clj.audio.openai :as whisper])
(require '[llm-clj.core :as llm])
(require '[llm-clj.providers.openai :as openai])
(require '[clojure.string :as str])

;; === Meeting Summarizer ===

(def chat-provider (openai/create-provider {}))

(defn summarize-meeting
  "Transcribes a meeting recording and generates a summary."
  [audio-path]
  (let [;; Transcribe
        _ (println "Transcribing meeting...")
        transcription (whisper/transcribe-file audio-path
                        {:prompt "This is a business meeting with multiple speakers."})
        full-text (:text transcription)

        ;; Generate summary
        _ (println "Generating summary...")
        summary-response (llm/chat-completion chat-provider
                           [{:role :system
                             :content "You are an expert at summarizing meetings.
                                       Provide: 1) Key points, 2) Action items, 3) Decisions made."}
                            {:role :user
                             :content (str "Please summarize this meeting transcript:\n\n" full-text)}]
                           {:temperature 0.3
                            :max-tokens 500})

        ;; Extract action items
        _ (println "Extracting action items...")
        actions-response (llm/chat-completion chat-provider
                           [{:role :system
                             :content "Extract action items from the meeting. Format as a bullet list with owner and deadline if mentioned."}
                            {:role :user
                             :content full-text}]
                           {:temperature 0.2
                            :max-tokens 300})]

    {:transcript full-text
     :summary (:content summary-response)
     :action-items (:content actions-response)}))

;; Usage
(def meeting (summarize-meeting "/path/to/meeting-recording.mp3"))

(println "=== MEETING SUMMARY ===")
(println (:summary meeting))
(println)
(println "=== ACTION ITEMS ===")
(println (:action-items meeting))
```

## Transcribing from Byte Arrays

You can transcribe audio from byte arrays (useful for streaming or in-memory audio):

```clojure
(require '[llm-clj.audio.core :as audio])
(require '[llm-clj.audio.openai :as whisper])
(require '[clojure.java.io :as io])

(def provider (whisper/create-provider {}))

;; Read audio file into bytes
(defn file->bytes [path]
  (with-open [in (io/input-stream path)
              out (java.io.ByteArrayOutputStream.)]
    (io/copy in out)
    (.toByteArray out)))

;; Transcribe from bytes
(def audio-bytes (file->bytes "/path/to/audio.mp3"))
(def result (audio/transcribe provider audio-bytes {}))

(println "Transcription:" (:text result))
```

## Performance Considerations

### Audio File Size

- Maximum file size: 25 MB
- For larger files, split into chunks

### Transcription Tips

1. **Use language hints** - Setting `:language` improves accuracy
2. **Use prompts** - Guide terminology and style
3. **Lower temperature** - Use 0.0-0.2 for more accurate results

### TTS Tips

1. **Use TTS-1 for real-time** - Lower latency
2. **Use TTS-1-HD for recordings** - Better quality
3. **Choose appropriate format** - Opus for streaming, WAV for editing

## Limitations

- **File size**: 25 MB maximum for transcription
- **Whisper languages**: ~50 languages supported (see OpenAI docs)
- **TTS languages**: English primarily, limited multilingual support
- **Real-time**: No streaming transcription (batch only)

## Next Steps

- [Token Counting](14-token-counting.md) - Manage context windows
- [Error Handling](06-errors.md) - Handle audio API errors
- [Batch Processing](12-batch-processing.md) - Process multiple files

(ns llm-clj.audio.core
  "Protocol definitions for the Audio API.")

(defprotocol AudioProvider
  "Protocol for providers implementing audio processing."

  (transcribe [this audio options]
    "Transcribes audio to text.

    audio - Path to audio file or byte array
            Supported formats: mp3, mp4, mpeg, mpga, m4a, wav, webm

    Options:
    - :model - Transcription model (default: whisper-1)
    - :language - ISO-639-1 language code (optional, auto-detected)
    - :prompt - Optional prompt to guide transcription style
    - :response-format - :json, :text, :srt, :verbose_json, or :vtt
    - :temperature - Sampling temperature (0-1)
    - :timestamp-granularities - [:word] or [:segment] for verbose_json

    Returns (depends on response-format):
    - :json/:text - {:text \"transcribed text\"}
    - :verbose_json - {:text \"...\" :segments [...] :words [...]}
    - :srt/:vtt - {:text \"subtitle content\"}")

  (translate [this audio options]
    "Translates audio to English text.

    audio - Path to audio file or byte array

    Options same as transcribe (except :language).

    Returns transcription in English.")

  (text-to-speech [this text options]
    "Converts text to spoken audio.

    Options:
    - :model - TTS model: tts-1 or tts-1-hd (default: tts-1)
    - :voice - Voice: :alloy, :echo, :fable, :onyx, :nova, :shimmer
    - :response-format - :mp3, :opus, :aac, :flac, :wav, :pcm
    - :speed - Speed multiplier (0.25-4.0, default: 1.0)

    Returns byte array of audio data."))

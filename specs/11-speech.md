# 11 — Speech: synthesis, recognition, conversation

**Status:** planned

## Intent

Hearing words and texts, reading aloud with feedback, and eventually speaking
with the tutor entirely in Japanese: understanding and answering by voice.
Conversation is the last step: it needs a good amount of grammar and vocabulary
first.

## Decided

- **Synthesis first**: audio for words, example sentences and texts, playable
  in quiet sessions with headphones.
- **Candidates to compare by listening**: **Qwen3-TTS** (0.6B and 1.7B, already
  in the local Hugging Face cache, Japanese supported) and **VOICEVOX**
  (Japanese-specific, local). The choice is made on a set of real sentences.
- **Recognition next**: **whisper.cpp**, with a general model or a
  Japanese-tuned one (kotoba-whisper) to compare. It serves reading aloud first
  (what was read compared with the text), then conversation.
- Speech models run as **separate processes**, like llama-server, never through
  a stock GPU framework in the backend ([`01`](01-application-architecture.md)).
- **Memory is not a constraint**: with 128 GB of unified memory, the LLM and
  both speech models can stay loaded together.
- Speaking exercises only appear when the session's context allows speaking
  aloud ([`09`](09-sessions.md)).

## Open

- Which synthesis engine, voice and speed. Slowed speech for beginners.
- How generated audio is cached.
- How reading aloud is judged: transcript compared with the text, word by word.
  Pitch accent is far later, if ever.
- Conversation: turn-taking, latency, keeping the tutor's language within what
  the learner knows, corrections during or after the dialogue.
- Whether the NPU runs recognition ([`10`](10-llm-runtime.md)).

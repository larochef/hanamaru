# 10 — LLM runtime and the AI tutor

**Status:** planned

## Intent

A local model provides everything interactive: generated texts and exercises,
correction of free answers, explanations, mnemonics, diagnosis of mistakes, and
later conversation. The backend runs it and keeps it within the rules of
[`00`](00-overview.md#principles).

## Decided

- **Engine: upstream llama.cpp `llama-server`, Vulkan build.** On a Strix Halo
  it works better than the Strix-specific fork. If ROCm is ever needed, use the
  **system ROCm** (10.1 or later). No separate ROCm tree is downloaded.
- **The backend launches and supervises `llama-server`** as a long-lived process,
  and speaks to its OpenAI-compatible API. The binary's path is in the local
  configuration; the model files are found in the Hugging Face cache by their
  repository and names. No runtime manager to begin with.
- **Model: Gemma 4 26B A4B instruct**, from the Hugging Face repository
  `unsloth/gemma-4-26b-a4b-it-GGUF`, read from the Hugging Face cache:
  - `gemma-4-26B-A4B-it-UD-Q4_K_XL.gguf`: the model (about 17 GB);
  - `MTP/mtp-gemma-4-26B-A4B-it-Q8_0.gguf`: its multi-token prediction head,
    for faster generation, to try.

  It is a mixture-of-experts model with few active parameters per token, so it
  is fast on a Strix Halo. The model stays swappable through the configuration.
- **Structured output**: every request whose answer the backend reads uses a
  JSON schema (llama-server's grammar-constrained output), not free text to
  parse.
- **The tutor's roles**, each a prompt of its own with what it may use (known
  words, current grammar point):
  - generate texts and exercises ([`07`](07-text-generation.md));
  - judge a free answer: verdict, corrected version, explanation in French;
  - answer questions on a text or a grammar point;
  - propose mnemonics;
  - **diagnose a mistake**: name the curriculum points involved, so a missing
    prerequisite can be reopened ([`03`](03-learner-model.md)). The tutor then
    goes back over it, with a short review inside the session.
- **What the tutor never decides**: stroke order, kanji readings, grades, the
  curriculum order, whether a word is in the dictionary.

## Open

- **NPU** (XDNA 2): a later optimisation, for example to run speech
  recognition continuously during conversation without loading the GPU. Linux
  tooling for it is still young.
- Context size, sampling settings per role, and how much of the learner model
  goes into a prompt.
- How MTP is enabled and what it gains here.
- Whether the model takes audio input directly, which would bear on
  [`11`](11-speech.md).
- Caching generated content, and generating ahead of time (the next lesson's
  text prepared while the learner works).

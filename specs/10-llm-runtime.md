# 10 — LLM runtime and the AI tutor

**Status:** runtime built; roles: judging meanings, writing sentences and lesson texts, correcting exercises

## Intent

A local model provides everything interactive: generated texts and exercises,
correction of free answers, explanations, mnemonics, diagnosis of mistakes, and
later conversation. The backend runs it and keeps it within the rules of
[`00`](00-overview.md#principles).

## Decided

- **Engine: upstream llama.cpp `llama-server`, Vulkan build.** On a Strix Halo
  it works better than the Strix-specific fork. If ROCm is ever needed, use the
  **system ROCm** (10.1 or later). No separate ROCm tree is downloaded.
- **The backend launches and supervises `llama-server`** (`LlamaServer`, built)
  as a long-lived process, and speaks to its OpenAI-compatible API:
  - its path is the `llama.server` setting of this machine, in
    `~/.config/hanamaru/hanamaru.properties` (`HANAMARU_CONFIG` moves it),
    outside the repository; `model.repository`, `model.file` and
    `model.context` override the model's defaults;
  - the model file is found in the Hugging Face cache (`HF_HUB_CACHE`, else
    `HF_HOME/hub`, else `~/.cache/huggingface/hub`), from the snapshot its
    `main` reference names;
  - it is launched as the backend starts, on a free port of `127.0.0.1` only,
    with every layer on the GPU, one request at a time, a 16k context, no
    thinking before answers (`--reasoning off`) and no web page; it is watched
    until `/health` answers, launched again if it stops, given up after three
    failures in a row, and stopped with the backend;
  - what it writes goes to `~/.cache/hanamaru/logs/llama-server.log`; the
    backend logs one line when it is ready;
  - the page shows whether the tutor is ready, loading, or off, and says what
    to set when it is not set up or its model is missing.
  On the Strix Halo, the model loads in about 7 s (2 s once in the system's
  cache) and writes about 47 tokens a second; a short answer constrained by a
  JSON schema takes under 3 s.
- **Later: a settings page** to manage the runner and the model: choosing the
  `llama-server` build and the model, including smaller models for more modest
  GPUs, instead of editing the settings file.
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
- **Asking the model** (`LlamaClient`): a system and a user message, the
  answer constrained by a JSON schema, temperature 0, read as a typed value; a
  30 s timeout; no answer when the tutor is not ready, and the caller does
  without it.
- **First role built: judging a meaning** typed for a word (`MeaningJudge`):
  given the word, its glosses numbered and the learner's answer, the tutor says
  whether it means the same as one of them, and which by number; the code
  checks the number, so the tutor only recognises right answers.
- **Second role built: writing a sentence** for a word with only the words
  the learner knows (`SentenceWriter`, [`07`](07-text-generation.md)), at
  temperature 0.7 so that a sentence refused is not written again the same.
- **Third role built: writing a lesson's reading text** (`TextWriter`,
  [`07`](07-text-generation.md)), told the grammar and words allowed and the
  particles forbidden, checked sentence by sentence by the code.
- **Fourth role built: correcting an exercise** (`ExerciseJudge`,
  [`06`](06-curriculum-and-lessons.md)), asked only when the code cannot tell:
  whether a particle or a sentence is right all the same, the learner's
  sentence corrected, and why, in French, Japanese in kana.
- **Fifth role built: answering the learner's questions**
  (`TutorQuestions`), in a chat a button away at the bottom right of every
  page (`TutorChat`), over the pop-ins, and on the questions page. Each screen
  tells the chat what the learner has before them as it changes
  (`TutorContext`): a lesson's exercise (what it asks, in words, with the
  answer expected), then the learner's answer and the verdict shown, the
  correction and explanation included; a word of a series, shown, asked one
  way (in its sentence, if any), then answered; a kanji read or written in its
  word. Without one, the page's lesson (a lesson's or a unit's page), else a
  question of the learner's own. The chat says what it is about above the
  questions, and starts a new conversation when the learner moves on to
  another sentence, word or lesson; while an exercise is being answered, the
  tutor is told not to give its answer away. The tutor is given that context
  and, from the server: the point's lesson, its examples and its unit (theme,
  words, kanji), the sentence's words as the analyser and the dictionary read
  them, the word's readings and senses, and every kanji in all of it with its
  meanings and readings from KANJIDIC (those the unit teaches marked), not as
  the tutor remembers them: without the kanji, asked how to say the sun, it
  answered たいよう and never linked it to the 日 of にほん before it. Told to be honest rather than agreeable: a
  learner doubting a right sentence is told it is right. Every question is
  kept (`tutor_question`) before it is answered, with its context, its answer
  and the model that gave it, so that
  none is lost when the tutor is away; the questions page shows them all. On
  a first trial, three questions on words (はは and おかあさん, あと and
  うしろ, はやし and もり) were answered right in about 3 s, and three right
  sentences said to be wrong were held right.
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
- A larger remote model (an API's) for the questions, and for writing more
  varied exercises: what it would leave the machine, where its key is kept,
  and whether the kept questions show the local tutor falls short. A sentence
  the learner finds wrong is not replaced yet: the tutor only says so.

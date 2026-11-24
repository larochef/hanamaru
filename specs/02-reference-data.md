# 02 — Reference data

**Status:** KanjiVG, JMdict and the JLPT lists imported

## Intent

The facts the AI must not invent come from open datasets, imported once into
the application's storage and queried by the backend.

## Decided

| Dataset | Used for |
|---|---|
| **KanjiVG** | The strokes of every kana and kanji as SVG paths, in order, grouped by component. Basis of the stroke animation and of the stroke matcher ([`05`](05-writing.md)) |
| **KANJIDIC2** (EDRDG) | Kanji readings (on, kun), meanings, stroke count, school grade, frequency |
| **JMdict** (EDRDG) | Dictionary: word forms (kanji and kana), readings, glosses (including French where present), priority tags |
| **Tatoeba** | Real example sentences, Japanese with French or English translations |
| **Kyōiku kanji list** | The goal: kanji by school grade, grades 1 to 6 |

- These are imported by the backend from where they are published, pinned to a
  version and checked, on first start. They are not committed to the repository.
- **KanjiVG** is pinned to release `r20260714` (its `main` archive, checked by
  its SHA-256), downloaded into `~/.cache/hanamaru/reference` in the background
  on first start, and read from the archive character by character: one SVG
  file per character, named by its code point. Until it is ready, the page says
  it is being downloaded. Its files' DTD is never fetched.
- **The kana taught** are a table written in `shared` (`Kana`), not imported:
  46 basic, 25 voiced and 4 small (ゃ ゅ ょ っ) in each script, laid out as the
  usual chart, read in Hepburn romaji (を is written *wo*, as typed). The
  obsolete ゐ and ゑ are left out.
- The kanji order follows the **school grades**, with the option of pulling a
  kanji forward when a lesson's vocabulary needs it.
- **JMdict** (CC BY-SA 4.0, EDRDG) is the full multilingual file
  (`JMdict.gz`), the only one with the frequency marks and the French glosses.
  EDRDG publishes it again every day under the same name, with no version to
  pin: it is downloaded once into the cache, kept with its date and SHA-256,
  and not downloaded again unless asked for (a settings page, later). It is
  imported into a SQLite database of its own, rebuilt from it at will
  (`~/.cache/hanamaru/reference/dictionary.db`, about 60 MB, 2 s to import):
  words with their frequency rank and common mark, their written forms and
  readings with JMdict's codes, their senses in French and English with
  their parts of speech and glosses. A change of the database's shape imports
  it again.
- **The core vocabulary list** (the order words are learned in, and the
  vocabulary placement) is the **JLPT lists**, N5 (the first words) to N1:
  Jonathan Waller's lists (tanos.co.uk, CC BY), each word tied to its JMdict
  entry by yomitan-jlpt-vocab (CC BY-SA 4.0), pinned to one commit and each
  file checked by its SHA-256; 8,283 words, a word in the easiest list it is
  in. Within a level, the words JMdict ranks most frequent come first.
  JMdict's own frequency ranks were the first choice and were dropped: they
  come from a newspaper corpus (食べる near the 12,000th word, 行く and 好き
  unranked, 安保 "security treaty" among the first 500).
- **Sudachi's core dictionary** (Works Applications, Apache 2.0), the
  analyser's ([`01`](01-application-architecture.md)), is pinned to release
  `v20261015` and checked by its SHA-256 (77 MB to download, 202 MB
  installed), downloaded and installed with the others on first start.
- **Tatoeba** is not imported yet: the sentences words are practised in are
  written by the tutor and checked by the code ([`08`](08-vocabulary-practice.md));
  real sentences will come with reading texts.
- Each dataset's licence (share-alike or attribution) is respected and credited
  in the page. KanjiVG is CC BY-SA 3.0 (© Ulrich Apel), as its files state.
- **KANJIDIC2** (EDRDG, CC BY-SA 4.0, credited in the page) is downloaded
  once and kept like JMdict, with its date and SHA-256, and imported into a
  SQLite database of its own (`kanji.db`): the 3,125 kanji that have a school
  grade or a frequency rank, with their stroke count, frequency, on and kun
  readings, and meanings, French first (all but one of the school kanji have
  French ones). Its grades 1 to 6 hold 80, 160, 200, 202, 193 and 191 kanji:
  1,026, the current (2020) school list.
- **French in JMdict**, measured: 57% of its common words have French glosses,
  79% of the 4,000 most frequent, and 98% of the JLPT N5 to N2 words (79% of
  N1). The words without French get the English glosses, said so.

## To verify

- The exact licence version of Tatoeba.


## Open

- When to add the kana combinations (yōon: きゃ, しゅ, ちょ…), the long-vowel
  mark ー and the extended katakana (ティ, ファ…): reading them is a skill of
  its own, writing them is not. Not with the first kana; later, likely with
  vocabulary.

- Whether KanjiVG's kana are drawn in a style close enough to textbook
  handwriting, or need small corrections.
- The storage format of Tatoeba. JMdict and KANJIDIC2 are each in a SQLite
  database of their own, KanjiVG stays in its archive.

# 02 — Reference data

**Status:** KanjiVG imported

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
- Each dataset's licence (share-alike or attribution) is respected and credited
  in the page. KanjiVG is CC BY-SA 3.0 (© Ulrich Apel), as its files state.

## To verify

- Exact licence versions of KANJIDIC2, JMdict and Tatoeba.
- That KANJIDIC2's grade field matches the current (2020) kyōiku list, which has
  1026 kanji. Otherwise the list is taken from another source.
- How much French JMdict actually carries. French glosses may be patchy, and
  the tutor may have to translate English glosses, flagged as such.

## Open

- The source of the **core vocabulary list** for the vocabulary placement and
  the first lessons: JMdict priority tags, a frequency list, or a JLPT-level list
  (unofficial).
- Whether KanjiVG's kana are drawn in a style close enough to textbook
  handwriting, or need small corrections.
- The storage format of the large datasets (KANJIDIC2, JMdict, Tatoeba):
  imported into SQLite, or kept as indexed files in the cache. KanjiVG stays in
  its archive.

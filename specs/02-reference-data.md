# 02 — Reference data

**Status:** planned

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
- The kanji order follows the **school grades**, with the option of pulling a
  kanji forward when a lesson's vocabulary needs it.
- Each dataset's licence (share-alike or attribution) is respected and credited
  in the page.

## To verify

- Exact licence versions of each dataset.
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
- The storage format: imported into SQLite, or kept as indexed files in the
  cache.

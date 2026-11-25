package hanamaru.backend.kanji

import hanamaru.backend.learner.*
import hanamaru.backend.reference.SentenceLayout
import hanamaru.shared.text.{KanjiShown, SentencePart}

/** The kanji the learner reads, as they stand, and how a sentence's words are
  * shown from them: a word whose kanji are all read (their reading past its
  * first steps), in kanji; a word whose kanji are all met, being learned, in
  * kanji with its reading above; any other, in kana. Only the units' kanji are
  * taught, so a word with any other kanji stays in kana. Applied as a sentence
  * is served, not as it is laid out, since the learner's kanji change between
  * two requests.
  */
final case class KanjiSight(read: Set[String], met: Set[String]) {

  def shown(parts: List[SentencePart]): List[SentencePart] =
    parts.map(part =>
      part.written.fold(part) { written =>
        // 々 repeats the kanji before it: it has no card of its own
        val kanji = written
          .filter(character =>
            SentenceLayout.isKanji(character) && character != '々'
          )
          .map(_.toString)
          .toSet
        part.copy(shownAs =
          if (kanji.forall(read)) KanjiShown.Kanji
          else if (kanji.forall(met)) KanjiShown.WithReading
          else KanjiShown.Kana
        )
      }
    )
}

object KanjiSight {

  /** The learner's kanji now, from their reading cards. */
  def of(learner: LearnerStore): KanjiSight = {
    val readings =
      learner.cards(ItemKind.Kanji).filter(_.kind == CardKind.Reading)
    KanjiSight(
      readings.filter(_.passed).map(_.key).toSet,
      readings.map(_.key).toSet
    )
  }
}

package hanamaru.backend.characters

import hanamaru.shared.characters.*

import scala.collection.immutable.ListMap

/** A focus on a new character: its cards asked in order (read and written in
  * turn, the first of each new: shown, written over its model), then each card
  * whose last answer was missed asked again, the one asked longest ago first, a
  * few times at most, so that a focus always ends.
  */
object CharacterFocus {

  /** How many times a focus reads and writes its character. */
  val rounds = 4

  /** How many cards missed come back, at most. */
  val comeBacksAtMost = 3

  /** The next card of a focus whose cards are `cards`, given its `answers` so
    * far; none once it is over.
    */
  def next(
      cards: List[CharacterExercise],
      answers: List[FocusAnswer]
  ): Option[CharacterExercise] =
    cards.lift(answers.size).orElse {
      // each card, how it last went, and when it was last asked
      val latest = answers.zipWithIndex.foldLeft(
        ListMap.empty[CharacterExercise, (CardResult, Int)]
      ) { case (results, (answer, index)) =>
        results.updated(plain(answer.exercise), answer.result -> index)
      }
      Option
        .when(answers.size - cards.size < comeBacksAtMost)(
          latest
            .collect { case (card, (CardResult.Missed, asked)) =>
              card -> asked
            }
            .minByOption(_._2)
            .map(_._1)
        )
        .flatten
    }

  /** How the last of a focus's cards that `asked` this went, if any was. */
  def lastOf(
      answers: List[FocusAnswer],
      asked: CharacterAsked
  ): Option[CardResult] =
    answers.reverse.collectFirst {
      case FocusAnswer(CharacterExercise.Kana(_, `asked`, _, _), result) =>
        result
      case FocusAnswer(CharacterExercise.Kanji(kanji), result)
          if kanji.asked == asked =>
        result
    }

  // a card as it comes back: no longer new
  private def plain(card: CharacterExercise): CharacterExercise = card match {
    case kana: CharacterExercise.Kana   => kana.copy(isNew = false)
    case CharacterExercise.Kanji(kanji) =>
      CharacterExercise.Kanji(kanji.copy(isNew = false))
    case focus: CharacterExercise.Focus => focus
  }
}

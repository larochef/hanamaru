package hanamaru.backend.kanji

import hanamaru.backend.learner.*
import hanamaru.backend.lessons.{Standing, Units}
import hanamaru.backend.reference.{KanjiBook, SentenceLayout}
import hanamaru.backend.writing.{WritingAttempts, WritingReview}
import hanamaru.shared.characters.CharacterAsked
import hanamaru.shared.dictionary.DictionaryWord
import hanamaru.shared.kana.Romaji
import hanamaru.shared.kanji.*
import hanamaru.shared.learner.{Grade, ReviewOutcome}
import hanamaru.shared.writing.WritingAttempt

import java.time.Instant
import scala.util.Random

import com.github.plokhotnyuk.jsoniter_scala.core.writeToString

/** The kanji of the units, practised in lesson sessions, each in a word the
  * learner knows: read first, its reading typed; written once its reading has
  * passed its first steps. A kanji is new only once one of the units' words
  * written with it is stable: the word is known before its kanji.
  */
final class KanjiPractice(
    book: () => Option[KanjiBook],
    learner: LearnerStore,
    units: Units,
    attempts: WritingAttempts,
    writing: WritingReview
) {
  private val dueAtMost = 6
  private val newKanjiAtMost = 2
  private val newWritingsAtMost = 2

  // the units' words so far, those written with kanji as usually written
  private def wordsSoFar: List[DictionaryWord] =
    units.soFar.flatMap(units.wordsOf).distinctBy(_.id).filterNot(_.usuallyKana)

  // The word a kanji is asked in: one of the stable words written with it, at
  // random among those whose other kanji are the fewest not taught so far
  // (`taught`), then not met yet: 学 asked in 大学 or 大学生 shows a 大 of a
  // unit to come; in 学生, a 生 of the unit under way. Else a word of the
  // units written with it, chosen alike.
  private def wordFor(
      literal: String,
      words: List[DictionaryWord],
      now: Standing,
      taught: Set[String]
  ): Option[DictionaryWord] = {
    val withIt = words.filter(writtenWith(_, literal))
    def strangers(word: DictionaryWord) = {
      val others = Units
        .carriers(List(word))
        .flatMap(_.filter(SentenceLayout.isKanji).map(_.toString))
        .distinct
        .filter(kanji => kanji != literal && kanji != "々")
      (others.count(!taught(_)), others.count(!now.symbolMet(_)))
    }
    def best(among: List[DictionaryWord]) =
      Random.shuffle(among).sortBy(strangers).headOption
    best(withIt.filter(word => now.stable(word.id))).orElse(best(withIt))
  }

  // as the units assign kanji ([[Units.carriers]])
  private def writtenWith(word: DictionaryWord, literal: String): Boolean =
    Units.carriers(List(word)).exists(_.contains(literal))

  /** The kanji exercises of a session: the cards due, the most overdue first;
    * then the writing of kanji read well enough; then a few new kanji, read.
    * Each in a word written with it, one of the stable ones at random.
    */
  def exercises(at: Instant): Either[String, List[KanjiExercise]] =
    book().toRight("The kanji are not ready").map { kanjiBook =>
      val now = units.standing()
      val words = wordsSoFar
      val taught = units.soFar.flatMap(units.kanjiOf(_).getOrElse(Nil)).distinct
      val cards = learner
        .cards(ItemKind.Kanji)
        .map(card => (card.key, card.kind) -> card)
        .toMap
      def card(literal: String, kind: CardKind) = cards.get((literal, kind))
      def known(literal: String) = words
        .filter(word => writtenWith(word, literal) && now.stable(word.id))
      def exercise(literal: String, asked: CharacterAsked, isNew: Boolean) =
        for {
          kanji <- kanjiBook.kanji(literal)
          word <- wordFor(literal, words, now, taught.toSet)
        } yield KanjiExercise(kanji, word, asked, isNew)
      val due = taught
        .flatMap(literal =>
          List(
            CharacterAsked.Reading -> CardKind.Reading,
            CharacterAsked.Writing -> CardKind.Writing
          )
            .flatMap((asked, kind) =>
              card(literal, kind).map(each => (literal, asked, each.due))
            )
        )
        .filter((_, _, due) => due <= at.toEpochMilli)
        .sortBy((_, _, due) => due)
        .take(dueAtMost)
        .flatMap((literal, asked, _) => exercise(literal, asked, isNew = false))
      val toWrite = taught
        .filter(literal =>
          card(literal, CardKind.Reading).exists(_.passed) &&
            card(literal, CardKind.Writing).isEmpty
        )
        .take(newWritingsAtMost)
        .flatMap(exercise(_, CharacterAsked.Writing, isNew = true))
      // new kanji each in a word not read yet in the session: two in one
      // word (学生) would ask the same reading twice; the second waits for
      // another session
      val readIn = (due ++ toWrite)
        .filter(_.asked == CharacterAsked.Reading)
        .map(_.word.id)
      val fresh = taught
        .filter(literal =>
          card(literal, CardKind.Reading).isEmpty && known(literal).nonEmpty
        )
        .flatMap(exercise(_, CharacterAsked.Reading, isNew = true))
        .foldLeft(List.empty[KanjiExercise])((chosen, next) =>
          if ((readIn ++ chosen.map(_.word.id)).contains(next.word.id)) chosen
          else chosen :+ next
        )
        .take(newKanjiAtMost)
      due ++ toWrite ++ fresh
    }

  /** A unit's kanji gone over out of turn (the unit one of those so far): each
    * met read again, each read written from memory (its writing new when never
    * written), in a word written with it, in a random order. Kept as reviews,
    * as any; a kanji not met yet is only shown on the unit's page.
    */
  def ofUnit(point: String, at: Instant): Either[String, List[KanjiExercise]] =
    for {
      kanjiBook <- book().toRight("The kanji are not ready")
      unit <- units.soFar
        .find(_.id == point)
        .toRight(s"No unit $point so far")
    } yield {
      val now = units.standing()
      val words = wordsSoFar
      val taught =
        units.soFar.flatMap(units.kanjiOf(_).getOrElse(Nil)).toSet
      val cards = learner.cards(ItemKind.Kanji)
      val met = cards.map(_.key).toSet
      val written =
        cards.filter(_.kind == CardKind.Writing).map(_.key).toSet
      def exercise(literal: String, asked: CharacterAsked, isNew: Boolean) =
        for {
          kanji <- kanjiBook.kanji(literal)
          word <- wordFor(literal, words, now, taught)
        } yield KanjiExercise(kanji, word, asked, isNew)
      Random.shuffle(
        units
          .kanjiOf(unit)
          .getOrElse(Nil)
          .filter(met)
          .flatMap(literal =>
            exercise(literal, CharacterAsked.Reading, isNew = false) ++
              Option
                .when(now.symbolCardPassed(literal, CardKind.Reading))(
                  exercise(
                    literal,
                    CharacterAsked.Writing,
                    isNew = !written(literal)
                  )
                )
                .flatten
          )
      )
    }

  /** A kanji's reading in a word, judged (the word's reading, in either
    * script), and kept as a review of its reading.
    */
  def read(
      answer: KanjiReadingAnswer,
      at: Instant
  ): Either[String, KanjiReadingVerdict] =
    wordsSoFar
      .find(_.id == answer.word)
      .toRight(s"No word ${answer.word} in the units so far")
      .map { word =>
        val typed = Romaji.asHiragana(answer.typed.filterNot(_.isWhitespace))
        val right = word.readings.map(Romaji.asHiragana).contains(typed)
        learner.reviewed(
          ItemKind.Kanji,
          answer.kanji,
          CardKind.Reading,
          if (right) Grade.Good else Grade.Again,
          writeToString(answer),
          at
        )
        KanjiReadingVerdict(right, word.readings.headOption.getOrElse(""))
      }

  /** A kanji written: kept, and, written from memory, a review of its writing,
    * graded by its strokes as a kana's is.
    */
  def written(attempt: WritingAttempt, at: Instant): Option[ReviewOutcome] = {
    attempts.record(attempt)
    for {
      _ <- book().flatMap(_.kanji(attempt.character))
      grade <- writing.gradeOf(attempt)
    } yield {
      val memory = learner.reviewed(
        ItemKind.Kanji,
        attempt.character,
        CardKind.Writing,
        grade,
        writeToString(attempt),
        at
      )
      ReviewOutcome(grade, memory.due.toEpochMilli)
    }
  }
}

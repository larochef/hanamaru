package hanamaru.backend.kanji

import hanamaru.backend.characters.CharacterFocus
import hanamaru.backend.learner.*
import hanamaru.backend.lessons.{Standing, Units}
import hanamaru.backend.reference.{KanjiBook, SentenceLayout}
import hanamaru.backend.writing.{WritingAttempts, WritingReview}
import hanamaru.shared.characters.{AnswerKept, CharacterAsked}
import hanamaru.shared.dictionary.DictionaryWord
import hanamaru.shared.kana.Romaji
import hanamaru.shared.kanji.*
import hanamaru.shared.learner.{Grade, ReviewOutcome}
import hanamaru.shared.writing.WritingAttempt

import java.time.{Duration, Instant}
import scala.util.Random

import com.github.plokhotnyuk.jsoniter_scala.core.writeToString

/** The kanji of the units, practised in lesson sessions, each in a word the
  * learner knows: read first, its reading typed; written once its reading has
  * passed its first steps, or after four readings at most. A kanji is new only
  * once one of the units' words written with it is stable, or met long enough
  * ago ([[Waiting]]): the word is known before its kanji.
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

  // the readings after which a kanji is written all the same, its reading not
  // past its first steps yet: recognising it comes easier once written
  private val readingsBeforeWriting = 4

  // whether a kanji is read well enough to be written: its reading past its
  // first steps, or read `readingsBeforeWriting` times
  private def readEnough(reading: Option[ItemCard]): Boolean =
    reading.exists(card => card.passed || card.reviews >= readingsBeforeWriting)

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

  /** The new kanji of a session, each to be learned in a focus, a few at most:
    * those of the units so far never met, once a word written with them is
    * stable, or met long enough ago ([[Waiting]]): a word that does not settle
    * holds its kanji back no longer.
    */
  def toFocus(at: Instant): List[String] = {
    val now = units.standing()
    val words = wordsSoFar
    val met = learner.cards(ItemKind.Kanji).map(_.key).toSet
    val wordsMetSince = learner
      .cards(ItemKind.Word)
      .groupMapReduce(_.key)(_.firstReviewed)(_ min _)
    def known(word: DictionaryWord) = now.stable(word.id) ||
      wordsMetSince
        .get(word.id.toString)
        .exists(since =>
          Waiting.overdue(Duration.ofMillis(at.toEpochMilli - since))
        )
    units.soFar
      .flatMap(units.kanjiOf(_).getOrElse(Nil))
      .distinct
      .filter(literal =>
        !met(literal) && words
          .exists(word => writtenWith(word, literal) && known(word))
      )
      .take(newKanjiAtMost)
  }

  /** The cards of a focus on a kanji ([[CharacterFocus]]): read in a word and
    * written, in turn, four times, the first of each new; read in up to three
    * of the words written with it whose other kanji are the fewest not taught
    * so far (学 in 学生, not in 大学), those met first.
    */
  def focusCards(literal: String): Either[String, List[KanjiExercise]] =
    for {
      kanjiBook <- book().toRight("The kanji are not ready")
      kanji <- kanjiBook.kanji(literal).toRight(s"No kanji $literal")
      words <- Some(focusWords(literal))
        .filter(_.nonEmpty)
        .toRight(s"No word of the units is written with $literal")
    } yield (0 until CharacterFocus.rounds).toList.flatMap { round =>
      val word = words(round % words.size)
      List(
        KanjiExercise(kanji, word, CharacterAsked.Reading, round == 0),
        KanjiExercise(kanji, word, CharacterAsked.Writing, round == 0)
      )
    }

  // the words a focus reads a kanji in, the same each time it is asked
  private def focusWords(literal: String): List[DictionaryWord] = {
    val now = units.standing()
    val taught = units.soFar.flatMap(units.kanjiOf(_).getOrElse(Nil)).toSet
    def strangers(word: DictionaryWord) = Units
      .carriers(List(word))
      .flatMap(_.filter(SentenceLayout.isKanji).map(_.toString))
      .distinct
      .count(kanji => kanji != literal && kanji != "々" && !taught(kanji))
    val withIt = wordsSoFar.filter(writtenWith(_, literal))
    // none with a kanji not taught so far, when there are words without
    val fewest = withIt.map(strangers).minOption.getOrElse(0)
    withIt
      .filter(strangers(_) == fewest)
      .sortBy(word => (!now.wordMet(word.id), word.id))
      .take(3)
  }

  /** The kanji exercises of a session: the cards due, the most overdue first;
    * then the writing of kanji read well enough, met before focuses were. Each
    * in a word written with it, one of the stable ones at random. New kanji
    * come in focuses ([[toFocus]]).
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
          readEnough(card(literal, CardKind.Reading)) &&
            card(literal, CardKind.Writing).isEmpty
        )
        .take(newWritingsAtMost)
        .flatMap(exercise(_, CharacterAsked.Writing, isNew = true))
      due ++ toWrite
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
      val reading =
        cards.filter(_.kind == CardKind.Reading).map(card => card.key -> card)
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
                .when(readEnough(reading.toMap.get(literal)))(
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
    * script), and kept as a review of its reading: right when the word is, or
    * when the kanji's own part of it is, a slip elsewhere in the word aside
    * ([[KanjiReadings]]).
    */
  private def formsOf(literal: String): List[String] =
    book().flatMap(_.kanji(literal)).fold(Nil)(KanjiReadings.formsOf)

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
        val reading = word.readings.headOption.getOrElse("")
        val kanjiRead = Option
          .unless(right)(
            word.writings.headOption.flatMap(writing =>
              KanjiReadings
                .spanOf(writing, reading, answer.kanji, formsOf)
                .filter(KanjiReadings.partRight(reading, typed, _))
                .map((from, until) =>
                  Romaji.asHiragana(reading).substring(from, until)
                )
            )
          )
          .flatten
        // in a focus, judged only: the focus is the review, once over
        if (answer.kept == AnswerKept.AsReview) {
          learner.reviewed(
            ItemKind.Kanji,
            answer.kanji,
            CardKind.Reading,
            if (right || kanjiRead.nonEmpty) Grade.Good else Grade.Again,
            writeToString(answer),
            at
          )
        }
        KanjiReadingVerdict(right, reading, kanjiRead)
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

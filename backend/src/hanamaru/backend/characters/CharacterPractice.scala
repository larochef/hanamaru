package hanamaru.backend.characters

import hanamaru.backend.kanji.KanjiPractice
import hanamaru.backend.learner.*
import hanamaru.backend.lessons.Units
import hanamaru.backend.reference.Dictionary
import hanamaru.shared.characters.*
import hanamaru.shared.kana.*
import hanamaru.shared.learner.Grade
import hanamaru.shared.placement.ReadingResult

import java.time.{Instant, ZoneId}
import scala.util.Random

import com.github.plokhotnyuk.jsoniter_scala.core.writeToString

/** The symbols practised in lesson sessions, kana and kanji alike: those due,
  * then the unit's new ones. The first unit's symbols are the hiragana, the
  * second's the katakana: their kana come a few each session, in the chart's
  * order, each learned in a focus ([[CharacterFocus]]); any kana met whose
  * reading was never asked is read, and any whose writing was never asked is
  * written, as a new one is. Then the kanji ([[KanjiPractice]]), new ones in
  * focuses too. A session takes a few kana due, the most overdue first, never
  * kana before they are due: what is left waits for the next session, or an
  * Express session.
  */
final class CharacterPractice(
    learner: LearnerStore,
    units: Units,
    kanji: KanjiPractice,
    dictionary: () => Option[Dictionary]
) {
  import CharacterPractice.*

  /** A unit's kanji gone over out of turn, from its page
    * ([[KanjiPractice.ofUnit]]).
    */
  def ofUnit(
      point: String,
      at: Instant
  ): Either[String, List[CharacterExercise]] =
    kanji.ofUnit(point, at).map(_.map(CharacterExercise.Kanji(_)))

  /** A session's kana and kanji exercises. */
  def exercises(at: Instant): List[CharacterExercise] =
    kana(learner.cards(ItemKind.Kana), at.toEpochMilli) ++
      kanji.exercises(at).getOrElse(Nil).map(CharacterExercise.Kanji(_)) ++
      kanji.toFocus(at).map(CharacterExercise.Focus(_))

  // The cards of a focus on a kana: written (new: shown, written over its
  // model, then from memory) and read in turn; a katakana read in a word
  // every other time.
  private def kanaFocus(character: String): List[CharacterExercise] =
    (0 until CharacterFocus.rounds).toList.flatMap { round =>
      val read =
        CharacterExercise.Kana(character, CharacterAsked.Reading, false)
      List(
        CharacterExercise.Kana(character, CharacterAsked.Writing, round == 0),
        if (round % 2 == 1) inAWord(read) else read
      )
    }

  private def focusCards(
      character: String
  ): Either[String, List[CharacterExercise]] =
    if (Kana.find(character).nonEmpty) Right(kanaFocus(character))
    else kanji.focusCards(character).map(_.map(CharacterExercise.Kanji(_)))

  /** The next card of a focus ([[CharacterFocus]]), none once it is over. */
  def focusNext(focus: FocusState): Either[String, Option[CharacterExercise]] =
    focusCards(focus.character).map(CharacterFocus.next(_, focus.answers))

  /** A focus over: its character's reading and writing reviewed once each, as
    * their last card in it went, then due no sooner than the next day: the
    * focus was their first steps.
    */
  def focusDone(focus: FocusState, at: Instant): Either[String, Unit] =
    Either.cond(
      focus.answers.nonEmpty, {
        val kind =
          if (Kana.find(focus.character).nonEmpty) ItemKind.Kana
          else ItemKind.Kanji
        val tomorrow = at
          .atZone(ZoneId.systemDefault())
          .toLocalDate
          .plusDays(1)
          .atTime(4, 0)
          .atZone(ZoneId.systemDefault())
          .toInstant
        List(
          CardKind.Reading -> CharacterAsked.Reading,
          CardKind.Writing -> CharacterAsked.Writing
        ).foreach { (card, asked) =>
          learner.reviewed(
            kind,
            focus.character,
            card,
            CharacterFocus.lastOf(focus.answers, asked) match {
              case Some(CardResult.Missed) => Grade.Again
              case _                       => Grade.Good
            },
            writeToString(focus),
            at,
            ReviewSource.Focus
          )
          learner.postponed(kind, focus.character, card, tomorrow)
        }
      },
      s"No card answered in the focus on ${focus.character}"
    )

  private def kana(
      cards: List[ItemCard],
      now: Long
  ): List[CharacterExercise] = {
    val met = cards.map(_.key).toSet
    val read = cards.filter(_.kind == CardKind.Reading).map(_.key).toSet
    val written = cards.filter(_.kind == CardKind.Writing).map(_.key).toSet
    val due = cards
      .filter(_.due <= now)
      .sortBy(_.due)
      .map(card => CharacterExercise.Kana(card.key, askedBy(card.kind), false))
    // the kana of the unit under way's script, when it teaches kana
    val script =
      units.current.flatMap(standing => units.kanaScriptOf(standing.point))
    val ofScript =
      script.toList.flatMap(each => Kana.all.filter(_.script == each))
    val unread = ofScript
      .map(_.character)
      .filter(character => met(character) && !read(character))
      .map(CharacterExercise.Kana(_, CharacterAsked.Reading, false))
    // a kana only read so far (the placement test, a session without the
    // stylus) is written, over its model first, as a new one is: the unit
    // asks both
    val unwritten = ofScript
      .map(_.character)
      .filter(character => met(character) && !written(character))
    val fresh = ofScript
      .map(_.character)
      .filterNot(met)
    val toWrite = unwritten.take(newKanaAtMost)
    (due ++ unread).distinct.take(kanaDueAtMost).map(inAWord) ++
      toWrite.map(CharacterExercise.Kana(_, CharacterAsked.Writing, true)) ++
      fresh.take(kanaFocusesAtMost).map(CharacterExercise.Focus(_))
  }

  // A katakana's reading asked in a loanword written with it, one the learner
  // has met if any, else one of the most basic: katakana live in words, which
  // lessons in hiragana seldom give.
  private def inAWord(exercise: CharacterExercise): CharacterExercise =
    exercise match {
      case kana @ CharacterExercise.Kana(
            character,
            CharacterAsked.Reading,
            _,
            _
          ) if Kana.find(character).exists(_.script == Script.Katakana) =>
        val words = dictionary().toList
          .flatMap(_.inKatakana(character, wordsToChooseFrom))
        val met = learner.metKeys(ItemKind.Word)
        val (known, others) = words.partition(word => met(word.id.toString))
        kana.copy(inWord =
          Random
            .shuffle(known)
            .headOption
            .orElse(Random.shuffle(others).headOption)
        )
      case other => other
    }

  /** A kana's reading judged, alone or in a word (the word's reading, long
    * vowels written ー or not), and kept as a review of its reading.
    */
  def readKana(
      answer: KanaReadingAnswer,
      at: Instant
  ): Either[String, ReadingResult] =
    Kana
      .find(answer.character)
      .toRight(s"No kana ${answer.character}")
      .map { kana =>
        val word = answer.word
          .flatMap(id => dictionary().flatMap(_.words(List(id)).headOption))
        val right = word match {
          case Some(inWord) =>
            answer.typed.exists(typed =>
              inWord.readings.headOption
                .exists(reading => spoken(reading) == spoken(typed))
            )
          case None => answer.typed.exists(Kana.reads(kana, _))
        }
        // in a focus, judged only: the focus is the review, once over
        if (answer.kept == AnswerKept.AsReview) {
          learner.reviewed(
            ItemKind.Kana,
            kana.character,
            CardKind.Reading,
            if (right) Grade.Good else Grade.Again,
            writeToString(answer),
            at
          )
        }
        ReadingResult(
          kana.character,
          right,
          word.flatMap(_.readings.headOption).getOrElse(Kana.shownReading(kana))
        )
      }
}

object CharacterPractice {

  /** The most kana due a session takes: the rest wait. */
  val kanaDueAtMost = 10

  /** The new kana a session brings, while a unit teaches them. */
  val newKanaAtMost = 5

  /** New kana a session learns, each in a focus of about ten cards. */
  val kanaFocusesAtMost = 2

  // the loanwords a katakana may be read in, the most basic
  private val wordsToChooseFrom = 6

  /** A reading as said: typed in romaji or kana, in hiragana, each ー the vowel
    * it lengthens (コーヒー as こおひい), without spaces.
    */
  def spoken(text: String): String = {
    val kana = Romaji.asHiragana(
      Romaji.toKana(text.filterNot(_.isWhitespace), finished = true)
    )
    kana.zipWithIndex.map {
      case ('ー', index) if index > 0 =>
        Kana
          .find(kana(index - 1).toString)
          .flatMap(_.romaji.lastOption)
          .flatMap(vowel => vowels.get(vowel))
          .getOrElse('ー')
      case (character, _) => character
    }.mkString
  }

  private val vowels =
    Map('a' -> 'あ', 'i' -> 'い', 'u' -> 'う', 'e' -> 'え', 'o' -> 'お')

  private def askedBy(kind: CardKind): CharacterAsked = kind match {
    case CardKind.Writing => CharacterAsked.Writing
    case _                => CharacterAsked.Reading
  }
}

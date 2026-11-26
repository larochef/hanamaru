package hanamaru.backend.characters

import hanamaru.backend.kanji.KanjiPractice
import hanamaru.backend.learner.{CardKind, ItemKind, LearnerStore}
import hanamaru.backend.lessons.{Curriculum, Units}
import hanamaru.backend.reference.Dictionary
import hanamaru.backend.storage.{Database, Places}
import hanamaru.backend.writing.{WritingAttempts, WritingReview}
import hanamaru.shared.characters.*
import hanamaru.shared.kana.{Kana, Script}
import hanamaru.shared.learner.Grade

import java.nio.file.{Files, Path}
import java.time.{Duration, Instant}
import scala.jdk.CollectionConverters.*

import utest.*

/** The kana of lesson sessions, without the dictionary nor the kanji: the first
  * unit, whose symbols are the hiragana, is under way.
  */
object CharacterPracticeTests extends TestSuite {

  private val at = Instant.parse("2026-11-24T10:00:00Z")

  private def withPractice(test: (CharacterPractice, LearnerStore) => Unit) = {
    val directory = Files.createTempDirectory("hanamaru-characters")
    val learner = LearnerStore(Database.open(directory.resolve("learner.db")))
    val units = Units(Curriculum.points, () => None, learner, () => None)
    val kanji = KanjiPractice(
      () => None,
      learner,
      units,
      WritingAttempts(directory.resolve("attempts.jsonl")),
      WritingReview(learner)
    )
    test(CharacterPractice(learner, units, kanji, () => None), learner)
  }

  private val hiragana = Kana.all.filter(_.script == Script.Hiragana)

  // the dictionary where the application keeps it, once it has been started
  private lazy val dictionary: Option[Dictionary] = {
    val places = Places.fromEnvironment(
      System.getenv().asScala.toMap,
      Path.of(System.getProperty("user.home"))
    )
    Some(places.reference.resolve("dictionary.db"))
      .filter(Files.exists(_))
      .map(file => Dictionary(Database.connect(file)))
  }

  val tests: Tests = Tests {
    test(
      "the first unit brings its hiragana, a few at a time, in order, each in a focus"
    ) {
      withPractice { (practice, _) =>
        assert(
          practice.exercises(at) ==
            hiragana
              .take(CharacterPractice.kanaFocusesAtMost)
              .map(kana => CharacterExercise.Focus(kana.character))
        )
      }
    }
    test(
      "a focus: written and read in turn; once over, reviewed from the next day on"
    ) {
      withPractice { (practice, learner) =>
        val first = practice.focusNext(FocusState("あ", Nil)).toOption.flatten
        assert(
          first.contains(
            CharacterExercise.Kana("あ", CharacterAsked.Writing, true)
          )
        )
        // answered right to its end
        val answers = LazyList
          .iterate(Option(List.empty[FocusAnswer]))(
            _.flatMap(answers =>
              practice
                .focusNext(FocusState("あ", answers))
                .toOption
                .flatten
                .map(card => answers :+ FocusAnswer(card, CardResult.Right))
            )
          )
          .takeWhile(_.nonEmpty)
          .flatten
          .last
        assert(answers.size == 2 * CharacterFocus.rounds)
        // no review while it lasts
        assert(learner.cards(ItemKind.Kana).isEmpty)
        practice.focusDone(FocusState("あ", answers), at)
        val cards = learner.cards(ItemKind.Kana)
        assert(
          cards.map(_.kind).toSet == Set(CardKind.Reading, CardKind.Writing),
          cards.forall(card =>
            card.due >= at.plus(Duration.ofHours(12)).toEpochMilli &&
              !card.passed
          )
        )
        assert(!practice.exercises(at).contains(CharacterExercise.Focus("あ")))
      }
    }
    test("the kana due, a few at most, never before they are due") {
      withPractice { (practice, learner) =>
        // every kana met an hour ago, due now; the next ones not before days
        hiragana.foreach(kana =>
          learner.reviewed(
            ItemKind.Kana,
            kana.character,
            CardKind.Writing,
            Grade.Good,
            "",
            at.minusSeconds(3600)
          )
        )
        val exercises = practice.exercises(at)
        assert(exercises.size == CharacterPractice.kanaDueAtMost)
        // read once each, right: not due again within the hour
        hiragana.foreach(kana =>
          practice.readKana(
            KanaReadingAnswer(
              kana.character,
              Some(Kana.shownReading(kana).takeWhile(_ != ' '))
            ),
            at
          )
        )
        assert(
          practice
            .exercises(at.plusSeconds(60))
            .forall {
              case CharacterExercise.Kana(_, asked, _, _) =>
                asked == CharacterAsked.Writing
              case _ => false
            }
        )
      }
    }
    test("a reading as said: long vowels written ー or not, in either script") {
      assert(
        CharacterPractice.spoken("コーヒー") == CharacterPractice.spoken("こおひい"),
        CharacterPractice.spoken("ko-hi-") == CharacterPractice.spoken("コーヒー"),
        CharacterPractice.spoken("テレビ") == "てれび",
        CharacterPractice.spoken("テレビ") != CharacterPractice.spoken("てれべ")
      )
    }
    test("a katakana due read in a loanword, judged on the word's reading") {
      dictionary.foreach { words =>
        val directory = Files.createTempDirectory("hanamaru-characters")
        val learner =
          LearnerStore(Database.open(directory.resolve("learner.db")))
        val units =
          Units(Curriculum.points, () => Some(words), learner, () => None)
        val practice = CharacterPractice(
          learner,
          units,
          KanjiPractice(
            () => None,
            learner,
            units,
            WritingAttempts(directory.resolve("attempts.jsonl")),
            WritingReview(learner)
          ),
          () => Some(words)
        )
        learner.reviewed(
          ItemKind.Kana,
          "テ",
          CardKind.Reading,
          Grade.Good,
          "",
          at.minusSeconds(3600)
        )
        val word = practice.exercises(at).collectFirst {
          case CharacterExercise.Kana(
                "テ",
                CharacterAsked.Reading,
                _,
                Some(word)
              ) =>
            word
        }
        assert(word.exists(_.readings.head.contains("テ")))
        val reading = word.get.readings.head
        val right = practice
          .readKana(
            KanaReadingAnswer("テ", Some(reading), Some(word.get.id)),
            at
          )
          .toOption
          .get
        val wrong = practice
          .readKana(KanaReadingAnswer("テ", Some("x"), Some(word.get.id)), at)
          .toOption
          .get
        assert(right.correct, !wrong.correct, wrong.romaji == reading)
      }
    }
    test("a kana only read so far is written, as a new one is") {
      withPractice { (practice, learner) =>
        // the placement test read every hiragana, writing none
        hiragana.foreach(kana =>
          learner.reviewed(
            ItemKind.Kana,
            kana.character,
            CardKind.Reading,
            Grade.Easy,
            "",
            at.minusSeconds(3600)
          )
        )
        val writings = practice.exercises(at).collect {
          case CharacterExercise.Kana(
                character,
                CharacterAsked.Writing,
                true,
                _
              ) =>
            character
        }
        assert(
          writings == hiragana
            .take(CharacterPractice.newKanaAtMost)
            .map(_.character)
        )
        // already read: not read again as new
        assert(
          !practice.exercises(at).exists {
            case CharacterExercise.Kana(_, CharacterAsked.Reading, false, _) =>
              true
            case _ => false
          }
        )
      }
    }
    test("a kana's reading judged and kept") {
      withPractice { (practice, learner) =>
        val right = practice
          .readKana(KanaReadingAnswer("し", Some("shi")), at)
          .toOption
          .get
        val wrong = practice
          .readKana(KanaReadingAnswer("ち", Some("shi")), at)
          .toOption
          .get
        assert(
          right.correct,
          !wrong.correct,
          wrong.romaji == Kana.shownReading(Kana.find("ち").get)
        )
        assert(learner.memory(ItemKind.Kana, "し", CardKind.Reading).isDefined)
      }
    }
  }
}

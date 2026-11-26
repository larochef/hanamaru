package hanamaru.backend.kanji

import hanamaru.backend.characters.CharacterFocus
import hanamaru.backend.learner.{CardKind, ItemKind, LearnerStore}
import hanamaru.backend.lessons.{Curriculum, Units}
import hanamaru.backend.reference.{Dictionary, KanjiBook}
import hanamaru.backend.storage.{Database, Places}
import hanamaru.backend.writing.{WritingAttempts, WritingReview}
import hanamaru.shared.characters.CharacterAsked
import hanamaru.shared.dictionary.DictionaryWord
import hanamaru.shared.kana.Kana
import hanamaru.shared.kanji.KanjiReadingAnswer
import hanamaru.shared.learner.Grade

import java.nio.file.{Files, Path}
import java.time.Instant
import scala.jdk.CollectionConverters.*

import utest.*

/** The kanji practised: needs the dictionary and the kanji in the cache. */
object KanjiPracticeTests extends TestSuite {

  private val places = Places.fromEnvironment(
    System.getenv().asScala.toMap,
    Path.of(System.getProperty("user.home"))
  )

  private def cached(name: String) =
    Some(places.reference.resolve(name)).filter(Files.exists(_))

  private val at = Instant.parse("2026-11-24T10:00:00Z")

  // the first unit with kanji under way, the units before acquired
  private def underWay(
      test: (KanjiPractice, LearnerStore, DictionaryWord) => Unit
  ): Unit =
    (cached("dictionary.db"), cached("kanji.db")) match {
      case (Some(dictionaryFile), Some(kanjiFile)) =>
        val directory = Files.createTempDirectory("hanamaru-kanji")
        val learner =
          LearnerStore(Database.open(directory.resolve("learner.db")))
        val dictionary = Dictionary(Database.connect(dictionaryFile))
        val book = KanjiBook(Database.connect(kanjiFile))
        val units = Units(
          Curriculum.points,
          () => Some(dictionary),
          learner,
          () => Some(literal => book.kanji(literal).flatMap(_.grade))
        )
        val practice = KanjiPractice(
          () => Some(book),
          learner,
          units,
          WritingAttempts(directory.resolve("attempts.jsonl")),
          WritingReview(learner)
        )
        def reviewed(kind: ItemKind, key: String, card: CardKind) =
          learner.reviewed(kind, key, card, Grade.Easy, "", at)
        for {
          kana <- Kana.all
          card <- List(CardKind.Reading, CardKind.Writing)
        } {
          reviewed(ItemKind.Kana, kana.character, card)
        }
        Curriculum.points.take(Units.firstUnitWithKanji).foreach { point =>
          units
            .wordsOf(point)
            .foreach(word =>
              reviewed(ItemKind.Word, word.id.toString, CardKind.Production)
            )
          reviewed(ItemKind.Grammar, point.id, CardKind.Usage)
        }
        units.moveOn(at)
        val student = units
          .wordsOf(Curriculum.points.head)
          .find(_.writings.headOption.contains("学生"))
          .get
        test(practice, learner, student)
      case _ =>
        println("Kanji practice not checked: the reference is not in the cache")
    }

  private def writing(practice: KanjiPractice, literal: String) =
    practice
      .exercises(at)
      .toOption
      .get
      .exists(exercise =>
        exercise.kanji.literal == literal &&
          exercise.asked == CharacterAsked.Writing
      )

  val tests: Tests = Tests {
    test("a kanji not read right is written all the same after four readings") {
      underWay { (practice, learner, _) =>
        def missed() = learner.reviewed(
          ItemKind.Kanji,
          "学",
          CardKind.Reading,
          Grade.Again,
          "",
          at
        )
        (1 to 3).foreach(_ => missed())
        assert(!writing(practice, "学"))
        missed()
        assert(writing(practice, "学"))
      }
    }
    test(
      "a new kanji comes in a focus, in words known; one read before focuses is written once read"
    ) {
      underWay { (practice, learner, student) =>
        // no kanji exercise of its own: the new ones come in focuses
        assert(practice.exercises(at).toOption.get.isEmpty)
        val toFocus = practice.toFocus(at)
        assert(toFocus.size == 2, toFocus.head == "学")
        val cards = practice.focusCards("学").toOption.get
        assert(
          cards.map(_.asked) == List
            .fill(CharacterFocus.rounds)(
              List(CharacterAsked.Reading, CharacterAsked.Writing)
            )
            .flatten,
          cards.take(2).forall(_.isNew),
          cards.drop(2).forall(!_.isNew),
          // in words whose other kanji are taught so far: 学 in 学生 (生, of
          // the same unit), never in 大学 or 大学生 (大, of a unit to come)
          cards.forall(!_.word.writings.headOption.exists(_.contains("大")))
        )
        // a kanji met before focuses were: read, then written once read
        val verdict = practice
          .read(KanjiReadingAnswer("学", student.id, "がく せい"), at)
          .toOption
          .get
        assert(verdict.right, !practice.toFocus(at).contains("学"))
        assert(!writing(practice, "学"))
        learner.reviewed(
          ItemKind.Kanji,
          "学",
          CardKind.Reading,
          Grade.Easy,
          "",
          at
        )
        assert(
          practice
            .exercises(at)
            .toOption
            .get
            .exists(exercise =>
              exercise.kanji.literal == "学" &&
                exercise.asked == CharacterAsked.Writing && exercise.isNew
            )
        )
      }
    }
  }
}

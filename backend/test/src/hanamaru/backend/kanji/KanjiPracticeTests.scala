package hanamaru.backend.kanji

import hanamaru.backend.learner.{CardKind, ItemKind, LearnerStore}
import hanamaru.backend.lessons.{Curriculum, Units}
import hanamaru.backend.reference.{Dictionary, KanjiBook}
import hanamaru.backend.storage.{Database, Places}
import hanamaru.backend.writing.{WritingAttempts, WritingReview}
import hanamaru.shared.characters.CharacterAsked
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

  val tests: Tests = Tests {
    test("a kanji read once its word is known, written once read") {
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
          // no kanji in the first units
          assert(practice.exercises(at).toOption.get.isEmpty)
          def reviewed(kind: ItemKind, key: String, card: CardKind) =
            learner.reviewed(kind, key, card, Grade.Easy, "", at)
          // the units before the first with kanji acquired, their kana too
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
          val fresh = practice.exercises(at).toOption.get
          // each in a word whose other kanji are taught so far: 学 in 学生
          // (生, of the same unit), never in 大学 or 大学生 (大, of a unit to
          // come), whatever the draw
          assert(
            (1 to 20).forall(_ =>
              practice
                .exercises(at)
                .toOption
                .get
                .filter(_.kanji.literal == "学")
                .forall(!_.word.writings.headOption.exists(_.contains("大")))
            )
          )
          // two new kanji, read in words of their own (学 and 生 both in 学生
          // would ask the same reading twice)
          assert(
            fresh.size == 2,
            fresh.head.kanji.literal == "学",
            fresh.map(_.word.id).distinct.size == 2,
            fresh.forall(exercise =>
              exercise.isNew && exercise.asked == CharacterAsked.Reading
            )
          )
          val verdict = practice
            .read(KanjiReadingAnswer("学", student.id, "がく せい"), at)
            .toOption
            .get
          assert(verdict.right)
          // read once: no more new, and its writing not yet
          assert(
            !practice.exercises(at).toOption.get.exists(_.kanji.literal == "学")
          )
          reviewed(ItemKind.Kanji, "学", CardKind.Reading)
          val toWrite = practice.exercises(at).toOption.get
          assert(
            toWrite.exists(exercise =>
              exercise.kanji.literal == "学" &&
                exercise.asked == CharacterAsked.Writing && exercise.isNew
            )
          )
        case _ =>
          println(
            "Kanji practice not checked: the reference is not in the cache"
          )
      }
    }
  }
}

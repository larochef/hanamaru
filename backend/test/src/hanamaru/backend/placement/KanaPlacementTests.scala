package hanamaru.backend.placement

import hanamaru.backend.learner.LearnerStore
import hanamaru.backend.storage.Database
import hanamaru.backend.writing.WritingAttempts
import hanamaru.shared.kana.{Kana, Script}
import hanamaru.shared.learner.ItemStatus
import hanamaru.shared.placement.*
import hanamaru.shared.writing.*

import java.nio.file.Files
import java.time.Instant

import utest.*

object KanaPlacementTests extends TestSuite {
  private val start = Instant.parse("2026-01-01T09:00:00Z")

  private def withTest[A](test: (KanaPlacementTest, LearnerStore) => A): A = {
    val directory = Files.createTempDirectory("hanamaru-placement")
    val database = Database.open(directory.resolve("learner.db"))
    val learner = LearnerStore(database)
    try {
      test(
        KanaPlacementTest(
          PlacementStore(database),
          learner,
          WritingAttempts(directory.resolve("attempts.jsonl"))
        ),
        learner
      )
    } finally database.close()
  }

  private def written(character: String, right: Boolean) = WritingAttempt(
    character,
    WritingStep.FromMemory,
    List(
      JudgedStroke(
        List(DrawnPoint(1, 1, 0.5, 0)),
        0,
        StrokeJudgement(
          if (right) StrokeVerdict.Accepted else StrokeVerdict.Misdrawn,
          1
        )
      )
    ),
    completed = right
  )

  /** Takes the whole test as a learner who reads and writes `known`, and
    * nothing else; every question asked.
    */
  private def taken(
      test: KanaPlacementTest,
      known: Set[String]
  ): List[PlacementQuestion] = {
    var state = test.started(start)
    var asked = List.empty[PlacementQuestion]
    while (state.question.isDefined) {
      val question = state.question.get
      asked :+= question
      val answer = question match {
        case PlacementQuestion.Reading(character) =>
          val typed =
            Option.when(known(character))(Kana.find(character).get.romaji)
          PlacementAnswer.Reading(character, typed, 1000)
        case PlacementQuestion.Writing(character) =>
          PlacementAnswer.Writing(written(character, known(character)))
      }
      state = test.answered(answer, start).toOption.get
    }
    asked
  }

  private def hiragana =
    Kana.all.filter(_.script == Script.Hiragana).map(_.character)

  val tests: Tests = Tests {
    test("readings: Hepburn and its variants, any case, small kana as typed") {
      val shi = Kana.find("し").get
      assert(
        Kana.reads(shi, "shi"),
        Kana.reads(shi, " SI "),
        !Kana.reads(shi, "chi")
      )
      val n = Kana.find("ン").get
      assert(Kana.reads(n, "n"), Kana.reads(n, "nn"))
      val small = Kana.find("ょ").get
      assert(
        Kana.reads(small, "yo"),
        Kana.reads(small, "xyo"),
        Kana.reads(small, "lyo")
      )
    }
    test("the kana do not come in the chart's order") {
      withTest { (test, _) =>
        val asked = taken(test, Set.empty).take(15).collect {
          case PlacementQuestion.Reading(character) => character
        }
        val chartOrder =
          Kana.rows(Script.Hiragana).take(3).flatten.map(_.character)
        assert(asked.toSet == chartOrder.toSet, asked != chartOrder)
        // nor in the same order from one script to the other
        val katakana = taken(test, Set.empty).drop(15).collect {
          case PlacementQuestion.Reading(character) =>
            Kana
              .rows(Script.Katakana)
              .flatten
              .indexWhere(_.character == character)
        }
        val hiraganaPlaces = asked.map(character =>
          Kana
            .rows(Script.Hiragana)
            .flatten
            .indexWhere(_.character == character)
        )
        assert(katakana != hiraganaPlaces)
      }
    }
    test("a beginner answers a block of three rows of each script") {
      withTest { (test, _) =>
        val asked = taken(test, Set.empty)
        assert(asked.size == 30)
        assert(asked.forall(_.isInstanceOf[PlacementQuestion.Reading]))
        val results = test.state().scripts
        assert(results.map(_.startAt) == List(Some("あ"), Some("ア")))
        assert(test.state().finished)
      }
    }
    test("knowing all hiragana: every one read, a kana written per row") {
      withTest { (test, learner) =>
        val asked = taken(test, hiragana.toSet)
        val readings = asked.collect {
          case PlacementQuestion.Reading(character) =>
            character
        }
        val writings = asked.collect {
          case PlacementQuestion.Writing(character) =>
            character
        }
        assert(readings.take(75).toSet == hiragana.toSet)
        assert(writings.size == Kana.rows(Script.Hiragana).size)
        val hiraganaResult = test.state().scripts.head
        assert(hiraganaResult.read == 75, hiraganaResult.written == 17)
        assert(hiraganaResult.startAt.isEmpty)
        // every hiragana read and written right: to confirm, writing included
        val progress = learner
          .kanaProgress(0)
          .map(each => each.character -> each.status)
          .toMap
        assert(hiragana.forall(progress.get(_).contains(ItemStatus.ToConfirm)))
      }
    }
    test("a gap in a row: the row is where to start, and it is to learn") {
      withTest { (test, learner) =>
        taken(test, hiragana.toSet - "ぬ")
        assert(test.state().scripts.head.startAt.contains("な"))
        val progress = learner
          .kanaProgress(0)
          .map(each => each.character -> each.status)
          .toMap
        assert(progress.get("ぬ").contains(ItemStatus.Learning))
        assert(progress.get("な").contains(ItemStatus.ToConfirm))
      }
    }
    test("a script given up is not asked") {
      withTest { (test, _) =>
        test.started(start)
        val state = test
          .answered(PlacementAnswer.SkipScript(Script.Hiragana), start)
          .toOption
          .get
        val asked = state.question.collect {
          case PlacementQuestion.Reading(character) => character
        }
        assert(asked.flatMap(Kana.find).exists(_.script == Script.Katakana))
        assert(state.scripts.head.skipped)
      }
    }
    test("an answer to another question is refused") {
      withTest { (test, _) =>
        val first = test.started(start).question
        val other =
          if (first.contains(PlacementQuestion.Reading("い"))) "う" else "い"
        assert(
          test
            .answered(PlacementAnswer.Reading(other, Some("x"), 1), start)
            .isLeft
        )
        assert(
          test
            .answered(PlacementAnswer.SkipScript(Script.Katakana), start)
            .isLeft
        )
      }
    }
    test("interrupted, then resumed where it was left") {
      withTest { (test, _) =>
        val first = test
          .started(start)
          .question
          .collect { case PlacementQuestion.Reading(character) =>
            Kana.find(character).get
          }
          .get
        test.answered(
          PlacementAnswer.Reading(first.character, Some(first.romaji), 1),
          start
        )
        val resumed = test.state()
        assert(
          resumed.question.exists(
            _ != PlacementQuestion.Reading(first.character)
          )
        )
        assert(
          resumed.lastReading
            .contains(ReadingResult(first.character, true, first.romaji))
        )
      }
    }
  }
}

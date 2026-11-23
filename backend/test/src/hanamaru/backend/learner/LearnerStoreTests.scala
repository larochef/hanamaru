package hanamaru.backend.learner

import hanamaru.backend.storage.Database
import hanamaru.backend.writing.WritingReview
import hanamaru.shared.learner.{Grade, ItemStatus}
import hanamaru.shared.writing.*

import java.nio.file.Files
import java.time.{Duration, Instant}

import utest.*

object LearnerStoreTests extends TestSuite {
  private val start = Instant.parse("2026-01-01T09:00:00Z")

  private def withStore[A](test: (Database, LearnerStore) => A): A = {
    val directory = Files.createTempDirectory("hanamaru-learner")
    val database = Database.open(directory.resolve("learner.db"))
    try test(database, LearnerStore(database))
    finally database.close()
  }

  private def stroke(verdict: StrokeVerdict) =
    JudgedStroke(List(DrawnPoint(1, 1, 0.5, 0)), 0, StrokeJudgement(verdict, 1))

  private def attempt(
      step: WritingStep,
      completed: Boolean,
      verdicts: StrokeVerdict*
  ) =
    WritingAttempt("あ", step, verdicts.toList.map(stroke), completed)

  val tests: Tests = Tests {
    test("reviews are kept, and move the card's memory on") {
      withStore { (database, store) =>
        val first =
          store.reviewed(
            ItemKind.Kana,
            "あ",
            CardKind.Writing,
            Grade.Good,
            "{}",
            start
          )
        val second = store.reviewed(
          ItemKind.Kana,
          "あ",
          CardKind.Writing,
          Grade.Good,
          "{}",
          first.due
        )
        assert(
          second == Fsrs.standard.reviewed(Some(first), Grade.Good, first.due)
        )
        val counts = database.read(session =>
          (
            session.queryOne("SELECT count(*) AS n FROM item")(_.int("n")),
            session.queryOne("SELECT count(*) AS n FROM card")(_.int("n")),
            session.queryOne("SELECT count(*) AS n FROM review")(_.int("n"))
          )
        )
        assert(counts == (Some(1), Some(1), Some(2)))
      }
    }
    test("a kana is learning until its memory is stable for weeks") {
      withStore { (_, store) =>
        var memory =
          store.reviewed(
            ItemKind.Kana,
            "あ",
            CardKind.Writing,
            Grade.Good,
            "{}",
            start
          )
        val progress = store.kanaProgress()
        assert(progress.map(_.status) == List(ItemStatus.Learning))
        assert(progress.head.due.contains(memory.due.toEpochMilli))
        while (memory.stability < 21) {
          memory = store.reviewed(
            ItemKind.Kana,
            "あ",
            CardKind.Writing,
            Grade.Good,
            "{}",
            memory.due
          )
        }
        assert(store.kanaProgress().map(_.status) == List(ItemStatus.Known))
      }
    }
    test(
      "only what is written from memory is a review, graded by its strokes"
    ) {
      withStore { (_, store) =>
        val review = WritingReview(store)
        val accepted = StrokeVerdict.Accepted
        assert(
          review
            .reviewed(attempt(WritingStep.Guided, true, accepted), start)
            .isEmpty
        )
        // a character left with strokes missing is written wrong
        assert(
          review
            .reviewed(attempt(WritingStep.FromMemory, false, accepted), start)
            .map(_.grade)
            .contains(Grade.Again)
        )
        val good =
          review.reviewed(
            attempt(WritingStep.FromMemory, true, accepted),
            start
          )
        assert(good.map(_.grade).contains(Grade.Good))
        assert(
          good
            .map(_.due)
            .contains(start.plus(Duration.ofMinutes(10)).toEpochMilli)
        )
        val again = review.reviewed(
          attempt(
            WritingStep.FromMemory,
            false,
            accepted,
            StrokeVerdict.WrongDirection
          ),
          start.plusSeconds(1)
        )
        assert(again.map(_.grade).contains(Grade.Again))
      }
    }
  }
}

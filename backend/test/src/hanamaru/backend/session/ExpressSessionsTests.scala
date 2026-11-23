package hanamaru.backend.session

import hanamaru.backend.learner.LearnerStore
import hanamaru.backend.storage.Database
import hanamaru.backend.writing.{WritingAttempts, WritingReview}
import hanamaru.shared.session.*
import hanamaru.shared.writing.*

import java.nio.file.Files
import java.time.{Duration, Instant}

import utest.*

object ExpressSessionsTests extends TestSuite {
  private val start = Instant.parse("2026-01-01T19:00:00Z")

  private def withSessions[A](test: ExpressSessions => A): A = {
    val directory = Files.createTempDirectory("hanamaru-session")
    val database = Database.open(directory.resolve("learner.db"))
    val learner = LearnerStore(database)
    try {
      test(
        ExpressSessions(
          SessionStore(database),
          learner,
          WritingReview(learner),
          WritingAttempts(directory.resolve("attempts.jsonl"))
        )
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

  val tests: Tests = Tests {
    test(
      "a first session: new kana, then their reading, a missed one back soon"
    ) {
      withSessions { sessions =>
        var state =
          sessions.started(SessionStart(10, stylus = true), start).toOption.get
        assert(state.exercise.contains(SessionExercise.NewKana("あ", true)))
        var at = start.plusSeconds(90)
        state = sessions
          .answered(state.id, SessionAnswer.Writing(written("あ", true)), at)
          .toOption
          .get
        // あ is now met: its reading comes before another new kana
        assert(state.exercise.contains(SessionExercise.Reading("あ")))
        at = at.plusSeconds(10)
        state = sessions
          .answered(state.id, SessionAnswer.Reading("あ", Some("o"), 3000), at)
          .toOption
          .get
        assert(state.lastReading.exists(!_.correct))
        assert(state.exercise.contains(SessionExercise.NewKana("い", true)))
        // the reading missed is due again a minute later, before anything new
        at = at.plusSeconds(90)
        state = sessions
          .answered(state.id, SessionAnswer.Writing(written("い", true)), at)
          .toOption
          .get
        assert(state.exercise.contains(SessionExercise.Reading("あ")))
        assert(state.summary == SessionSummary(3, 2, List("あ", "い")))
      }
    }
    test("an answer to another exercise is refused") {
      withSessions { sessions =>
        val state =
          sessions.started(SessionStart(10, stylus = true), start).toOption.get
        assert(
          sessions
            .answered(state.id, SessionAnswer.Reading("あ", Some("a"), 1), start)
            .isLeft
        )
      }
    }
    test("over when the time is up, or when the learner stops") {
      withSessions { sessions =>
        val state =
          sessions.started(SessionStart(5, stylus = true), start).toOption.get
        val late = start.plus(Duration.ofMinutes(6))
        val over = sessions
          .answered(state.id, SessionAnswer.Writing(written("あ", true)), late)
          .toOption
          .get
        assert(over.exercise.isEmpty)
        assert(
          sessions
            .answered(state.id, SessionAnswer.Writing(written("い", true)), late)
            .isLeft
        )
        val other =
          sessions.started(SessionStart(10, stylus = false), start).toOption.get
        val stopped =
          sessions.ended(other.id, start.plusSeconds(30)).toOption.get
        assert(stopped.exercise.isEmpty)
      }
    }
    test("a session lasts from 5 to 60 minutes") {
      withSessions { sessions =>
        assert(sessions.started(SessionStart(2, stylus = true), start).isLeft)
        assert(sessions.started(SessionStart(90, stylus = true), start).isLeft)
      }
    }
  }
}

package hanamaru.backend.vocabulary

import hanamaru.backend.lessons.Units
import hanamaru.backend.learner.{ItemKind, LearnerStore}
import hanamaru.backend.models.*
import hanamaru.backend.reference.SampleJmdict
import hanamaru.backend.storage.Database
import hanamaru.backend.writing.{WritingAttempts, WritingReview}
import hanamaru.shared.learner.Grade
import hanamaru.shared.vocabulary.*

import java.net.http.HttpClient
import java.nio.file.{Files, Path}
import java.time.{Duration, Instant}

import utest.*

object VocabularyPracticeTests extends TestSuite {
  private val start = Instant.parse("2026-01-01T19:00:00Z")

  /** A practice over the dictionary already imported in `reference`, with a
    * tutor that is not set up.
    */
  private def withPractice[A](
      test: (VocabularyPractice, LearnerStore) => A
  ): A = {
    val directory = Files.createTempDirectory("hanamaru-vocabulary")
    val database = Database.open(directory.resolve("learner.db"))
    val learner = LearnerStore(database)
    val http = HttpClient.newHttpClient()
    val dictionary = SampleJmdict.dictionaryIn(directory)
    val tutor = LlamaServer(
      LocalSettings(Map.empty),
      Path.of("/none"),
      HuggingFaceCache(directory),
      directory,
      http
    )
    try
      test(
        VocabularyPractice(
          () => Some(dictionary),
          learner,
          MeaningJudge(LlamaClient(tutor, http)),
          WritingAttempts(directory.resolve("attempts.jsonl")),
          WritingReview(learner),
          SentenceWriter(LlamaClient(tutor, http), () => None),
          Units(Nil, () => Some(dictionary), learner, () => Some(_ => None))
        ),
        learner
      )
    finally database.close()
  }

  val tests: Tests = Tests {
    test("a first series: new words in the order they are learned") {
      withPractice { (practice, _) =>
        val series = practice.series(start).toOption.get
        assert(series.map(_.word.id) == List(2000002L, 2000001L, 2000003L))
        assert(series.forall(_.isNew))
      }
    }
    test(
      "answers judged and kept as reviews; the words met come back when due"
    ) {
      withPractice { (practice, learner) =>
        def answer(
            direction: Direction,
            typed: String,
            known: Boolean = false
        ) =
          practice
            .answered(
              VocabularyAnswer(2000002L, direction, typed, 1000, known),
              start
            )
            .toOption
            .get
        assert(
          answer(Direction.Production, "neko") == VocabularyVerdict(
            Verdict.Right,
            Some(Grade.Good),
            None
          )
        )
        assert(answer(Direction.Meaning, "Chat").verdict == Verdict.Right)
        // not found among the meanings, no tutor: the learner is to say
        val unsure = answer(Direction.Meaning, "félin")
        assert(unsure == VocabularyVerdict(Verdict.Unsure, None, None))
        practice.selfGraded(
          SelfGrade(2000002L, Direction.Meaning, "félin", right = true),
          start
        )
        assert(learner.metKeys(ItemKind.Word) == Set("2000002"))
        // the word met is not new any more; due again, it comes first
        val later =
          practice.series(start.plus(Duration.ofDays(30))).toOption.get
        assert(
          later.head == later.head.copy(isNew = false),
          later.head.word.id == 2000002L
        )
      }
    }
    test("a new word said to be known and answered right is to confirm") {
      withPractice { (practice, learner) =>
        val verdict = practice
          .answered(
            VocabularyAnswer(
              2000001L,
              Direction.Production,
              "kirei",
              900,
              true
            ),
            start
          )
          .toOption
          .get
        assert(verdict.grade.contains(Grade.Easy))
        assert(learner.metKeys(ItemKind.Word) == Set("2000001"))
        assert(learner.dueKeys(ItemKind.Word, start.toEpochMilli, 10).isEmpty)
      }
    }
  }
}

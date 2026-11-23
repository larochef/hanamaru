package hanamaru.backend.lessons

import hanamaru.backend.models.*
import hanamaru.shared.lessons.*
import hanamaru.shared.text.SentencePart

import java.net.http.HttpClient
import java.nio.file.{Files, Path}

import utest.*

object ExerciseJudgeTests extends TestSuite {

  // a tutor never set up: what the code cannot judge stays unjudged
  private val judge = {
    val directory = Files.createTempDirectory("hanamaru-exercises")
    val http = HttpClient.newHttpClient()
    val tutor = LlamaServer(
      LocalSettings(Map.empty),
      Path.of("/none"),
      HuggingFaceCache(directory),
      directory,
      http
    )
    ExerciseJudge(LlamaClient(tutor, http), () => None)
  }

  private def exercise(kind: ExerciseKind, expected: String) = LessonExercise(
    "wa",
    kind,
    List(
      SentencePart("わたし", false, false),
      SentencePart("は", true, false),
      SentencePart("がくせいです。", false, true)
    ),
    "私は学生です。",
    "Je suis étudiant.",
    List("は", "も", "の", "か"),
    expected
  )

  private val particle = exercise(ExerciseKind.Particle, "は")
  private val translation = exercise(ExerciseKind.Translation, "わたしはがくせいです。")

  val tests: Tests = Tests {
    test("the particle expected is right, by the code") {
      assert(
        judge.judged(ExerciseAnswer(particle, "は")) == ExerciseVerdict(
          right = true,
          byTutor = false,
          None,
          None
        )
      )
    }
    test("another particle, with no tutor to judge it, is not right") {
      val verdict = judge.judged(ExerciseAnswer(particle, "も"))
      assert(!verdict.right, !verdict.byTutor)
    }
    test(
      "a translation as expected, whatever its spaces, punctuation and script"
    ) {
      assert(judge.judged(ExerciseAnswer(translation, "わたしは がくせいです")).right)
      assert(judge.judged(ExerciseAnswer(translation, "ワタシハ ガクセイデス。")).right)
    }
    test("は typed as it sounds is right, and said so") {
      val verdict = judge.judged(ExerciseAnswer(translation, "わたしわがくせいです"))
      assert(verdict.right, verdict.explanation.exists(_.contains("は")))
    }
    test("nothing typed is not right, and the tutor is not asked") {
      assert(
        judge.judged(ExerciseAnswer(translation, "")) == ExerciseVerdict(
          right = false,
          byTutor = false,
          None,
          None
        )
      )
    }
  }
}

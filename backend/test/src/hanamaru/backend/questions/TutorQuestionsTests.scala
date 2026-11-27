package hanamaru.backend.questions

import hanamaru.backend.learner.LearnerStore
import hanamaru.backend.lessons.{Curriculum, Units}
import hanamaru.backend.models.*
import hanamaru.backend.storage.Database
import hanamaru.shared.questions.*

import java.net.http.HttpClient
import java.nio.file.{Files, Path}
import java.time.Instant

import utest.*

object TutorQuestionsTests extends TestSuite {

  // a tutor never set up: every question is kept, unanswered
  private def questions(): TutorQuestions = {
    val directory = Files.createTempDirectory("hanamaru-questions")
    val http = HttpClient.newHttpClient()
    val tutor = LlamaServer(
      LocalSettings(Map.empty),
      Path.of("/none"),
      HuggingFaceCache(directory),
      directory,
      http
    )
    val database = Database.open(directory.resolve("learner.db"))
    TutorQuestions(
      database,
      LlamaClient(tutor, http),
      () => None,
      () => None,
      () => None,
      Units(Curriculum.points, () => None, LearnerStore(database), () => None),
      "none"
    )
  }

  val tests: Tests = Tests {
    test("a question kept, the tutor away, and one following it") {
      val kept = questions()
      val context = QuestionContext(
        AskedFrom.Correction,
        Some("no"),
        Some("私の本です。"),
        Some("C'est mon livre.")
      )
      val first = kept.asked(
        TutorQuestion(context, "Pourquoi の ici ?"),
        Instant.ofEpochMilli(1000)
      )
      val second = kept.asked(
        TutorQuestion(context, "Et sans の ?", Some(first.id)),
        Instant.ofEpochMilli(2000)
      )
      assert(
        first.answer.isEmpty,
        second.follows.contains(first.id),
        kept.all == List(second, first)
      )
    }
  }
}

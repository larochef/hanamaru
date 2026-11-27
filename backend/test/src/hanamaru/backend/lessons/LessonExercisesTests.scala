package hanamaru.backend.lessons

import hanamaru.backend.learner.LearnerStore
import hanamaru.backend.models.*
import hanamaru.backend.reference.{Analyser, Dictionary}
import hanamaru.backend.storage.{Database, Places}
import hanamaru.shared.lessons.*

import java.net.http.HttpClient
import java.nio.file.{Files, Path}
import java.time.Instant
import scala.jdk.CollectionConverters.*

import utest.*

/** The exercises made from the lessons' examples (no tutor: no reading text).
  * Needs the analyser's and the dictionary's data in the cache.
  */
object LessonExercisesTests extends TestSuite {

  private val places = Places.fromEnvironment(
    System.getenv().asScala.toMap,
    Path.of(System.getProperty("user.home"))
  )

  private lazy val lessons: Option[Exercises] = for {
    analyserData <- Some(Analyser.dictionaryIn(places.reference))
      .filter(Files.exists(_))
    dictionaryFile <- Some(places.reference.resolve("dictionary.db"))
      .filter(Files.exists(_))
  } yield {
    val directory = Files.createTempDirectory("hanamaru-lessons")
    val http = HttpClient.newHttpClient()
    val tutor = LlamaServer(
      LocalSettings(Map.empty),
      Path.of("/none"),
      HuggingFaceCache(directory),
      directory,
      http
    )
    val analyser = Analyser(analyserData)
    val dictionary = Dictionary(Database.connect(dictionaryFile))
    val database = Database.open(directory.resolve("learner.db"))
    val learner = LearnerStore(database)
    Exercises(
      Lessons(
        () => Some(analyser),
        () => Some(dictionary),
        learner,
        TextWriter(LlamaClient(tutor, http), Refusals(database, "none")),
        Units(Curriculum.points, () => Some(dictionary), learner, () => None)
      ),
      () => Some(analyser),
      () => Some(dictionary),
      learner,
      () => None
    )
  }

  val tests: Tests = Tests {
    test("a form chosen: its ending blanked as one, the others of its kind") {
      lessons match {
        case None =>
          println(
            "Lesson exercises not made: the analyser or dictionary is not in the cache"
          )
        case Some(made) =>
          val forms = List("masu", "masen-mashita", "mashou", "tai")
            .flatMap(made.exercises(_).toOption.get)
            .filter(_.kind == ExerciseKind.Form)
          assert(forms.nonEmpty)
          forms.foreach { exercise =>
            val blanks = exercise.parts.filter(_.marked)
            // the sentence with the ending put back is the sentence
            val filled = exercise.parts.map(_.text).mkString
            assert(
              blanks.size == 1,
              blanks.head.text == exercise.expected,
              exercise.choices.contains(exercise.expected),
              exercise.choices.distinct.size == exercise.choices.size,
              exercise.choices.size == 4,
              exercise.choices.forall(choice =>
                Exercises.endings.exists(_._1 == choice)
              ),
              filled.nonEmpty
            )
          }
          println(
            "FORMS " + forms
              .map(each =>
                s"${each.parts.map(part => if (part.marked) "＿" else part.text).mkString} (${each.french}) ${each.expected} / ${each.choices.mkString(",")}"
              )
              .mkString(" | ")
          )
      }
    }
    test("the exercises done counted, a kanji written left out") {
      lessons.foreach { made =>
        val tally = made
          .done(
            ExercisesDone(
              "masu",
              List(
                ExerciseOutcome(ExerciseKind.Translation, right = true),
                ExerciseOutcome(ExerciseKind.Meaning, right = false),
                ExerciseOutcome(ExerciseKind.KanjiWriting, right = true)
              )
            ),
            Instant.parse("2026-11-24T10:00:00Z")
          )
          .toOption
          .get
        assert(tally == ExercisesTally(1, 2))
      }
    }
    test("no form to choose while ます is the only one learned") {
      lessons.foreach { made =>
        assert(
          !made
            .exercises("masu")
            .toOption
            .get
            .exists(_.kind == ExerciseKind.Form)
        )
      }
    }
    test("meanings asked in sentences not shown in French before them") {
      lessons.foreach { made =>
        Curriculum.points.map(_.id).foreach { point =>
          val all = made.exercises(point).toOption.get
          val shownInFrench = all
            .filter(exercise =>
              Set(
                ExerciseKind.Translation,
                ExerciseKind.Particle,
                ExerciseKind.Form
              )(exercise.kind)
            )
            .map(_.japanese)
            .toSet
          val meant =
            all.filter(_.kind == ExerciseKind.Meaning).map(_.japanese)
          assert(meant.forall(!shownInFrench(_)))
        }
      }
    }
  }
}

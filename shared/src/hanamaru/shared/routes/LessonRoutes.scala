package hanamaru.shared.routes

import hanamaru.shared.lessons.*

import sttp.tapir.*
import sttp.tapir.json.jsoniter.*

object LessonRoutes {

  /** The grammar curriculum, in the order it is learned. */
  val curriculum: PublicEndpoint[Unit, String, List[GrammarPoint], Any] =
    Routes.baseRoute.get
      .in("lessons")
      .errorOut(Routes.refused)
      .out(jsonBody[List[GrammarPoint]])

  /** A grammar point's lesson. */
  val lesson: PublicEndpoint[String, String, GrammarLesson, Any] =
    Routes.baseRoute.get
      .in("lessons" / path[String]("point"))
      .errorOut(Routes.refused)
      .out(jsonBody[GrammarLesson])

  /** A new reading text for a lesson, written by the tutor and checked; none
    * when none could be written.
    */
  val text: PublicEndpoint[String, String, Option[LessonText], Any] =
    Routes.baseRoute.get
      .in("lessons" / path[String]("point") / "text")
      .errorOut(Routes.refused)
      .out(jsonBody[Option[LessonText]])

  /** Exercises on a lesson's point, in sentences of its examples and of a new
    * reading text.
    */
  val exercises: PublicEndpoint[String, String, List[LessonExercise], Any] =
    Routes.baseRoute.get
      .in("lessons" / path[String]("point") / "exercises")
      .errorOut(Routes.refused)
      .out(jsonBody[List[LessonExercise]])

  /** An exercise's answer judged: by the code, else by the tutor. */
  val answer: PublicEndpoint[ExerciseAnswer, String, ExerciseVerdict, Any] =
    Routes.baseRoute.post
      .in("lessons" / "answers")
      .in(jsonBody[ExerciseAnswer])
      .errorOut(Routes.refused)
      .out(jsonBody[ExerciseVerdict])

  /** A lesson's exercises done, recorded as one review of its point. */
  val done: PublicEndpoint[ExercisesDone, String, ExercisesTally, Any] =
    Routes.baseRoute.post
      .in("lessons" / "done")
      .in(jsonBody[ExercisesDone])
      .errorOut(Routes.refused)
      .out(jsonBody[ExercisesTally])

  /** A lesson session's plan: what is due or new, else, nothing being, the unit
    * under way gone over again.
    */
  val plan: PublicEndpoint[Unit, String, LessonPlan, Any] =
    Routes.baseRoute.get
      .in("lesson-session")
      .errorOut(Routes.refused)
      .out(jsonBody[LessonPlan])

  /** The unit under way, none once every unit is left. */
  val unit: PublicEndpoint[Unit, String, Option[UnitProgress], Any] =
    Routes.baseRoute.get
      .in("unit")
      .errorOut(Routes.refused)
      .out(jsonBody[Option[UnitProgress]])

  /** A unit gathered to be gone over: one of those so far. */
  val recap: PublicEndpoint[String, String, UnitRecap, Any] =
    Routes.baseRoute.get
      .in("unit" / path[String]("point"))
      .errorOut(Routes.refused)
      .out(jsonBody[UnitRecap])

  /** Moves on from the unit under way, once acquired: the unit under way then.
    */
  val moveOn: PublicEndpoint[Unit, String, Option[UnitProgress], Any] =
    Routes.baseRoute.post
      .in("unit" / "onward")
      .errorOut(Routes.refused)
      .out(jsonBody[Option[UnitProgress]])
}

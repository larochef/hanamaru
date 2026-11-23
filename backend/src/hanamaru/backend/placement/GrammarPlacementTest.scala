package hanamaru.backend.placement

import hanamaru.backend.learner.*
import hanamaru.backend.lessons.{CurriculumPoint, PlacementItem}
import hanamaru.backend.reference.{Analyser, SentenceLayout}
import hanamaru.shared.learner.Grade
import hanamaru.shared.placement.*

import java.time.Instant

/** The grammar placement test taken: its answers kept as they are given, a
  * point passed seeded in the learner model as to confirm, its card's next
  * review confirming it or not; a point failed is a lesson to do.
  */
final class GrammarPlacementTest(
    store: PlacementStore,
    learner: LearnerStore,
    points: List[CurriculumPoint],
    analyser: () => Option[Analyser]
) {
  private val domain = "grammar"
  private val placement = GrammarPlacement(points)

  def state(): Either[String, GrammarPlacementState] =
    store.lastRun(domain).fold(Right(notStarted))(stateOf)

  def started(at: Instant): Either[String, GrammarPlacementState] =
    stateOf(store.started(domain, at))

  def answered(
      answer: GrammarPlacementAnswer,
      at: Instant
  ): Either[String, GrammarPlacementState] =
    store
      .lastRun(domain)
      .filterNot(_.finished)
      .toRight("No grammar placement test under way")
      .flatMap { run =>
        val before = store.answers(run.id)
        answer match {
          case GrammarPlacementAnswer.GiveUp =>
            store.record(
              run.id,
              Answered(AnswerKind.Skip, domain, correct = false),
              "",
              at
            )
          case GrammarPlacementAnswer.Chosen(point, number, choice) =>
            placement.progress(before)._1 match {
              case Some((asked, askedNumber))
                  if asked.id == point && askedNumber == number =>
                val right = choice.contains(asked.placement(number).answer)
                store.record(
                  run.id,
                  Answered(AnswerKind.Grammar, s"$point#$number", right),
                  choice.getOrElse(""),
                  at
                )
              case _ => ()
            }
        }
        val after = store.answers(run.id)
        // the points this answer decided passed: seeded, to confirm
        val decidedBefore = placement.progress(before)._2.map(_._1.id).toSet
        placement.progress(after)._2.collect {
          case (point, true) if !decidedBefore(point.id) =>
            learner.reviewed(
              ItemKind.Grammar,
              point.id,
              CardKind.Usage,
              Grade.Easy,
              "placement",
              at,
              ReviewSource.Placement,
              toConfirm = true
            )
        }
        if (placement.progress(after)._1.isEmpty) store.finish(run.id, at)
        stateOf(run.copy(finished = placement.progress(after)._1.isEmpty))
      }

  private def notStarted =
    GrammarPlacementState(started = false, finished = false, None, Nil, None)

  private def stateOf(
      run: PlacementRun
  ): Either[String, GrammarPlacementState] = {
    val (next, decided) = placement.progress(store.answers(run.id))
    val question = next.filter(_ => !run.finished) match {
      case None                  => Right(None)
      case Some((point, number)) =>
        analyser()
          .toRight("The Japanese analyser is not ready")
          .map(analysis => Some(questionOf(analysis, point, number)))
    }
    question.map(asked =>
      GrammarPlacementState(
        started = true,
        finished = asked.isEmpty,
        asked,
        decided.map((point, passed) =>
          GrammarPointResult(point.summary, passed)
        ),
        Option
          .when(asked.isEmpty)(
            points.find(point =>
              !decided.exists((decidedPoint, passed) =>
                decidedPoint.id == point.id && passed
              )
            )
          )
          .flatten
          .map(_.summary)
      )
    )
  }

  // the sentence filled with its answer, laid out, the answer's tokens marked
  // as the blank
  private def questionOf(
      analysis: Analyser,
      point: CurriculumPoint,
      number: Int
  ): GrammarQuestion = {
    val item = point.placement(number)
    val tokens = analysis.tokens(item.filled)
    GrammarQuestion(
      point.summary,
      number,
      SentenceLayout.laidOut(
        tokens,
        GrammarPlacementTest.inBlank(tokens.map(_.surface), item)
      ),
      item.french,
      item.choices
    )
  }
}

object GrammarPlacementTest {

  /** Which tokens (by index) are the blank's, given their surfaces in order. */
  def inBlank(surfaces: List[String], item: PlacementItem): Set[Int] = {
    val (from, until) = item.blank
    val starts = surfaces.scanLeft(0)(_ + _.length)
    surfaces.indices
      .filter(index =>
        starts(index) >= from && starts(index) + surfaces(index).length <= until
      )
      .toSet
  }
}

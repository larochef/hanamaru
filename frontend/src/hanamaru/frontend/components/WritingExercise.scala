package hanamaru.frontend.components

import hanamaru.frontend.i18n.Tr
import hanamaru.shared.writing.*

import com.raquo.laminar.api.L.*

/** A character written with the stylus. Guided, over its faint model, each
  * stroke is judged as it ends: a wrong one is said, erased, and drawn again.
  * From memory, in an empty box, the learner writes the whole character as they
  * think it goes, then says it is done, and only then is it corrected (shown
  * here when `showCorrection`, else left to the page). Each attempt ended comes
  * out of `attempts`: what becomes of it is for the page to say.
  */
class WritingExercise(
    character: CharacterStrokes,
    step: WritingStep,
    canStartAgain: Boolean,
    showCorrection: Boolean = true,
    doneLabel: Option[String] = None
) extends ReactiveElement {
  private val size = CharacterStrokes.boxSize
  private val guided = step == WritingStep.Guided
  private val model =
    character.strokes.map(stroke => SvgPath.points(stroke.path))

  // a stroke shorter than this is a slip of the pen, not a stroke
  private val shortestStroke = 2.0

  // guided: the strokes accepted; from memory: every stroke drawn
  private val accepted = Var(List.empty[List[DrawnPoint]])
  private val corrected = Var(Option.empty[WritingAttempt])
  private val drawing = Var(Option.empty[List[DrawnPoint]])
  private val judged = Var(List.empty[JudgedStroke])

  /** The last wrong stroke: which stroke was expected, and why it was refused.
    */
  private val refused = Var(Option.empty[(Int, StrokeVerdict)])
  private val ended = Var(false)
  private val attemptsEnded = EventBus[WritingAttempt]()

  val attempts: EventStream[WritingAttempt] = attemptsEnded.events
  // A Var set while an event is handled takes its value once the event is
  // through: new values are worked out here before they are set.
  private def strokeEnded(points: List[DrawnPoint]): Unit = {
    val line = points.map(_.point)
    if (StrokeMatcher.length(line) < shortestStroke) {
      ()
    } else if (!guided) {
      accepted.set(accepted.now() :+ points)
    } else {
      val expected = accepted.now().size
      val judgement = StrokeMatcher.judge(line, model, expected)
      val judgedSoFar =
        judged.now() :+ JudgedStroke(points, expected, judgement)
      judged.set(judgedSoFar)
      if (judgement.verdict == StrokeVerdict.Accepted) {
        val acceptedSoFar = accepted.now() :+ points
        accepted.set(acceptedSoFar)
        refused.set(None)
        if (acceptedSoFar.size == model.size) {
          ended.set(true)
          record(judgedSoFar, completed = true)
        }
      } else {
        refused.set(Some(expected -> judgement.verdict))
      }
    }
  }

  private def record(strokes: List[JudgedStroke], completed: Boolean): Unit =
    attemptsEnded.writer.onNext(
      WritingAttempt(character.character, step, strokes, completed, model.size)
    )

  // from memory: the character written as it stands, corrected
  private def finish(): Unit = {
    val attempt =
      WritingCorrection.attempt(character.character, accepted.now(), model)
    ended.set(true)
    corrected.set(Some(attempt))
    attemptsEnded.writer.onNext(attempt)
  }

  private def restart(): Unit = {
    if (guided && !ended.now() && judged.now().nonEmpty) {
      record(judged.now(), completed = false)
    }
    accepted.set(Nil)
    corrected.set(None)
    drawing.set(None)
    judged.set(Nil)
    refused.set(None)
    ended.set(false)
  }

  // the stroke that was expected, drawn from its start, so its direction shows
  private def expectedStroke(index: Int): SvgElement = svg.g(
    svg.cls := "expected",
    svg.path(svg.d := character.strokes(index).path, svg.pathLength := "1")
  )

  private def box: SvgElement = svg.svg(
    svg.viewBox := s"0 0 $size $size",
    svg.cls := "writing-box",
    PenStrokes.guide,
    Option.when(guided)(
      svg.g(
        svg.cls := "model",
        character.strokes.map(stroke => svg.path(svg.d := stroke.path))
      )
    ),
    svg.g(
      svg.cls := "written",
      children <-- accepted.signal.map(_.map(PenStrokes.polyline)),
      child.maybe <-- drawing.signal.map(_.map(PenStrokes.polyline))
    ),
    child.maybe <-- refused.signal.map(
      _.map((index, _) => expectedStroke(index))
    ),
    PenStrokes(drawing, () => !ended.now(), strokeEnded)
  )

  private def refusal(index: Int, verdict: StrokeVerdict): String = {
    val number = index + 1
    verdict match {
      case StrokeVerdict.WrongDirection =>
        Tr(
          s"Stroke $number goes the other way.",
          s"Le trait $number va dans l'autre sens."
        )
      case StrokeVerdict.WrongOrder(drawn) =>
        Tr(
          s"That is stroke $drawn: stroke $number comes first.",
          s"C'est le trait $drawn : le trait $number vient avant."
        )
      case StrokeVerdict.Misdrawn | StrokeVerdict.Accepted =>
        Tr(
          s"That is not stroke $number.",
          s"Ce n'est pas le trait $number."
        )
    }
  }

  private val message: Signal[Option[HtmlElement]] =
    refused.signal.combineWith(ended.signal).map {
      case (_, true) if accepted.now().size == model.size =>
        Some(
          p(
            cls := "writing-message is-done",
            Tr("Well written!", "Bien écrit !")
          )
        )
      case (Some((index, verdict)), isEnded) =>
        val next =
          if (isEnded) {
            Tr(" It is shown in red.", " Il est montré en rouge.")
          } else {
            Tr(" Draw it again.", " Recommence-le.")
          }
        Some(
          p(cls := "writing-message is-failed", refusal(index, verdict) + next)
        )
      case _ => None
    }

  private def freeControls: HtmlElement = div(
    cls := "writing-controls",
    button(
      Tr("Erase the last stroke", "Effacer le dernier trait"),
      disabled <-- accepted.signal
        .combineWith(ended.signal)
        .map((strokes, isEnded) => strokes.isEmpty || isEnded),
      onClick --> (_ => accepted.update(_.dropRight(1)))
    ),
    button(
      cls := "primary",
      doneLabel.getOrElse(Tr("Done", "Terminé")),
      disabled <-- accepted.signal
        .combineWith(ended.signal)
        .map((strokes, isEnded) => strokes.isEmpty || isEnded),
      onClick --> (_ => finish())
    )
  )

  override lazy val node: HtmlElement = div(
    cls := "writing-exercise",
    child <-- corrected.signal.map {
      case Some(attempt) if showCorrection =>
        CorrectionView(character, attempt).node
      case _ => div(box)
    },
    Option.when(!guided)(
      child.maybe <-- ended.signal.map(isEnded =>
        Option.unless(isEnded)(freeControls)
      )
    ),
    Option.when(guided)(child.maybe <-- message),
    Option.when(canStartAgain)(
      button(
        cls := "replay",
        Tr("Start again", "Recommencer"),
        onClick --> (_ => restart())
      )
    )
  )
}

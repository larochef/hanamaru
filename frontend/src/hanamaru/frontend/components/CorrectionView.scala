package hanamaru.frontend.components

import hanamaru.frontend.i18n.Tr
import hanamaru.shared.writing.*

import com.raquo.laminar.api.L.*

/** A character written from memory, corrected: the learner's strokes over the
  * faint model, each in the colour of its verdict and numbered in the order
  * drawn, what was wrong in words, and the stroke order to watch again.
  */
class CorrectionView(character: CharacterStrokes, attempt: WritingAttempt)
    extends ReactiveElement {
  private val size = CharacterStrokes.boxSize
  private val watching = Var(false)

  private def verdictClass(index: Int, stroke: JudgedStroke): String =
    stroke.judgement.verdict match {
      case _ if index >= character.strokes.size => "is-wrong"
      case StrokeVerdict.Accepted               => "is-right"
      case StrokeVerdict.WrongDirection | StrokeVerdict.WrongOrder(_) =>
        "is-near"
      case StrokeVerdict.Misdrawn => "is-wrong"
    }

  private def box: SvgElement = svg.svg(
    svg.viewBox := s"0 0 $size $size",
    svg.cls := "writing-box correction-box",
    svg.g(
      svg.cls := "model",
      character.strokes.map(stroke => svg.path(svg.d := stroke.path))
    ),
    svg.g(
      svg.cls := "written",
      attempt.strokes.zipWithIndex.map((stroke, index) =>
        svg.polyline(
          svg.cls := verdictClass(index, stroke),
          svg.points := stroke.points
            .map(point => s"${point.x},${point.y}")
            .mkString(" ")
        )
      )
    ),
    svg.g(
      svg.cls := "numbers",
      attempt.strokes.zipWithIndex.flatMap((stroke, index) =>
        stroke.points.headOption.map(start =>
          svg.text(
            svg.x := s"${start.x - 6}",
            svg.y := s"${start.y - 2}",
            s"${index + 1}"
          )
        )
      )
    )
  )

  private def explained(issue: WritingIssue): String = issue match {
    case WritingIssue.WrongDirection(stroke) =>
      Tr(
        s"Your stroke $stroke goes the other way.",
        s"Ton trait $stroke va dans l'autre sens."
      )
    case WritingIssue.WrongOrder(stroke, isStroke) =>
      Tr(
        s"Your stroke $stroke is the character's stroke $isStroke: the order is not right.",
        s"Ton trait $stroke est le trait $isStroke du caractère : l'ordre n'est pas le bon."
      )
    case WritingIssue.Misdrawn(stroke) =>
      Tr(
        s"Your stroke $stroke is not the one expected, in shape or in place.",
        s"Ton trait $stroke n'est pas celui attendu, par sa forme ou sa place."
      )
    case WritingIssue.Extra(stroke) =>
      Tr(
        s"Your stroke $stroke is one too many.",
        s"Ton trait $stroke est en trop."
      )
    case WritingIssue.Missing(count, total) =>
      Tr(
        s"$count stroke(s) missing: this character has $total.",
        s"Il manque $count trait(s) : ce caractère en a $total."
      )
    case WritingIssue.OtherCharacter(characters) =>
      Tr(
        s"What you wrote looks more like ${characters.mkString(", ")}.",
        s"Ce que tu as écrit ressemble plutôt à ${characters.mkString(", ")}."
      )
  }

  private def summary: HtmlElement = WritingCorrection.outcome(attempt) match {
    case WritingOutcome.Right =>
      p(cls := "writing-message is-done", Tr("Well written!", "Bien écrit !"))
    case WritingOutcome.StrokeOrder =>
      p(
        cls := "writing-message is-near",
        Tr(
          "Well formed, but mind the strokes:",
          "Bien formé, mais attention aux traits :"
        )
      )
    case WritingOutcome.Wrong =>
      p(cls := "writing-message is-failed", Tr("To work on:", "À revoir :"))
  }

  override lazy val node: HtmlElement = div(
    cls := "correction",
    div(
      cls := "correction-body",
      box,
      div(
        cls := "correction-words",
        span(cls := "correction-character", lang := "ja", character.character),
        summary,
        ul(
          WritingCorrection.issues(attempt).map(issue => li(explained(issue)))
        ),
        button(
          Tr("Watch the stroke order", "Revoir le tracé"),
          onClick --> (_ => watching.update(!_))
        )
      )
    ),
    child.maybe <-- watching.signal.map(
      Option.when(_)(StrokeDemonstration(character).node)
    )
  )
}

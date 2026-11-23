package hanamaru.frontend.components

import hanamaru.frontend.i18n.Tr
import hanamaru.shared.kana.Kana

import scala.scalajs.js

import com.raquo.laminar.api.L.*

/** A reading given: typed in romaji, none for "I don't know", and how long it
  * took, in milliseconds.
  */
final case class TypedReading(typed: Option[String], millis: Double)

/** A kana shown, its reading typed in romaji, or "I don't know". The answer
  * comes out of `answers`, once; other ways out, as giving up a whole script,
  * are the page's, given as `more` and guarded by `claimAnswer`.
  */
class ReadingQuestion(kana: Kana, more: Modifier[HtmlElement]*)
    extends ReactiveElement {
  private val shownAt = js.Date.now()
  private val sent = Var(false)
  private val readings = EventBus[TypedReading]()

  val answers: EventStream[TypedReading] = readings.events

  /** Whether the question can still be answered, and from now on it cannot. */
  def claimAnswer(): Boolean = {
    val free = !sent.now()
    sent.set(true)
    free
  }

  private def send(typed: Option[String]): Unit =
    if (claimAnswer()) {
      readings.writer.onNext(TypedReading(typed, js.Date.now() - shownAt))
    }

  private val field = input(
    cls := "reading-input",
    placeholder := "romaji",
    autoComplete := "off",
    spellCheck := false,
    onMountFocus
  )

  override lazy val node: HtmlElement = div(
    cls := "reading-question",
    p(cls := "placement-prompt", Tr("How is it read?", "Comment se lit-il ?")),
    span(cls := "placement-kana", lang := "ja", kana.character),
    form(
      onSubmit.preventDefault --> { _ =>
        Option(field.ref.value)
          .filter(_.trim.nonEmpty)
          .foreach(typed => send(Some(typed)))
      },
      field,
      button(typ := "submit", cls := "primary", Tr("Answer", "Valider"))
    ),
    div(
      cls := "placement-escapes",
      button(
        Tr("I don't know", "Je ne sais pas"),
        onClick --> (_ => send(None))
      ),
      more
    )
  )
}

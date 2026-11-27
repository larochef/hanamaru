package hanamaru.frontend.components

import hanamaru.frontend.i18n.Tr

import com.raquo.laminar.api.L.*

/** The end of every answer, always in the same place: "I don't know", then the
  * answer given beside it. Inside a form, the answer is its submission; else
  * `submit` gives it, once `ready`.
  */
class AnswerActions(
    dontKnow: () => Unit,
    submit: Option[() => Unit] = None,
    ready: Signal[Boolean] = Val(true)
) extends ReactiveElement {

  override lazy val node: HtmlElement = div(
    cls := "answer-actions",
    button(
      typ := "button",
      Tr("I don't know", "Je ne sais pas"),
      onClick --> (_ => dontKnow())
    ),
    button(
      typ := (if (submit.isEmpty) "submit" else "button"),
      cls := "primary answer-submit",
      disabled <-- ready.map(!_),
      submit.map(answered => onClick --> (_ => answered())),
      ButtonIcon(ButtonIcon.Shape.Check).node,
      Tr("Answer", "Valider")
    )
  )
}

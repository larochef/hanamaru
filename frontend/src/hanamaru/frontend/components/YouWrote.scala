package hanamaru.frontend.components

import hanamaru.frontend.i18n.Tr

import com.raquo.laminar.api.L.*

/** An answer judged wrong, as the learner typed or wrote it: where they went
  * wrong is seen against the answer expected.
  */
class YouWrote(answer: String) extends ReactiveElement {

  override lazy val node: HtmlElement = p(
    cls := "you-wrote",
    Tr("You wrote: ", "Tu as écrit : "),
    span(cls := "typed-answer", answer)
  )
}

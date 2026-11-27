package hanamaru.frontend.components

import hanamaru.frontend.model.*
import hanamaru.shared.questions.QuestionContext

import com.raquo.laminar.api.L.*

/** What a screen shows the learner (`context`, none when nothing to say), told
  * to the tutor's chat as it changes, and left once the screen goes.
  */
class TutorContext(bus: AppBus, context: Signal[Option[QuestionContext]])
    extends ReactiveElement {

  // the context told last, to be left when another comes or the screen goes
  private var told = Option.empty[QuestionContext]

  private def tell(next: Option[QuestionContext]): Unit = {
    told
      .filterNot(next.contains)
      .foreach(left => bus.publish(AppEvent.ContextLeft(left)))
    next
      .filterNot(told.contains)
      .foreach(shown => bus.publish(AppEvent.ContextShown(shown)))
    told = next
  }

  override lazy val node: HtmlElement = span(
    cls := "tutor-context",
    context --> tell,
    onUnmountCallback(_ => tell(None))
  )
}

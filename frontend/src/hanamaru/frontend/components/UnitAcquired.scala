package hanamaru.frontend.components

import hanamaru.frontend.i18n.Tr
import hanamaru.frontend.model.*
import hanamaru.shared.lessons.UnitOnward

import com.raquo.laminar.api.L.*

/** A unit acquired, as the server found it: the units acquired, and the choice
  * to move on to the next or to go on practising this one (`goOn`, the element
  * that does it).
  */
class UnitAcquired(bus: AppBus, onward: UnitOnward, goOn: HtmlElement)
    extends ReactiveElement {

  private val moving = Var(false)

  private def acquired: String =
    onward.acquired.map(_.title).mkString(", ")

  override lazy val node: HtmlElement = sectionTag(
    cls := "unit-acquired",
    h2(Tr("Well done!", "Bravo !")),
    p(
      if (onward.acquired.size == 1)
        Tr(
          s"You have learned the unit $acquired: its words, its lesson and its symbols.",
          s"Tu as bien appris l'unité $acquired : ses mots, sa leçon et ses symboles."
        )
      else
        Tr(
          s"You have learned the units $acquired: their words, their lessons and their symbols.",
          s"Tu as bien appris les unités $acquired : leurs mots, leurs leçons et leurs symboles."
        )
    ),
    p(
      onward.next.fold(
        Tr(
          "It was the last one: the reviews go on.",
          "C'était la dernière : les révisions continuent."
        )
      )(next => Tr(s"Next: ${next.title}.", s"La suivante : ${next.title}."))
    ),
    div(
      cls := "placement-actions",
      button(
        cls := "primary",
        onMountFocus,
        disabled <-- moving.signal,
        onward.next.fold(Tr("Move on", "Passer à la suite"))(_ =>
          Tr("Move on to the next unit", "Passer à l'unité suivante")
        ),
        onClick --> { _ =>
          moving.set(true)
          bus.send(AppCommand.MoveOnUnit)
        }
      ),
      goOn
    )
  )
}

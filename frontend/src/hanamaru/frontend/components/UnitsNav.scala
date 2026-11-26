package hanamaru.frontend.components

import hanamaru.frontend.i18n.Tr
import hanamaru.frontend.model.*
import hanamaru.shared.lessons.UnitRecap

import com.raquo.laminar.api.L.*

/** The unit of `recap` among the units so far, on one line however many they
  * are: the one before and the one after it, a click away, and the whole list,
  * each with its number and its point, in a menu on its name. The unit under
  * way opens in My lesson.
  */
class UnitsNav(bus: AppBus, recap: UnitRecap) extends ReactiveElement {

  private val index = recap.units.indexWhere(_.id == recap.point.id)

  // the last of the units so far is the one under way: My lesson shows it
  private def pageOf(at: Int, point: String): Page =
    if (at == recap.units.size - 1) Page.LessonSession else Page.Unit(point)

  private def step(to: Int, shape: ButtonIcon.Shape, said: String) =
    recap.units
      .lift(to)
      .filter(_ => to >= 0)
      .map(point =>
        PageLink(
          bus,
          pageOf(to, point.id),
          cls := "icon-button",
          aria.label := said,
          title := point.title,
          ButtonIcon(shape).node
        ).node
      )

  private def listed: HtmlElement = ol(
    cls := "units-menu-list",
    recap.units.zipWithIndex.map((point, at) =>
      li(
        PageLink(
          bus,
          pageOf(at, point.id),
          cls("is-selected") := at == index,
          span(cls := "units-menu-number", (at + 1).toString),
          point.title
        ).node
      )
    )
  )

  override lazy val node: HtmlElement = div(
    cls := "units-nav",
    step(
      index - 1,
      ButtonIcon.Shape.Previous,
      Tr("Unit before", "Unité précédente")
    ),
    detailsTag(
      cls := "units-menu",
      summaryTag(
        Tr(s"Unit ${index + 1}", s"Unité ${index + 1}"),
        ButtonIcon(ButtonIcon.Shape.Down).node
      ),
      listed
    ),
    step(index + 1, ButtonIcon.Shape.Next, Tr("Unit after", "Unité suivante"))
  )
}

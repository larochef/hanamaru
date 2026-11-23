package hanamaru.frontend.components

import hanamaru.frontend.i18n.Tr
import hanamaru.frontend.model.{AppBus, Page}
import hanamaru.shared.kana.*

import com.raquo.laminar.api.L.*

/** Every kana, script by script, laid out as the usual chart; each leads to how
  * it is written.
  */
class KanaChart(bus: AppBus) extends ReactiveElement {

  private def scriptName(script: Script): String = script match {
    case Script.Hiragana => Tr("Hiragana", "Hiragana")
    case Script.Katakana => Tr("Katakana", "Katakana")
  }

  private def groupName(group: KanaGroup): String = group match {
    case KanaGroup.Basic  => Tr("Basic sounds", "Sons de base")
    case KanaGroup.Voiced =>
      Tr(
        "Voiced sounds (dakuten, handakuten)",
        "Sons voisés (dakuten, handakuten)"
      )
    case KanaGroup.Small => Tr("Small kana", "Petits kana")
  }

  private def cell(kana: Option[Kana]): HtmlElement = kana match {
    case Some(kana) =>
      PageLink(
        bus,
        Page.Character(kana),
        cls := "kana-cell",
        span(cls := "kana", lang := "ja", kana.character),
        span(cls := "romaji", kana.romaji)
      ).node
    case None => div(cls := "kana-cell is-gap")
  }

  private def scriptChart(script: Script): HtmlElement = sectionTag(
    cls := "kana-script",
    h2(scriptName(script)),
    Kana
      .chart(script)
      .map((group, rows) =>
        div(
          cls := "kana-group",
          h3(groupName(group)),
          div(cls := "kana-grid", rows.flatten.map(cell))
        )
      )
  )

  override lazy val node: HtmlElement = div(
    cls := "kana-chart",
    Script.values.toList.map(scriptChart)
  )
}

package hanamaru.frontend.components

import hanamaru.frontend.i18n.Tr

import com.raquo.laminar.api.L.*

/** Where the facts shown come from, as their licences ask. */
class Credits extends ReactiveElement {

  override lazy val node: HtmlElement = footerTag(
    cls := "credits",
    Tr("Character strokes: ", "Tracés des caractères : "),
    a(href := "https://kanjivg.tagaini.net", "KanjiVG"),
    " © Ulrich Apel, ",
    a(
      href := "https://creativecommons.org/licenses/by-sa/3.0/",
      "CC BY-SA 3.0"
    )
  )
}

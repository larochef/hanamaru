package hanamaru.frontend.components

import hanamaru.frontend.i18n.Tr

import com.raquo.laminar.api.L.*

/** Where the facts shown come from, as their licences ask. */
class Credits extends ReactiveElement {

  override lazy val node: HtmlElement = footerTag(
    cls := "credits",
    p(
      Tr("Character strokes: ", "Tracés des caractères : "),
      a(href := "https://kanjivg.tagaini.net", "KanjiVG"),
      " © Ulrich Apel, ",
      a(
        href := "https://creativecommons.org/licenses/by-sa/3.0/",
        "CC BY-SA 3.0"
      )
    ),
    p(
      Tr("Dictionary: ", "Dictionnaire : "),
      a(href := "https://www.edrdg.org/jmdict/j_jmdict.html", "JMdict"),
      " © Electronic Dictionary Research and Development Group, ",
      a(
        href := "https://creativecommons.org/licenses/by-sa/4.0/",
        "CC BY-SA 4.0"
      )
    ),
    p(
      Tr("JLPT word lists: ", "Listes de mots du JLPT : "),
      a(href := "http://www.tanos.co.uk/jlpt/", "Jonathan Waller"),
      ", ",
      a(href := "https://creativecommons.org/licenses/by/4.0/", "CC BY"),
      Tr(", tied to JMdict by ", ", rattachées à JMdict par "),
      a(
        href := "https://github.com/stephenmk/yomitan-jlpt-vocab",
        "yomitan-jlpt-vocab"
      ),
      ", ",
      a(
        href := "https://creativecommons.org/licenses/by-sa/4.0/",
        "CC BY-SA 4.0"
      )
    ),
    p(
      Tr("Japanese analysis: ", "Analyse du japonais : "),
      a(href := "https://github.com/WorksApplications/Sudachi", "Sudachi"),
      Tr(
        " and its dictionary, Works Applications, ",
        " et son dictionnaire, Works Applications, "
      ),
      a(href := "https://www.apache.org/licenses/LICENSE-2.0", "Apache 2.0")
    )
  )
}

package hanamaru.frontend.components

import hanamaru.frontend.i18n.Tr
import hanamaru.shared.kana.{Kana, Script}

import com.raquo.laminar.api.L.*

/** A kana's reading, as asked to write it: in its script's colour (hiragana
  * blue, katakana brown), the script named below it, so that "gi" says which of
  * ぎ and ギ is meant.
  */
object KanaReading {

  def apply(kana: Kana, className: String): HtmlElement =
    span(
      cls := s"$className kana-reading ${scriptClass(kana.script)}",
      Kana.shownReading(kana),
      span(cls := "kana-script", scriptName(kana.script))
    )

  def scriptName(script: Script): String = script match {
    case Script.Hiragana => Tr("hiragana", "hiragana")
    case Script.Katakana => Tr("katakana", "katakana")
  }

  private def scriptClass(script: Script): String = script match {
    case Script.Hiragana => "in-hiragana"
    case Script.Katakana => "in-katakana"
  }
}

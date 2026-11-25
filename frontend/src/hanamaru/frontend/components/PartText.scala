package hanamaru.frontend.components

import hanamaru.shared.text.{KanjiShown, SentencePart}

import com.raquo.laminar.api.L.*

/** A word of a sentence as the server says to show it: in kana, in kanji, or in
  * kanji with its reading above.
  */
class PartText(part: SentencePart) extends ReactiveElement {

  private val rubyTag = htmlTag("ruby")
  private val rtTag = htmlTag("rt")

  override lazy val node: HtmlElement = span(
    (part.written, part.shownAs) match {
      case (Some(written), KanjiShown.Kanji)       => written
      case (Some(written), KanjiShown.WithReading) =>
        rubyTag(written, rtTag(part.text))
      case _ => part.text
    }
  )
}

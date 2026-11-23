package hanamaru.frontend.components

import hanamaru.shared.kana.Romaji

import com.raquo.laminar.api.L.*

/** A field Japanese is typed in, in romaji turned into kana as it is typed:
  * only the romaji at its end is converted, so that a kana is made once its
  * letters are all there.
  */
class KanaInput extends ReactiveElement {

  private def converted(value: String): String = {
    val romaji = value.reverse.takeWhile(_ < 128).reverse
    value.dropRight(romaji.length) + Romaji.toKana(romaji)
  }

  /** What is typed, as it stands. */
  def value: String = node.ref.value

  override lazy val node: Input = input(
    cls := "reading-input kana-input",
    lang := "ja",
    placeholder := "romaji → かな",
    autoComplete := "off",
    spellCheck := false,
    onMountFocus,
    onInput --> { _ =>
      val typed = node.ref.value
      val kana = converted(typed)
      if (kana != typed) {
        node.ref.value = kana
      }
    }
  )
}

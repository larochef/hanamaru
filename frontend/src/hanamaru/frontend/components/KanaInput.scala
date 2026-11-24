package hanamaru.frontend.components

import hanamaru.frontend.i18n.Tr
import hanamaru.shared.kana.Romaji

import com.raquo.laminar.api.L.*

/** A field Japanese is typed in, in romaji turned into kana as it is typed:
  * only the romaji at its end is converted, so that a kana is made once its
  * letters are all there. A switch beside it, あ or ア, says which script the
  * kana are made in, and changes it; `katakana` is the script it starts in.
  */
class KanaInput(katakana: Boolean = false) extends ReactiveElement {

  private val inKatakana = Var(katakana)

  private def converted(value: String, finished: Boolean = false): String = {
    val romaji = value.reverse.takeWhile(_ < 128).reverse
    val kana = Romaji.toKana(romaji, finished)
    value.dropRight(romaji.length) +
      (if (inKatakana.now()) Romaji.asKatakana(kana) else kana)
  }

  private val field: Input = input(
    cls := "reading-input kana-input",
    cls("is-katakana") <-- inKatakana.signal,
    lang := "ja",
    placeholder <-- inKatakana.signal.map(
      if (_) "romaji → カナ" else "romaji → かな"
    ),
    autoComplete := "off",
    spellCheck := false,
    onMountFocus,
    onInput --> { _ =>
      val typed = field.ref.value
      val kana = converted(typed)
      if (kana != typed) {
        field.ref.value = kana
      }
    }
  )

  /** What is typed, its last romaji made kana (a final n is ん). */
  def value: String = converted(field.ref.value, finished = true)

  /** Whether the kana are made in katakana. */
  def inKatakanaNow: Boolean = inKatakana.now()

  /** A character written elsewhere (with the stylus), put at the end. */
  def append(character: String): Unit = field.ref.value = value + character

  /** The last character replaced by `character`. */
  def replaceLast(character: String): Unit =
    field.ref.value = value.dropRight(1) + character

  /** The last character, if any. */
  def last: Option[String] = value.lastOption.map(_.toString)

  /** The last character taken away. */
  def deleteLast(): Unit = field.ref.value = value.dropRight(1)

  override lazy val node: HtmlElement = span(
    cls := "kana-field",
    field,
    button(
      typ := "button",
      cls := "script-switch",
      lang := "ja",
      title := Tr("Hiragana or katakana", "Hiragana ou katakana"),
      child.text <-- inKatakana.signal.map(if (_) "ア" else "あ"),
      onClick --> { _ =>
        inKatakana.update(!_)
        field.ref.focus()
      }
    )
  )
}

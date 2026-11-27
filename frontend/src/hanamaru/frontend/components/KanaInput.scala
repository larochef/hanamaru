package hanamaru.frontend.components

import hanamaru.frontend.i18n.Tr
import hanamaru.shared.kana.Romaji

import com.raquo.laminar.api.L.*

/** A field Japanese is typed in, in romaji turned into kana as it is typed:
  * only the romaji at its end is converted, so that a kana is made once its
  * letters are all there. A switch beside it, あ, ア or 漢, says what is written
  * in it, and changes it: hiragana or katakana, typed or written with the
  * stylus, or, when the learner writes some (`kanji`), kanji, written with the
  * stylus (typed, hiragana); `starts` is what it starts in. As a blank in a
  * sentence (`place`), it is small, in hiragana, without switch, and not
  * focused at once; `focused` tells when the learner goes to it.
  */
class KanaInput(
    starts: KanaInput.Mode = KanaInput.Mode.Hiragana,
    val kanji: List[String] = Nil,
    place: KanaInput.Place = KanaInput.Place.Alone
) extends ReactiveElement {
  import KanaInput.{Mode, Place}

  private val focusing = EventBus[Unit]()

  val focused: EventStream[Unit] = focusing.events

  private val mode = Var(starts)
  private val inKatakana = mode.signal.map(_ == Mode.Katakana)

  private def converted(value: String, finished: Boolean = false): String = {
    val romaji = value.reverse.takeWhile(_ < 128).reverse
    val kana = Romaji.toKana(romaji, finished)
    value.dropRight(romaji.length) +
      (if (mode.now() == Mode.Katakana) Romaji.asKatakana(kana) else kana)
  }

  private val field: Input = input(
    cls := (place match {
      case Place.Alone      => "reading-input kana-input"
      case Place.InSentence => "kana-input particle-input"
    }),
    cls("is-katakana") <-- inKatakana,
    lang := "ja",
    placeholder <-- inKatakana.map(katakana =>
      place match {
        case Place.Alone      => if (katakana) "romaji → カナ" else "romaji → かな"
        case Place.InSentence => ""
      }
    ),
    autoComplete := "off",
    spellCheck := false,
    Option.when(place == Place.Alone)(onMountFocus),
    onFocus.mapToUnit --> focusing.writer,
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

  /** What is written in it now. */
  def modeNow: Mode = mode.now()

  /** A character written elsewhere (with the stylus), put at the end. */
  def append(character: String): Unit = field.ref.value = value + character

  /** The last character replaced by `character`. */
  def replaceLast(character: String): Unit =
    field.ref.value = value.dropRight(1) + character

  /** The last character, if any. */
  def last: Option[String] = value.lastOption.map(_.toString)

  /** The last character taken away. */
  def deleteLast(): Unit = field.ref.value = value.dropRight(1)

  /** The field given the focus. */
  def focus(): Unit = field.ref.focus()

  override lazy val node: HtmlElement = place match {
    case Place.InSentence => span(cls := "kana-blank", field)
    case Place.Alone      => alone
  }

  private def alone: HtmlElement = span(
    cls := "kana-field",
    field,
    button(
      typ := "button",
      cls := "script-switch",
      lang := "ja",
      title := (
        if (kanji.isEmpty) Tr("Hiragana or katakana", "Hiragana ou katakana")
        else
          Tr(
            "Hiragana, katakana or the kanji you write",
            "Hiragana, katakana ou les kanji que tu sais écrire"
          )
      ),
      child.text <-- mode.signal.map {
        case Mode.Hiragana => "あ"
        case Mode.Katakana => "ア"
        case Mode.Kanji    => "漢"
      },
      onClick --> { _ =>
        mode.update {
          case Mode.Hiragana                   => Mode.Katakana
          case Mode.Katakana if kanji.nonEmpty => Mode.Kanji
          case Mode.Katakana | Mode.Kanji      => Mode.Hiragana
        }
        field.ref.focus()
      }
    )
  )
}

object KanaInput {

  /** What a field is written in. */
  enum Mode {
    case Hiragana, Katakana, Kanji
  }

  /** Where a field stands: alone, the answer, or a blank in a sentence. */
  enum Place {
    case Alone, InSentence
  }
}

package hanamaru.frontend.components

import hanamaru.shared.kana.Romaji

import com.raquo.laminar.api.L.*

/** A field Japanese is typed in, in romaji turned into kana as it is typed:
  * only the romaji at its end is converted, so that a kana is made once its
  * letters are all there, in the script `mode` says (hiragana or katakana;
  * kanji, the learner writing some (`kanji`), are written with the stylus).
  * While the stylus writes in it (`stylus`), it never opens the screen's
  * keyboard. As a blank in a sentence (`place`), it is small and not focused at
  * once; `focused` tells when the learner goes to it.
  */
class KanaInput(
    val mode: Var[KanaInput.Mode] = Var(KanaInput.Mode.Hiragana),
    val kanji: List[String] = Nil,
    place: KanaInput.Place = KanaInput.Place.Alone,
    stylus: Signal[Boolean] = Val(false)
) extends ReactiveElement {
  import KanaInput.{Mode, Place}

  private val focusing = EventBus[Unit]()

  val focused: EventStream[Unit] = focusing.events

  private def converted(value: String, finished: Boolean = false): String = {
    val romaji = value.reverse.takeWhile(_ < 128).reverse
    val kana = Romaji.toKana(romaji, finished)
    value.dropRight(romaji.length) +
      (if (mode.now() == Mode.Katakana) Romaji.asKatakana(kana) else kana)
  }

  private val field: Input = input(
    cls := (place match {
      case Place.Alone      => "answer-input"
      case Place.InSentence => "particle-input"
    }),
    cls <-- mode.signal.map(Mode.className),
    lang := "ja",
    placeholder <-- mode.signal
      .combineWith(stylus)
      .map((now, writing) =>
        place match {
          case Place.Alone if !writing =>
            if (now == Mode.Katakana) "romaji → カナ" else "romaji → かな"
          case _ => ""
        }
      ),
    inputMode <-- stylus.map(writing => if (writing) "none" else "text"),
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

  /** A character written elsewhere (with the stylus), put at the end. */
  def append(character: String): Unit = field.ref.value = value + character

  /** The last character replaced by `character`. */
  def replaceLast(character: String): Unit =
    field.ref.value = value.dropRight(1) + character

  /** The last character, if any. */
  def last: Option[String] = value.lastOption.map(_.toString)

  /** The characters selected changed, each by `change` (made small, a dakuten
    * added…), and left selected; none selected, the last one.
    */
  def changeSelected(change: String => String): Unit =
    selected.fold(last.foreach(each => replaceLast(change(each)))) {
      (start, end) =>
        val text = field.ref.value
        val changed =
          text.slice(start, end).map(each => change(each.toString)).mkString
        field.ref.value = text.take(start) + changed + text.drop(end)
        field.ref.setSelectionRange(start, start + changed.length)
    }

  /** The characters selected taken away; none selected, the last one. */
  def deleteSelected(): Unit =
    selected.fold(field.ref.value = value.dropRight(1)) { (start, end) =>
      val text = field.ref.value
      field.ref.value = text.take(start) + text.drop(end)
      field.ref.setSelectionRange(start, start)
    }

  /** Whether characters are selected in the field. */
  def hasSelection: Boolean =
    field.ref.selectionEnd > field.ref.selectionStart

  // what is selected in the field, its romaji made kana first, if anything
  private def selected: Option[(Int, Int)] = {
    val start = field.ref.selectionStart
    val end = field.ref.selectionEnd
    Option.when(end > start) {
      field.ref.value = value
      (start, end.min(field.ref.value.length))
    }
  }

  /** The field given the focus. */
  def focus(): Unit = field.ref.focus()

  override lazy val node: HtmlElement = place match {
    case Place.InSentence => span(cls := "kana-blank", field)
    case Place.Alone      => field
  }
}

object KanaInput {

  /** What a field is written in, and the stylus recognises. */
  enum Mode {
    case Hiragana, Katakana, Kanji

    /** Its sign, on its button. */
    def sign: String = this match {
      case Hiragana => "あ"
      case Katakana => "ア"
      case Kanji    => "漢"
    }

    /** Its name, under its sign. */
    def name: String = this match {
      case Hiragana => "hiragana"
      case Katakana => "katakana"
      case Kanji    => "kanji"
    }
  }

  object Mode {

    /** The class that gives a field or a box the colour of its script. */
    def className(mode: Mode): String = s"is-${mode.name}"
  }

  /** Where a field stands: alone, the answer, or a blank in a sentence. */
  enum Place {
    case Alone, InSentence
  }
}

package hanamaru.shared.kana

/** Typing Japanese without the system's input method: romaji turned into
  * hiragana as it is typed, the way input methods do it (Hepburn and its usual
  * variants: `si`, `tu`, `hu`; a doubled consonant for っ; `nn` or `n'` for ん;
  * `x` or `l` before a small kana; `-` for ー).
  */
object Romaji {

  private val vowels = "aiueo"

  private val syllables: Map[String, String] = {
    val rows = List(
      "" -> "あいうえお",
      "k" -> "かきくけこ",
      "g" -> "がぎぐげご",
      "s" -> "さしすせそ",
      "z" -> "ざじずぜぞ",
      "t" -> "たちつてと",
      "d" -> "だぢづでど",
      "n" -> "なにぬねの",
      "h" -> "はひふへほ",
      "b" -> "ばびぶべぼ",
      "p" -> "ぱぴぷぺぽ",
      "m" -> "まみむめも",
      "r" -> "らりるれろ"
    )
    val regular = rows.flatMap((consonant, kana) =>
      vowels
        .zip(kana)
        .map((vowel, character) => s"$consonant$vowel" -> character.toString)
    )
    // the rows whose second column gives the small-ya combinations
    val combining = List(
      "ky" -> "き",
      "gy" -> "ぎ",
      "sh" -> "し",
      "sy" -> "し",
      "j" -> "じ",
      "jy" -> "じ",
      "zy" -> "じ",
      "ch" -> "ち",
      "ty" -> "ち",
      "cy" -> "ち",
      "dy" -> "ぢ",
      "ny" -> "に",
      "hy" -> "ひ",
      "by" -> "び",
      "py" -> "ぴ",
      "my" -> "み",
      "ry" -> "り"
    )
    val small = Map('a' -> "ゃ", 'u' -> "ゅ", 'o' -> "ょ", 'e' -> "ぇ")
    val combined = combining.flatMap((start, kana) =>
      small.map((vowel, ending) => s"$start$vowel" -> s"$kana$ending")
    )
    val smalls = List(
      "a" -> "ぁ",
      "i" -> "ぃ",
      "u" -> "ぅ",
      "e" -> "ぇ",
      "o" -> "ぉ",
      "ya" -> "ゃ",
      "yu" -> "ゅ",
      "yo" -> "ょ",
      "tu" -> "っ",
      "tsu" -> "っ",
      "wa" -> "ゎ"
    ).flatMap((sound, kana) => List(s"x$sound" -> kana, s"l$sound" -> kana))
    val others = List(
      "shi" -> "し",
      "chi" -> "ち",
      "tsu" -> "つ",
      "fu" -> "ふ",
      "ji" -> "じ",
      "ya" -> "や",
      "yu" -> "ゆ",
      "yo" -> "よ",
      "wa" -> "わ",
      "wo" -> "を",
      "fa" -> "ふぁ",
      "fi" -> "ふぃ",
      "fe" -> "ふぇ",
      "fo" -> "ふぉ",
      "ti" -> "ち",
      "si" -> "し",
      "zi" -> "じ",
      "di" -> "ぢ",
      "hu" -> "ふ",
      "tu" -> "つ",
      "du" -> "づ",
      "ye" -> "いぇ",
      "she" -> "しぇ",
      "je" -> "じぇ",
      "che" -> "ちぇ",
      "thi" -> "てぃ",
      "dhi" -> "でぃ",
      "nn" -> "ん",
      "n'" -> "ん",
      "-" -> "ー"
    )
    (regular ++ combined ++ smalls ++ others).toMap
  }

  private val longest = syllables.keys.map(_.length).max

  // the letters romaji is typed with: kana already there are left alone
  private def roman(character: Char): Boolean =
    character >= 'a' && character <= 'z'

  /** The kana of what is typed so far; what may still become a kana (`k`, `sh`,
    * a final `n`) is left as it is, unless `finished`.
    */
  def toKana(typed: String, finished: Boolean = false): String = {
    val input = typed.toLowerCase
    val output = StringBuilder()
    var index = 0
    while (index < input.length) {
      val character = input(index)
      val next = input.lift(index + 1)
      val matched = (longest to 1 by -1).iterator
        .map(size => input.slice(index, index + size))
        .find(syllables.contains)
      if (
        character == 'n' && next.contains('n') &&
        input
          .lift(index + 2)
          .exists(letter => vowels.contains(letter) || letter == 'y')
      ) {
        // nn before a vowel: ん, the second n starting the next kana (konnichiha)
        output ++= "ん"
        index += 1
      } else if (
        character == 'n' && next.exists(letter =>
          if (roman(letter))
            !vowels.contains(letter) && letter != 'y' && letter != 'n'
          else letter != '\''
        )
      ) {
        // n before another consonant, a space or a punctuation mark
        output ++= "ん"
        index += 1
      } else if (
        roman(character) && !vowels.contains(character) && character != 'n' &&
        (next.contains(character) || (character == 't' && next.contains('c')))
      ) {
        // a doubled consonant, or tch: a small tsu before it
        output ++= "っ"
        index += 1
      } else {
        matched match {
          // a final nn may still be ん then a kana of the n row: kept for now
          case Some("nn") if !finished && index + 2 == input.length =>
            output ++= "nn"
            index += 2
          case Some(sound) =>
            output ++= syllables(sound)
            index += sound.length
          case None if character == 'n' && finished && next.isEmpty =>
            output ++= "ん"
            index += 1
          case None =>
            output += character
            index += 1
        }
      }
    }
    output.result()
  }

  /** Hiragana written as katakana, the rest left as it is. */
  def asKatakana(text: String): String =
    text.map(character =>
      if (character >= 'ぁ' && character <= 'ゖ') (character + 0x60).toChar
      else character
    )

  /** Whether a text is written in katakana (ー included), and only in them. */
  def isKatakana(text: String): Boolean =
    text.nonEmpty && text.forall(character =>
      (character >= 'ァ' && character <= 'ヶ') || character == 'ー'
    )

  /** Hiragana and katakana made alike, to compare answers whatever the script
    * they are typed or written in.
    */
  def asHiragana(text: String): String =
    text.map(character =>
      if (character >= 'ァ' && character <= 'ヶ') (character - 0x60).toChar
      else character
    )
}

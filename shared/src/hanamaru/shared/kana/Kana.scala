package hanamaru.shared.kana

import sttp.tapir.Schema

/** The two kana scripts. */
enum Script {
  case Hiragana, Katakana
}

object Script {
  given Schema[Script] = Schema.derived
}

/** Where a kana stands in the chart: the basic sounds, those voiced with
  * dakuten or handakuten, and the small ones written in combinations.
  */
enum KanaGroup {
  case Basic, Voiced, Small
}

/** One kana as it is taught: its character, how it is read in Hepburn romaji,
  * and its script and group.
  */
final case class Kana(
    character: String,
    romaji: String,
    script: Script,
    group: KanaGroup
)

/** The kana taught, laid out as the chart is, in rows of five columns: a gap is
  * a column the row does not have (や row, わ row, ん).
  */
object Kana {
  type Row = List[Option[Kana]]

  private val gap = "・"

  // The obsolete ゐ and ゑ are left out, as in today's teaching.
  private val basicRows = List(
    "あいうえお" -> "a i u e o",
    "かきくけこ" -> "ka ki ku ke ko",
    "さしすせそ" -> "sa shi su se so",
    "たちつてと" -> "ta chi tsu te to",
    "なにぬねの" -> "na ni nu ne no",
    "はひふへほ" -> "ha hi fu he ho",
    "まみむめも" -> "ma mi mu me mo",
    "や・ゆ・よ" -> "ya - yu - yo",
    "らりるれろ" -> "ra ri ru re ro",
    "わ・・・を" -> "wa - - - wo",
    "ん・・・・" -> "n - - - -"
  )

  private val voicedRows = List(
    "がぎぐげご" -> "ga gi gu ge go",
    "ざじずぜぞ" -> "za ji zu ze zo",
    "だぢづでど" -> "da ji zu de do",
    "ばびぶべぼ" -> "ba bi bu be bo",
    "ぱぴぷぺぽ" -> "pa pi pu pe po"
  )

  private val smallRows = List("ゃゅょっ・" -> "ya yu yo tsu -")

  /** The chart of a script, group by group. */
  def chart(script: Script): List[(KanaGroup, List[Row])] = List(
    KanaGroup.Basic -> basicRows,
    KanaGroup.Voiced -> voicedRows,
    KanaGroup.Small -> smallRows
  ).map((group, rows) =>
    group -> rows.map((characters, romaji) =>
      row(characters, romaji, script, group)
    )
  )

  val all: List[Kana] =
    Script.values.toList.flatMap(chart(_).flatMap(_._2.flatten.flatten))

  def find(character: String): Option[Kana] = all.find(_.character == character)

  // each kana that has a small form, then that form
  private val smallForms =
    "あぁいぃうぅえぇおぉつっやゃゆゅよょわゎアァイィウゥエェオォツッヤャユュヨョワヮ"
      .grouped(2)
      .toList

  /** A kana's small form, or a small kana's usual one (や and ゃ); the character
    * itself when it has none.
    */
  def smallOrLarge(character: String): String =
    toggled(smallForms, character).getOrElse(character)

  // each kana voiced by a dakuten, then voiced; those with a handakuten
  private val voicedForms =
    ("かがきぎくぐけげこごさざしじすずせぜそぞただちぢつづてでとどはばひびふぶへべほぼ" +
      "カガキギクグケゲコゴサザシジスズセゼソゾタダチヂツヅテデトドハバヒビフブヘベホボ")
      .grouped(2)
      .toList
  private val halfVoicedForms =
    "はぱひぴふぷへぺほぽハパヒピフプヘペホポ".grouped(2).toList

  private def toggled(pairs: List[String], character: String) =
    pairs.collectFirst {
      case pair if pair.take(1) == character => pair.drop(1)
      case pair if pair.drop(1) == character => pair.take(1)
    }

  /** A kana with a dakuten added (か to が, ぱ to ば), or taken away (が to か); the
    * character itself when it can have none.
    */
  def withDakuten(character: String): String =
    toggled(voicedForms, character)
      .orElse(toggled(halfVoicedForms, character).map(withDakuten))
      .getOrElse(character)

  /** A kana with a handakuten added (は to ぱ, ば to ぱ), or taken away (ぱ to は);
    * the character itself when it can have none.
    */
  def withHandakuten(character: String): String =
    toggled(halfVoicedForms, character)
      .orElse(
        voicedForms
          .find(_.drop(1) == character)
          .flatMap(pair => toggled(halfVoicedForms, pair.take(1)))
      )
      .getOrElse(character)

  /** A kana written as another with marks: が as か and a dakuten's two strokes,
    * ぱ as は and a handakuten's circle.
    */
  def marksOf(character: String): Option[(String, Int)] =
    voicedForms
      .find(_.drop(1) == character)
      .map(_.take(1) -> 2)
      .orElse(halfVoicedForms.find(_.drop(1) == character).map(_.take(1) -> 1))

  /** Whether a character is a small kana (ゃ, ッ, ァ). */
  def isSmall(character: String): Boolean =
    smallForms.exists(_.drop(1) == character)

  // Other romanisations of the same sound, as other systems or keyboards
  // write it; a small kana can be typed as its keyboard input.
  private val variants: Map[String, Set[String]] = Map(
    "shi" -> Set("si"),
    "chi" -> Set("ti"),
    "tsu" -> Set("tu"),
    "fu" -> Set("hu"),
    "ji" -> Set("zi", "di"),
    "zu" -> Set("du"),
    "wo" -> Set("o"),
    "n" -> Set("nn")
  )

  /** A kana's reading as shown: its romaji, and for ぢ and づ, read as じ and ず
    * are, the romaji they are typed with too ("ji (di)", "zu (du)"), so that
    * asked to write "ji", the learner knows which of the two is meant.
    */
  def shownReading(kana: Kana): String =
    if (kana.character == "ぢ" || kana.character == "ヂ") s"${kana.romaji} (di)"
    else if (kana.character == "づ" || kana.character == "ヅ")
      s"${kana.romaji} (du)"
    else kana.romaji

  /** Every reading accepted for a kana typed in romaji, in lower case. */
  def readings(kana: Kana): Set[String] = {
    // di and du are ぢ and づ only, not じ and ず
    val typedApart = Map("di" -> Set("ぢ", "ヂ"), "du" -> Set("づ", "ヅ"))
    val sounds = (variants.getOrElse(kana.romaji, Set.empty) + kana.romaji)
      .filter(sound => typedApart.get(sound).forall(_.contains(kana.character)))
    kana.group match {
      case KanaGroup.Small =>
        sounds ++ sounds.flatMap(sound => Set("x" + sound, "l" + sound))
      case _ => sounds
    }
  }

  /** Whether `typed` is a reading of `kana`, whatever its case and spaces. */
  def reads(kana: Kana, typed: String): Boolean =
    readings(kana).contains(typed.trim.toLowerCase)

  /** A script's kana row by row, in the chart's order, without its gaps. */
  def rows(script: Script): List[List[Kana]] =
    chart(script).flatMap(_._2).map(_.flatten)

  private def row(
      hiragana: String,
      romaji: String,
      script: Script,
      group: KanaGroup
  ): Row =
    hiragana.map(_.toString).toList.zip(romaji.split(' ')).map {
      (character, reading) =>
        Option.when(character != gap)(
          Kana(written(character, script), reading, script, group)
        )
    }

  // Each katakana sits at a fixed distance from its hiragana in Unicode.
  private def written(hiragana: String, script: Script): String =
    script match {
      case Script.Hiragana => hiragana
      case Script.Katakana => (hiragana.head + 0x60).toChar.toString
    }
}

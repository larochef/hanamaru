package hanamaru.shared.kana

/** The two kana scripts. */
enum Script {
  case Hiragana, Katakana
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

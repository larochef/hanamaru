package hanamaru.backend.reference

import hanamaru.backend.storage.Database
import hanamaru.shared.kanji.Kanji

import com.github.plokhotnyuk.jsoniter_scala.core.readFromString

/** The kanji, as imported from KANJIDIC2. */
final class KanjiBook(database: Database) {
  import Jmdict.given

  /** What the kanji were imported from: its date and SHA-256. */
  def version: Map[String, String] = database.read(
    _.query("SELECT key, value FROM kanji_info")(row =>
      row.string("key") -> row.string("value")
    ).toMap
  )

  /** A school grade's kanji, the most frequent first, the others by strokes. */
  def ofGrade(grade: Int): List[Kanji] = database.read(
    _.query(
      """SELECT * FROM kanji WHERE grade = ?
        |ORDER BY frequency IS NULL, frequency, strokes""".stripMargin,
      grade
    )(kanjiOf)
  )

  def kanji(literal: String): Option[Kanji] = database.read(
    _.queryOne("SELECT * FROM kanji WHERE literal = ?", literal)(kanjiOf)
  )

  private def kanjiOf(row: Database.Row): Kanji = {
    val french = readFromString[List[String]](row.string("french"))
    Kanji(
      row.string("literal"),
      row.intOption("grade"),
      row.int("strokes"),
      row.intOption("frequency"),
      readFromString[List[String]](row.string("on_readings")),
      readFromString[List[String]](row.string("kun_readings")),
      if (french.nonEmpty) french
      else readFromString[List[String]](row.string("english")),
      inEnglish = french.isEmpty
    )
  }
}

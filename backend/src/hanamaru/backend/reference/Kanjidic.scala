package hanamaru.backend.reference

import java.io.{BufferedInputStream, InputStream}
import java.net.URI
import java.nio.file.*
import java.sql.DriverManager
import java.util.zip.GZIPInputStream
import javax.xml.stream.*
import scala.collection.mutable.ListBuffer
import scala.util.Using

import com.github.plokhotnyuk.jsoniter_scala.core.writeToString

/** KANJIDIC2 (https://www.edrdg.org/wiki/index.php/KANJIDIC_Project, EDRDG, CC
  * BY-SA 4.0): every kanji with its school grade, stroke count, frequency rank,
  * readings and meanings (French among them).
  *
  * Like JMdict, it is published again every day under the same name: it is
  * downloaded once, and kept, with its date and SHA-256.
  */
object Kanjidic {
  import Jmdict.given
  val uri: URI = URI.create("http://ftp.edrdg.org/pub/Nihongo/kanjidic2.xml.gz")

  /** The shape of the database it is imported into. */
  val schemaVersion = "1"

  /** A kanji as KANJIDIC2 gives it; `grade` 1 to 6 for the school years, 8 for
    * the rest of the jōyō list, 9 and 10 for name kanji.
    */
  final case class Character(
      literal: String,
      grade: Option[Int],
      strokes: Int,
      frequency: Option[Int],
      on: List[String],
      kun: List[String],
      french: List[String],
      english: List[String]
  )

  /** Every kanji of a file that has a grade or a frequency rank: the rest (rare
    * characters) is left out.
    */
  def characters(gzipped: InputStream)(each: Character => Unit): Unit = {
    val factory = XMLInputFactory.newInstance()
    // its DTD is inside the file: read, nothing outside it fetched
    factory.setProperty(XMLInputFactory.SUPPORT_DTD, true)
    factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false)
    val reader = factory.createXMLStreamReader(
      BufferedInputStream(GZIPInputStream(gzipped)),
      "UTF-8"
    )
    try {
      while (reader.hasNext) {
        if (
          reader.next() == XMLStreamConstants.START_ELEMENT &&
          reader.getLocalName == "character"
        ) {
          val read = character(reader)
          if (read.grade.nonEmpty || read.frequency.nonEmpty) each(read)
        }
      }
    } finally reader.close()
  }

  /** The date the file was made, from its header. */
  def createdIn(gzipped: InputStream): Option[String] = {
    val created =
      """<date_of_creation>([^<]+)</date_of_creation>""".r.unanchored
    Using.resource(
      scala.io.Source.fromInputStream(GZIPInputStream(gzipped), "UTF-8")
    )(_.getLines().take(2000).collectFirst { case created(date) => date })
  }

  private def character(reader: XMLStreamReader): Character = {
    var literal = ""
    var grade = Option.empty[Int]
    var strokes = 0
    var frequency = Option.empty[Int]
    val on = ListBuffer[String]()
    val kun = ListBuffer[String]()
    val french = ListBuffer[String]()
    val english = ListBuffer[String]()
    var depth = 1
    while (depth > 0) {
      reader.next() match {
        case XMLStreamConstants.START_ELEMENT =>
          reader.getLocalName match {
            case "literal" => literal = reader.getElementText
            case "grade"   => grade = reader.getElementText.toIntOption
            // the first count is the right one; others are common mistakes
            case "stroke_count" if strokes == 0 =>
              strokes = reader.getElementText.toInt
            case "freq"    => frequency = reader.getElementText.toIntOption
            case "reading" =>
              val kind = reader.getAttributeValue(null, "r_type")
              val text = reader.getElementText
              if (kind == "ja_on") on += text
              else if (kind == "ja_kun") kun += text
            case "meaning" =>
              val language = Option(reader.getAttributeValue(null, "m_lang"))
              val text = reader.getElementText
              if (language.isEmpty) english += text
              else if (language.contains("fr")) french += text
            case _ => depth += 1
          }
        case XMLStreamConstants.END_ELEMENT => depth -= 1
        case _                              => ()
      }
    }
    Character(
      literal,
      grade,
      strokes,
      frequency,
      on.toList,
      kun.toList,
      french.toList,
      english.toList
    )
  }

  /** Builds the kanji's database from a KANJIDIC2 file: written apart, then put
    * in place whole. `version` is kept with it.
    */
  def imported(
      kanjidic: Path,
      database: Path,
      version: Map[String, String]
  ): Int = {
    val partial =
      Files.createTempFile(
        database.getParent,
        database.getFileName.toString,
        ".part"
      )
    Files.delete(partial)
    var count = 0
    try {
      Using.resource(DriverManager.getConnection(s"jdbc:sqlite:$partial")) {
        connection =>
          Using.resource(connection.createStatement()) { statement =>
            statement.executeUpdate(
              """CREATE TABLE kanji (
              |  literal TEXT PRIMARY KEY,
              |  grade INTEGER,
              |  strokes INTEGER NOT NULL,
              |  frequency INTEGER,
              |  on_readings TEXT NOT NULL,
              |  kun_readings TEXT NOT NULL,
              |  french TEXT NOT NULL,
              |  english TEXT NOT NULL
              |)""".stripMargin
            )
            statement.executeUpdate("CREATE INDEX kanji_grade ON kanji (grade)")
            statement.executeUpdate(
              "CREATE TABLE kanji_info (key TEXT PRIMARY KEY, value TEXT NOT NULL)"
            )
          }
          connection.setAutoCommit(false)
          Using.resource(
            connection.prepareStatement(
              """INSERT INTO kanji (literal, grade, strokes, frequency, on_readings,
              |kun_readings, french, english) VALUES (?, ?, ?, ?, ?, ?, ?, ?)""".stripMargin
            )
          ) { insert =>
            Using.resource(Files.newInputStream(kanjidic)) { input =>
              characters(input) { read =>
                insert.setString(1, read.literal)
                read.grade.fold(insert.setNull(2, java.sql.Types.INTEGER))(
                  insert.setInt(2, _)
                )
                insert.setInt(3, read.strokes)
                read.frequency.fold(insert.setNull(4, java.sql.Types.INTEGER))(
                  insert.setInt(4, _)
                )
                insert.setString(5, writeToString(read.on))
                insert.setString(6, writeToString(read.kun))
                insert.setString(7, writeToString(read.french))
                insert.setString(8, writeToString(read.english))
                insert.addBatch()
                count += 1
              }
            }
            insert.executeBatch()
          }
          Using.resource(
            connection.prepareStatement(
              "INSERT INTO kanji_info (key, value) VALUES (?, ?)"
            )
          ) { insert =>
            version.foreach { (key, value) =>
              insert.setString(1, key)
              insert.setString(2, value)
              insert.addBatch()
            }
            insert.executeBatch()
          }
          connection.commit()
      }
      Files.move(
        partial,
        database,
        StandardCopyOption.REPLACE_EXISTING,
        StandardCopyOption.ATOMIC_MOVE
      )
      count
    } finally Files.deleteIfExists(partial)
  }
}

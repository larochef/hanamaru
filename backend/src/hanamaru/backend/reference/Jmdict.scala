package hanamaru.backend.reference

import hanamaru.backend.storage.Database

import java.io.{BufferedInputStream, InputStream}
import java.net.URI
import java.nio.file.*
import java.sql.DriverManager
import java.util.zip.GZIPInputStream
import javax.xml.stream.*
import scala.collection.mutable.ListBuffer
import scala.util.Using

import com.github.plokhotnyuk.jsoniter_scala.core.{
  writeToString,
  JsonValueCodec
}
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker

/** JMdict (https://www.edrdg.org/jmdict/j_jmdict.html, EDRDG, CC BY-SA 4.0):
  * the dictionary, every word with its written forms, its readings, its senses
  * and their glosses in several languages, and its frequency marks.
  *
  * It is published again every day under the same name, with no version to pin:
  * it is downloaded once, and kept, with its date and SHA-256, until it is
  * asked for again.
  */
object Jmdict {
  val uri: URI = URI.create("http://ftp.edrdg.org/pub/Nihongo/JMdict.gz")

  /** The database's schema, moved on by its migrations. */
  val schema: Database.Schema =
    Database.Schema("Dictionary", "db/dictionary", baselined = true)

  /** What is imported, kept in the database under `schema` (its name from the
    * first imports): a database imported otherwise is imported again. Its
    * tables' shape moves on by [[schema]]'s migrations instead.
    */
  val importVersion = "2"

  /** The languages whose glosses are kept. */
  val languages: Set[String] = Set("eng", "fre")

  // the priority marks JMdict's documentation calls common
  private val commonMarks = Set("news1", "ichi1", "spec1", "spec2", "gai1")

  /** A word as JMdict describes it. `rank` is its frequency rank, from 1 (the
    * 500 most frequent words) to 48, when it has one.
    */
  final case class Entry(
      id: Long,
      rank: Option[Int],
      common: Boolean,
      forms: List[Form],
      senses: List[Sense]
  )

  /** A written form (`kanji`) or a reading (`kana`), with JMdict's codes about
    * it (rare, irregular…) and, for a reading, the forms it is restricted to.
    */
  final case class Form(
      kana: Boolean,
      text: String,
      common: Boolean,
      info: List[String],
      restrictedTo: List[String]
  )

  /** A sense in one language: its parts of speech and usage codes (on English
    * senses only, as JMdict gives them), and its glosses.
    */
  final case class Sense(
      language: String,
      partsOfSpeech: List[String],
      misc: List[String],
      glosses: List[String]
  )

  given JsonValueCodec[List[String]] = JsonCodecMaker.make

  /** Reads every entry of a JMdict file, gzipped, as it goes. */
  def entries(gzipped: InputStream)(each: Entry => Unit): Unit = {
    val reader = parser().createXMLStreamReader(
      GZIPInputStream(BufferedInputStream(gzipped, 1 << 16), 1 << 16),
      "UTF-8"
    )
    try {
      while (reader.hasNext) {
        if (
          reader
            .next() == XMLStreamConstants.START_ELEMENT && reader.getLocalName == "entry"
        ) {
          each(entry(reader))
        }
      }
    } finally reader.close()
  }

  /** The date JMdict says the file was made, from its header. */
  def createdIn(gzipped: InputStream): Option[String] = {
    val created = """<!-- JMdict created: (\S+) -->""".r
    Using.resource(
      scala.io.Source.fromInputStream(GZIPInputStream(gzipped), "UTF-8")
    )(
      _.getLines().take(5000).collectFirst { case created(date) => date }
    )
  }

  // Codes such as &n; are kept as their names: the DTD is read for them only,
  // nothing outside the file is fetched, and JMdict's many references are
  // allowed.
  private def parser(): XMLInputFactory = {
    val factory = XMLInputFactory.newInstance()
    factory.setProperty(XMLInputFactory.SUPPORT_DTD, true)
    factory.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, false)
    factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false)
    factory.setProperty(
      "http://www.oracle.com/xml/jaxp/properties/entityExpansionLimit",
      "0"
    )
    factory.setProperty(
      "http://www.oracle.com/xml/jaxp/properties/totalEntitySizeLimit",
      "0"
    )
    factory.setProperty(
      "http://www.oracle.com/xml/jaxp/properties/maxGeneralEntitySizeLimit",
      "0"
    )
    factory
  }

  private def entry(reader: XMLStreamReader): Entry = {
    var id = 0L
    val forms = ListBuffer.empty[Form]
    val senses = ListBuffer.empty[Sense]
    val marks = ListBuffer.empty[String]
    while (
      !(reader
        .next() == XMLStreamConstants.END_ELEMENT && reader.getLocalName == "entry")
    ) {
      if (reader.getEventType == XMLStreamConstants.START_ELEMENT) {
        reader.getLocalName match {
          case "ent_seq" => id = reader.getElementText.toLong
          case "k_ele"   =>
            forms += form(reader, "k_ele", "keb", "ke_inf", "ke_pri", marks)
          case "r_ele" =>
            forms += form(reader, "r_ele", "reb", "re_inf", "re_pri", marks)
          case "sense" => senses += sense(reader)
          case _       => ()
        }
      }
    }
    val rank = marks.collect {
      case mark if mark.startsWith("nf") => mark.drop(2).toIntOption
    }.flatten
    Entry(
      id,
      rank.minOption,
      marks.exists(commonMarks),
      forms.toList,
      senses.toList.filter(sense =>
        languages(sense.language) && sense.glosses.nonEmpty
      )
    )
  }

  private def form(
      reader: XMLStreamReader,
      element: String,
      textElement: String,
      infoElement: String,
      priorityElement: String,
      marks: ListBuffer[String]
  ): Form = {
    var text = ""
    val info = ListBuffer.empty[String]
    val priorities = ListBuffer.empty[String]
    val restricted = ListBuffer.empty[String]
    while (
      !(reader
        .next() == XMLStreamConstants.END_ELEMENT && reader.getLocalName == element)
    ) {
      if (reader.getEventType == XMLStreamConstants.START_ELEMENT) {
        reader.getLocalName match {
          case `textElement`     => text = reader.getElementText
          case `infoElement`     => info ++= codes(reader, infoElement)
          case `priorityElement` => priorities += reader.getElementText
          case "re_restr"        => restricted += reader.getElementText
          case _                 => ()
        }
      }
    }
    marks ++= priorities
    Form(
      element == "r_ele",
      text,
      priorities.exists(commonMarks),
      info.toList,
      restricted.toList
    )
  }

  private def sense(reader: XMLStreamReader): Sense = {
    var language = "eng"
    val partsOfSpeech = ListBuffer.empty[String]
    val misc = ListBuffer.empty[String]
    val glosses = ListBuffer.empty[String]
    while (
      !(reader
        .next() == XMLStreamConstants.END_ELEMENT && reader.getLocalName == "sense")
    ) {
      if (reader.getEventType == XMLStreamConstants.START_ELEMENT) {
        reader.getLocalName match {
          case "pos"   => partsOfSpeech ++= codes(reader, "pos")
          case "misc"  => misc ++= codes(reader, "misc")
          case "gloss" =>
            language = Option(
              reader.getAttributeValue(
                "http://www.w3.org/XML/1998/namespace",
                "lang"
              )
            ).getOrElse("eng")
            glosses += reader.getElementText
          case _ => ()
        }
      }
    }
    Sense(language, partsOfSpeech.toList, misc.toList, glosses.toList)
  }

  // the names of the codes an element holds, as &n; &vs;
  private def codes(reader: XMLStreamReader, element: String): List[String] = {
    val found = ListBuffer.empty[String]
    while (
      !(reader
        .next() == XMLStreamConstants.END_ELEMENT && reader.getLocalName == element)
    ) {
      if (reader.getEventType == XMLStreamConstants.ENTITY_REFERENCE) {
        found += reader.getLocalName
      }
    }
    found.toList
  }

  /** Builds the dictionary's database from a JMdict file: written apart, then
    * put in place whole. `version` is kept with it, as `created` and `sha256`.
    */
  def imported(
      jmdict: Path,
      core: List[(Int, List[Long])],
      database: Path,
      version: Map[String, String]
  ): Int = {
    val partial = Files.createTempFile(
      database.getParent,
      database.getFileName.toString,
      ".part"
    )
    Files.delete(partial)
    var count = 0
    try {
      Database.migrate(partial, schema)
      Using.resource(DriverManager.getConnection(s"jdbc:sqlite:$partial")) {
        connection =>
          Using.resource(connection.createStatement()) { statement =>
            statement.execute("PRAGMA journal_mode = OFF")
            statement.execute("PRAGMA synchronous = OFF")
          }
          connection.setAutoCommit(false)
          Using.Manager { use =>
            def prepared(sql: String) = use(connection.prepareStatement(sql))
            val words =
              prepared("INSERT INTO word (id, rank, common) VALUES (?, ?, ?)")
            val forms = prepared(
              """INSERT INTO form (word_id, position, kana, text, common, info, restricted_to)
              |VALUES (?, ?, ?, ?, ?, ?, ?)""".stripMargin
            )
            val senses = prepared(
              """INSERT INTO sense (word_id, position, language, parts_of_speech, misc, glosses)
              |VALUES (?, ?, ?, ?, ?, ?)""".stripMargin
            )
            Using.resource(Files.newInputStream(jmdict)) { input =>
              entries(input) { entry =>
                words.setLong(1, entry.id)
                entry.rank.fold(words.setNull(2, java.sql.Types.INTEGER))(
                  words.setInt(2, _)
                )
                words.setBoolean(3, entry.common)
                words.addBatch()
                entry.forms.zipWithIndex.foreach { (form, position) =>
                  forms.setLong(1, entry.id)
                  forms.setInt(2, position)
                  forms.setBoolean(3, form.kana)
                  forms.setString(4, form.text)
                  forms.setBoolean(5, form.common)
                  forms.setString(6, writeToString(form.info))
                  forms.setString(7, writeToString(form.restrictedTo))
                  forms.addBatch()
                }
                entry.senses.zipWithIndex.foreach { (sense, position) =>
                  senses.setLong(1, entry.id)
                  senses.setInt(2, position)
                  senses.setString(3, sense.language)
                  senses.setString(4, writeToString(sense.partsOfSpeech))
                  senses.setString(5, writeToString(sense.misc))
                  senses.setString(6, writeToString(sense.glosses))
                  senses.addBatch()
                }
                count += 1
                if (count % 10000 == 0) {
                  Seq(words, forms, senses).foreach(_.executeBatch())
                }
              }
            }
            Seq(words, forms, senses).foreach(_.executeBatch())
            // the core vocabulary, a word in the first (easiest) list it is in
            val coreWords = prepared(
              "INSERT INTO core_word (word_id, level, position) VALUES (?, ?, ?)"
            )
            core
              .flatMap((level, entries) => entries.map(level -> _))
              .distinctBy(_._2)
              .zipWithIndex
              .foreach { case ((level, entry), position) =>
                coreWords.setLong(1, entry)
                coreWords.setInt(2, level)
                coreWords.setInt(3, position)
                coreWords.addBatch()
              }
            coreWords.executeBatch()
            val info =
              prepared("INSERT INTO dictionary_info (key, value) VALUES (?, ?)")
            version.foreach { (key, value) =>
              info.setString(1, key)
              info.setString(2, value)
              info.addBatch()
            }
            info.executeBatch()
          }.get
          connection.commit()
      }
      Files.move(
        partial,
        database,
        StandardCopyOption.ATOMIC_MOVE,
        StandardCopyOption.REPLACE_EXISTING
      )
      count
    } finally {
      Files.deleteIfExists(partial)
    }
  }

}

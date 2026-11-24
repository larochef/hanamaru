package hanamaru.backend.reference

import hanamaru.backend.storage.Database
import hanamaru.shared.ReferenceDataState

import java.nio.file.{Files, Path}
import java.time.Instant
import scala.util.Using
import scala.util.control.NonFatal

import com.typesafe.scalalogging.Logger

/** The open datasets the application reads its facts from, downloaded into the
  * cache on first start, meanwhile the server serves: the strokes (KanjiVG)
  * first, then the dictionary (JMdict), then the analyser's dictionary
  * (Sudachi), then the kanji (KANJIDIC2), each usable as soon as it is ready.
  */
final class ReferenceData(directory: Path, download: Download) {
  private val logger = Logger("hanamaru.backend.reference")

  @volatile private var strokesState: ReferenceDataState =
    ReferenceDataState.Preparing
  @volatile private var dictionaryState: ReferenceDataState =
    ReferenceDataState.Preparing
  @volatile private var analyserState: ReferenceDataState =
    ReferenceDataState.Preparing
  @volatile private var analysis: Option[Analyser] = None
  @volatile private var kanjiState: ReferenceDataState =
    ReferenceDataState.Preparing
  @volatile private var book: Option[KanjiBook] = None
  @volatile private var archive: Option[KanjiVgArchive] = None
  @volatile private var words: Option[Dictionary] = None

  def strokes: ReferenceDataState = strokesState

  def dictionaryData: ReferenceDataState = dictionaryState

  def analyserData: ReferenceDataState = analyserState

  def analyser: Option[Analyser] = analysis

  def kanjiData: ReferenceDataState = kanjiState

  def kanji: Option[KanjiBook] = book

  def kanjiVg: Option[KanjiVgArchive] = archive

  def dictionary: Option[Dictionary] = words

  def prepare(): Unit = {
    strokesState = prepared("KanjiVG") {
      val file = KanjiVg.archiveIn(directory)
      if (!Files.exists(file)) {
        logger.info(s"Downloading KanjiVG ${KanjiVg.version}")
        download.fetched(KanjiVg.uri, Some(KanjiVg.sha256), file)
      }
      archive = Some(KanjiVgArchive(file))
      s"KanjiVG ${KanjiVg.version} ready"
    }
    dictionaryState = prepared("JMdict") {
      val opened = Dictionary(Database.connect(importedDictionary()))
      words = Some(opened)
      s"JMdict of ${opened.version.getOrElse("created", "?")} ready"
    }
    analyserState = prepared("Sudachi") {
      val file = Analyser.dictionaryIn(directory)
      if (!Files.exists(file)) {
        logger.info(s"Downloading Sudachi's dictionary ${Analyser.version}")
        val archive = directory.resolve(s"sudachi-${Analyser.version}-core.zip")
        download.fetched(Analyser.uri, Some(Analyser.sha256), archive)
        Analyser.installed(archive, file)
        Files.delete(archive)
      }
      analysis = Some(Analyser(file))
      s"Sudachi's dictionary ${Analyser.version} ready"
    }
    kanjiState = prepared("KANJIDIC2") {
      val opened = KanjiBook(Database.connect(importedKanji()))
      book = Some(opened)
      s"KANJIDIC2 of ${opened.version.getOrElse("created", "?")} ready"
    }
  }

  // The kanji's database, imported once from KANJIDIC2's file, itself
  // downloaded once and kept: again only when the database's shape changes.
  private def importedKanji(): Path = {
    val database = directory.resolve("kanji.db")
    val current = Files.exists(database) &&
      Using
        .resource(Database.connect(database))(
          _.read(
            _.queryOne("SELECT value FROM kanji_info WHERE key = 'schema'")(
              _.string("value")
            )
          )
        )
        .contains(Kanjidic.schemaVersion)
    if (!current) {
      val file = directory.resolve("kanjidic2.xml.gz")
      val sha256 =
        if (Files.exists(file)) Download.sha256Of(file)
        else {
          logger.info("Downloading KANJIDIC2")
          download.fetched(Kanjidic.uri, None, file)
        }
      val created =
        Using.resource(Files.newInputStream(file))(Kanjidic.createdIn)
      val count = Kanjidic.imported(
        file,
        database,
        Map(
          "schema" -> Kanjidic.schemaVersion,
          "created" -> created.getOrElse("unknown"),
          "sha256" -> sha256,
          "imported" -> Instant.now().toString
        )
      )
      logger.info(s"KANJIDIC2 imported: $count kanji")
    }
    database
  }

  // The dictionary's database, imported once from JMdict's file, itself
  // downloaded once and kept: again only when the database's shape changes.
  private def importedDictionary(): Path = {
    val database = directory.resolve("dictionary.db")
    val current = Files.exists(database) &&
      Using
        .resource(Database.connect(database))(
          _.read(
            _.queryOne(
              "SELECT value FROM dictionary_info WHERE key = 'schema'"
            )(
              _.string("value")
            )
          )
        )
        .contains(Jmdict.schemaVersion)
    if (!current) {
      val file = directory.resolve("JMdict.gz")
      val sha256 =
        if (Files.exists(file)) {
          Download.sha256Of(file)
        } else {
          logger.info("Downloading JMdict")
          download.fetched(Jmdict.uri, None, file)
        }
      val created = Using.resource(Files.newInputStream(file))(Jmdict.createdIn)
      val core = JlptLists.levels.map { (level, sha256) =>
        val list = JlptLists.fileIn(directory, level)
        if (!Files.exists(list)) {
          download.fetched(JlptLists.uri(level), Some(sha256), list)
        }
        level -> JlptLists.entries(list)
      }
      val count = Jmdict.imported(
        file,
        core,
        database,
        Map(
          "schema" -> Jmdict.schemaVersion,
          "created" -> created.getOrElse("unknown"),
          "sha256" -> sha256,
          "imported" -> Instant.now().toString
        )
      )
      logger.info(s"JMdict imported: $count words")
    }
    database
  }

  private def prepared(name: String)(work: => String): ReferenceDataState =
    try {
      logger.info(work)
      ReferenceDataState.Ready
    } catch {
      case NonFatal(error) =>
        logger.warn(s"$name could not be prepared", error)
        ReferenceDataState.Failed(
          Option(error.getMessage).getOrElse(error.toString)
        )
    }
}

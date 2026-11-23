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
  * first, then the dictionary (JMdict), each usable as soon as it is ready.
  */
final class ReferenceData(directory: Path, download: Download) {
  private val logger = Logger("hanamaru.backend.reference")

  @volatile private var strokesState: ReferenceDataState =
    ReferenceDataState.Preparing
  @volatile private var dictionaryState: ReferenceDataState =
    ReferenceDataState.Preparing
  @volatile private var archive: Option[KanjiVgArchive] = None
  @volatile private var words: Option[Dictionary] = None

  def strokes: ReferenceDataState = strokesState

  def dictionaryData: ReferenceDataState = dictionaryState

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

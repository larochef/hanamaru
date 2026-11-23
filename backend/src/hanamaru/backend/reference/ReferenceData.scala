package hanamaru.backend.reference

import hanamaru.shared.ReferenceDataState

import java.nio.file.{Files, Path}
import scala.util.control.NonFatal

import com.typesafe.scalalogging.Logger

/** The open datasets the application reads its facts from, downloaded into the
  * cache on first start, meanwhile the server serves.
  */
final class ReferenceData(directory: Path, download: Download) {
  private val logger = Logger("hanamaru.backend.reference")

  @volatile private var current: ReferenceDataState =
    ReferenceDataState.Preparing
  @volatile private var archive: Option[KanjiVgArchive] = None

  def state: ReferenceDataState = current

  def kanjiVg: Option[KanjiVgArchive] = archive

  def prepare(): Unit = {
    try {
      val file = KanjiVg.archiveIn(directory)
      if (!Files.exists(file)) {
        logger.info(s"Downloading KanjiVG ${KanjiVg.version}")
        download.fetched(KanjiVg.uri, KanjiVg.sha256, file)
      }
      archive = Some(KanjiVgArchive(file))
      current = ReferenceDataState.Ready
      logger.info(s"KanjiVG ${KanjiVg.version} ready")
    } catch {
      case NonFatal(error) =>
        logger.warn("The reference data could not be prepared", error)
        current = ReferenceDataState.Failed(
          Option(error.getMessage).getOrElse(error.toString)
        )
    }
  }
}

package hanamaru.backend.models

import java.io.FileReader
import java.nio.file.{Files, Path}
import java.util.Properties
import scala.jdk.CollectionConverters.*
import scala.util.Using

/** The settings of this machine, kept outside the repository in a properties
  * file (`key = value` lines): where its model servers and models are. A
  * missing file is no settings.
  */
final case class LocalSettings(values: Map[String, String]) {
  def get(key: String): Option[String] =
    values.get(key).map(_.trim).filter(_.nonEmpty)
}

object LocalSettings {

  /** The path to the `llama-server` executable. */
  val llamaServer = "llama.server"

  /** The Hugging Face repository the tutor's model is read from, and its file.
    */
  val modelRepository = "model.repository"
  val modelFile = "model.file"

  /** The tokens of context the tutor's model is given. */
  val modelContext = "model.context"

  def read(file: Path): LocalSettings =
    if (!Files.exists(file)) {
      LocalSettings(Map.empty)
    } else {
      val properties = Properties()
      Using.resource(FileReader(file.toFile))(properties.load)
      LocalSettings(properties.asScala.toMap)
    }
}

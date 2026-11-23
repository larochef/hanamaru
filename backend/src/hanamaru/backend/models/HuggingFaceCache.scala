package hanamaru.backend.models

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*
import scala.util.Using

/** The Hugging Face cache of this machine, where models downloaded with its
  * tools are kept: `HF_HUB_CACHE`, else `HF_HOME/hub`, else
  * `~/.cache/huggingface/hub`.
  */
final class HuggingFaceCache(root: Path) {

  /** A file of a repository, from the snapshot its `main` reference names, or
    * else from any snapshot that has it.
    */
  def file(repository: String, name: String): Option[Path] = {
    val folder = root.resolve("models--" + repository.replace("/", "--"))
    val snapshots = folder.resolve("snapshots")
    val main = Option(folder.resolve("refs/main"))
      .filter(Files.isRegularFile(_))
      .map(Files.readString(_).trim)
      .map(snapshots.resolve)
    val others =
      if (Files.isDirectory(snapshots)) {
        Using.resource(Files.list(snapshots))(
          _.iterator().asScala.toList.sorted
        )
      } else {
        Nil
      }
    (main.toList ++ others).map(_.resolve(name)).find(Files.exists(_))
  }
}

object HuggingFaceCache {

  def rootOf(environment: Map[String, String], home: Path): Path =
    environment
      .get("HF_HUB_CACHE")
      .map(Path.of(_))
      .orElse(environment.get("HF_HOME").map(Path.of(_).resolve("hub")))
      .getOrElse(home.resolve(".cache/huggingface/hub"))
}

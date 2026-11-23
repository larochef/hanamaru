package hanamaru.backend.storage

import java.nio.file.{Files, Path}

/** Where Hanamaru keeps what it keeps, as the XDG base directories say: the
  * learner's data under `~/.local/share/hanamaru`, what it can make again
  * (imported reference data, generated audio) under `~/.cache/hanamaru`.
  * `HANAMARU_DATA` and `HANAMARU_CACHE` move them.
  */
final case class Places(data: Path, cache: Path) {
  def database: Path = data.resolve("learner.db")

  /** The open datasets, as downloaded. */
  def reference: Path = cache.resolve("reference")

  def created(): Places = {
    Files.createDirectories(data)
    Files.createDirectories(cache)
    this
  }
}

object Places {
  private val name = "hanamaru"

  def fromEnvironment(environment: Map[String, String], home: Path): Places = {
    def xdg(variable: String, fallback: String): Path =
      environment
        .get(variable)
        .filter(_.nonEmpty)
        .map(Path.of(_))
        .getOrElse(home.resolve(fallback))
        .resolve(name)
    Places(
      data = environment
        .get("HANAMARU_DATA")
        .map(Path.of(_))
        .getOrElse(xdg("XDG_DATA_HOME", ".local/share")),
      cache = environment
        .get("HANAMARU_CACHE")
        .map(Path.of(_))
        .getOrElse(xdg("XDG_CACHE_HOME", ".cache"))
    )
  }
}

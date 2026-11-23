package hanamaru.backend.storage

import java.nio.file.{Files, Path}

/** Where Hanamaru keeps what it keeps, as the XDG base directories say: the
  * learner's data under `~/.local/share/hanamaru`, what it can make again
  * (imported reference data, generated audio, logs) under `~/.cache/hanamaru`,
  * the settings of this machine under `~/.config/hanamaru`. `HANAMARU_DATA`,
  * `HANAMARU_CACHE` and `HANAMARU_CONFIG` move them.
  */
final case class Places(data: Path, cache: Path, config: Path) {
  def database: Path = data.resolve("learner.db")

  /** Every character written, one JSON line each, as `WritingAttempts` keeps
    * them.
    */
  def writingAttempts: Path = data.resolve("writing-attempts.jsonl")

  /** The open datasets, as downloaded. */
  def reference: Path = cache.resolve("reference")

  /** What the model servers write as they run. */
  def logs: Path = cache.resolve("logs")

  /** The settings of this machine: paths to its model servers and models. */
  def settings: Path = config.resolve("hanamaru.properties")

  def created(): Places = {
    Files.createDirectories(data)
    Files.createDirectories(cache)
    Files.createDirectories(logs)
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
        .getOrElse(xdg("XDG_CACHE_HOME", ".cache")),
      config = environment
        .get("HANAMARU_CONFIG")
        .map(Path.of(_))
        .getOrElse(xdg("XDG_CONFIG_HOME", ".config"))
    )
  }
}

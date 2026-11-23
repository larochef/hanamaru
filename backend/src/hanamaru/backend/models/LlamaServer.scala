package hanamaru.backend.models

import hanamaru.shared.ModelState

import java.net.{ServerSocket, URI}
import java.net.http.{HttpClient, HttpRequest}
import java.net.http.HttpResponse.BodyHandlers
import java.nio.file.{Files, Path}
import java.time.Duration
import java.util.concurrent.TimeUnit
import scala.util.Using
import scala.util.control.NonFatal

import com.typesafe.scalalogging.Logger

/** The tutor's model, served by `llama-server` (llama.cpp) as a process of its
  * own: launched as the backend starts, on a free port of this machine only,
  * watched until it answers, launched again if it stops, stopped with the
  * backend. What it writes goes to its log file, not to the backend's.
  */
final class LlamaServer(
    settings: LocalSettings,
    settingsFile: Path,
    huggingFace: HuggingFaceCache,
    logs: Path,
    http: HttpClient
) {
  private val logger = Logger("hanamaru.backend.models")

  private val defaultRepository = "unsloth/gemma-4-26b-a4b-it-GGUF"
  private val defaultFile = "gemma-4-26B-A4B-it-UD-Q4_K_XL.gguf"
  private val defaultContext = 16384
  private val alias = "tutor"
  private val readyTimeout = Duration.ofMinutes(5)
  private val attempts = 3
  private val pause = Duration.ofSeconds(5)

  @volatile private var current: ModelState = ModelState.Starting
  @volatile private var process: Option[Process] = None
  @volatile private var stopping = false

  def state: ModelState = current

  private def log: Path = logs.resolve("llama-server.log")

  /** Launches the server and keeps it running until `stop`; returns when it
    * cannot be launched, or has failed too many times in a row.
    */
  def run(): Unit = {
    val repository =
      settings.get(LocalSettings.modelRepository).getOrElse(defaultRepository)
    val name = settings.get(LocalSettings.modelFile).getOrElse(defaultFile)
    (
      settings.get(LocalSettings.llamaServer),
      huggingFace.file(repository, name)
    ) match {
      case (None, _) =>
        current = ModelState.NotConfigured(
          LocalSettings.llamaServer,
          settingsFile.toString
        )
        logger.info(
          s"No tutor model: ${LocalSettings.llamaServer} is not set in $settingsFile"
        )
      case (Some(executable), _) if !Files.isExecutable(Path.of(executable)) =>
        current = ModelState.Failed(s"$executable is not an executable file")
      case (_, None) =>
        current = ModelState.ModelMissing(s"$repository/$name")
        logger.info(
          s"No tutor model: $repository/$name is not in the Hugging Face cache"
        )
      case (Some(executable), Some(model)) =>
        supervised(Path.of(executable), model, failures = 0)
    }
  }

  def stop(): Unit = {
    stopping = true
    process.foreach { running =>
      running.destroy()
      if (!running.waitFor(10, TimeUnit.SECONDS)) {
        running.destroyForcibly()
      }
    }
  }

  // launched again after each stop it did not ask for, until it fails too
  // often in a row
  @annotation.tailrec
  private def supervised(executable: Path, model: Path, failures: Int): Unit = {
    val outcome =
      try launched(executable, model)
      catch {
        case NonFatal(error) =>
          Left(Option(error.getMessage).getOrElse(error.toString))
      }
    if (!stopping) {
      val failed = outcome.isLeft
      val reason = outcome.left.toOption.getOrElse("it stopped")
      if (failed && failures + 1 >= attempts) {
        current = ModelState.Failed(reason)
        logger.warn(s"The tutor model could not be served: $reason (see $log)")
      } else {
        current = ModelState.Starting
        logger.warn(s"The tutor model stopped: $reason; launching it again")
        Thread.sleep(pause.toMillis)
        supervised(executable, model, if (failed) failures + 1 else 0)
      }
    }
  }

  // Runs one server until it stops: Left when it never became ready.
  private def launched(executable: Path, model: Path): Either[String, Unit] = {
    val port = freePort()
    val context = settings
      .get(LocalSettings.modelContext)
      .flatMap(_.toIntOption)
      .getOrElse(defaultContext)
    current = ModelState.Starting
    val started = System.nanoTime()
    val running = ProcessBuilder(
      executable.toString,
      "--model",
      model.toString,
      "--host",
      "127.0.0.1",
      "--port",
      port.toString,
      "--alias",
      alias,
      // every layer on the GPU, one request at a time
      "--gpu-layers",
      "999",
      "--parallel",
      "1",
      "--ctx-size",
      context.toString,
      // answers are read by the backend: no thinking aloud before them
      "--reasoning",
      "off",
      "--no-webui"
    ).redirectErrorStream(true)
      .redirectOutput(log.toFile)
      .start()
    process = Some(running)
    val uri = URI.create(s"http://127.0.0.1:$port")
    if (awaitReady(running, uri)) {
      current = ModelState.Ready
      val seconds = (System.nanoTime() - started) / 1e9
      logger.info(f"Tutor model ready in $seconds%.1f s: ${model.getFileName}")
      running.waitFor()
      Right(())
    } else {
      running.destroyForcibly()
      Left(lastLine().getOrElse("it did not become ready"))
    }
  }

  private def awaitReady(running: Process, uri: URI): Boolean = {
    val deadline = System.nanoTime() + readyTimeout.toNanos
    val health = HttpRequest.newBuilder(uri.resolve("/health")).GET().build()
    def answers =
      try http.send(health, BodyHandlers.discarding()).statusCode() == 200
      catch { case NonFatal(_) => false }
    var ready = false
    while (
      !ready && running.isAlive && !stopping && System.nanoTime() < deadline
    ) {
      ready = answers
      if (!ready) Thread.sleep(500)
    }
    ready
  }

  private def lastLine(): Option[String] =
    Option
      .when(Files.exists(log))(Files.readAllLines(log))
      .flatMap(lines =>
        lines
          .toArray(Array.empty[String])
          .reverseIterator
          .map(_.trim)
          .find(_.nonEmpty)
      )

  private def freePort(): Int = Using.resource(ServerSocket(0))(_.getLocalPort)
}

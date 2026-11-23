package hanamaru.backend

import hanamaru.backend.reference.{Download, ReferenceData}
import hanamaru.backend.status.StatusApi
import hanamaru.backend.storage.{Database, Places}
import hanamaru.backend.writing.{WritingApi, WritingAttempts}

import java.net.http.HttpClient
import java.nio.file.Path

import com.typesafe.scalalogging.Logger
import ox.*
import sttp.tapir.server.netty.NettySocketConfig
import sttp.tapir.server.netty.sync.NettySyncServer

object Main extends OxApp {
  private val logger = Logger("hanamaru.backend.main")

  override def run(args: Vector[String])(using Ox): ExitCode = {
    val places =
      Places.fromEnvironment(sys.env, Path.of(sys.props("user.home"))).created()
    val port = sys.env.getOrElse("HANAMARU_PORT", "4323").toInt
    logger.info(s"Data in ${places.data}, cache in ${places.cache}")
    // opened at start, so that unreadable learner data stops the server at
    // once rather than at the first answer
    useCloseableInScope(Database.open(places.database)).discard
    val http = HttpClient
      .newBuilder()
      .followRedirects(HttpClient.Redirect.NORMAL)
      .build()
    val reference = ReferenceData(places.reference, Download(http))
    forkUnsupervised(reference.prepare()).discard
    val server = NettySyncServer()
      // a restart does not have to wait for the previous run's port
      .modifyConfig(_.socketConfig(NettySocketConfig.default.withReuseAddress))
      // the page and its models are on this machine, nowhere else
      .host("localhost")
      .port(port)
      .addEndpoints(StatusApi(reference).endpoints)
      .addEndpoints(
        WritingApi(reference, WritingAttempts(places.writingAttempts)).endpoints
      )
      .addEndpoints(FrontendApi.endpoints)

    logger.info(s"Serving on http://localhost:$port")
    fork(server.startAndWait()).join()

    ExitCode.Success
  }
}

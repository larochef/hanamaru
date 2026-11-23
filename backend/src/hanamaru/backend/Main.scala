package hanamaru.backend

import hanamaru.backend.learner.{LearnerApi, LearnerStore}
import hanamaru.backend.placement.*
import hanamaru.backend.reference.{Download, ReferenceData}
import hanamaru.backend.session.*
import hanamaru.backend.status.StatusApi
import hanamaru.backend.storage.{Database, Places}
import hanamaru.backend.writing.*

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
    val database = useCloseableInScope(Database.open(places.database))
    val learner = LearnerStore(database)
    val attempts = WritingAttempts(places.writingAttempts)
    val writingReview = WritingReview(learner)
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
        WritingApi(
          reference,
          attempts,
          writingReview
        ).endpoints
      )
      .addEndpoints(LearnerApi(learner).endpoints)
      .addEndpoints(
        PlacementApi(
          KanaPlacementTest(PlacementStore(database), learner, attempts)
        ).endpoints
      )
      .addEndpoints(
        SessionApi(
          ExpressSessions(
            SessionStore(database),
            learner,
            writingReview,
            attempts
          )
        ).endpoints
      )
      .addEndpoints(FrontendApi.endpoints)

    logger.info(s"Serving on http://localhost:$port")
    fork(server.startAndWait()).join()

    ExitCode.Success
  }
}

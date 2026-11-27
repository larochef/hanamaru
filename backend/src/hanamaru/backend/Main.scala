package hanamaru.backend

import hanamaru.backend.characters.{CharacterApi, CharacterPractice}
import hanamaru.backend.kanji.*
import hanamaru.backend.learner.{LearnerApi, LearnerStore}
import hanamaru.backend.lessons.*
import hanamaru.backend.models.*
import hanamaru.backend.placement.*
import hanamaru.backend.questions.{QuestionApi, TutorQuestions}
import hanamaru.backend.reference.*
import hanamaru.backend.session.*
import hanamaru.backend.status.StatusApi
import hanamaru.backend.storage.{Database, Places}
import hanamaru.backend.vocabulary.*
import hanamaru.backend.writing.*

import java.net.http.HttpClient
import java.nio.file.Path
import scala.concurrent.duration.*

import com.typesafe.scalalogging.Logger
import ox.*
import sttp.tapir.server.netty.NettySocketConfig
import sttp.tapir.server.netty.sync.NettySyncServer

object Main extends OxApp {
  private val logger = Logger("hanamaru.backend.main")

  override def run(args: Vector[String])(using Ox): ExitCode = {
    val home = Path.of(sys.props("user.home"))
    val places =
      Places.fromEnvironment(sys.env, home).created()
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
    val model = LlamaServer(
      LocalSettings.read(places.settings),
      places.settings,
      HuggingFaceCache(HuggingFaceCache.rootOf(sys.env, home)),
      places.logs,
      http
    )
    releaseAfterScope(model.stop())
    forkUnsupervised(model.run()).discard
    val tutor = LlamaClient(model, http)
    val units = Units(
      Curriculum.points,
      () => reference.dictionary,
      learner,
      () =>
        reference.kanji.map(book =>
          literal => book.kanji(literal).flatMap(_.grade)
        )
    )
    val vocabulary = VocabularyPractice(
      () => reference.dictionary,
      learner,
      MeaningJudge(tutor),
      attempts,
      writingReview,
      SentenceWriter(tutor, () => reference.analyser),
      units,
      KanjiHand.of(() => reference.kanji, learner)
    )
    val kanji = KanjiPractice(
      () => reference.kanji,
      learner,
      units,
      attempts,
      writingReview
    )
    val characters =
      CharacterPractice(learner, units, kanji, () => reference.dictionary)
    val lessons = Lessons(
      () => reference.analyser,
      () => reference.dictionary,
      learner,
      TextWriter(tutor),
      units
    )
    val exercises = Exercises(
      lessons,
      () => reference.analyser,
      () => reference.dictionary,
      learner,
      () => reference.kanji
    )
    val kanaPlacement =
      KanaPlacementTest(PlacementStore(database), learner, attempts)
    val grammarPlacement = GrammarPlacementTest(
      PlacementStore(database),
      learner,
      Curriculum.points,
      () => reference.analyser
    )
    val myLesson = MyLesson(lessons, vocabulary, characters)
    val today =
      Today(learner, lessons, myLesson, kanaPlacement, grammarPlacement)
    val server = NettySyncServer()
      // a restart does not have to wait for the previous run's port; and
      // stopping does not wait for the page's open connections longer than
      // OxApp waits for the whole application to stop, else the tutor's model
      // would outlive it
      .modifyConfig(
        _.socketConfig(NettySocketConfig.default.withReuseAddress)
          .withGracefulShutdownTimeout(2.seconds)
          // the tutor may take a while: a text is written in up to three tries
          .requestTimeout(2.minutes)
      )
      // the page and its models are on this machine, nowhere else
      .host("localhost")
      .port(port)
      .addEndpoints(StatusApi(reference, model).endpoints)
      .addEndpoints(
        WritingApi(
          reference,
          attempts,
          PadCharacters(places.padCharacters),
          writingReview
        ).endpoints
      )
      .addEndpoints(LearnerApi(learner).endpoints)
      .addEndpoints(DictionaryApi(reference).endpoints)
      .addEndpoints(KanjiApi(reference).endpoints)
      .addEndpoints(KanjiPracticeApi(kanji).endpoints)
      .addEndpoints(CharacterApi(characters).endpoints)
      .addEndpoints(VocabularyApi(vocabulary).endpoints)
      .addEndpoints(
        LessonApi(
          lessons,
          exercises,
          ExerciseJudge(
            tutor,
            () => reference.analyser,
            KanjiHand.of(() => reference.kanji, learner)
          ),
          myLesson,
          UnitRecaps(units, () => reference.kanji)
        ).endpoints
      )
      .addEndpoints(
        QuestionApi(
          TutorQuestions(
            database,
            tutor,
            () => reference.analyser,
            () => reference.dictionary,
            () => reference.kanji,
            units,
            model.modelFile
          )
        ).endpoints
      )
      .addEndpoints(
        PlacementApi(kanaPlacement, grammarPlacement).endpoints
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
      .addEndpoints(TodayApi(today).endpoints)
      .addEndpoints(FrontendApi.endpoints)

    logger.info(s"Serving on http://localhost:$port")
    fork(server.startAndWait()).join()

    ExitCode.Success
  }
}

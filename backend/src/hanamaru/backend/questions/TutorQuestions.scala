package hanamaru.backend.questions

import hanamaru.backend.lessons.{Curriculum, Units}
import hanamaru.backend.models.LlamaClient
import hanamaru.backend.reference.*
import hanamaru.backend.storage.Database
import hanamaru.shared.questions.*

import java.time.Instant

import com.github.plokhotnyuk.jsoniter_scala.core.*
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker

/** The learner's questions to the tutor, each kept before it is answered, so
  * that none is lost when the tutor is away, then answered with what the
  * learner had before them: the grammar point's lesson, its examples and its
  * unit (theme, words, kanji), the sentence and its words as the dictionary
  * gives them (not as the tutor remembers them), the kanji in all of it as
  * KANJIDIC gives them, and the conversation so far. Kept to be gone over:
  * whether the tutor answers well enough, and what the learner wonders about.
  */
final class TutorQuestions(
    database: Database,
    tutor: LlamaClient,
    analyser: () => Option[Analyser],
    dictionary: () => Option[Dictionary],
    kanji: () => Option[KanjiBook],
    units: Units,
    answeredBy: String
) {
  import TutorQuestions.*

  def asked(question: TutorQuestion, at: Instant): AskedQuestion = {
    val before = question.follows.toList.flatMap(thread)
    val id = database.transaction(
      _.insert(
        """INSERT INTO tutor_question (asked_at, context, question, follows)
          |VALUES (?, ?, ?, ?)""".stripMargin,
        at.toEpochMilli,
        writeToString(question.context),
        question.question,
        question.follows
      )
    )
    val answer = tutor
      .structured[Answer](
        system,
        prompt(question, before),
        schema,
        maxTokens = 1024
      )
      .toOption
      .map(_.answer.trim)
      .filter(_.nonEmpty)
    answer.foreach(text =>
      database.transaction(
        _.update(
          "UPDATE tutor_question SET answer = ?, answered_by = ? WHERE id = ?",
          text,
          answeredBy,
          id
        )
      )
    )
    AskedQuestion(
      id,
      at.toEpochMilli,
      question.context,
      question.question,
      question.follows,
      answer
    )
  }

  /** Every question asked, the latest first. */
  def all: List[AskedQuestion] = database.read(
    _.query("SELECT * FROM tutor_question ORDER BY asked_at DESC, id DESC")(
      readQuestion
    )
  )

  // the conversation up to a question, the first first
  private def thread(id: Long): List[AskedQuestion] =
    database.read(session =>
      List
        .unfold(Option(id))(
          _.flatMap(each =>
            session
              .queryOne("SELECT * FROM tutor_question WHERE id = ?", each)(
                readQuestion
              )
              .map(found => found -> found.follows)
          )
        )
        .reverse
    )

  private def readQuestion(row: Database.Row): AskedQuestion = AskedQuestion(
    row.long("id"),
    row.long("asked_at"),
    readFromString[QuestionContext](row.string("context")),
    row.string("question"),
    row.longOption("follows"),
    row.stringOption("answer")
  )

  // what the learner had before them, and the conversation so far
  private def prompt(
      question: TutorQuestion,
      before: List[AskedQuestion]
  ): String = {
    val context = question.context
    val lesson = context.point.flatMap(id => Curriculum.points.find(_.id == id))
    val unitWords = lesson.toList.flatMap(units.wordsOf)
    val unitKanji = lesson.toList.flatMap(units.kanjiOf).flatten
    val point = lesson.map(point =>
      s"""The lesson: ${point.title} (${point.pattern})
         |${point.explanation.mkString("\n")}
         |Its examples:
         |${point.examples
          .map(example => s"- ${example.japanese}: ${example.french}")
          .mkString("\n")}
         |Its unit's theme: ${point.theme}. Its unit's words:
         |${unitWords
          .map(word =>
            s"- ${word.writings.headOption.getOrElse(word.readings.head)} (${word.readings.mkString("/")}): ${word.firstMeaning}"
          )
          .mkString("\n")}""".stripMargin
    )
    val kanjiShown = (unitKanji ++ (context.sentence.toList ++
      context.word.toList ++ lesson.toList.flatMap(_.examples.map(_.japanese)))
      .flatMap(_.codePoints.toArray.toList)
      .filter(isKanji)
      .map(Character.toString)).distinct
    val kanjiSaid = kanji().toList
      .flatMap(book => kanjiShown.flatMap(book.kanji))
      .map(each =>
        s"- ${each.literal}" +
          (if (unitKanji.contains(each.literal)) " (taught in this unit)"
           else "") +
          s": ${each.meanings.mkString(", ")}; on ${each.on.mkString("/")}; kun ${each.kun.mkString("/")}"
      )
    val sentence = context.sentence.map { japanese =>
      val words = (for {
        analysis <- analyser()
        words <- dictionary()
      } yield {
        val glosses = Glosses(words)
        analysis
          .tokens(japanese)
          .filterNot(_.partOfSpeech.headOption.contains("補助記号"))
          .map(token =>
            s"- ${token.surface} (${token.reading}, ${token.partOfSpeech.take(2).mkString(",")})" +
              glosses
                .of(token)
                .filter(_.french.nonEmpty)
                .fold("")(word => s": ${word.french}")
          )
          .mkString("\n")
      }).getOrElse("")
      s"""The sentence: $japanese
         |${context.french.fold("")(french =>
          s"Its meaning: $french\n"
        )}Its words, as the analyser and the dictionary read them:
         |$words""".stripMargin
    }
    val word = context.word.map { written =>
      val entries = dictionary().toList.flatMap(_.withForm(written)).take(2)
      s"The word: $written" + entries
        .map(entry =>
          s"\n- read ${entry.readings.mkString("/")}: " + entry.meanings
            .filter(_.language == "fre")
            .flatMap(_.glosses)
            .take(5)
            .mkString(", ")
        )
        .mkString
    }
    val exercise = List(
      context.task.map(task => s"What the exercise asks: $task"),
      context.answer.map(answer => s"The learner answered: $answer"),
      context.verdict.map(verdict =>
        s"The verdict shown to the learner: $verdict"
      )
    ).flatten
    val where = context.from match {
      case AskedFrom.Questions  => "The learner asks a question of their own."
      case AskedFrom.Lesson     => "The learner is reading the lesson."
      case AskedFrom.Correction =>
        "The learner has answered an exercise on this sentence and sees its correction."
      case AskedFrom.Exercise =>
        "The learner is answering an exercise on this sentence: do not give its answer away unless they ask for it."
    }
    val conversation = before
      .map(each =>
        s"Learner: ${each.question}\nTutor: ${each.answer.getOrElse("(no answer)")}"
      )
      .mkString("\n")
    List(
      Some(where),
      point,
      sentence,
      word,
      Option.when(kanjiSaid.nonEmpty)(
        s"The kanji in all this, as KANJIDIC gives them:\n${kanjiSaid.mkString("\n")}"
      ),
      Option.when(exercise.nonEmpty)(exercise.mkString("\n")),
      Option.when(conversation.nonEmpty)(
        s"The conversation so far:\n$conversation"
      ),
      Some(s"The learner's question: ${question.question}")
    ).flatten.mkString("\n\n")
  }
}

object TutorQuestions {

  private def isKanji(codePoint: Int): Boolean =
    Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN

  final case class Answer(answer: String)

  given JsonValueCodec[Answer] = JsonCodecMaker.make

  private val schema =
    """{"type":"object","properties":{"answer":{"type":"string"}},"required":["answer"]}"""

  private val system =
    """You are a Japanese teacher answering a French-speaking beginner's question about Japanese. Answer in French, plainly and briefly (a few sentences; an example when it helps). Write Japanese in kana, with its kanji only when quoting a sentence shown.
      |Be honest rather than agreeable: when the learner doubts something correct, say it is correct and why; when a sentence shown is wrong or unnatural, say so and give a natural one. When you are not sure, say so.""".stripMargin
}

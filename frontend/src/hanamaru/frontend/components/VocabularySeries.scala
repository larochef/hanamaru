package hanamaru.frontend.components

import hanamaru.frontend.i18n.{Language, Tr}
import hanamaru.frontend.model.*
import hanamaru.shared.dictionary.{DictionaryWord, Meaning}
import hanamaru.shared.kana.Romaji
import hanamaru.shared.questions.{AskedFrom, QuestionContext}
import hanamaru.shared.vocabulary.*

import scala.scalajs.js

import com.raquo.laminar.api.L.*

/** A rapid series: a few words, those due and some new ones, asked both ways in
  * a mixed order. A new word is shown first, unless the learner already knows
  * it, then asked twice for itself; a word missed comes back a few questions
  * later. After each answer the whole word is shown. A series is asked each
  * time `starts` fires; once it is over, `goOn`, when given, leads on to what
  * follows it. Its words are those `of` says ([[SeriesOf]]).
  */
class VocabularySeries(
    bus: AppBus,
    starts: EventStream[Unit],
    of: SeriesOf,
    goOn: Option[() => Unit] = None
) extends ReactiveElement {

  private enum Phase {
    case Asking
    case Judged(
        task: SeriesTask.Ask,
        answer: VocabularyAnswer,
        verdict: VocabularyVerdict
    )
    case SelfGrading(task: SeriesTask.Ask, typed: String)
    case Done
  }

  /** How the answers went: right, nearly, missed. */
  final private case class Tally(right: Int, near: Int, missed: Int)

  private val queue = Var(List.empty[SeriesTask])
  private val phase = Var[Phase](Phase.Asking)
  private val known = Var(Set.empty[Long])

  // the sentences written for the stable words, fetched one after the other,
  // the tutor answering one request at a time
  private val sentences = Var(Map.empty[Long, WordSentence])
  private val sentencesWanted = Var(List.empty[Long])
  // the word whose sentence this series waits for: another series, on another
  // page, asks for its own on the same bus
  private val awaited = Var(Option.empty[Long])

  // the word asked for itself is written with the stylus, or typed
  private val stylus = Var(true)
  private val tally = Var(Tally(0, 0, 0))
  private val startedAgain = EventBus[Unit]()
  private val submitted = EventBus[VocabularyAnswer]()
  private val selfGraded = EventBus[SelfGrade]()

  private val loaded: EventStream[AppResult[List[SeriesTask]]] = EventStream
    .merge(starts, startedAgain.events)
    .flatMapSwitch(_ =>
      bus.request(AppCommand.LoadSeries(of)) {
        case AppEvent.SeriesLoaded(`of`, tasks) => tasks
      }
    )

  private val failure: Signal[Option[String]] = bus.events.events
    .collect {
      case AppEvent.RequestFailed(
            AppCommand.LoadSeries(`of`) | _: AppCommand.AnswerVocabulary |
            _: AppCommand.SelfGradeVocabulary,
            reason
          ) =>
        Some(reason)
      case AppEvent.SeriesLoaded(`of`, _) | AppEvent.VocabularyJudged(_, _) =>
        None
    }
    .startWith(None)

  private def started(tasks: List[SeriesTask]): Unit = {
    val wanted = tasks.collect {
      case SeriesTask.Ask(word, _, _, _) if word.inSentence => word.word.id
    }.distinct
    sentences.set(Map.empty)
    sentencesWanted.set(wanted.drop(1))
    awaited.set(wanted.headOption)
    wanted.headOption.foreach(word => bus.send(AppCommand.LoadSentence(word)))
    queue.set(tasks)
    known.set(Set.empty)
    tally.set(Tally(0, 0, 0))
    phase.set(if (tasks.isEmpty) Phase.Done else Phase.Asking)
  }

  // The next question; a missed one comes back as many questions later as
  // the server says. A Var set while an event is handled takes its value once
  // the event is through: the queue is worked out here, from `tasks`, before
  // it is set.
  private def movedOn(
      comesBack: Option[Int],
      tasks: List[SeriesTask] = queue.now()
  ): Unit = {
    val remaining = (tasks, comesBack) match {
      case ((ask: SeriesTask.Ask) :: rest, Some(after)) =>
        rest.patch(
          after.min(rest.size),
          List(ask.copy(attempt = ask.attempt + 1)),
          0
        )
      case (_ :: rest, _) => rest
      case (Nil, _)       => Nil
    }
    queue.set(remaining)
    phase.set(if (remaining.isEmpty) Phase.Done else Phase.Asking)
  }

  private def judged(
      answer: VocabularyAnswer,
      verdict: VocabularyVerdict
  ): Unit =
    queue.now().headOption.collect { case ask: SeriesTask.Ask => ask }.foreach {
      ask =>
        tally.update(counted =>
          verdict.verdict match {
            case Verdict.Right => counted.copy(right = counted.right + 1)
            case Verdict.NearMiss | Verdict.OtherWord(_) =>
              counted.copy(near = counted.near + 1)
            case Verdict.Wrong  => counted.copy(missed = counted.missed + 1)
            case Verdict.Unsure => counted
          }
        )
        phase.set(
          if (verdict.verdict == Verdict.Unsure)
            Phase.SelfGrading(ask, answer.typed)
          else Phase.Judged(ask, answer, verdict)
        )
    }

  // The learner knows this new word: it is asked once each way, not shown,
  // nor asked a second time for itself (the server's `again`).
  private def alreadyKnown(word: SeriesWord): Unit = {
    known.update(_ + word.word.id)
    movedOn(
      None,
      queue.now().filter {
        case SeriesTask.Ask(asked, _, true, _) => asked != word
        case _                                 => true
      }
    )
  }

  private def frenchFirst(word: DictionaryWord): (List[Meaning], Boolean) = {
    val french = word.meanings.filter(_.language == "fre")
    if (french.nonEmpty && Language.current.code == "fr") (french, false)
    else
      (word.meanings.filter(_.language == "eng"), Language.current.code == "fr")
  }

  private def written(word: DictionaryWord): List[String] =
    if (word.usuallyKana) Nil else word.writings.take(1)

  private def wordShown(word: DictionaryWord): HtmlElement = div(
    cls := "word-shown",
    span(
      cls := "word-big",
      lang := "ja",
      word.readings.headOption.getOrElse("")
    ),
    written(word).map(form => span(cls := "word-writing", lang := "ja", form))
  )

  private def meaningsShown(word: DictionaryWord): HtmlElement = {
    val (meanings, inEnglish) = frenchFirst(word)
    div(
      cls := "word-meanings",
      meanings
        .take(3)
        .map(meaning => div(meaning.glosses.take(4).mkString(", "))),
      Option.when(inEnglish)(span(cls := "in-english", Tr("", "en anglais"))),
      Option.when(!inEnglish)(
        word.meanings
          .find(_.language == "eng")
          .map(english =>
            div(cls := "in-english", english.glosses.take(2).mkString(", "))
          )
      )
    )
  }

  private def intro(word: SeriesWord): HtmlElement = div(
    cls := "series-task",
    p(cls := "placement-prompt", Tr("A new word:", "Un nouveau mot :")),
    wordShown(word.word),
    meaningsShown(word.word),
    div(
      cls := "placement-actions",
      button(
        cls := "primary",
        Tr("Noted", "C'est noté"),
        onMountFocus,
        onClick --> (_ => movedOn(None))
      ),
      button(
        Tr("I already know it", "Je le connais déjà"),
        onClick --> (_ => alreadyKnown(word))
      )
    )
  )

  private def sentenceLoaded(
      word: Long,
      sentence: Option[WordSentence]
  ): Unit = if (awaited.now().contains(word)) {
    sentence.foreach(written =>
      sentences.set(sentences.now() + (word -> written))
    )
    val rest = sentencesWanted.now()
    sentencesWanted.set(rest.drop(1))
    awaited.set(rest.headOption)
    rest.headOption.foreach(next => bus.send(AppCommand.LoadSentence(next)))
  }

  /** A sentence in kana, its words spaced, the word asked blanked until
    * `revealed`.
    */
  private def sentenceShown(
      sentence: WordSentence,
      revealed: Boolean
  ): HtmlElement = p(
    cls := "word-sentence",
    lang := "ja",
    sentence.parts.map(part =>
      span(
        cls("is-asked") := part.marked,
        Option.when(part.spaceBefore)(" "),
        if (part.marked && !revealed) span("＿＿＿") else PartText(part).node
      )
    )
  )

  // the word asked for itself in a sentence, when one is ready
  private def inSentence(word: DictionaryWord): Option[HtmlElement] =
    sentences
      .now()
      .get(word.id)
      .map(sentence =>
        div(
          cls := "series-sentence",
          sentenceShown(sentence, revealed = false),
          p(
            cls := "in-english",
            Tr(
              "The missing word, in its dictionary form:",
              "Le mot qui manque, sous sa forme du dictionnaire :"
            )
          )
        )
      )

  // the word asked for itself, written with the stylus: in kana, its script
  // the learner's to know, or with the kanji they write
  private def writtenByHand(
      task: SeriesTask.Ask,
      sentence: Option[String],
      shownAt: Double,
      submit: String => Unit
  ): HtmlElement = {
    val word = task.word.word
    val reading = word.readings.headOption.getOrElse("")
    val writing = HandwrittenWord(
      bus,
      Map(
        KanaInput.Mode.Hiragana -> reading,
        KanaInput.Mode.Katakana -> reading
      ) ++ task.word.inKanji.map(KanaInput.Mode.Kanji -> _),
      if (task.word.inKanji.nonEmpty) KanaInput.Mode.Kanji
      else KanaInput.Mode.Hiragana,
      () => submit(""),
      Some(stylus)
    )
    writing.node.amend(
      writing.written --> ((form, characters) =>
        submitted.writer.onNext(
          VocabularyAnswer(
            word.id,
            Direction.Production,
            form,
            js.Date.now() - shownAt,
            alreadyKnown = known.now().contains(word.id),
            written = characters,
            sentence = sentence,
            attempt = task.attempt
          )
        )
      )
    )
  }

  // the word asked for itself, typed, its script said
  private def typedIn(
      task: SeriesTask.Ask,
      submit: String => Unit
  ): HtmlElement = {
    val katakana =
      task.word.word.readings.headOption.exists(Romaji.isKatakana)
    val mode = Var(
      if (katakana) KanaInput.Mode.Katakana else KanaInput.Mode.Hiragana
    )
    val field = KanaInput(mode)
    div(
      p(
        cls := "expected-script",
        Tr("Written in ", "S'écrit en "),
        span(cls := "script-name", if (katakana) "katakana" else "hiragana")
      ),
      form(
        cls := "written-answer",
        onSubmit.preventDefault --> (_ => submit(field.value)),
        AnswerPanel(
          bus,
          () => field,
          stylus,
          mode,
          Set(KanaInput.Mode.Hiragana, KanaInput.Mode.Katakana),
          field.node
        ).node,
        AnswerActions(() => submit("")).node
      )
    )
  }

  private def asked(task: SeriesTask.Ask): HtmlElement = {
    val shownAt = js.Date.now()
    val word = task.word.word
    val sentence = sentences.now().get(word.id).map(_.japanese)
    def submit(typed: String): Unit =
      submitted.writer.onNext(
        VocabularyAnswer(
          word.id,
          task.direction,
          typed,
          js.Date.now() - shownAt,
          alreadyKnown = known.now().contains(word.id),
          sentence =
            sentence.filter(_ => task.direction == Direction.Production),
          attempt = task.attempt
        )
      )
    task.direction match {
      // a known word, written in kanji from memory, always by hand
      case Direction.InKanji =>
        val (meanings, _) = frenchFirst(word)
        val form = task.word.inKanji.getOrElse(written(word).mkString)
        val writing = HandwrittenWord(
          bus,
          Map(KanaInput.Mode.Kanji -> form),
          KanaInput.Mode.Kanji,
          () => submit("")
        )
        div(
          cls := "series-task",
          p(
            cls := "placement-prompt",
            if (task.word.isNew)
              Tr(
                "New: you write all its kanji now. Write it in kanji, from memory:",
                "Nouveau : tu sais écrire tous ses kanji. Écris-le en kanji, de mémoire :"
              )
            else
              Tr(
                "Write it in kanji, from memory:",
                "Écris-le en kanji, de mémoire :"
              )
          ),
          div(
            cls := "series-meaning",
            meanings.headOption.fold("")(_.glosses.take(3).mkString(", "))
          ),
          span(
            cls := "word-reading",
            lang := "ja",
            word.readings.headOption.getOrElse("")
          ),
          writing.node.amend(
            writing.written --> ((_, characters) =>
              submitted.writer.onNext(
                VocabularyAnswer(
                  word.id,
                  Direction.InKanji,
                  characters.map(_.character).mkString,
                  js.Date.now() - shownAt,
                  alreadyKnown = false,
                  written = characters,
                  attempt = task.attempt
                )
              )
            )
          )
        )
      case Direction.Production =>
        val (meanings, inEnglish) = frenchFirst(word)
        div(
          cls := "series-task",
          p(
            cls := "placement-prompt",
            Tr("How do you say:", "Comment dit-on :")
          ),
          inSentence(word),
          div(
            cls := "series-meaning",
            meanings.headOption.fold("")(_.glosses.take(3).mkString(", "))
          ),
          Option.when(!inEnglish)(
            word.meanings
              .find(_.language == "eng")
              .map(english =>
                div(cls := "in-english", english.glosses.take(2).mkString(", "))
              )
          ),
          child <-- stylus.signal.map(on =>
            if (on) writtenByHand(task, sentence, shownAt, submit)
            else typedIn(task, submit)
          )
        )
      case Direction.Meaning =>
        val field = input(
          cls := "meaning-input",
          placeholder := Tr("its meaning", "son sens"),
          autoComplete := "off",
          onMountFocus
        )
        div(
          cls := "series-task",
          p(
            cls := "placement-prompt",
            Tr("What does it mean?", "Que veut dire :")
          ),
          wordShown(word),
          form(
            onSubmit.preventDefault --> (_ => submit(field.ref.value)),
            field,
            button(typ := "submit", cls := "primary", Tr("Answer", "Valider"))
          ),
          button(
            Tr("I don't know", "Je ne sais pas"),
            onClick --> (_ => submit(""))
          )
        )
    }
  }

  private def verdictLine(verdict: VocabularyVerdict): HtmlElement =
    verdict.verdict match {
      case Verdict.Right =>
        p(
          cls := "last-answer is-right",
          Tr("Right!", "Juste !"),
          verdict.tutorMatched.map(gloss =>
            span(
              cls := "tutor-note",
              Tr(
                s" The tutor recognises « $gloss ».",
                s" Le tuteur reconnaît « $gloss »."
              )
            )
          )
        )
      case Verdict.NearMiss =>
        p(
          cls := "last-answer is-near",
          Tr(
            "Nearly: mind the spelling.",
            "Presque : attention à l'orthographe."
          )
        )
      case Verdict.OtherWord(other) =>
        p(
          cls := "last-answer is-near",
          span(lang := "ja", other),
          Tr(
            " says it too, but the word asked is:",
            " le dit aussi, mais le mot demandé est :"
          )
        )
      case Verdict.Wrong | Verdict.Unsure =>
        p(
          cls := "last-answer is-failed",
          Tr("Missed. The word is:", "Raté. Le mot est :")
        )
    }

  private def judgedView(
      task: SeriesTask.Ask,
      answer: VocabularyAnswer,
      verdict: VocabularyVerdict
  ): HtmlElement = div(
    cls := "series-task",
    verdictLine(verdict),
    // answered in kana: the word as the learner writes it, kanji and all
    verdict.inKanji.map(written =>
      p(
        cls := "kanji-reminder",
        Tr(
          "With the kanji you write: ",
          "Avec les kanji que tu sais écrire : "
        ),
        span(lang := "ja", written)
      )
    ),
    Option.when(verdict.verdict != Verdict.Right && answer.typed.nonEmpty)(
      YouWrote(answer.typed).node
    ),
    // asked in kanji, missed: how it is written
    Option.when(
      task.direction == Direction.InKanji && verdict.verdict != Verdict.Right
    )(
      task.word.inKanji.map(form =>
        p(
          cls := "kanji-reminder",
          Tr("In kanji: ", "En kanji : "),
          span(lang := "ja", form)
        )
      )
    ),
    wordShown(task.word.word),
    meaningsShown(task.word.word),
    // asked in a sentence: the sentence whole, and in French
    answer.sentence
      .flatMap(_ => sentences.now().get(task.word.word.id))
      .map(sentence =>
        div(
          cls := "series-sentence",
          sentenceShown(sentence, revealed = true),
          p(cls := "in-english", sentence.french)
        )
      ),
    // written with the stylus: each character corrected
    Option.when(answer.written.nonEmpty)(
      div(
        cls := "corrections",
        answer.written.map(attempt =>
          StrokesOf(
            bus,
            attempt.character,
            CorrectionView(_, attempt).node
          ).node
        )
      )
    ),
    button(
      cls := "primary",
      Tr("Continue", "Continuer"),
      onMountFocus,
      onClick --> (_ => movedOn(verdict.comesBack))
    )
  )

  private def selfGrading(task: SeriesTask.Ask, typed: String): HtmlElement =
    div(
      cls := "series-task",
      p(
        Tr(
          s"« $typed » is not among its meanings. Did you mean the same?",
          s"« $typed » n'est pas parmi ses sens. Voulais-tu dire la même chose ?"
        )
      ),
      wordShown(task.word.word),
      meaningsShown(task.word.word),
      div(
        cls := "placement-actions",
        Seq(
          true -> Tr("Yes, the same", "Oui, la même"),
          false -> Tr("No", "Non")
        )
          .map((right, label) =>
            button(
              cls := (if (right) "primary" else ""),
              label,
              onClick.mapTo(
                SelfGrade(
                  task.word.word.id,
                  task.direction,
                  typed,
                  right,
                  task.attempt
                )
              ) --> selfGraded.writer
            )
          )
      )
    )

  private def done: HtmlElement = div(
    cls := "series-task",
    h2(Tr("Series over", "Série terminée")),
    child.text <-- tally.signal.map(counted =>
      Tr(
        s"Right: ${counted.right}, nearly: ${counted.near}, missed: ${counted.missed}.",
        s"Justes : ${counted.right}, presque : ${counted.near}, ratées : ${counted.missed}."
      )
    ),
    goOn.fold(
      div(
        cls := "placement-actions",
        button(
          cls := "primary",
          Tr("Another series", "Nouvelle série"),
          onClick.mapTo(()) --> startedAgain.writer
        ),
        PageLink(
          bus,
          Page.Vocabulary,
          cls := "button",
          Tr("Vocabulary", "Vocabulaire")
        )
      )
    )(next =>
      div(
        cls := "placement-actions",
        button(
          cls := "primary",
          Tr("Go on", "Continuer"),
          onClick --> (_ => next())
        ),
        button(
          Tr("Another series", "Nouvelle série"),
          onClick.mapTo(()) --> startedAgain.writer
        )
      )
    )
  )

  /** Each time a series is over. */
  val finished: EventStream[Unit] =
    phase.signal.changes.collect { case Phase.Done => () }

  private def shown(tasks: List[SeriesTask], now: Phase): HtmlElement =
    now match {
      case Phase.Done                          => done
      case Phase.Judged(task, answer, verdict) =>
        judgedView(task, answer, verdict)
      case Phase.SelfGrading(task, typed) => selfGrading(task, typed)
      case Phase.Asking                   =>
        tasks.headOption match {
          case Some(SeriesTask.Intro(word)) => intro(word)
          case Some(ask: SeriesTask.Ask)    => asked(ask)
          case None                         => done
        }
    }

  // the word under way, as the tutor is told it: shown, asked one way (in
  // its sentence, if any), then answered and judged
  private val told: Signal[Option[QuestionContext]] = queue.signal
    .combineWith(phase.signal, sentences.signal)
    .map { (tasks, now, written) =>
      def about(word: DictionaryWord, task: String) = {
        val sentence = written.get(word.id)
        QuestionContext(
          AskedFrom.Exercise,
          word = this.written(word).headOption,
          sentence = sentence.map(_.japanese),
          french = sentence.map(_.french),
          task = Some(task)
        )
      }
      def taskOf(ask: SeriesTask.Ask) = ask.direction match {
        case Direction.Production =>
          s"Write in Japanese the word meaning: ${ask.word.word.firstMeaning} (expected: ${this.written(ask.word.word).mkString(" or ")})"
        case Direction.Meaning =>
          s"Give the meaning, in French, of: ${this.written(ask.word.word).mkString(", ")}"
        case Direction.InKanji =>
          s"Write in kanji, from memory, the word read ${ask.word.word.readings.headOption.getOrElse("")}, meaning: ${ask.word.word.firstMeaning} (expected: ${ask.word.inKanji.getOrElse("")})"
      }
      (tasks, now) match {
        case (SeriesTask.Intro(word) :: _, Phase.Asking) =>
          Some(about(word.word, "A new word, shown with its meanings"))
        case ((ask: SeriesTask.Ask) :: _, Phase.Asking) =>
          Some(about(ask.word.word, taskOf(ask)))
        case (Nil, Phase.Asking)                      => None
        case (_, Phase.Judged(task, answer, verdict)) =>
          Some(
            about(task.word.word, taskOf(task)).copy(
              from = AskedFrom.Correction,
              answer = Some(
                if (answer.written.nonEmpty)
                  answer.written.map(_.character).mkString
                else answer.typed
              ).filter(_.nonEmpty),
              verdict = Some(verdict.verdict.toString)
            )
          )
        case (_, Phase.SelfGrading(task, typed)) =>
          Some(
            about(task.word.word, taskOf(task)).copy(
              from = AskedFrom.Correction,
              answer = Some(typed),
              verdict = Some("not judged: the learner compares their answer")
            )
          )
        case (_, Phase.Done) => None
      }
    }

  override lazy val node: HtmlElement = div(
    cls := "vocabulary-series",
    TutorContext(bus, told).node,
    loaded.collect { case Right(words) => words } --> (started(_)),
    bus.events.events.collect {
      case AppEvent.SentenceLoaded(word, sentence) => word -> sentence
      case AppEvent.RequestFailed(AppCommand.LoadSentence(word), _) =>
        word -> None
    } --> ((word, sentence) => sentenceLoaded(word, sentence)),
    submitted.events.flatMapSwitch(answer =>
      bus
        .request(AppCommand.AnswerVocabulary(answer)) {
          case AppEvent.VocabularyJudged(`answer`, verdict) => verdict
        }
        .collect { case Right(verdict) => answer -> verdict }
    ) --> ((answer, verdict) => judged(answer, verdict)),
    selfGraded.events.flatMapSwitch(grade =>
      bus
        .request(AppCommand.SelfGradeVocabulary(grade)) {
          case AppEvent.SelfGradeKept(`grade`, comesBack) => grade -> comesBack
        }
        .collect { case Right(kept) => kept }
    ) --> { (kept, comesBack) =>
      if (kept.right)
        tally.update(counted => counted.copy(right = counted.right + 1))
      else tally.update(counted => counted.copy(missed = counted.missed + 1))
      movedOn(comesBack.after)
    },
    child.maybe <-- failure.map(_.map(reason => p(cls := "is-failed", reason))),
    p(
      cls := "series-progress",
      child.text <-- queue.signal.combineWith(phase.signal).map {
        (tasks, now) =>
          if (now == Phase.Done) ""
          else
            Tr(
              s"${tasks.size} questions left",
              s"Encore ${tasks.size} questions"
            )
      }
    ),
    child <-- queue.signal.combineWith(phase.signal).map(shown)
  )
}

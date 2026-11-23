package hanamaru.frontend.components

import hanamaru.frontend.i18n.{Language, Tr}
import hanamaru.frontend.model.*
import hanamaru.shared.dictionary.{DictionaryWord, Meaning}
import hanamaru.shared.vocabulary.*

import scala.scalajs.js
import scala.util.Random

import com.raquo.laminar.api.L.*

/** A rapid series: a few words, those due and some new ones, asked both ways in
  * a mixed order. A new word is shown first, unless the learner already knows
  * it, then asked twice for itself; a word missed comes back a few questions
  * later. After each answer the whole word is shown.
  */
class SeriesPage(bus: AppBus) extends ReactiveElement {

  private enum Task {
    case Intro(word: SeriesWord)
    case Ask(word: SeriesWord, direction: Direction, attempt: Int)
  }

  private enum Phase {
    case Asking
    case Judged(
        task: Task.Ask,
        answer: VocabularyAnswer,
        verdict: VocabularyVerdict
    )
    case SelfGrading(task: Task.Ask, typed: String)
    case Done
  }

  /** How the answers went: right, nearly, missed. */
  final private case class Tally(right: Int, near: Int, missed: Int)

  // a missed question comes back this many questions later, twice at most
  private val comesBackAfter = 4
  private val mostReturns = 2

  private val queue = Var(List.empty[Task])
  private val phase = Var[Phase](Phase.Asking)
  private val known = Var(Set.empty[Long])

  // the word asked for itself is written with the stylus, or typed
  private val stylus = Var(true)
  private val tally = Var(Tally(0, 0, 0))
  private val startedAgain = EventBus[Unit]()
  private val submitted = EventBus[VocabularyAnswer]()
  private val selfGraded = EventBus[SelfGrade]()

  private val loaded: EventStream[AppResult[List[SeriesWord]]] = EventStream
    .merge(
      bus.events.events.collect { case AppEvent.PageShown(Page.Series) => () },
      startedAgain.events
    )
    .flatMapSwitch(_ =>
      bus.request(AppCommand.LoadSeries) { case AppEvent.SeriesLoaded(words) =>
        words
      }
    )

  private val failure: Signal[Option[String]] = bus.events.events
    .collect {
      case AppEvent.RequestFailed(
            AppCommand.LoadSeries | _: AppCommand.AnswerVocabulary |
            _: AppCommand.SelfGradeVocabulary,
            reason
          ) =>
        Some(reason)
      case AppEvent.SeriesLoaded(_) | AppEvent.VocabularyJudged(_, _) => None
    }
    .startWith(None)

  // The new words shown first; every word asked for itself, then for its
  // meaning; the new ones asked for themselves once more, at the end.
  private def planned(words: List[SeriesWord]): List[Task] = {
    val fresh = words.filter(_.isNew)
    fresh.map(Task.Intro(_)) ++
      Random.shuffle(words.map(Task.Ask(_, Direction.Production, 0))) ++
      Random.shuffle(words.map(Task.Ask(_, Direction.Meaning, 0))) ++
      Random.shuffle(fresh.map(Task.Ask(_, Direction.Production, 0)))
  }

  private def started(words: List[SeriesWord]): Unit = {
    queue.set(planned(words))
    known.set(Set.empty)
    tally.set(Tally(0, 0, 0))
    phase.set(if (words.isEmpty) Phase.Done else Phase.Asking)
  }

  // the next question; a missed one comes back a few questions later
  // The next question; a missed one comes back a few questions later. A Var
  // set while an event is handled takes its value once the event is through:
  // the queue is worked out here, from `tasks`, before it is set.
  private def movedOn(
      missed: Boolean,
      tasks: List[Task] = queue.now()
  ): Unit = {
    val remaining = tasks match {
      case (ask @ Task.Ask(_, _, attempt)) :: rest
          if missed && attempt < mostReturns =>
        rest.patch(
          comesBackAfter.min(rest.size),
          List(ask.copy(attempt = attempt + 1)),
          0
        )
      case _ :: rest => rest
      case Nil       => Nil
    }
    queue.set(remaining)
    phase.set(if (remaining.isEmpty) Phase.Done else Phase.Asking)
  }

  private def judged(
      answer: VocabularyAnswer,
      verdict: VocabularyVerdict
  ): Unit =
    queue.now().headOption.collect { case ask: Task.Ask => ask }.foreach {
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
  // nor asked a second time for itself.
  private def alreadyKnown(word: SeriesWord): Unit = {
    known.update(_ + word.word.id)
    val tasks = queue.now()
    val productions = tasks.zipWithIndex.collect {
      case (Task.Ask(`word`, Direction.Production, 0), index) => index
    }
    movedOn(
      missed = false,
      if (productions.size > 1) tasks.patch(productions.last, Nil, 1) else tasks
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
        onClick --> (_ => movedOn(missed = false))
      ),
      button(
        Tr("I already know it", "Je le connais déjà"),
        onClick --> (_ => alreadyKnown(word))
      )
    )
  )

  private def asked(task: Task.Ask): HtmlElement = {
    val shownAt = js.Date.now()
    val word = task.word.word
    def submit(typed: String): Unit =
      submitted.writer.onNext(
        VocabularyAnswer(
          word.id,
          task.direction,
          typed,
          js.Date.now() - shownAt,
          alreadyKnown = known.now().contains(word.id)
        )
      )
    task.direction match {
      case Direction.Production if stylus.now() =>
        val (meanings, inEnglish) = frenchFirst(word)
        val reading = word.readings.headOption.getOrElse("")
        val writing = HandwrittenWord(bus, reading)
        div(
          cls := "series-task",
          p(
            cls := "placement-prompt",
            Tr("Write from memory, in kana:", "Écris de mémoire, en kana :")
          ),
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
          writing.node.amend(
            writing.written --> (written =>
              submitted.writer.onNext(
                VocabularyAnswer(
                  word.id,
                  Direction.Production,
                  reading,
                  js.Date.now() - shownAt,
                  alreadyKnown = known.now().contains(word.id),
                  written = written
                )
              )
            )
          ),
          button(
            Tr("I don't know", "Je ne sais pas"),
            onClick --> (_ => submit(""))
          )
        )
      case Direction.Production =>
        val field = KanaInput()
        val (meanings, inEnglish) = frenchFirst(word)
        div(
          cls := "series-task",
          p(
            cls := "placement-prompt",
            Tr("How do you say:", "Comment dit-on :")
          ),
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
          form(
            onSubmit.preventDefault --> (_ => submit(field.value)),
            field,
            button(typ := "submit", cls := "primary", Tr("Answer", "Valider"))
          ),
          button(
            Tr("I don't know", "Je ne sais pas"),
            onClick --> (_ => submit(""))
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
      task: Task.Ask,
      answer: VocabularyAnswer,
      verdict: VocabularyVerdict
  ): HtmlElement = div(
    cls := "series-task",
    verdictLine(verdict),
    wordShown(task.word.word),
    meaningsShown(task.word.word),
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
      onClick --> (_ => movedOn(missed = verdict.verdict == Verdict.Wrong))
    )
  )

  private def selfGrading(task: Task.Ask, typed: String): HtmlElement = div(
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
      Seq(true -> Tr("Yes, the same", "Oui, la même"), false -> Tr("No", "Non"))
        .map((right, label) =>
          button(
            cls := (if (right) "primary" else ""),
            label,
            onClick.mapTo(
              SelfGrade(task.word.word.id, task.direction, typed, right)
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
  )

  private def shown(tasks: List[Task], now: Phase): HtmlElement = now match {
    case Phase.Done                          => done
    case Phase.Judged(task, answer, verdict) =>
      judgedView(task, answer, verdict)
    case Phase.SelfGrading(task, typed) => selfGrading(task, typed)
    case Phase.Asking                   =>
      tasks.headOption match {
        case Some(Task.Intro(word)) => intro(word)
        case Some(ask: Task.Ask)    => asked(ask)
        case None                   => done
      }
  }

  override lazy val node: HtmlElement = div(
    cls := "series-page",
    div(
      cls := "review-bar",
      h1(Tr("Rapid series", "Série rapide")),
      label(
        cls := "session-context",
        input(
          typ := "checkbox",
          checked <-- stylus.signal,
          onInput.mapToChecked --> stylus.writer
        ),
        Tr("Answer with the stylus", "Répondre au stylet")
      )
    ),
    loaded.collect { case Right(words) => words } --> (started(_)),
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
          case AppEvent.SelfGradeKept(`grade`) => grade
        }
        .collect { case Right(kept) => kept }
    ) --> { kept =>
      if (kept.right)
        tally.update(counted => counted.copy(right = counted.right + 1))
      else tally.update(counted => counted.copy(missed = counted.missed + 1))
      movedOn(missed = !kept.right)
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

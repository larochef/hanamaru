package hanamaru.frontend.components

import hanamaru.frontend.i18n.Tr
import hanamaru.frontend.model.*
import hanamaru.shared.kana.Kana
import hanamaru.shared.learner.{ItemStatus, KanaToReview}

import com.raquo.laminar.api.L.*

/** Kana reviewed one after the other: each shown, written guided, then from
  * memory, as long as the learner wishes, who moves on when ready. The kana
  * missed are chosen first.
  */
class ReviewPage(bus: AppBus) extends ReactiveElement {

  private val asked = EventBus[Option[String]]()

  // a kana picked when the page is shown, then at each "next kana"
  private val picked: Signal[Option[AppResult[Option[KanaToReview]]]] =
    EventStream
      .merge(
        bus.events.events.collect { case AppEvent.PageShown(Page.Review) =>
          None
        },
        asked.events
      )
      .flatMapSwitch(after =>
        bus.request(AppCommand.PickKanaToReview(after)) {
          case AppEvent.KanaToReviewPicked(kana) => kana
        }
      )
      .map(Some(_))
      .startWith(None)

  private val current: Signal[Option[KanaToReview]] =
    picked.map(_.flatMap(_.toOption).flatten)

  private val kana: Signal[Option[Kana]] =
    current.map(_.flatMap(review => Kana.find(review.character)))

  private def why(review: KanaToReview): String = review.status match {
    case ItemStatus.Learning  => Tr("Being learned", "En apprentissage")
    case ItemStatus.ToConfirm => Tr("To confirm", "À confirmer")
    case ItemStatus.Known     => Tr("Known", "Acquis")
    case ItemStatus.Unknown   => Tr("New", "Nouveau")
  }

  private def nothingYet: HtmlElement = p(
    Tr(
      "Nothing to review yet: take the placement test, or write kana from memory from the chart.",
      "Rien à réviser pour l'instant : passe le test de placement, ou écris des kana de mémoire depuis le tableau."
    )
  )

  override lazy val node: HtmlElement = div(
    cls := "review-page",
    div(
      cls := "review-bar",
      h1(Tr("Review", "Révision")),
      child.maybe <-- current.map(
        _.map(review => span(cls := "review-why", why(review)))
      ),
      child.maybe <-- current.map(
        _.map(review =>
          button(
            cls := "primary",
            Tr("Next kana →", "Kana suivant →"),
            onClick.mapTo(Some(review.character)) --> asked.writer
          )
        )
      )
    ),
    child.maybe <-- picked.map {
      case Some(Right(None))  => Some(nothingYet)
      case Some(Left(reason)) => Some(p(cls := "is-failed", reason))
      case _                  => None
    },
    KanaPractice(bus, kana)
  )
}

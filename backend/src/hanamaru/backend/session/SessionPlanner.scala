package hanamaru.backend.session

import hanamaru.backend.learner.*
import hanamaru.shared.kana.Kana
import hanamaru.shared.session.SessionExercise

import scala.util.Random

/** What an Express session asks next, from the kana's cards as they stand:
  *   1. the cards due, those being learned or relearned first (a card missed
  *      comes back a few minutes later, within the session), then the most
  *      overdue;
  *   2. the first reading of the kana met but never read;
  *   3. a few new kana, in the chart's order, only once nothing is due: a busy
  *      day's reviews are not buried under new kana;
  *   4. once nothing is due, any card of the session's kana ([[Rotation]]),
  *      those it has not asked yet (`asked`) first: a session asked for is not
  *      ended early, and covers every kana rather than going round the few
  *      being learned, whose next steps are minutes away.
  * Only the session's `kana` are asked (a script's, or all). Writing is asked
  * only with the stylus at hand, and the same card never twice in a row when
  * there is another. Nothing once the time is up.
  */
object SessionPlanner {

  /** The time a new kana takes, shown, written guided, then from memory. */
  val newKanaMillis: Long = 2 * 60 * 1000

  /** New kana per session: one every three minutes of it. */
  def newKanaFor(minutes: Int): Int = (minutes / 3).max(1)

  def next(
      cards: List[KanaCard],
      now: Long,
      endsAt: Long,
      stylus: Boolean,
      introduced: Int,
      maxNew: Int,
      last: Option[SessionExercise],
      kana: List[Kana] = Kana.all,
      asked: Set[(String, CardKind)] = Set.empty,
      random: Random = Random()
  ): Option[SessionExercise] = {
    // the session's kana only: a script, or both
    val inPlay = kana.map(_.character).toSet
    val allowed = cards.filter(card =>
      inPlay(card.character) && (stylus || card.kind == CardKind.Reading)
    )
    def exercise(card: KanaCard): SessionExercise = card.kind match {
      case CardKind.Reading => SessionExercise.Reading(card.character)
      case CardKind.Writing => SessionExercise.Writing(card.character)
      // a kana has no word or grammar cards
      case CardKind.Production | CardKind.Meaning | CardKind.Usage =>
        throw IllegalStateException(s"${card.kind} is not a kana card")
    }
    def notJustAsked[A](
        candidates: List[A]
    )(asked: A => SessionExercise): List[A] =
      candidates.filter(candidate => !last.contains(asked(candidate))) match {
        case Nil    => candidates
        case others => others
      }
    def learning(card: KanaCard) = card.phase != Phase.Review
    val due = notJustAsked(
      allowed.filter(_.due <= now).sortBy(card => (!learning(card), card.due))
    )(exercise)
    lazy val unread = notJustAsked(
      kana
        .map(_.character)
        .filter(character =>
          cards.exists(_.character == character) &&
            !cards.exists(card =>
              card.character == character && card.kind == CardKind.Reading
            )
        )
    )(SessionExercise.Reading(_))
    val taught = if (stylus) CardKind.Writing else CardKind.Reading
    lazy val unmet = kana.find(each =>
      !cards.exists(card =>
        card.character == each.character && card.kind == taught
      )
    )
    lazy val anyOther = Rotation.drawn(
      notJustAsked(allowed)(exercise),
      1,
      card => asked((card.character, card.kind)),
      random
    )
    if (now >= endsAt) {
      None
    } else {
      due.headOption
        .map(exercise)
        .orElse(unread.headOption.map(SessionExercise.Reading(_)))
        .orElse(
          unmet
            .filter(_ => introduced < maxNew && endsAt - now >= newKanaMillis)
            .map(kana => SessionExercise.NewKana(kana.character, stylus))
        )
        .orElse(anyOther.headOption.map(exercise))
    }
  }
}

package hanamaru.shared.learner

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker
import sttp.tapir.Schema

/** How well a card was answered, as FSRS grades it (1 to 4). */
enum Grade(val value: Int) {
  case Again extends Grade(1)
  case Hard extends Grade(2)
  case Good extends Grade(3)
  case Easy extends Grade(4)
}

object Grade {
  given Schema[Grade] = Schema.derived
}

/** Where an item stands, from its cards: never reviewed, being learned, found
  * probably known by the placement test and waiting for a review to confirm it,
  * or known (every card's memory stable for weeks).
  */
enum ItemStatus {
  case Unknown, Learning, ToConfirm, Known
}

object ItemStatus {
  given Schema[ItemStatus] = Schema.derived
}

/** A card just reviewed: its grade, and when it is due again, in milliseconds
  * since the epoch.
  */
final case class ReviewOutcome(grade: Grade, due: Long)

/** Where the learner stands on one kana: its status, when its first card is due
  * for review, in milliseconds since the epoch, once it has a card, and whether
  * it is due now (said by the server, whose clock decides).
  */
final case class KanaProgress(
    character: String,
    status: ItemStatus,
    due: Option[Long],
    dueNow: Boolean = false
)

object ReviewOutcome {
  given JsonValueCodec[Option[ReviewOutcome]] = JsonCodecMaker.make
  given Schema[ReviewOutcome] = Schema.derived
}

object KanaProgress {
  given JsonValueCodec[List[KanaProgress]] = JsonCodecMaker.make
  given Schema[KanaProgress] = Schema.derived
}

/** The next kana to review, and where the learner stands on it. */
final case class KanaToReview(character: String, status: ItemStatus)

object KanaToReview {
  given JsonValueCodec[Option[KanaToReview]] = JsonCodecMaker.make
  given Schema[KanaToReview] = Schema.derived
}

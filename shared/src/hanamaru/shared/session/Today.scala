package hanamaru.shared.session

import hanamaru.shared.lessons.{LessonPlan, UnitProgress}

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker
import sttp.tapir.Schema

/** What the kana ask of today, besides My lesson: the placement test, while no
  * kana is met; or an Express session, when more are due than My lesson takes.
  */
enum KanaToday {
  case PlacementToTake(started: Boolean)
  case Backlog(due: Int)
}

/** Today, as the learner stands: what the kana ask besides My lesson, the unit
  * under way (none once all are acquired), My lesson's plan, and whether the
  * grammar placement test is worth offering (never taken, the unit's lesson
  * still to do).
  */
final case class Today(
    kana: Option[KanaToday],
    unit: Option[UnitProgress],
    plan: LessonPlan,
    grammarPlacement: Boolean
)

/** What the menu offers: the kana, until they are settled. */
final case class Menu(kana: Boolean)

object KanaToday {
  given Schema[KanaToday] = Schema.derived
}

object Today {
  given JsonValueCodec[Today] = JsonCodecMaker.make
  given Schema[Today] = Schema.derived
}

object Menu {
  given JsonValueCodec[Menu] = JsonCodecMaker.make
  given Schema[Menu] = Schema.derived
}

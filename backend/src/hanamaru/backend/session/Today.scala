package hanamaru.backend.session

import hanamaru.backend.characters.CharacterPractice
import hanamaru.backend.learner.*
import hanamaru.backend.lessons.Lessons
import hanamaru.backend.placement.{GrammarPlacementTest, KanaPlacementTest}
import hanamaru.shared.kana.Kana
import hanamaru.shared.learner.{ItemStatus, KanaProgress}
import hanamaru.shared.session.{Today as TodayShown, *}

import java.time.Instant

/** Today, as the learner stands, decided here for the page to show as it is:
  * the kana, besides My lesson, only for the placement test (no kana met yet,
  * or the test under way) or when more are due than My lesson takes; My lesson,
  * the unit under way and its plan; the grammar placement test while never
  * taken and the unit's lesson is to do. And the menu: the kana in it until
  * they are settled.
  */
final class Today(
    learner: LearnerStore,
    lessons: Lessons,
    myLesson: MyLesson,
    kanaPlacement: KanaPlacementTest,
    grammarPlacement: GrammarPlacementTest
) {
  import Today.*

  def today(at: Instant): TodayShown = {
    val kanaCards = learner.cards(ItemKind.Kana)
    // the kana due, each once: an Express session asks their readings even
    // without the stylus
    val due =
      kanaCards.filter(_.due <= at.toEpochMilli).map(_.key).distinct.size
    val placement = kanaPlacement.state()
    val unit = lessons.unit
    TodayShown(
      // offered while no kana is met, and resumed until it is over
      if (!placement.finished && (placement.started || kanaCards.isEmpty)) {
        Some(KanaToday.PlacementToTake(placement.started))
      } else {
        Option.when(due > CharacterPractice.kanaDueAtMost)(
          KanaToday.Backlog(due)
        )
      },
      unit,
      myLesson.plan(at),
      grammarPlacement.state().exists(!_.finished) &&
        unit.exists(!_.practised)
    )
  }

  def menu(at: Instant): Menu =
    Menu(kana = !settled(learner.kanaProgress(at.toEpochMilli)))
}

object Today {

  /** The share of the kana that must be known for them to be settled. */
  val settledShare = 0.9

  /** Whether the kana are settled: nearly all known, their memory holding three
    * weeks. They then need no place in the menu.
    */
  def settled(progress: List[KanaProgress]): Boolean =
    progress.count(_.status == ItemStatus.Known) >= settledShare * Kana.all.size
}

package hanamaru.backend.lessons

import hanamaru.backend.learner.*
import hanamaru.backend.reference.Dictionary
import hanamaru.shared.dictionary.DictionaryWord
import hanamaru.shared.kana.Romaji

import java.util.concurrent.ConcurrentHashMap

/** The curriculum's units: each point with its theme, its words, found in the
  * dictionary, and its kanji, given their school grade (`grades`, once the
  * kanji are ready); the unit under way is the first not acquired: its point
  * practised, most of its words stable and its kanji learned. The vocabulary
  * and kanji practised come from it, and the next unit opens only once it is
  * acquired.
  */
final class Units(
    points: List[CurriculumPoint],
    dictionary: () => Option[Dictionary],
    learner: LearnerStore,
    grades: () => Option[String => Option[Int]]
) {
  import Units.*

  private val found = ConcurrentHashMap[String, List[DictionaryWord]]()
  @volatile private var spread: Option[Map[String, List[String]]] = None

  /** A unit's kanji ([[kanjiByUnit]]); none known until the kanji are ready.
    */
  def kanjiOf(point: CurriculumPoint): Option[List[String]] =
    spread
      .orElse {
        grades().map { grade =>
          val byUnit = kanjiByUnit(points, grade).toMap
          spread = Some(byUnit)
          byUnit
        }
      }
      .map(_.getOrElse(point.id, Nil))

  /** A kanji learned: read and written past their first steps. */
  def kanjiLearned(literal: String): Boolean =
    List(CardKind.Reading, CardKind.Writing).forall(kind =>
      learner
        .memory(ItemKind.Kanji, literal, kind)
        .exists(_.phase == Phase.Review)
    )

  /** A unit's words, those the dictionary has, in the order written. */
  def wordsOf(point: CurriculumPoint): List[DictionaryWord] =
    Option(found.get(point.id)).getOrElse {
      dictionary().fold(Nil) { words =>
        val resolved = point.words
          .flatMap { written =>
            written.split('#') match {
              // an entry named by its number, when its form is no guide
              case Array(_, id) => words.words(List(id.toLong))
              case _            =>
                val (form, reading) = written.split('|') match {
                  case Array(form, reading) => (form, Some(reading))
                  case _                    => (written, None)
                }
                chosen(words.withForm(form), form, reading)
            }
          }
          .distinctBy(_.id)
        found.put(point.id, resolved)
        resolved
      }
    }

  /** A word whose production has passed its first steps. */
  def stable(word: Long): Boolean =
    learner
      .memory(ItemKind.Word, word.toString, CardKind.Production)
      .exists(_.phase == Phase.Review)

  /** A point practised: its exercises passed (or placed), its card past its
    * first steps.
    */
  def practised(point: CurriculumPoint): Boolean =
    learner
      .memory(ItemKind.Grammar, point.id, CardKind.Usage)
      .exists(_.phase == Phase.Review)

  /** The unit under way, and its words; none once every unit is acquired. A
    * unit is not acquired while its kanji are not known, the kanji not ready.
    */
  def current: Option[(CurriculumPoint, List[DictionaryWord])] =
    points.iterator
      .map(point => point -> wordsOf(point))
      .find((point, words) =>
        !(practised(point) &&
          words.count(word => stable(word.id)) >= acquiredShare * words.size &&
          kanjiOf(point).exists(_.forall(kanjiLearned)))
      )

  /** The units up to the one under way (all of them past the last), in order.
    */
  def soFar: List[CurriculumPoint] = current.fold(points)((point, _) =>
    points.takeWhile(_.id != point.id) :+ point
  )
}

object Units {

  /** The share of a unit's words that must be stable for it to be acquired. */
  val acquiredShare = 0.8

  /** The school grades whose kanji the units teach. */
  val kanjiGrades = 2

  /** The most kanji a unit teaches. */
  val kanjiPerUnit = 4

  /** The units before this one teach no kanji: the kana are still being
    * settled.
    */
  val firstUnitWithKanji = 2

  /** Each unit's kanji: those of the first school grades, as they come in the
    * units' words (as written), each in the first unit whose words have it; at
    * most a few per unit, the others carried to the next units, so that a unit
    * with many (the first: 学生, 先生, 会社…) does not bring them all at once; none
    * in the first units, while the kana settle; any still waiting after the
    * last unit go with it. The words come first, in kana; their kanji follow.
    */
  def kanjiByUnit(
      points: List[CurriculumPoint],
      grade: String => Option[Int]
  ): List[(String, List[String])] = {
    val (byUnit, _, left) = points.zipWithIndex.foldLeft(
      (
        Vector.empty[(String, List[String])],
        Set.empty[String],
        List.empty[String]
      )
    ) { case ((done, seen, waiting), (point, index)) =>
      val met = point.wordForms
        .flatMap(_.map(_.toString))
        .filter(character =>
          !seen(character) && grade(character).exists(_ <= kanjiGrades)
        )
        .distinct
      val queue = waiting ++ met
      val taken = if (index < firstUnitWithKanji) 0 else kanjiPerUnit
      (
        done :+ (point.id -> queue.take(taken)),
        seen ++ met,
        queue.drop(taken)
      )
    }
    // what is still waiting goes with the last unit, not lost
    byUnit.lastOption
      .fold(byUnit)((id, kanji) => byUnit.init :+ (id -> (kanji ++ left)))
      .toList
  }

  /** The dictionary's word a unit means by a form (and a reading): one with
    * that reading if given; else one written so first, or read so first when
    * written in kana; the most basic in the core vocabulary first.
    */
  def chosen(
      entries: List[DictionaryWord],
      form: String,
      reading: Option[String]
  ): Option[DictionaryWord] = {
    val kana = form.forall(character =>
      character >= 'ぁ' && character <= 'ヶ' || character == 'ー'
    )
    entries
      .filter(entry =>
        reading.forall(entry.readings.map(Romaji.asHiragana).contains)
      )
      .sortBy(entry =>
        (
          // the form as the word is usually written
          !(entry.writings.headOption.contains(form) ||
            (kana && entry.readings.headOption.contains(form))),
          // the most basic level first (N5 is 5), words in no list last
          -entry.level,
          // the reading asked for, as the word's first
          !reading.forall(
            entry.readings.headOption.map(Romaji.asHiragana).contains
          )
        )
      )
      .headOption
  }
}

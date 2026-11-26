package hanamaru.backend.lessons

import hanamaru.backend.learner.*
import hanamaru.backend.reference.Dictionary
import hanamaru.shared.dictionary.DictionaryWord
import hanamaru.shared.kana.*
import hanamaru.shared.lessons.SymbolKind

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/** The curriculum's units: each point with its theme, its words, found in the
  * dictionary, and its symbols: the hiragana for the first unit, the katakana
  * for the second, then kanji, given their school grade (`grades`, once the
  * kanji are ready). The unit under way is the first not acquired: its point
  * practised, most of its words stable and its symbols learned. The vocabulary
  * and symbols practised come from it, and the next unit opens only once it is
  * acquired. The first unit's words wait until its hiragana are all met: they
  * are written with them.
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

  /** A unit's kanji ([[kanjiByUnit]]); none known until the kanji and the
    * dictionary are ready.
    */
  def kanjiOf(point: CurriculumPoint): Option[List[String]] =
    spread
      .orElse {
        for {
          grade <- grades()
          _ <- dictionary()
        } yield {
          val byUnit = kanjiByUnit(
            points.map(each => each.id -> carriers(wordsOf(each))),
            grade
          ).toMap
          spread = Some(byUnit)
          byUnit
        }
      }
      .map(_.getOrElse(point.id, Nil))

  /** A unit's symbols: the kana of its script for the first two, else its kanji
    * (none known until the kanji are ready).
    */
  def symbolsOf(point: CurriculumPoint): Option[Symbols] =
    kanaScriptOf(point) match {
      case Some(script) =>
        Some(
          Symbols(
            SymbolKind.fromScript(script),
            Kana.all.filter(_.script == script).map(_.character)
          )
        )
      case None =>
        kanjiOf(point).map(Symbols(SymbolKind.Kanji, _))
    }

  /** The kana script a unit teaches, for the first two units. */
  def kanaScriptOf(point: CurriculumPoint): Option[Script] =
    points.indexWhere(_.id == point.id) match {
      case 0 => Some(Script.Hiragana)
      case 1 => Some(Script.Katakana)
      case _ => None
    }

  /** The cards of the units' items, read at once to judge many of them. */
  def standing(): Standing = Standing(
    List(ItemKind.Word, ItemKind.Grammar, ItemKind.Kana, ItemKind.Kanji)
      .flatMap(kind => learner.cards(kind).map(card => (kind, card)))
      .groupMap((kind, card) => (kind, card.key))(_._2)
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

  /** Where a unit stands, from the cards as they are. */
  def progressOf(point: CurriculumPoint, now: Standing): UnitStanding = {
    val words = wordsOf(point)
    val symbols = symbolsOf(point)
    UnitStanding(
      point,
      words,
      words.count(word => now.stable(word.id)),
      (acquiredShare * words.size).ceil.toInt,
      now.practised(point.id),
      symbols,
      symbols.fold(0)(_.characters.count(now.symbolPassed)),
      // the first unit's words are written in hiragana: they come once its
      // hiragana are met; a kanji comes once a word written with it is known,
      // and the second unit's words are not in katakana: none waits
      symbols
        .filter(_.kind == SymbolKind.Hiragana)
        .forall(_.characters.forall(now.symbolMet))
    )
  }

  /** The unit under way, where it stands: the first the learner has not moved
    * on from, acquired or not (an acquired one waits for the learner to move
    * on, or to go on practising it); none once every unit is left. A unit is
    * not acquired while its symbols are not learned, the kanji not ready.
    */
  def current: Option[UnitStanding] = {
    val left = learner.unitsLeft()
    points.find(point => !left(point.id)).map(progressOf(_, standing()))
  }

  /** Once the unit under way is acquired: it and the units right after it
    * acquired too (by a placement, or practised ahead), which the learner
    * leaves together; then the unit that comes next, none past the last.
    */
  def onward: Option[(List[CurriculumPoint], Option[CurriculumPoint])] = {
    val left = learner.unitsLeft()
    val now = standing()
    val ahead = points.dropWhile(point => left(point.id))
    val (acquired, rest) =
      ahead.span(point => progressOf(point, now).acquired)
    Option.when(acquired.nonEmpty)(acquired -> rest.headOption)
  }

  /** Moves the learner on from the unit under way, once it is acquired, and
    * from those right after it acquired too ([[onward]]).
    */
  def moveOn(at: Instant): Unit =
    onward.foreach((acquired, _) => learner.leaveUnits(acquired.map(_.id), at))

  /** The units up to the one under way (all of them past the last), in order.
    */
  def soFar: List[CurriculumPoint] = current.fold(points)(standing =>
    points.takeWhile(_.id != standing.point.id) :+ standing.point
  )
}

/** A unit's symbols: their kind, and the characters. */
final case class Symbols(kind: SymbolKind, characters: List[String])

/** Where a unit stands: its words and how many are stable (and needed), its
  * point practised, its symbols and how many are learned, and whether its
  * hiragana are all met (the first unit's words wait for them; no other unit's
  * words wait).
  */
final case class UnitStanding(
    point: CurriculumPoint,
    words: List[DictionaryWord],
    stable: Int,
    wordsNeeded: Int,
    practised: Boolean,
    symbols: Option[Symbols],
    symbolsLearned: Int,
    symbolsMet: Boolean
) {
  def acquired: Boolean =
    practised && stable >= wordsNeeded &&
      symbols.exists(_.characters.size == symbolsLearned)
}

/** The cards of the units' items, as they stand: an item passed once its cards
  * have passed their first steps ([[ItemCard.passed]]).
  */
final case class Standing(cards: Map[(ItemKind, String), List[ItemCard]]) {

  private def passed(kind: ItemKind, key: String, card: CardKind) =
    cards
      .get((kind, key))
      .exists(_.exists(each => each.kind == card && each.passed))

  /** A word whose production has passed its first steps. */
  def stable(word: Long): Boolean =
    passed(ItemKind.Word, word.toString, CardKind.Production)

  /** A point practised: its exercises passed (or placed). */
  def practised(point: String): Boolean =
    passed(ItemKind.Grammar, point, CardKind.Usage)

  private def kindOf(symbol: String) =
    if (Kana.find(symbol).isDefined) ItemKind.Kana else ItemKind.Kanji

  /** A kana or kanji learned: read and written past their first steps. */
  def symbolPassed(symbol: String): Boolean =
    List(CardKind.Reading, CardKind.Writing)
      .forall(passed(kindOf(symbol), symbol, _))

  /** A word met: with a card. */
  def wordMet(word: Long): Boolean =
    cards.contains((ItemKind.Word, word.toString))

  /** A kana or kanji's reading or writing past its first steps. */
  def symbolCardPassed(symbol: String, card: CardKind): Boolean =
    passed(kindOf(symbol), symbol, card)

  /** A kana or kanji met: with a card. */
  def symbolMet(symbol: String): Boolean =
    cards.contains((kindOf(symbol), symbol))
}

object Units {

  /** The share of a unit's words that must be stable for it to be acquired. */
  val acquiredShare = 0.8

  /** The school grades whose kanji the units teach. */
  val kanjiGrades = 2

  /** The most kanji a unit teaches. */
  val kanjiPerUnit = 4

  /** The words a kanji can be learned in: those usually written with kanji
    * (分かる, usually in kana, cannot carry 分), as they are written first. The
    * kanji's assignment and its practice go by the same words.
    */
  def carriers(words: List[DictionaryWord]): List[String] =
    words.filterNot(_.usuallyKana).flatMap(_.writings.headOption)

  /** The units before this one teach no kanji: the kana are still being
    * settled.
    */
  val firstUnitWithKanji = 2

  /** Each unit's kanji, from its words as they are written (`written`, unit by
    * unit: [[carriers]]): those of the first school grades, as they come in the
    * units' words, each in the first unit whose words have it; at most a few
    * per unit, the others carried to the next units, so that a unit with many
    * (the first: 学生, 先生, 会社…) does not bring them all at once; none in the
    * first units, while the kana settle; any still waiting after the last unit
    * go with it. The words come first, in kana; their kanji follow.
    */
  def kanjiByUnit(
      written: List[(String, List[String])],
      grade: String => Option[Int]
  ): List[(String, List[String])] = {
    val (byUnit, _, left) = written.zipWithIndex.foldLeft(
      (
        Vector.empty[(String, List[String])],
        Set.empty[String],
        List.empty[String]
      )
    ) { case ((done, seen, waiting), ((unit, forms), index)) =>
      val met = forms
        .flatMap(_.map(_.toString))
        .filter(character =>
          !seen(character) && grade(character).exists(_ <= kanjiGrades)
        )
        .distinct
      val queue = waiting ++ met
      val taken = if (index < firstUnitWithKanji) 0 else kanjiPerUnit
      (
        done :+ (unit -> queue.take(taken)),
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

package hanamaru.backend.learner

import hanamaru.backend.storage.Database
import hanamaru.shared.learner.*

import java.time.Instant

/** What can be known, by kind, as the database names it. */
enum ItemKind(val name: String) {
  case Kana extends ItemKind("kana")
}

/** A way of testing an item, as the database names it. */
enum CardKind(val name: String) {

  /** The reading given, the character written from memory with the stylus. */
  case Writing extends CardKind("writing")

  /** The character shown, its reading typed. */
  case Reading extends CardKind("reading")
}

/** Where an answer was given, as the database names it. */
enum ReviewSource(val name: String) {
  case Practice extends ReviewSource("practice")
  case Placement extends ReviewSource("placement")
}

/** What the learner knows: items, their cards with their FSRS memory, and every
  * review, in the learner's database.
  */
final class LearnerStore(database: Database) {

  /** A card's memory stable for this many days or more is known. */
  private val knownStability = 21.0

  /** Keeps an answer to a card, graded, and schedules the card again; the
    * card's memory after it. `answer` is what was answered, as JSON. A card
    * reviewed `toConfirm` waits for its next review to be confirmed; any other
    * review settles it.
    */
  def reviewed(
      itemKind: ItemKind,
      key: String,
      cardKind: CardKind,
      grade: Grade,
      answer: String,
      at: Instant,
      source: ReviewSource = ReviewSource.Practice,
      toConfirm: Boolean = false
  ): Memory = database.transaction { session =>
    val item = itemId(session, itemKind, key)
    val card = session.queryOne(
      "SELECT * FROM card WHERE item_id = ? AND kind = ?",
      item,
      cardKind.name
    )(row => row.long("id") -> memoryOf(row))
    val memory = Fsrs.standard.reviewed(card.map(_._2), grade, at)
    val cardId = card match {
      case Some((id, _)) =>
        session.update(
          """UPDATE card SET phase = ?, step = ?, stability = ?, difficulty = ?,
            |due = ?, last_review = ?, to_confirm = ? WHERE id = ?""".stripMargin,
          (values(memory) ++ Seq(toConfirm, id))*
        )
        id
      case None => inserted(session, item, cardKind, memory, toConfirm)
    }
    session.insert(
      """INSERT INTO review (card_id, reviewed_at, grade, answer, source)
        |VALUES (?, ?, ?, ?, ?)""".stripMargin,
      cardId,
      at.toEpochMilli,
      grade.value,
      answer,
      source.name
    )
    memory
  }

  /** A card never reviewed, found probably known from other answers: it is
    * scheduled as a card first answered easily, to be confirmed, without a
    * review of its own. A card already there is left as it is.
    */
  def seededToConfirm(
      itemKind: ItemKind,
      key: String,
      cardKind: CardKind,
      at: Instant
  ): Unit = database.transaction { session =>
    val item = itemId(session, itemKind, key)
    val exists = session
      .queryOne(
        "SELECT id FROM card WHERE item_id = ? AND kind = ?",
        item,
        cardKind.name
      )(_.long("id"))
      .isDefined
    if (!exists) {
      inserted(
        session,
        item,
        cardKind,
        Fsrs.standard.reviewed(None, Grade.Easy, at),
        toConfirm = true
      )
    }
  }

  /** Every kana with a card: its status, and when its first card is due. */
  def kanaProgress(): List[KanaProgress] = database.read { session =>
    session
      .query(
        """SELECT item.key, card.phase, card.stability, card.due, card.to_confirm
          |FROM item JOIN card ON card.item_id = item.id
          |WHERE item.kind = ?""".stripMargin,
        ItemKind.Kana.name
      )(row =>
        row.string("key") -> CardStanding(
          known = Phase.valueOf(row.string("phase")) == Phase.Review &&
            row.double("stability") >= knownStability,
          toConfirm = row.boolean("to_confirm"),
          due = row.long("due")
        )
      )
      .groupMap(_._1)(_._2)
      .toList
      .sortBy(_._1)
      .map((key, cards) =>
        KanaProgress(key, statusOf(cards), Some(cards.map(_.due).min))
      )
  }

  final private case class CardStanding(
      known: Boolean,
      toConfirm: Boolean,
      due: Long
  )

  // An item is only as far as its least known card.
  private def statusOf(cards: List[CardStanding]): ItemStatus =
    if (cards.exists(card => !card.known && !card.toConfirm)) {
      ItemStatus.Learning
    } else if (cards.exists(_.toConfirm)) {
      ItemStatus.ToConfirm
    } else {
      ItemStatus.Known
    }

  private def itemId(
      session: Database.Session,
      itemKind: ItemKind,
      key: String
  ): Long =
    session
      .queryOne(
        "SELECT id FROM item WHERE kind = ? AND key = ?",
        itemKind.name,
        key
      )(
        _.long("id")
      )
      .getOrElse(
        session.insert(
          "INSERT INTO item (kind, key) VALUES (?, ?)",
          itemKind.name,
          key
        )
      )

  private def values(memory: Memory): Seq[Any] = Seq(
    memory.phase.toString,
    memory.step,
    memory.stability,
    memory.difficulty,
    memory.due.toEpochMilli,
    memory.lastReview.toEpochMilli
  )

  private def inserted(
      session: Database.Session,
      item: Long,
      cardKind: CardKind,
      memory: Memory,
      toConfirm: Boolean
  ): Long =
    session.insert(
      """INSERT INTO card (item_id, kind, phase, step, stability, difficulty,
        |due, last_review, to_confirm) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""".stripMargin,
      (Seq(item, cardKind.name) ++ values(memory) :+ toConfirm)*
    )

  private def memoryOf(row: Database.Row): Memory =
    Memory(
      Phase.valueOf(row.string("phase")),
      row.intOption("step"),
      row.double("stability"),
      row.double("difficulty"),
      Instant.ofEpochMilli(row.long("due")),
      Instant.ofEpochMilli(row.long("last_review"))
    )
}

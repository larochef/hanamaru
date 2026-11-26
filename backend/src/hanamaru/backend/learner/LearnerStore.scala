package hanamaru.backend.learner

import hanamaru.backend.storage.Database
import hanamaru.shared.learner.*
import hanamaru.shared.session.SessionSummary

import java.time.Instant

/** What can be known, by kind, as the database names it. */
enum ItemKind(val name: String) {
  case Kana extends ItemKind("kana")

  /** A word of the dictionary, keyed by its JMdict number. */
  case Word extends ItemKind("word")

  /** A grammar point of the curriculum, keyed by its id. */
  case Grammar extends ItemKind("grammar")

  /** A kanji, keyed by itself: read in a word (its reading card), written from
    * a word's reading and meaning (its writing card).
    */
  case Kanji extends ItemKind("kanji")
}

/** A way of testing an item, as the database names it. */
enum CardKind(val name: String) {

  /** The reading given, the character written from memory with the stylus. */
  case Writing extends CardKind("writing")

  /** The character shown, its reading typed. */
  case Reading extends CardKind("reading")

  /** A word's meaning given, the word typed in Japanese. */
  case Production extends CardKind("production")

  /** A word given, its meaning typed. */
  case Meaning extends CardKind("meaning")

  /** A grammar point used in sentences: its exercises, graded together. */
  case Usage extends CardKind("usage")
}

/** A card as plans see it: its item's key, what it tests, its phase, when it is
  * due, how many times it was reviewed, and when first, in milliseconds since
  * the epoch.
  */
final case class ItemCard(
    key: String,
    kind: CardKind,
    phase: Phase,
    due: Long,
    reviews: Int = 0,
    firstReviewed: Long = 0
) {

  /** Past its first steps, once: in review, or relearning after a lapse. What
    * was passed once is not taken back by a lapse, so that the learner never
    * finds a unit acquired closed again.
    */
  def passed: Boolean = phase != Phase.Learning
}

/** A kana's card as the session planner sees it: the kana, what the card tests,
  * its phase, and when it is due, in milliseconds since the epoch.
  */
final case class KanaCard(
    character: String,
    kind: CardKind,
    phase: Phase,
    due: Long
)

/** Where an answer was given, as the database names it. */
enum ReviewSource(val name: String) {
  case Practice extends ReviewSource("practice")
  case Placement extends ReviewSource("placement")

  /** A focus on a new character, kept as one review of each of its cards. */
  case Focus extends ReviewSource("focus")
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
      toConfirm: Boolean = false,
      studySession: Option[Long] = None
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
      """INSERT INTO review (card_id, reviewed_at, grade, answer, source,
        |session_id) VALUES (?, ?, ?, ?, ?, ?)""".stripMargin,
      cardId,
      at.toEpochMilli,
      grade.value,
      answer,
      source.name,
      studySession
    )
    memory
  }

  /** A card not due before `until`: a character learned in a focus, its first
    * steps done there, is reviewed from the next day on.
    */
  def postponed(
      itemKind: ItemKind,
      key: String,
      cardKind: CardKind,
      until: Instant
  ): Unit = database.transaction(
    _.update(
      """UPDATE card SET due = max(due, ?) WHERE kind = ? AND item_id =
        |(SELECT id FROM item WHERE kind = ? AND key = ?)""".stripMargin,
      until.toEpochMilli,
      cardKind.name,
      itemKind.name,
      key
    )
  )

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

  /** A card's memory, once it has been reviewed. */
  def memory(
      itemKind: ItemKind,
      key: String,
      cardKind: CardKind
  ): Option[Memory] =
    database.read(
      _.queryOne(
        """SELECT card.* FROM card JOIN item ON item.id = card.item_id
          |WHERE item.kind = ? AND item.key = ? AND card.kind = ?""".stripMargin,
        itemKind.name,
        key,
        cardKind.name
      )(memoryOf)
    )

  /** The items of a kind that have a card due by `now`, the most overdue first,
    * at most `count` of them.
    */
  def dueKeys(itemKind: ItemKind, now: Long, count: Int): List[String] =
    database.read(
      _.query(
        """SELECT item.key, min(card.due) AS due FROM item
          |JOIN card ON card.item_id = item.id
          |WHERE item.kind = ? AND card.due <= ?
          |GROUP BY item.key ORDER BY due LIMIT ?""".stripMargin,
        itemKind.name,
        now,
        count
      )(_.string("key"))
    )

  /** The items of a kind the learner has met: with at least one card. */
  def metKeys(itemKind: ItemKind): Set[String] = database.read(
    _.query(
      """SELECT DISTINCT item.key FROM item JOIN card ON card.item_id = item.id
        |WHERE item.kind = ?""".stripMargin,
      itemKind.name
    )(_.string("key")).toSet
  )

  /** Every card of the items of a kind, in one query. */
  def cards(itemKind: ItemKind): List[ItemCard] = database.read(
    _.query(
      """SELECT item.key, card.kind, card.phase, card.due,
        |  (SELECT count(*) FROM review WHERE review.card_id = card.id)
        |    AS reviews,
        |  (SELECT min(reviewed_at) FROM review WHERE review.card_id = card.id)
        |    AS first_reviewed
        |FROM item JOIN card ON card.item_id = item.id
        |WHERE item.kind = ?""".stripMargin,
      itemKind.name
    )(row =>
      ItemCard(
        row.string("key"),
        CardKind.values
          .find(_.name == row.string("kind"))
          .getOrElse(throw IllegalStateException(row.string("kind"))),
        Phase.valueOf(row.string("phase")),
        row.long("due"),
        row.int("reviews"),
        row.longOption("first_reviewed").getOrElse(row.long("due"))
      )
    )
  )

  /** Every card of every kana: what it tests, its phase, and when it is due. */
  def kanaCards(): List[KanaCard] = cards(ItemKind.Kana).map(card =>
    KanaCard(card.key, card.kind, card.phase, card.due)
  )

  /** The units the learner moved on from, by their points. */
  def unitsLeft(): Set[String] = database.read(
    _.query("SELECT point_id FROM unit_left")(_.string("point_id")).toSet
  )

  /** Moves the learner on from units, by their points. */
  def leaveUnits(points: List[String], at: Instant): Unit =
    database.transaction(session =>
      points.foreach(point =>
        session.update(
          "INSERT OR IGNORE INTO unit_left (point_id, left_at) VALUES (?, ?)",
          point,
          at.toEpochMilli
        )
      )
    )

  /** The items of a kind reviewed since `since`, in milliseconds since the
    * epoch.
    */
  def reviewedSince(itemKind: ItemKind, since: Long): Set[String] =
    database.read(
      _.query(
        """SELECT DISTINCT item.key FROM review
          |JOIN card ON card.id = review.card_id
          |JOIN item ON item.id = card.item_id
          |WHERE item.kind = ? AND review.reviewed_at >= ?""".stripMargin,
        itemKind.name,
        since
      )(_.string("key")).toSet
    )

  /** The cards a study session has asked so far: each kana and what it tests.
    */
  def sessionAsked(studySession: Long): Set[(String, CardKind)] =
    database.read(
      _.query(
        """SELECT DISTINCT item.key, card.kind FROM review
          |JOIN card ON card.id = review.card_id
          |JOIN item ON item.id = card.item_id
          |WHERE review.session_id = ?""".stripMargin,
        studySession
      )(row =>
        row.string("key") -> CardKind.values
          .find(_.name == row.string("kind"))
          .getOrElse(throw IllegalStateException(row.string("kind")))
      ).toSet
    )

  /** What a study session did: its reviews, those answered right, and the kana
    * it brought in (first reviewed in it), in the order they came.
    */
  def sessionSummary(studySession: Long): SessionSummary = database.read {
    session =>
      val grades = session.query(
        "SELECT grade FROM review WHERE session_id = ?",
        studySession
      )(_.int("grade"))
      val introduced = session.query(
        """SELECT DISTINCT item.key FROM review
        |JOIN card ON card.id = review.card_id
        |JOIN item ON item.id = card.item_id
        |WHERE review.session_id = ?
        |AND NOT EXISTS (SELECT 1 FROM review earlier
        |  JOIN card other ON other.id = earlier.card_id
        |  WHERE other.item_id = card.item_id AND earlier.id < review.id)
        |ORDER BY review.id""".stripMargin,
        studySession
      )(_.string("key"))
      SessionSummary(
        grades.size,
        grades.count(_ >= Grade.Good.value),
        introduced
      )
  }

  /** Every kana with a card: its status, and when its first card is due. */
  def kanaProgress(now: Long): List[KanaProgress] = database.read { session =>
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
        KanaProgress(
          key,
          statusOf(cards),
          Some(cards.map(_.due).min),
          cards.exists(_.due <= now)
        )
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

package hanamaru.backend.models

import hanamaru.backend.storage.Database

import java.time.Instant

import ox.discard

/** What a tutor's text was written for. */
enum WrittenFor(val key: String) {

  /** A sentence for a word of the vocabulary series. */
  case WordSentence extends WrittenFor("word-sentence")

  /** A lesson's reading text. */
  case LessonText extends WrittenFor("lesson-text")

  /** A sentence of free practice. */
  case FreeSentence extends WrittenFor("free-sentence")
}

/** What the tutor wrote and the code refused, kept (`refused_sentence`) to be
  * gone over: which instructions would have avoided it.
  */
final class Refusals(database: Database, writtenBy: String) {

  /** Keeps a refusal: what it was written for and on (`asked`: a word, a point
    * and its words), its sentences, and the problems named.
    */
  def kept(
      writtenFor: WrittenFor,
      asked: String,
      sentences: List[(String, String)],
      problems: List[String]
  ): Unit =
    database
      .transaction(
        _.insert(
          """INSERT INTO refused_sentence
            |(refused_at, written_for, asked, japanese, french, problems, written_by)
            |VALUES (?, ?, ?, ?, ?, ?, ?)""".stripMargin,
          Instant.now().toEpochMilli,
          writtenFor.key,
          asked,
          sentences.map(_._1).mkString("\n"),
          sentences.map(_._2).mkString("\n"),
          problems.mkString("\n"),
          writtenBy
        )
      )
      .discard
}

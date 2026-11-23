package hanamaru.frontend.model

import hanamaru.shared.kana.Kana

import scala.scalajs.js.URIUtils

/** A page the application shows, each at an address of its own. */
enum Page {

  /** Every kana, as a chart. */
  case Chart

  /** One kana: how it is written. */
  case Character(kana: Kana)

  /** The kana placement test. */
  case Placement

  /** Kana reviewed one after the other, as long as the learner wishes. */
  case Review

  /** A planned session: reviews due, then new kana, in the time given. */
  case Session

  /** The core vocabulary, in the order it is learned. */
  case Vocabulary

  /** A rapid series of words, both ways. */
  case Series

  /** The grammar curriculum, in the order it is learned. */
  case Grammar

  /** A grammar point's lesson. */
  case Lesson(point: String)

  /** The grammar placement test. */
  case GrammarPlacement

  /** A lesson session: the grammar reviews due, then the next lesson. */
  case LessonSession

  def path: String = this match {
    case Chart           => "/"
    case Character(kana) =>
      s"/write/${URIUtils.encodeURIComponent(kana.character)}"
    case Placement        => "/placement"
    case Review           => "/review"
    case Session          => "/session"
    case Vocabulary       => "/vocabulary"
    case Series           => "/vocabulary/series"
    case Grammar          => "/grammar"
    case GrammarPlacement => "/grammar/placement"
    case LessonSession    => "/grammar/session"
    case Lesson(point)    =>
      s"/grammar/${URIUtils.encodeURIComponent(point)}"
  }
}

object Page {

  /** The page at an address; the chart for any address that is not a page. */
  def at(path: String): Page =
    path.stripPrefix("/").split('/').toList match {
      case List("write", character) =>
        Kana
          .find(URIUtils.decodeURIComponent(character))
          .map(Character(_))
          .getOrElse(Chart)
      case List("placement")            => Placement
      case List("review")               => Review
      case List("session")              => Session
      case List("vocabulary")           => Vocabulary
      case List("vocabulary", "series") => Series
      case List("grammar")              => Grammar
      case List("grammar", "placement") => GrammarPlacement
      case List("grammar", "session")   => LessonSession
      case List("grammar", point)       =>
        Lesson(URIUtils.decodeURIComponent(point))
      case _ => Chart
    }
}

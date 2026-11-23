package hanamaru.frontend.model

import hanamaru.shared.kana.Kana

import scala.scalajs.js.URIUtils

/** A page the application shows, each at an address of its own. */
enum Page {

  /** Every kana, as a chart. */
  case Chart

  /** One kana: how it is written. */
  case Character(kana: Kana)

  def path: String = this match {
    case Chart           => "/"
    case Character(kana) =>
      s"/write/${URIUtils.encodeURIComponent(kana.character)}"
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
      case _ => Chart
    }
}

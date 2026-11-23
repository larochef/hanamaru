package hanamaru.frontend.i18n

import scala.scalajs.js

/** How long until a moment, in words: minutes, hours, then days. */
object Delays {

  def until(millis: Long): String = {
    val minutes = ((millis - js.Date.now()) / 60000).round.max(1)
    val hours = (minutes / 60.0).round
    val days = (minutes / 1440.0).round
    if (minutes < 60) {
      Tr(s"in $minutes min", s"dans $minutes min")
    } else if (hours < 24) {
      Tr(s"in $hours h", s"dans $hours h")
    } else if (days == 1) {
      Tr("tomorrow", "demain")
    } else {
      Tr(s"in $days days", s"dans $days jours")
    }
  }
}

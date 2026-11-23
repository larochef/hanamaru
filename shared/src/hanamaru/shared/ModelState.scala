package hanamaru.shared

import sttp.tapir.Schema

/** Where the tutor's model stands: its server not set up on this machine (the
  * setting missing, and the file it goes in), its model file not found, being
  * loaded, ready, or failed and why.
  */
enum ModelState {
  case NotConfigured(setting: String, file: String)
  case ModelMissing(model: String)
  case Starting
  case Ready
  case Failed(reason: String)
}

object ModelState {
  given Schema[ModelState] = Schema.derived
}

# Frontend Architecture

Stack: Scala.js, Laminar and Airstream (`Var`, `Signal`, `EventStream`), sttp and the Tapir endpoints of `shared`.

## The page decides nothing

- The page only shows and sends the learner's actions: what to show, in which order, every count, threshold or rule about the learner is decided by the backend, which answers ready to be shown (`TodayRoutes.today`, shown as it is by `HomePage`). Logic in the page duplicates the backend's, drifts from it, and long work there freezes the page

## Bus

- No `Future`: only Laminar and Airstream
- Components never reference a service. They push `AppCommand`s on the `AppBus` and react to its `AppEvent`s. `bus.request(command)(answer)` sends the command each time the returned stream starts, so binding it with `-->` or a `flatMapSwitch` replaces mount callbacks
- Events state facts. A business outcome has its own event; any other failure is `RequestFailed(command, reason)`
- Services subscribe to the commands and publish events. A new service takes the bus and the clients it needs, and runs each request with `AppStreams.runOnce`
- `ApiClient` returns `EventStream[AppResult[T]]`: failures travel as values, not stream errors. Don't alias the whole stream type: a one-parameter alias of a stream breaks the inference of `flatMapSwitch`
- State a component shows is derived from the bus's events (`events.collect { ... }.startWith(...)`), not kept in a `Var` that a service would have to update

## Components

- Every component is a class extending `ReactiveElement`, whose `lazy val node` is the Laminar element, built once. No factory objects
- A conditional modifier or element is `Option.when(condition)(...)`, never an `if` with an empty branch
- Every font is bundled with the page, never left to the system's: Japanese glyphs differ from one font to the next, and stroke shapes must match the textbook style
- Every text shown is `Tr(english, french)`: no text left in one language
- A sign alone in a button (+, −, ×) is drawn (`ButtonIcon`), never typed: the font's tall Japanese em box puts a text glyph off centre

## Airstream sttp backend

The sttp backend whose effect is an `EventStream` (`AjaxBackend`):

- A stream keeps no result, unlike a `Future`: restarting it sends the request again, and a late subscriber gets nothing
- Requests don't abort when their stream stops, and streaming bodies, file responses and web sockets aren't supported
- It relies on sttp's `internal` `BodyFromResponseAs`: check it when upgrading sttp

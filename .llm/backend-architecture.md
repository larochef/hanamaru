# Backend Architecture

## Routes

- Routes are defined in `shared`, one object per domain, on a common base route
- They are implemented in the domain's `*Api` class, next to what it serves, and registered in `Main`
- **Keep route handlers thin**: delegate to a method of the domain
- Types exchanged with the page are in `shared`, each with its jsoniter codec and Tapir schema in its companion
- A failure the page shows to the user is an error output carrying the reason in words

## Server

- Netty Sync (`tapir-netty-server-sync`), set up in `Main.scala`
- Anything under `/api` that matches no route is a 404; any other path gets the page's shell

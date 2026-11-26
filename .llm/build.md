# Build System

Mill, build file `build.mill`.

## THE MILL LOCK: CHECK IT BEFORE EVERY COMMAND

Mill locks `out/` for as long as a command runs, a `backend.run` and the IDE's build server included. A second `./mill` waits behind it without a word, for minutes.

- Run every Mill command as `./mill --no-wait-for-build-lock ...`, under a timeout: it fails at once when the lock is taken
- The lock is held by something you started: stop that one process, by its own PID, and retry
- The lock is held by anything else (the developer's `./mill backend.run`, the IDE): **stop and ask**. Never kill it, never wait for it. Meanwhile `MILL_OUTPUT_DIR=<another folder> ./mill ...` builds apart, with a lock of its own
- Never free a port or a lock with a blanket kill (`fuser -k`, `pkill`): find the process and check it is yours first
- Never start a test server with `./mill backend.run`, which keeps the lock

## Commands

- Compile: `./mill __.compile`, or `./mill [module].compile`. Warnings are always errors, by `-Wconf:any:e` in `build.mill`, not `-Werror`: scalafix's own compile must see unused imports as warnings to remove them
- Tests: `./mill __.test`. The heavy handwriting checks (the stroke matcher on every school kanji, the recogniser on all the learner wrote) are in `backend.calibration`, which `__.test` leaves out: run `./mill backend.calibration` whenever `shared/.../writing` changes
- After development is complete, always run `./mill __.fix + __.reformat` (scalafix import ordering, then scalafmt)

## Dependencies

- `mvn"groupId:artifactId:version"` in the module's `mvnDeps`; Scala.js dependencies use `mvn"groupId::artifactId::version"`
- Versions shared by several dependencies are vals at the top of `build.mill`
- Always the latest stable release: check Maven Central or npm when adding or touching one
- JavaScript libraries are npm dependencies in the root `package.json`, bundled by Vite

## Models

- No model runs on the GPU through a stock framework (PyTorch, TensorFlow, JAX, ONNX Runtime with a GPU provider): one long launch starves the desktop's graphics ring and can lose the session. Models run in their own servers (llama.cpp, whisper.cpp and the like), launched by the backend
- Local paths (model files, server binaries) come from the local configuration, never from the repository

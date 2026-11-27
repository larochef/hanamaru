-- What the tutor wrote and the code refused, kept to be gone over so as to
-- find instructions that avoid it: what it was written for and on, its
-- sentences (a text's one per line), the problems the code named (one per
-- line), and the model that wrote it.
CREATE TABLE refused_sentence (
  id INTEGER PRIMARY KEY,
  refused_at INTEGER NOT NULL,
  written_for TEXT NOT NULL,
  asked TEXT NOT NULL,
  japanese TEXT NOT NULL,
  french TEXT NOT NULL,
  problems TEXT NOT NULL,
  written_by TEXT NOT NULL
);

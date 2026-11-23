-- A card the placement test found probably known: its next review confirms it
-- or not.
ALTER TABLE card ADD COLUMN to_confirm INTEGER NOT NULL DEFAULT 0;

-- Where an answer was given: in practice, or in a placement test, whose
-- answers FSRS's parameters are not fitted on.
ALTER TABLE review ADD COLUMN source TEXT NOT NULL DEFAULT 'practice';

-- A placement test taken, for one domain (`kana`); not finished while it can
-- be resumed.
CREATE TABLE placement_run (
  id INTEGER PRIMARY KEY,
  domain TEXT NOT NULL,
  started_at INTEGER NOT NULL,
  finished_at INTEGER
);

-- Every answer of a placement test, in order: the question's kind
-- (`reading`, `writing`, or `skip` for a script given up), what it was about
-- (a kana, or the script), whether it was right, and the answer as JSON.
CREATE TABLE placement_answer (
  id INTEGER PRIMARY KEY,
  run_id INTEGER NOT NULL REFERENCES placement_run (id),
  question TEXT NOT NULL,
  subject TEXT NOT NULL,
  correct INTEGER NOT NULL,
  answer TEXT NOT NULL,
  answered_at INTEGER NOT NULL
);

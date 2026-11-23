-- A study session: its format (`express`), the minutes and context the
-- learner declared at its start, the exercise it asks now (as JSON, none once
-- it is over), and when it started and ended.
CREATE TABLE study_session (
  id INTEGER PRIMARY KEY,
  format TEXT NOT NULL,
  budget_minutes INTEGER NOT NULL,
  stylus INTEGER NOT NULL,
  current_exercise TEXT,
  started_at INTEGER NOT NULL,
  ended_at INTEGER
);

-- The session a review was given in, if any: how long exercises take is
-- measured from them.
ALTER TABLE review ADD COLUMN session_id INTEGER REFERENCES study_session (id);

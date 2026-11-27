-- The learner's questions to the tutor, kept whether it answered or not: what
-- they had before them (the context, as JSON), the question it goes on from
-- in a conversation, the answer and the model that gave it.
CREATE TABLE tutor_question (
  id INTEGER PRIMARY KEY,
  asked_at INTEGER NOT NULL,
  context TEXT NOT NULL,
  question TEXT NOT NULL,
  follows INTEGER REFERENCES tutor_question (id),
  answer TEXT,
  answered_by TEXT
);

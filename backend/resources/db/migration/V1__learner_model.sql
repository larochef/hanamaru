-- What can be known: a kana, a kanji, a word, a grammar point. `key` names it
-- within its kind: the character, for a kana.
CREATE TABLE item (
  id INTEGER PRIMARY KEY,
  kind TEXT NOT NULL,
  key TEXT NOT NULL,
  UNIQUE (kind, key)
);

-- One way of testing an item, scheduled by FSRS; there is a row once the card
-- has been reviewed. Times are milliseconds since the epoch, stability is in
-- days.
CREATE TABLE card (
  id INTEGER PRIMARY KEY,
  item_id INTEGER NOT NULL REFERENCES item (id),
  kind TEXT NOT NULL,
  phase TEXT NOT NULL,
  step INTEGER,
  stability REAL NOT NULL,
  difficulty REAL NOT NULL,
  due INTEGER NOT NULL,
  last_review INTEGER NOT NULL,
  UNIQUE (item_id, kind)
);

-- Every answer, kept: what was answered (as JSON) and its grade, 1 (again) to
-- 4 (easy), so that FSRS's parameters can be fitted on them later.
CREATE TABLE review (
  id INTEGER PRIMARY KEY,
  card_id INTEGER NOT NULL REFERENCES card (id),
  reviewed_at INTEGER NOT NULL,
  grade INTEGER NOT NULL,
  answer TEXT NOT NULL
);

CREATE INDEX review_by_card ON review (card_id, reviewed_at);

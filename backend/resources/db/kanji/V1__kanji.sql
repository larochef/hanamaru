-- A kanji as KANJIDIC2 gives it; readings and meanings are JSON lists.
CREATE TABLE kanji (
  literal TEXT PRIMARY KEY,
  grade INTEGER,
  strokes INTEGER NOT NULL,
  frequency INTEGER,
  on_readings TEXT NOT NULL,
  kun_readings TEXT NOT NULL,
  french TEXT NOT NULL,
  english TEXT NOT NULL
);

CREATE INDEX kanji_grade ON kanji (grade);

CREATE TABLE kanji_info (key TEXT PRIMARY KEY, value TEXT NOT NULL);

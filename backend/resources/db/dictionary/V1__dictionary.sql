-- A word: its JMdict number, its frequency rank, whether it is common.
CREATE TABLE word (id INTEGER PRIMARY KEY, rank INTEGER, common INTEGER NOT NULL);

-- Its written forms and readings, in JMdict's order; info and restricted_to
-- are JSON lists.
CREATE TABLE form (word_id INTEGER NOT NULL, position INTEGER NOT NULL,
kana INTEGER NOT NULL, text TEXT NOT NULL, common INTEGER NOT NULL,
info TEXT NOT NULL, restricted_to TEXT NOT NULL);

-- Its senses, by language; codes and glosses are JSON lists.
CREATE TABLE sense (word_id INTEGER NOT NULL, position INTEGER NOT NULL,
language TEXT NOT NULL, parts_of_speech TEXT NOT NULL, misc TEXT NOT NULL,
glosses TEXT NOT NULL);

CREATE TABLE dictionary_info (key TEXT PRIMARY KEY, value TEXT NOT NULL);

-- The core vocabulary: the JLPT level of the list a word is learned from (5,
-- the first words, to 1), and its place in that list.
CREATE TABLE core_word (word_id INTEGER PRIMARY KEY, level INTEGER NOT NULL,
position INTEGER NOT NULL);

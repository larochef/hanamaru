-- Words looked up by their forms, and their forms and senses by word. A
-- dictionary imported before may already have them.
CREATE INDEX IF NOT EXISTS word_by_rank ON word (rank);
CREATE INDEX IF NOT EXISTS form_by_word ON form (word_id, position);
CREATE INDEX IF NOT EXISTS form_by_text ON form (text);
CREATE INDEX IF NOT EXISTS sense_by_word ON sense (word_id, position);

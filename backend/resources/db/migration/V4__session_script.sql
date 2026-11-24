-- The kana script a session works on (`Hiragana`, `Katakana`); none for both.
ALTER TABLE study_session ADD COLUMN kana_script TEXT;

-- A unit the learner moved on from, once it was acquired, and when: the unit
-- under way is the first not left.
CREATE TABLE unit_left (
  point_id TEXT PRIMARY KEY,
  left_at INTEGER NOT NULL
);

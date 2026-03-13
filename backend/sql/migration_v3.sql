USE todo_db;

ALTER TABLE todo_task
    ADD COLUMN recurring_type VARCHAR(16) NOT NULL DEFAULT 'NONE' AFTER due_at,
    ADD COLUMN recurring_interval INT NOT NULL DEFAULT 1 AFTER recurring_type,
    ADD COLUMN version INT NOT NULL DEFAULT 1 AFTER recurring_interval;

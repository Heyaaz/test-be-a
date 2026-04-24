ALTER TABLE enrollments
    DROP INDEX uk_enrollments_class_user,
    ADD COLUMN active_user_id BIGINT GENERATED ALWAYS AS (
        CASE
            WHEN status <> 'CANCELLED' THEN user_id
            ELSE NULL
        END
    ) VIRTUAL,
    ADD CONSTRAINT uk_enrollments_class_active_user UNIQUE (class_id, active_user_id);

CREATE INDEX idx_enrollments_class_user_status
    ON enrollments (class_id, user_id, status);

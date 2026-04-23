CREATE INDEX idx_enrollments_user_status_id_desc
    ON enrollments (user_id, status, id DESC);

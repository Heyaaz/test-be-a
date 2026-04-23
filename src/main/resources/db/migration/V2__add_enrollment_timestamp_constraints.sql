ALTER TABLE enrollments
    ADD CONSTRAINT chk_enrollments_confirmed_at
        CHECK (status <> 'CONFIRMED' OR confirmed_at IS NOT NULL),
    ADD CONSTRAINT chk_enrollments_cancelled_at
        CHECK (status <> 'CANCELLED' OR cancelled_at IS NOT NULL);

CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    name VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT chk_users_role CHECK (role IN ('CREATOR', 'STUDENT'))
);

CREATE TABLE classes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    creator_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT NULL,
    price INT NOT NULL,
    capacity INT NOT NULL,
    enrolled_count INT NOT NULL DEFAULT 0,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_classes_creator FOREIGN KEY (creator_id) REFERENCES users (id),
    CONSTRAINT chk_classes_price CHECK (price >= 0),
    CONSTRAINT chk_classes_capacity CHECK (capacity > 0),
    CONSTRAINT chk_classes_enrolled_count CHECK (enrolled_count >= 0 AND enrolled_count <= capacity),
    CONSTRAINT chk_classes_date_range CHECK (end_date >= start_date),
    CONSTRAINT chk_classes_status CHECK (status IN ('DRAFT', 'OPEN', 'CLOSED'))
);

CREATE TABLE enrollments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    class_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    requested_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    confirmed_at DATETIME(6) NULL,
    cancelled_at DATETIME(6) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_enrollments_class FOREIGN KEY (class_id) REFERENCES classes (id),
    CONSTRAINT fk_enrollments_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uk_enrollments_class_user UNIQUE (class_id, user_id),
    CONSTRAINT chk_enrollments_status CHECK (status IN ('WAITING', 'PENDING', 'CONFIRMED', 'CANCELLED'))
);

CREATE INDEX idx_classes_status_id ON classes (status, id);
CREATE INDEX idx_classes_creator_id ON classes (creator_id, id);
CREATE INDEX idx_enrollments_user_id_desc ON enrollments (user_id, id DESC);
CREATE INDEX idx_enrollments_class_status_requested_id ON enrollments (class_id, status, requested_at, id);

INSERT INTO users (id, email, name, role) VALUES
    (1, 'creator1@example.com', 'Creator One', 'CREATOR'),
    (2, 'student1@example.com', 'Student One', 'STUDENT'),
    (3, 'creator2@example.com', 'Creator Two', 'CREATOR');

-- ADR-042: Database-backed users with ADMIN / VIEWER roles (upgrades ADR-005 flat role)

CREATE TABLE app_user (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL UNIQUE,
    full_name VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(10) NOT NULL DEFAULT 'VIEWER' CHECK (role IN ('ADMIN','VIEWER')),
    is_active BOOLEAN NOT NULL DEFAULT true,
    must_change_password BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(255),
    last_login_at TIMESTAMPTZ
);

-- Seed initial admin. Password is BCrypt of 'ChangeMe123!' — must be changed on first login.
INSERT INTO app_user (email, full_name, password_hash, role, must_change_password)
VALUES (
    'admin@cognologix.com',
    'System Administrator',
    '$2a$12$.nbnbhHqfmFzedS.IqbQn.izdkiO3xCj1xnjAp9xDABr60McbTPvG',
    'ADMIN',
    true
);

CREATE TABLE login_attempt (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL,
    attempted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    success BOOLEAN NOT NULL,
    ip_address VARCHAR(45)
);

CREATE INDEX idx_login_attempt_email_time ON login_attempt (email, attempted_at);

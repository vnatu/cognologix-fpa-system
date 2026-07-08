-- Module 1: People & Payroll Master Data Foundation (ADR-018, ADR-019, ADR-020)

-- 1. period
CREATE TABLE period (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    period_month INTEGER NOT NULL CHECK (period_month BETWEEN 1 AND 12),
    period_year INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (period_month, period_year)
);

-- 2. period_version
CREATE TABLE period_version (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    period_id UUID NOT NULL REFERENCES period(id),
    version_number INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('OPEN','SNAPSHOTS_UPLOADED','MASTER_BUILT','FINALISED')),
    is_latest_finalised BOOLEAN NOT NULL DEFAULT false,
    finalised_at TIMESTAMPTZ,
    finalised_by VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(255),
    UNIQUE (period_id, version_number)
);

-- 3. snapshot_upload
CREATE TABLE snapshot_upload (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    period_version_id UUID NOT NULL REFERENCES period_version(id),
    import_type VARCHAR(30) NOT NULL CHECK (import_type IN ('ZOHO_PEOPLE','ZOHO_PAYROLL','ZOHO_PEOPLE_EXITED')),
    uploaded_by VARCHAR(255) NOT NULL,
    uploaded_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    original_filename VARCHAR(500) NOT NULL,
    row_count INTEGER NOT NULL
);

-- 4. import_column_mapping
CREATE TABLE import_column_mapping (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    import_type VARCHAR(30) NOT NULL CHECK (import_type IN ('ZOHO_PEOPLE','ZOHO_PAYROLL','ZOHO_PEOPLE_EXITED')),
    template_name VARCHAR(255) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 5. import_column_mapping_line
CREATE TABLE import_column_mapping_line (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    mapping_id UUID NOT NULL REFERENCES import_column_mapping(id),
    excel_column_name VARCHAR(255) NOT NULL,
    system_attribute VARCHAR(100) NOT NULL,
    UNIQUE (mapping_id, system_attribute)
);

-- 6. employee_registry
CREATE TABLE employee_registry (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_id VARCHAR(100) NOT NULL UNIQUE,
    full_name VARCHAR(255) NOT NULL,
    date_of_joining DATE,
    exit_status VARCHAR(10) NOT NULL DEFAULT 'ACTIVE' CHECK (exit_status IN ('ACTIVE','EXITED')),
    exit_date DATE,
    exit_date_precision VARCHAR(12) CHECK (exit_date_precision IN ('MONTH_LEVEL','DAY_LEVEL')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 7. alternate_id_link
CREATE TABLE alternate_id_link (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_registry_id UUID NOT NULL REFERENCES employee_registry(id),
    alternate_employee_no VARCHAR(100) NOT NULL UNIQUE,
    mapped_by VARCHAR(255) NOT NULL,
    mapped_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 8. people_snapshot
CREATE TABLE people_snapshot (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    snapshot_upload_id UUID NOT NULL REFERENCES snapshot_upload(id),
    period_version_id UUID NOT NULL REFERENCES period_version(id),
    employee_id VARCHAR(100) NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    practice_unit VARCHAR(255) NOT NULL,
    business_unit VARCHAR(255) NOT NULL,
    bu_code VARCHAR(100),
    project_code VARCHAR(100),
    billable_status VARCHAR(1) NOT NULL CHECK (billable_status IN ('Y','N')),
    job_level VARCHAR(100),
    job_sub_level VARCHAR(100),
    title VARCHAR(255),
    date_of_joining DATE,
    UNIQUE (period_version_id, employee_id)
);

-- 9. payroll_snapshot
CREATE TABLE payroll_snapshot (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    snapshot_upload_id UUID NOT NULL REFERENCES snapshot_upload(id),
    period_version_id UUID NOT NULL REFERENCES period_version(id),
    employee_no VARCHAR(100) NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    gross_pay NUMERIC(12,2) NOT NULL,
    net_pay NUMERIC(12,2) NOT NULL,
    ctc_per_annum NUMERIC(14,2),
    UNIQUE (period_version_id, employee_no)
);

-- 10. master_record
CREATE TABLE master_record (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    period_version_id UUID NOT NULL REFERENCES period_version(id),
    employee_registry_id UUID NOT NULL REFERENCES employee_registry(id),
    people_snapshot_id UUID REFERENCES people_snapshot(id),
    payroll_snapshot_id UUID REFERENCES payroll_snapshot(id),
    practice_unit VARCHAR(255),
    business_unit VARCHAR(255),
    billable_status VARCHAR(1),
    job_level VARCHAR(100),
    gross_pay NUMERIC(12,2),
    is_delivery_pu BOOLEAN NOT NULL DEFAULT false,
    is_billable BOOLEAN NOT NULL DEFAULT false,
    is_bench BOOLEAN NOT NULL DEFAULT false,
    is_support BOOLEAN NOT NULL DEFAULT false,
    is_leadership BOOLEAN NOT NULL DEFAULT false,
    is_management BOOLEAN NOT NULL DEFAULT false,
    reconciliation_status VARCHAR(25) NOT NULL CHECK (reconciliation_status IN
        ('MATCHED','PAYROLL_PENDING','AUTO_MATCHED_EXITED','UNMATCHED','MANUALLY_MAPPED')),
    built_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    built_by VARCHAR(255),
    UNIQUE (period_version_id, employee_registry_id)
);

-- 11. classification_config
CREATE TABLE classification_config (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    config_type VARCHAR(20) NOT NULL CHECK (config_type IN ('DELIVERY_PU','MANAGEMENT_BU','LEADERSHIP_BU')),
    value VARCHAR(255) NOT NULL,
    UNIQUE (config_type, value)
);

INSERT INTO classification_config (config_type, value) VALUES
    ('DELIVERY_PU', 'Product Engineering'),
    ('DELIVERY_PU', 'DevOps & Cloud'),
    ('DELIVERY_PU', 'Data & AI'),
    ('MANAGEMENT_BU', 'Management'),
    ('LEADERSHIP_BU', 'Leadership');

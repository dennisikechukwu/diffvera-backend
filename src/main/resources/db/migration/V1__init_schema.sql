-- Diffvera Relational Schema V1
-- PostgreSQL 16+ Migration

-- Users
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    github_user_id BIGINT UNIQUE NOT NULL,
    login VARCHAR(100) NOT NULL,
    name VARCHAR(255),
    email VARCHAR(255),
    avatar_url VARCHAR(512),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- GitHub App Installations
CREATE TABLE installations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    github_installation_id BIGINT UNIQUE NOT NULL,
    account_login VARCHAR(100) NOT NULL,
    account_type VARCHAR(20) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'connected',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Repositories
CREATE TABLE repositories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    installation_id UUID NOT NULL REFERENCES installations(id) ON DELETE CASCADE,
    github_repo_id BIGINT UNIQUE NOT NULL,
    name VARCHAR(150) NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    is_private BOOLEAN NOT NULL DEFAULT false,
    language VARCHAR(50),
    default_branch VARCHAR(100) NOT NULL DEFAULT 'main',
    access_status VARCHAR(30) NOT NULL DEFAULT 'available',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_repositories_full_name ON repositories(full_name);
CREATE INDEX idx_repositories_installation ON repositories(installation_id);

-- Pull Requests
CREATE TABLE pull_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    repository_id UUID NOT NULL REFERENCES repositories(id) ON DELETE CASCADE,
    github_pr_id BIGINT NOT NULL,
    pr_number INT NOT NULL,
    title VARCHAR(500) NOT NULL,
    author_login VARCHAR(100) NOT NULL,
    base_branch VARCHAR(100) NOT NULL,
    head_branch VARCHAR(100) NOT NULL,
    base_sha VARCHAR(40) NOT NULL,
    head_sha VARCHAR(40) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'open',
    additions INT NOT NULL DEFAULT 0,
    deletions INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_repo_pr_number UNIQUE (repository_id, pr_number)
);

CREATE INDEX idx_pull_requests_repo_status ON pull_requests(repository_id, status);
CREATE INDEX idx_pull_requests_head_sha ON pull_requests(head_sha);

-- Architecture Governance Rules
CREATE TABLE architecture_rules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    repository_id UUID NOT NULL REFERENCES repositories(id) ON DELETE CASCADE,
    name VARCHAR(200) NOT NULL,
    is_enabled BOOLEAN NOT NULL DEFAULT true,
    current_version INT NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_architecture_rules_repo ON architecture_rules(repository_id);

-- Immutable Rule Versions
CREATE TABLE rule_versions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rule_id UUID NOT NULL REFERENCES architecture_rules(id) ON DELETE CASCADE,
    version_number INT NOT NULL,
    name VARCHAR(200) NOT NULL,
    boundary TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_rule_version UNIQUE (rule_id, version_number)
);

CREATE INDEX idx_rule_versions_rule_id ON rule_versions(rule_id);

-- Review Runs
CREATE TABLE review_runs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    pull_request_id UUID NOT NULL REFERENCES pull_requests(id) ON DELETE CASCADE,
    commit_sha VARCHAR(40) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'queued',
    failure_reason TEXT,
    requested_by_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    requested_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ
);

CREATE INDEX idx_review_runs_pr_commit ON review_runs(pull_request_id, commit_sha);
CREATE INDEX idx_review_runs_status ON review_runs(status);

-- Review Rule Snapshots
CREATE TABLE review_rule_snapshots (
    review_run_id UUID NOT NULL REFERENCES review_runs(id) ON DELETE CASCADE,
    rule_version_id UUID NOT NULL REFERENCES rule_versions(id) ON DELETE RESTRICT,
    PRIMARY KEY (review_run_id, rule_version_id)
);

-- Findings
CREATE TABLE findings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    review_run_id UUID NOT NULL REFERENCES review_runs(id) ON DELETE CASCADE,
    rule_version_id UUID REFERENCES rule_versions(id) ON DELETE SET NULL,
    category VARCHAR(30) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    title VARCHAR(300) NOT NULL,
    summary TEXT NOT NULL,
    file_path VARCHAR(1000) NOT NULL,
    line_start INT NOT NULL,
    line_end INT NOT NULL,
    code_excerpt TEXT NOT NULL,
    explanation TEXT NOT NULL,
    suggested_correction TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_findings_review_run ON findings(review_run_id);
CREATE INDEX idx_findings_category_severity ON findings(category, severity);

-- Finding Feedback
CREATE TABLE finding_feedback (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    finding_id UUID NOT NULL REFERENCES findings(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    feedback VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_finding_user_feedback UNIQUE (finding_id, user_id)
);

-- Idempotency Keys (Webhooks and Trigger Deduplication)
CREATE TABLE idempotency_keys (
    key VARCHAR(255) PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

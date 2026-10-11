-- Add pushed_at column to repositories to track latest commit activity
ALTER TABLE repositories ADD COLUMN IF NOT EXISTS pushed_at TIMESTAMPTZ;
CREATE INDEX IF NOT EXISTS idx_repositories_pushed_at ON repositories(pushed_at DESC NULLS LAST);

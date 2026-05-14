-- This file runs automatically when PostgresSQL volume is first created.
-- It ensures the pgvector extension is available for the application.
CREATE EXTENSION IF NOT EXISTS vector;
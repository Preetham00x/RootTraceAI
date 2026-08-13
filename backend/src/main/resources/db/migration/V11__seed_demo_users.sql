-- V11: Seed Demo Users for Quick-Fill Profiles
CREATE EXTENSION IF NOT EXISTS pgcrypto;

INSERT INTO users (id, email, password_hash, first_name, last_name, role)
VALUES 
  ('11111111-1111-1111-1111-111111111111', 'admin@roottrace.com', crypt('Admin123!', gen_salt('bf', 12)), 'Demo', 'Admin', 'ADMIN'),
  ('22222222-2222-2222-2222-222222222222', 'engineer@roottrace.com', crypt('Engineer123!', gen_salt('bf', 12)), 'Demo', 'Engineer', 'ENGINEER'),
  ('33333333-3333-3333-3333-333333333333', 'viewer@roottrace.com', crypt('Viewer123!', gen_salt('bf', 12)), 'Demo', 'Viewer', 'VIEWER')
ON CONFLICT (email) DO NOTHING;

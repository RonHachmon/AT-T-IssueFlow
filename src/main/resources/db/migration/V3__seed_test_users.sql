-- Seed users for development and testing.
-- Passwords (BCrypt strength 10):
--   admin     / Admin1234!
--   developer / Dev1234!
-- Regenerate hashes with BCryptPasswordEncoder if encoder strength changes.
INSERT INTO users (username, email, full_name, role, password_hash)
VALUES
  ('admin',
   'admin@issueflow.local',
   'Admin User',
   'ADMIN',
   '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy'),
  ('developer',
   'developer@issueflow.local',
   'Dev User',
   'DEVELOPER',
   '$2a$10$c6bON1nU.GxpwK3HCDKkPO5HGn2Wp1qEqjHKxkWPGwrRtjy1ziqjm')
ON CONFLICT DO NOTHING;

-- Remove users seeded by V3 with incorrect BCrypt hashes.
-- Dev/test users are now created programmatically by DevDataInitializer
-- using the application's PasswordEncoder, ensuring hashes always match.
DELETE FROM users WHERE username IN ('admin', 'developer');

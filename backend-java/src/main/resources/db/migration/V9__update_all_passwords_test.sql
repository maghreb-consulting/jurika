-- Met a jour tous les mots de passe des utilisateurs existants vers 'Test1234!'
UPDATE users 
SET password_hash = '$2a$10$Q1XLphECVhU8V/qiuBR/DOnovmJOsC7nwcFyIQOa/bolmPrZqNpLe';

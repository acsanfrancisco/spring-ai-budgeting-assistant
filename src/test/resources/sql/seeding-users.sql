INSERT INTO tb_user (id, full_name, email, password) VALUES
('00000000-0000-0000-0000-000000000001', 'First Test User', 'first@example.com', 'encoded-first-password'),
('00000000-0000-0000-0000-000000000002', 'Second Test User', 'second@example.com', 'encoded-second-password');

INSERT INTO tb_user_role (user_id, role) VALUES
('00000000-0000-0000-0000-000000000001', 'ROLE_USER'),
('00000000-0000-0000-0000-000000000002', 'ROLE_USER'),
('00000000-0000-0000-0000-000000000002', 'ROLE_ADMIN');

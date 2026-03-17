-- Test seed data for integration tests (H2 compatible - single row inserts)

-- Permissions
INSERT INTO permissions (id, name, description) VALUES ('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'user:read', 'Read users');
INSERT INTO permissions (id, name, description) VALUES ('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a12', 'user:manage', 'Create/update/delete users');
INSERT INTO permissions (id, name, description) VALUES ('c0eebc99-9c0b-4ef8-bb6d-6bb9bd380a13', 'department:read', 'Read departments');
INSERT INTO permissions (id, name, description) VALUES ('d0eebc99-9c0b-4ef8-bb6d-6bb9bd380a14', 'department:manage', 'Create/update/delete departments');
INSERT INTO permissions (id, name, description) VALUES ('e0eebc99-9c0b-4ef8-bb6d-6bb9bd380a15', 'role:read', 'Read roles');
INSERT INTO permissions (id, name, description) VALUES ('f0eebc99-9c0b-4ef8-bb6d-6bb9bd380a16', 'role:manage', 'Create/update/delete roles');
INSERT INTO permissions (id, name, description) VALUES ('01000000-0000-0000-0000-000000000001', 'resume:read', 'Read resumes');
INSERT INTO permissions (id, name, description) VALUES ('01000000-0000-0000-0000-000000000002', 'resume:manage', 'Manage resumes');
INSERT INTO permissions (id, name, description) VALUES ('01000000-0000-0000-0000-000000000003', 'job:read', 'Read job descriptions');
INSERT INTO permissions (id, name, description) VALUES ('01000000-0000-0000-0000-000000000004', 'job:manage', 'Manage job descriptions');
INSERT INTO permissions (id, name, description) VALUES ('01000000-0000-0000-0000-000000000005', 'match:read', 'Read match results');
INSERT INTO permissions (id, name, description) VALUES ('01000000-0000-0000-0000-000000000006', 'match:execute', 'Execute matching');

-- Roles
INSERT INTO roles (id, name, description) VALUES ('02000000-0000-0000-0000-000000000001', 'SUPER_ADMIN', 'Super Administrator');
INSERT INTO roles (id, name, description) VALUES ('02000000-0000-0000-0000-000000000002', 'HR_ADMIN', 'HR Administrator');
INSERT INTO roles (id, name, description) VALUES ('02000000-0000-0000-0000-000000000003', 'DEPT_ADMIN', 'Department Administrator');
INSERT INTO roles (id, name, description) VALUES ('02000000-0000-0000-0000-000000000004', 'USER', 'Regular User');

-- Role-Permission assignments
-- SUPER_ADMIN gets all permissions
INSERT INTO role_permissions (role_id, permission_id) SELECT '02000000-0000-0000-0000-000000000001', id FROM permissions;

-- HR_ADMIN
INSERT INTO role_permissions (role_id, permission_id) VALUES ('02000000-0000-0000-0000-000000000002', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11');
INSERT INTO role_permissions (role_id, permission_id) VALUES ('02000000-0000-0000-0000-000000000002', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a12');
INSERT INTO role_permissions (role_id, permission_id) VALUES ('02000000-0000-0000-0000-000000000002', 'c0eebc99-9c0b-4ef8-bb6d-6bb9bd380a13');
INSERT INTO role_permissions (role_id, permission_id) VALUES ('02000000-0000-0000-0000-000000000002', 'd0eebc99-9c0b-4ef8-bb6d-6bb9bd380a14');
INSERT INTO role_permissions (role_id, permission_id) VALUES ('02000000-0000-0000-0000-000000000002', 'e0eebc99-9c0b-4ef8-bb6d-6bb9bd380a15');
INSERT INTO role_permissions (role_id, permission_id) VALUES ('02000000-0000-0000-0000-000000000002', 'f0eebc99-9c0b-4ef8-bb6d-6bb9bd380a16');
INSERT INTO role_permissions (role_id, permission_id) VALUES ('02000000-0000-0000-0000-000000000002', '01000000-0000-0000-0000-000000000001');
INSERT INTO role_permissions (role_id, permission_id) VALUES ('02000000-0000-0000-0000-000000000002', '01000000-0000-0000-0000-000000000002');
INSERT INTO role_permissions (role_id, permission_id) VALUES ('02000000-0000-0000-0000-000000000002', '01000000-0000-0000-0000-000000000003');
INSERT INTO role_permissions (role_id, permission_id) VALUES ('02000000-0000-0000-0000-000000000002', '01000000-0000-0000-0000-000000000004');
INSERT INTO role_permissions (role_id, permission_id) VALUES ('02000000-0000-0000-0000-000000000002', '01000000-0000-0000-0000-000000000005');
INSERT INTO role_permissions (role_id, permission_id) VALUES ('02000000-0000-0000-0000-000000000002', '01000000-0000-0000-0000-000000000006');

-- DEPT_ADMIN
INSERT INTO role_permissions (role_id, permission_id) VALUES ('02000000-0000-0000-0000-000000000003', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11');
INSERT INTO role_permissions (role_id, permission_id) VALUES ('02000000-0000-0000-0000-000000000003', 'c0eebc99-9c0b-4ef8-bb6d-6bb9bd380a13');
INSERT INTO role_permissions (role_id, permission_id) VALUES ('02000000-0000-0000-0000-000000000003', '01000000-0000-0000-0000-000000000001');
INSERT INTO role_permissions (role_id, permission_id) VALUES ('02000000-0000-0000-0000-000000000003', '01000000-0000-0000-0000-000000000002');
INSERT INTO role_permissions (role_id, permission_id) VALUES ('02000000-0000-0000-0000-000000000003', '01000000-0000-0000-0000-000000000003');
INSERT INTO role_permissions (role_id, permission_id) VALUES ('02000000-0000-0000-0000-000000000003', '01000000-0000-0000-0000-000000000004');
INSERT INTO role_permissions (role_id, permission_id) VALUES ('02000000-0000-0000-0000-000000000003', '01000000-0000-0000-0000-000000000005');
INSERT INTO role_permissions (role_id, permission_id) VALUES ('02000000-0000-0000-0000-000000000003', '01000000-0000-0000-0000-000000000006');

-- USER
INSERT INTO role_permissions (role_id, permission_id) VALUES ('02000000-0000-0000-0000-000000000004', '01000000-0000-0000-0000-000000000001');
INSERT INTO role_permissions (role_id, permission_id) VALUES ('02000000-0000-0000-0000-000000000004', '01000000-0000-0000-0000-000000000003');
INSERT INTO role_permissions (role_id, permission_id) VALUES ('02000000-0000-0000-0000-000000000004', '01000000-0000-0000-0000-000000000005');

-- Default Department
INSERT INTO departments (id, name, parent_id) VALUES ('03000000-0000-0000-0000-000000000001', 'Headquarters', NULL);
INSERT INTO departments (id, name, parent_id) VALUES ('03000000-0000-0000-0000-000000000002', 'Engineering', '03000000-0000-0000-0000-000000000001');
INSERT INTO departments (id, name, parent_id) VALUES ('03000000-0000-0000-0000-000000000003', 'Human Resources', '03000000-0000-0000-0000-000000000001');

-- Default Admin User (password: admin123, BCrypt hashed)
INSERT INTO users (id, username, password, email, enabled, department_id) VALUES ('04000000-0000-0000-0000-000000000001', 'admin', '$2b$10$7LwiHnYbHiFA7/xDLxJgBOZOBEkDfeAVckl980mtY8.8SNXnAUiVO', 'admin@aihiring.com', TRUE, '03000000-0000-0000-0000-000000000001');

-- Assign admin to SUPER_ADMIN role
INSERT INTO user_roles (user_id, role_id) VALUES ('04000000-0000-0000-0000-000000000001', '02000000-0000-0000-0000-000000000001');

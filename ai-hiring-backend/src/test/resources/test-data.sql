-- Test seed data for integration tests (H2 compatible - single row inserts, idempotent via MERGE INTO)

-- Permissions
MERGE INTO permissions (id, name, description) KEY(id) VALUES ('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'user:read', 'Read users');
MERGE INTO permissions (id, name, description) KEY(id) VALUES ('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a12', 'user:manage', 'Create/update/delete users');
MERGE INTO permissions (id, name, description) KEY(id) VALUES ('c0eebc99-9c0b-4ef8-bb6d-6bb9bd380a13', 'department:read', 'Read departments');
MERGE INTO permissions (id, name, description) KEY(id) VALUES ('d0eebc99-9c0b-4ef8-bb6d-6bb9bd380a14', 'department:manage', 'Create/update/delete departments');
MERGE INTO permissions (id, name, description) KEY(id) VALUES ('e0eebc99-9c0b-4ef8-bb6d-6bb9bd380a15', 'role:read', 'Read roles');
MERGE INTO permissions (id, name, description) KEY(id) VALUES ('f0eebc99-9c0b-4ef8-bb6d-6bb9bd380a16', 'role:manage', 'Create/update/delete roles');
MERGE INTO permissions (id, name, description) KEY(id) VALUES ('01000000-0000-0000-0000-000000000001', 'resume:read', 'Read resumes');
MERGE INTO permissions (id, name, description) KEY(id) VALUES ('01000000-0000-0000-0000-000000000002', 'resume:manage', 'Manage resumes');
MERGE INTO permissions (id, name, description) KEY(id) VALUES ('01000000-0000-0000-0000-000000000003', 'job:read', 'Read job descriptions');
MERGE INTO permissions (id, name, description) KEY(id) VALUES ('01000000-0000-0000-0000-000000000004', 'job:manage', 'Manage job descriptions');
MERGE INTO permissions (id, name, description) KEY(id) VALUES ('01000000-0000-0000-0000-000000000005', 'match:read', 'Read match results');
MERGE INTO permissions (id, name, description) KEY(id) VALUES ('01000000-0000-0000-0000-000000000006', 'match:execute', 'Execute matching');

-- Roles
MERGE INTO roles (id, name, description) KEY(id) VALUES ('02000000-0000-0000-0000-000000000001', 'SUPER_ADMIN', 'Super Administrator');
MERGE INTO roles (id, name, description) KEY(id) VALUES ('02000000-0000-0000-0000-000000000002', 'HR_ADMIN', 'HR Administrator');
MERGE INTO roles (id, name, description) KEY(id) VALUES ('02000000-0000-0000-0000-000000000003', 'DEPT_ADMIN', 'Department Administrator');
MERGE INTO roles (id, name, description) KEY(id) VALUES ('02000000-0000-0000-0000-000000000004', 'USER', 'Regular User');

-- Role-Permission assignments
-- SUPER_ADMIN gets all permissions
MERGE INTO role_permissions (role_id, permission_id) KEY(role_id, permission_id) SELECT '02000000-0000-0000-0000-000000000001', id FROM permissions;

-- HR_ADMIN
MERGE INTO role_permissions (role_id, permission_id) KEY(role_id, permission_id) VALUES ('02000000-0000-0000-0000-000000000002', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11');
MERGE INTO role_permissions (role_id, permission_id) KEY(role_id, permission_id) VALUES ('02000000-0000-0000-0000-000000000002', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a12');
MERGE INTO role_permissions (role_id, permission_id) KEY(role_id, permission_id) VALUES ('02000000-0000-0000-0000-000000000002', 'c0eebc99-9c0b-4ef8-bb6d-6bb9bd380a13');
MERGE INTO role_permissions (role_id, permission_id) KEY(role_id, permission_id) VALUES ('02000000-0000-0000-0000-000000000002', 'd0eebc99-9c0b-4ef8-bb6d-6bb9bd380a14');
MERGE INTO role_permissions (role_id, permission_id) KEY(role_id, permission_id) VALUES ('02000000-0000-0000-0000-000000000002', 'e0eebc99-9c0b-4ef8-bb6d-6bb9bd380a15');
MERGE INTO role_permissions (role_id, permission_id) KEY(role_id, permission_id) VALUES ('02000000-0000-0000-0000-000000000002', 'f0eebc99-9c0b-4ef8-bb6d-6bb9bd380a16');
MERGE INTO role_permissions (role_id, permission_id) KEY(role_id, permission_id) VALUES ('02000000-0000-0000-0000-000000000002', '01000000-0000-0000-0000-000000000001');
MERGE INTO role_permissions (role_id, permission_id) KEY(role_id, permission_id) VALUES ('02000000-0000-0000-0000-000000000002', '01000000-0000-0000-0000-000000000002');
MERGE INTO role_permissions (role_id, permission_id) KEY(role_id, permission_id) VALUES ('02000000-0000-0000-0000-000000000002', '01000000-0000-0000-0000-000000000003');
MERGE INTO role_permissions (role_id, permission_id) KEY(role_id, permission_id) VALUES ('02000000-0000-0000-0000-000000000002', '01000000-0000-0000-0000-000000000004');
MERGE INTO role_permissions (role_id, permission_id) KEY(role_id, permission_id) VALUES ('02000000-0000-0000-0000-000000000002', '01000000-0000-0000-0000-000000000005');
MERGE INTO role_permissions (role_id, permission_id) KEY(role_id, permission_id) VALUES ('02000000-0000-0000-0000-000000000002', '01000000-0000-0000-0000-000000000006');

-- DEPT_ADMIN
MERGE INTO role_permissions (role_id, permission_id) KEY(role_id, permission_id) VALUES ('02000000-0000-0000-0000-000000000003', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11');
MERGE INTO role_permissions (role_id, permission_id) KEY(role_id, permission_id) VALUES ('02000000-0000-0000-0000-000000000003', 'c0eebc99-9c0b-4ef8-bb6d-6bb9bd380a13');
MERGE INTO role_permissions (role_id, permission_id) KEY(role_id, permission_id) VALUES ('02000000-0000-0000-0000-000000000003', '01000000-0000-0000-0000-000000000001');
MERGE INTO role_permissions (role_id, permission_id) KEY(role_id, permission_id) VALUES ('02000000-0000-0000-0000-000000000003', '01000000-0000-0000-0000-000000000002');
MERGE INTO role_permissions (role_id, permission_id) KEY(role_id, permission_id) VALUES ('02000000-0000-0000-0000-000000000003', '01000000-0000-0000-0000-000000000003');
MERGE INTO role_permissions (role_id, permission_id) KEY(role_id, permission_id) VALUES ('02000000-0000-0000-0000-000000000003', '01000000-0000-0000-0000-000000000004');
MERGE INTO role_permissions (role_id, permission_id) KEY(role_id, permission_id) VALUES ('02000000-0000-0000-0000-000000000003', '01000000-0000-0000-0000-000000000005');
MERGE INTO role_permissions (role_id, permission_id) KEY(role_id, permission_id) VALUES ('02000000-0000-0000-0000-000000000003', '01000000-0000-0000-0000-000000000006');

-- USER
MERGE INTO role_permissions (role_id, permission_id) KEY(role_id, permission_id) VALUES ('02000000-0000-0000-0000-000000000004', '01000000-0000-0000-0000-000000000001');
MERGE INTO role_permissions (role_id, permission_id) KEY(role_id, permission_id) VALUES ('02000000-0000-0000-0000-000000000004', '01000000-0000-0000-0000-000000000003');
MERGE INTO role_permissions (role_id, permission_id) KEY(role_id, permission_id) VALUES ('02000000-0000-0000-0000-000000000004', '01000000-0000-0000-0000-000000000005');

-- Default Department
MERGE INTO departments (id, name, parent_id) KEY(id) VALUES ('03000000-0000-0000-0000-000000000001', 'Headquarters', NULL);
MERGE INTO departments (id, name, parent_id) KEY(id) VALUES ('03000000-0000-0000-0000-000000000002', 'Engineering', '03000000-0000-0000-0000-000000000001');
MERGE INTO departments (id, name, parent_id) KEY(id) VALUES ('03000000-0000-0000-0000-000000000003', 'Human Resources', '03000000-0000-0000-0000-000000000001');

-- Default Admin User (password: admin123, BCrypt hashed)
MERGE INTO users (id, username, password, email, enabled, department_id) KEY(id) VALUES ('04000000-0000-0000-0000-000000000001', 'admin', '$2b$10$7LwiHnYbHiFA7/xDLxJgBOZOBEkDfeAVckl980mtY8.8SNXnAUiVO', 'admin@aihiring.com', TRUE, '03000000-0000-0000-0000-000000000001');

-- Assign admin to SUPER_ADMIN role
MERGE INTO user_roles (user_id, role_id) KEY(user_id, role_id) VALUES ('04000000-0000-0000-0000-000000000001', '02000000-0000-0000-0000-000000000001');

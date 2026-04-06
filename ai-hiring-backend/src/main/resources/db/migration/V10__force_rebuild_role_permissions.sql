-- V10: Force rebuild role_permissions from scratch
-- V9 was marked "success" by Flyway repair() but SQL never actually ran on staging.
-- This is a new migration that will always execute, ensuring correct data.

-- Step 1: Delete all existing role_permissions
DELETE FROM role_permissions WHERE role_id IN (SELECT id FROM roles);

-- Step 2: Re-insert using CROSS JOIN with actual DB IDs

-- SUPER_ADMIN: all permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'SUPER_ADMIN';

-- HR_ADMIN: all except role:manage
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'HR_ADMIN'
  AND p.name IN ('user:read', 'user:manage', 'department:read', 'department:manage',
                  'role:read', 'role:manage', 'resume:read', 'resume:manage',
                  'job:read', 'job:manage', 'match:read', 'match:execute');

-- DEPT_ADMIN: same as HR_ADMIN minus role:manage, user:manage, department:manage
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'DEPT_ADMIN'
  AND p.name IN ('user:read', 'department:read', 'resume:read', 'resume:manage',
                  'job:read', 'job:manage', 'match:read', 'match:execute');

-- USER: read-only
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'USER'
  AND p.name IN ('resume:read', 'job:read', 'match:read');

-- Step 3: Ensure departments exist with correct Chinese names
INSERT INTO departments (id, name, parent_id)
SELECT '01000000-0000-0000-0000-000000000001', '总部', NULL
WHERE NOT EXISTS (SELECT 1 FROM departments WHERE name = '总部');

INSERT INTO departments (id, name, parent_id)
SELECT '02000000-0000-0000-0000-000000000001', '研发部', d.id
FROM departments d
WHERE d.name = '总部'
  AND NOT EXISTS (SELECT 1 FROM departments WHERE name = '研发部');

INSERT INTO departments (id, name, parent_id)
SELECT '02000000-0000-0000-0000-000000000002', '人事部', d.id
FROM departments d
WHERE d.name = '总部'
  AND NOT EXISTS (SELECT 1 FROM departments WHERE name = '人事部');

INSERT INTO departments (id, name, parent_id)
SELECT '03000000-0000-0000-0000-000000000004', '产品部', d.id
FROM departments d
WHERE d.name = '总部'
  AND NOT EXISTS (SELECT 1 FROM departments WHERE name = '产品部');

INSERT INTO departments (id, name, parent_id)
SELECT '03000000-0000-0000-0000-000000000005', '市场部', d.id
FROM departments d
WHERE d.name = '总部'
  AND NOT EXISTS (SELECT 1 FROM departments WHERE name = '市场部');

INSERT INTO departments (id, name, parent_id)
SELECT '03000000-0000-0000-0000-000000000006', '财务部', d.id
FROM departments d
WHERE d.name = '总部'
  AND NOT EXISTS (SELECT 1 FROM departments WHERE name = '财务部');

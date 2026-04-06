-- V9__unify_seed_data_chinese_dept_names.sql
-- Unify seed data: fix role_permissions using dynamic IDs, rename departments to Chinese.
-- This replaces DataInitializer.java with Flyway-only approach.

-- Step 1: Re-sync all role_permissions using CROSS JOIN with actual DB IDs
DELETE FROM role_permissions WHERE role_id IN (SELECT id FROM roles);

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

-- Step 2: Update department names to Chinese
UPDATE departments SET name = '总部' WHERE name = 'Headquarters';
UPDATE departments SET name = '研发部' WHERE name = 'Engineering';
UPDATE departments SET name = '人事部' WHERE name = 'Human Resources';

-- Step 3: Add missing departments under HQ
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

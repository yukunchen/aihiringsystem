-- V8__force_sync_role_permissions.sql
-- Force re-sync role_permissions for SUPER_ADMIN.
-- V7 used ON CONFLICT DO NOTHING which didn't fix the actual issue.
-- This migration uses DELETE + re-INSERT to guarantee correctness.

-- Step 1: Delete all existing role_permissions for SUPER_ADMIN
DELETE FROM role_permissions
WHERE role_id = (SELECT id FROM roles WHERE name = 'SUPER_ADMIN');

-- Step 2: Re-insert ALL permissions for SUPER_ADMIN using actual DB IDs
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'SUPER_ADMIN';

-- Step 3: Also fix HR_ADMIN, DEPT_ADMIN, USER roles
DELETE FROM role_permissions
WHERE role_id = (SELECT id FROM roles WHERE name = 'HR_ADMIN');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'HR_ADMIN';

DELETE FROM role_permissions
WHERE role_id IN (SELECT id FROM roles WHERE name IN ('DEPT_ADMIN', 'USER'));

-- DEPT_ADMIN: all except user:manage, department:manage, role:manage, role:read
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

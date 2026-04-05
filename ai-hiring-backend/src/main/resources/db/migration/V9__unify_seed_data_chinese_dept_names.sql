-- V9__unify_seed_data_chinese_dept_names.sql
 --
-- =============================================
-- 组织架构部门
 应该有初始数据:
 使用中文名称,研发部'、人事部'。
但你
 否部门虽然 '人事部' 统一由总部下面管理，所以创建 JD 的时候
部门下拉
菜单会显示这些部门。
总部下面应该也能有子部门（产品部、市场部等）。
这样前端创建 JD 时才能选择部门。

但如果没有部门，系统会为空，

departmentId` 为必填。
`departmentId` (创建 JD 时 `departmentId` 从 departments WHERE name IN ('研发部', '人事部'))
 VALUES ('product', 'product部'),
        ('market', '市场部'),
        ('finance', '财务部');

 -- 如果 not exists, do nothing

 -- ensure all role-permission mappings are correct
使用 dynamic IDs
        DELETE FROM role_permissions WHERE role_id IN (SELECT id from roles where name in ('SUPER_ADMIN'));
        INSERT INTO role_permissions (role_id, permission_id)
        SELECT r.id, p.id
 FROM roles r, CROSS JOIN permissions p
 WHERE r.name = 'SUPER_ADMIN';

        -- HR_ADMIN: all except role:manage
        INSERT INTO role_permissions (role_id, permission_id)
        SELECT r.id, p.id
 FROM roles r
 CROSS JOIN permissions p
 WHERE r.name = 'HR_ADMIN' AND p.name in ('user:read', 'user:manage', 'department:read', 'department:manage', 'role:read', 'role:manage',
 'resume:read', 'resume:manage', 'job:read', 'job:manage', 'match:read', 'match:execute');
        -- DEPT_ADMIN
 same as HR_ADMIN minus role:manage, all
        INSERT INTO role_permissions (role_id, permission_id)
        SELECT r.id, p.id
 FROM roles r
 CROSS JOIN permissions p
 where r.name = 'DEPT_ADMIN' AND p.name in ('user:read', 'department:read', 'resume:read', 'resume:manage', 'job:read', 'job:manage', 'match:read', 'match:execute');
        -- USER: read-only
        INSERT INTO role_permissions (role_id, permission_id)
        SELECT r.id, p.id
 FROM roles r
 CROSS JOIN permissions p
 where r.name = 'USER' and p.name in ('resume:read', 'job:read', 'match:read');
        -- Step 4: Update department names to Chinese
UPDATE departments SET name = '总部' WHERE name = 'Headquarters';
UPDATE departments SET name = '研发部' WHERE name = 'Engineering';
UPDATE departments SET name = '人事部' WHERE name = 'Human Resources';
        -- Add missing departments
        INSERT INTO departments (name, id, parent_id)
 VALUES
 ('产品部', hq_id), ('市场部', hq_id), ('财务部', hq_id);
END CASE when not exists;


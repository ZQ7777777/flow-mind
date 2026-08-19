-- 文件编码：UTF-8
-- 脚本可重复执行：按稳定 ID 新增或修复部门、用户记录。
PRAGMA foreign_keys = ON;
BEGIN IMMEDIATE;

-- =========================================================
-- 1. 新增交割部、结算部
-- =========================================================

INSERT INTO mock_department (id, name, parent_id, manager_id)
VALUES (
           'dept_delivery',
           '交割部',
           'dept_company',
           'u_delivery_manager_01'
       )
ON CONFLICT(id) DO UPDATE SET
    name = excluded.name,
    parent_id = excluded.parent_id,
    manager_id = excluded.manager_id;

INSERT INTO mock_department (id, name, parent_id, manager_id)
VALUES (
           'dept_settlement',
           '结算部',
           'dept_company',
           'u_settlement_manager_01'
       )
ON CONFLICT(id) DO UPDATE SET
    name = excluded.name,
    parent_id = excluded.parent_id,
    manager_id = excluded.manager_id;


-- =========================================================
-- 2. 交割部：5 名职工 + 1 名经理
-- 密码沿用现有 Mock 用户密码
-- =========================================================

INSERT INTO mock_user
(id, username, password, real_name, dept_id, position, user_type, status)
VALUES
    (
        'u_delivery_01',
        'delivery01',
        '$2b$10$qW04e8jifpX5WfdIPCOe6.Qa5H3r76tK0EbpSaIIRLLvXzCHlc.Iu',
        '交割职工一',
        'dept_delivery',
        '交割职工',
        'USER',
        1
    ),
    (
        'u_delivery_02',
        'delivery02',
        '$2b$10$qW04e8jifpX5WfdIPCOe6.Qa5H3r76tK0EbpSaIIRLLvXzCHlc.Iu',
        '交割职工二',
        'dept_delivery',
        '交割职工',
        'USER',
        1
    ),
    (
        'u_delivery_03',
        'delivery03',
        '$2b$10$qW04e8jifpX5WfdIPCOe6.Qa5H3r76tK0EbpSaIIRLLvXzCHlc.Iu',
        '交割职工三',
        'dept_delivery',
        '交割职工',
        'USER',
        1
    ),
    (
        'u_delivery_04',
        'delivery04',
        '$2b$10$qW04e8jifpX5WfdIPCOe6.Qa5H3r76tK0EbpSaIIRLLvXzCHlc.Iu',
        '交割职工四',
        'dept_delivery',
        '交割职工',
        'USER',
        1
    ),
    (
        'u_delivery_05',
        'delivery05',
        '$2b$10$qW04e8jifpX5WfdIPCOe6.Qa5H3r76tK0EbpSaIIRLLvXzCHlc.Iu',
        '交割职工五',
        'dept_delivery',
        '交割职工',
        'USER',
        1
    ),
    (
        'u_delivery_manager_01',
        'manager_delivery',
        '$2b$10$qW04e8jifpX5WfdIPCOe6.Qa5H3r76tK0EbpSaIIRLLvXzCHlc.Iu',
        '交割经理',
        'dept_delivery',
        '交割经理',
        'USER',
        1
    )
ON CONFLICT(id) DO UPDATE SET
    username = excluded.username,
    password = excluded.password,
    real_name = excluded.real_name,
    dept_id = excluded.dept_id,
    position = excluded.position,
    user_type = excluded.user_type,
    status = excluded.status;


-- =========================================================
-- 3. 结算部：5 名职工 + 1 名经理
-- =========================================================

INSERT INTO mock_user
(id, username, password, real_name, dept_id, position, user_type, status)
VALUES
    (
        'u_settlement_01',
        'settlement01',
        '$2b$10$qW04e8jifpX5WfdIPCOe6.Qa5H3r76tK0EbpSaIIRLLvXzCHlc.Iu',
        '结算职工一',
        'dept_settlement',
        '结算职工',
        'USER',
        1
    ),
    (
        'u_settlement_02',
        'settlement02',
        '$2b$10$qW04e8jifpX5WfdIPCOe6.Qa5H3r76tK0EbpSaIIRLLvXzCHlc.Iu',
        '结算职工二',
        'dept_settlement',
        '结算职工',
        'USER',
        1
    ),
    (
        'u_settlement_03',
        'settlement03',
        '$2b$10$qW04e8jifpX5WfdIPCOe6.Qa5H3r76tK0EbpSaIIRLLvXzCHlc.Iu',
        '结算职工三',
        'dept_settlement',
        '结算职工',
        'USER',
        1
    ),
    (
        'u_settlement_04',
        'settlement04',
        '$2b$10$qW04e8jifpX5WfdIPCOe6.Qa5H3r76tK0EbpSaIIRLLvXzCHlc.Iu',
        '结算职工四',
        'dept_settlement',
        '结算职工',
        'USER',
        1
    ),
    (
        'u_settlement_05',
        'settlement05',
        '$2b$10$qW04e8jifpX5WfdIPCOe6.Qa5H3r76tK0EbpSaIIRLLvXzCHlc.Iu',
        '结算职工五',
        'dept_settlement',
        '结算职工',
        'USER',
        1
    ),
    (
        'u_settlement_manager_01',
        'manager_settlement',
        '$2b$10$qW04e8jifpX5WfdIPCOe6.Qa5H3r76tK0EbpSaIIRLLvXzCHlc.Iu',
        '结算经理',
        'dept_settlement',
        '结算经理',
        'USER',
        1
    )
ON CONFLICT(id) DO UPDATE SET
    username = excluded.username,
    password = excluded.password,
    real_name = excluded.real_name,
    dept_id = excluded.dept_id,
    position = excluded.position,
    user_type = excluded.user_type,
    status = excluded.status;

COMMIT;

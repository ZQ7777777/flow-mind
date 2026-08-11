CREATE TABLE IF NOT EXISTS mock_user (
    id VARCHAR(64) PRIMARY KEY,
    username VARCHAR(64) NOT NULL UNIQUE,
    password VARCHAR(100) NOT NULL,
    real_name VARCHAR(100) NOT NULL,
    dept_id VARCHAR(64) NOT NULL,
    position VARCHAR(32) NOT NULL,
    user_type VARCHAR(16) NOT NULL CHECK (user_type IN ('ADMIN', 'USER')),
    status INTEGER NOT NULL DEFAULT 1 CHECK (status IN (0, 1)),
    FOREIGN KEY (dept_id) REFERENCES mock_department(id)
);

CREATE INDEX IF NOT EXISTS idx_mock_user_department ON mock_user(dept_id);
CREATE INDEX IF NOT EXISTS idx_mock_user_type ON mock_user(user_type);
CREATE INDEX IF NOT EXISTS idx_mock_user_position ON mock_user(position);
CREATE INDEX IF NOT EXISTS idx_mock_user_status ON mock_user(status);

-- BCrypt hash for the local test password 123456.
INSERT INTO mock_user (id, username, password, real_name, dept_id, position, user_type, status) VALUES
('u_admin_01', 'admin01', '$2b$10$qW04e8jifpX5WfdIPCOe6.Qa5H3r76tK0EbpSaIIRLLvXzCHlc.Iu', '系统管理员一', 'dept_company', '系统管理员', 'ADMIN', 1),
('u_admin_02', 'admin02', '$2b$10$qW04e8jifpX5WfdIPCOe6.Qa5H3r76tK0EbpSaIIRLLvXzCHlc.Iu', '系统管理员二', 'dept_company', '系统管理员', 'ADMIN', 1),
('u_admin_03', 'admin03', '$2b$10$qW04e8jifpX5WfdIPCOe6.Qa5H3r76tK0EbpSaIIRLLvXzCHlc.Iu', '系统管理员三', 'dept_company', '系统管理员', 'ADMIN', 1),
('u_admin_04', 'admin04', '$2b$10$qW04e8jifpX5WfdIPCOe6.Qa5H3r76tK0EbpSaIIRLLvXzCHlc.Iu', '系统管理员四', 'dept_company', '系统管理员', 'ADMIN', 1),
('u_admin_05', 'admin05', '$2b$10$qW04e8jifpX5WfdIPCOe6.Qa5H3r76tK0EbpSaIIRLLvXzCHlc.Iu', '系统管理员五', 'dept_company', '系统管理员', 'ADMIN', 1),
('u_sales_01', 'sales01', '$2b$10$qW04e8jifpX5WfdIPCOe6.Qa5H3r76tK0EbpSaIIRLLvXzCHlc.Iu', '张三', 'dept_sales', '业务员', 'USER', 1),
('u_sales_02', 'sales02', '$2b$10$qW04e8jifpX5WfdIPCOe6.Qa5H3r76tK0EbpSaIIRLLvXzCHlc.Iu', '业务员二', 'dept_sales', '业务员', 'USER', 1),
('u_sales_03', 'sales03', '$2b$10$qW04e8jifpX5WfdIPCOe6.Qa5H3r76tK0EbpSaIIRLLvXzCHlc.Iu', '业务员三', 'dept_sales', '业务员', 'USER', 1),
('u_sales_04', 'sales04', '$2b$10$qW04e8jifpX5WfdIPCOe6.Qa5H3r76tK0EbpSaIIRLLvXzCHlc.Iu', '业务员四', 'dept_sales', '业务员', 'USER', 1),
('u_sales_05', 'sales05', '$2b$10$qW04e8jifpX5WfdIPCOe6.Qa5H3r76tK0EbpSaIIRLLvXzCHlc.Iu', '业务员五', 'dept_sales', '业务员', 'USER', 1),
('u_sales_06', 'sales06', '$2b$10$qW04e8jifpX5WfdIPCOe6.Qa5H3r76tK0EbpSaIIRLLvXzCHlc.Iu', '业务员六', 'dept_sales', '业务员', 'USER', 1),
('u_sales_07', 'sales07', '$2b$10$qW04e8jifpX5WfdIPCOe6.Qa5H3r76tK0EbpSaIIRLLvXzCHlc.Iu', '业务员七', 'dept_sales', '业务员', 'USER', 1),
('u_sales_08', 'sales08', '$2b$10$qW04e8jifpX5WfdIPCOe6.Qa5H3r76tK0EbpSaIIRLLvXzCHlc.Iu', '业务员八', 'dept_sales', '业务员', 'USER', 1),
('u_group_leader_01', 'group01', '$2b$10$qW04e8jifpX5WfdIPCOe6.Qa5H3r76tK0EbpSaIIRLLvXzCHlc.Iu', '业务组长', 'dept_sales', '组长', 'USER', 1),
('u_dept_manager_01', 'manager_sales', '$2b$10$qW04e8jifpX5WfdIPCOe6.Qa5H3r76tK0EbpSaIIRLLvXzCHlc.Iu', '李四', 'dept_sales', '部门经理', 'USER', 1),
('u_finance_01', 'finance01', '$2b$10$qW04e8jifpX5WfdIPCOe6.Qa5H3r76tK0EbpSaIIRLLvXzCHlc.Iu', '王五', 'dept_finance', '财务职工', 'USER', 1),
('u_finance_02', 'finance02', '$2b$10$qW04e8jifpX5WfdIPCOe6.Qa5H3r76tK0EbpSaIIRLLvXzCHlc.Iu', '财务职工二', 'dept_finance', '财务职工', 'USER', 1),
('u_finance_03', 'finance03', '$2b$10$qW04e8jifpX5WfdIPCOe6.Qa5H3r76tK0EbpSaIIRLLvXzCHlc.Iu', '财务职工三', 'dept_finance', '财务职工', 'USER', 1),
('u_finance_04', 'finance04', '$2b$10$qW04e8jifpX5WfdIPCOe6.Qa5H3r76tK0EbpSaIIRLLvXzCHlc.Iu', '财务职工四', 'dept_finance', '财务职工', 'USER', 1),
('u_finance_05', 'finance05', '$2b$10$qW04e8jifpX5WfdIPCOe6.Qa5H3r76tK0EbpSaIIRLLvXzCHlc.Iu', '财务职工五', 'dept_finance', '财务职工', 'USER', 1),
('u_finance_06', 'finance06', '$2b$10$qW04e8jifpX5WfdIPCOe6.Qa5H3r76tK0EbpSaIIRLLvXzCHlc.Iu', '财务职工六', 'dept_finance', '财务职工', 'USER', 1),
('u_dept_manager_02', 'manager_finance', '$2b$10$qW04e8jifpX5WfdIPCOe6.Qa5H3r76tK0EbpSaIIRLLvXzCHlc.Iu', '赵六', 'dept_finance', '财务经理', 'USER', 1),
('u_operations_01', 'operations01', '$2b$10$qW04e8jifpX5WfdIPCOe6.Qa5H3r76tK0EbpSaIIRLLvXzCHlc.Iu', '运营职工一', 'dept_operations', '运营职工', 'USER', 1),
('u_operations_02', 'operations02', '$2b$10$qW04e8jifpX5WfdIPCOe6.Qa5H3r76tK0EbpSaIIRLLvXzCHlc.Iu', '运营职工二', 'dept_operations', '运营职工', 'USER', 1),
('u_operations_03', 'operations03', '$2b$10$qW04e8jifpX5WfdIPCOe6.Qa5H3r76tK0EbpSaIIRLLvXzCHlc.Iu', '运营职工三', 'dept_operations', '运营职工', 'USER', 1),
('u_operations_04', 'operations04', '$2b$10$qW04e8jifpX5WfdIPCOe6.Qa5H3r76tK0EbpSaIIRLLvXzCHlc.Iu', '运营职工四', 'dept_operations', '运营职工', 'USER', 1),
('u_operations_05', 'operations05', '$2b$10$qW04e8jifpX5WfdIPCOe6.Qa5H3r76tK0EbpSaIIRLLvXzCHlc.Iu', '运营职工五', 'dept_operations', '运营职工', 'USER', 1),
('u_operations_06', 'operations06', '$2b$10$qW04e8jifpX5WfdIPCOe6.Qa5H3r76tK0EbpSaIIRLLvXzCHlc.Iu', '运营职工六', 'dept_operations', '运营职工', 'USER', 1),
('u_operations_manager_01', 'manager_operations', '$2b$10$qW04e8jifpX5WfdIPCOe6.Qa5H3r76tK0EbpSaIIRLLvXzCHlc.Iu', '运营经理', 'dept_operations', '运营经理', 'USER', 1),
('u_risk_01', 'risk01', '$2b$10$qW04e8jifpX5WfdIPCOe6.Qa5H3r76tK0EbpSaIIRLLvXzCHlc.Iu', '风控职工一', 'dept_risk', '风控职工', 'USER', 1),
('u_risk_02', 'risk02', '$2b$10$qW04e8jifpX5WfdIPCOe6.Qa5H3r76tK0EbpSaIIRLLvXzCHlc.Iu', '风控职工二', 'dept_risk', '风控职工', 'USER', 1),
('u_risk_03', 'risk03', '$2b$10$qW04e8jifpX5WfdIPCOe6.Qa5H3r76tK0EbpSaIIRLLvXzCHlc.Iu', '风控职工三', 'dept_risk', '风控职工', 'USER', 1),
('u_risk_04', 'risk04', '$2b$10$qW04e8jifpX5WfdIPCOe6.Qa5H3r76tK0EbpSaIIRLLvXzCHlc.Iu', '风控职工四', 'dept_risk', '风控职工', 'USER', 1),
('u_risk_05', 'risk05', '$2b$10$qW04e8jifpX5WfdIPCOe6.Qa5H3r76tK0EbpSaIIRLLvXzCHlc.Iu', '风控职工五', 'dept_risk', '风控职工', 'USER', 1),
('u_risk_06', 'risk06', '$2b$10$qW04e8jifpX5WfdIPCOe6.Qa5H3r76tK0EbpSaIIRLLvXzCHlc.Iu', '风控职工六', 'dept_risk', '风控职工', 'USER', 1),
('u_risk_manager_01', 'manager_risk', '$2b$10$qW04e8jifpX5WfdIPCOe6.Qa5H3r76tK0EbpSaIIRLLvXzCHlc.Iu', '风控经理', 'dept_risk', '风控经理', 'USER', 1)
ON CONFLICT(id) DO UPDATE SET
    username = excluded.username,
    password = excluded.password,
    real_name = excluded.real_name,
    dept_id = excluded.dept_id,
    position = excluded.position,
    user_type = excluded.user_type,
    status = excluded.status;

UPDATE mock_department SET manager_id = 'u_admin_01' WHERE id = 'dept_company';
UPDATE mock_department SET manager_id = 'u_dept_manager_01' WHERE id = 'dept_sales';
UPDATE mock_department SET manager_id = 'u_dept_manager_02' WHERE id = 'dept_finance';
UPDATE mock_department SET manager_id = 'u_operations_manager_01' WHERE id = 'dept_operations';
UPDATE mock_department SET manager_id = 'u_risk_manager_01' WHERE id = 'dept_risk';

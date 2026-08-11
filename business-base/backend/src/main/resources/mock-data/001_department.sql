CREATE TABLE IF NOT EXISTS mock_department (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    parent_id VARCHAR(64),
    manager_id VARCHAR(64),
    FOREIGN KEY (parent_id) REFERENCES mock_department(id)
);

CREATE INDEX IF NOT EXISTS idx_mock_department_parent ON mock_department(parent_id);
CREATE INDEX IF NOT EXISTS idx_mock_department_manager ON mock_department(manager_id);

INSERT INTO mock_department (id, name, parent_id, manager_id) VALUES
('dept_company', '总公司', NULL, NULL),
('dept_sales', '业务一部', 'dept_company', NULL),
('dept_finance', '财务部', 'dept_company', NULL),
('dept_operations', '运营部', 'dept_company', NULL),
('dept_risk', '风控部', 'dept_company', NULL)
ON CONFLICT(id) DO UPDATE SET
    name = excluded.name,
    parent_id = excluded.parent_id;

# SQLite Schema

# SQLite Schema

- 新建本地数据库：执行 `001_init_flow_platform.sql`。
- 由旧版 `001_init_flow_platform.sql` 创建、且尚未包含 M2 幂等动作的数据库：在部署 M2 运行时服务前，按顺序执行一次 `002_m2_runtime_operation_actions.sql`。该迁移仅重建并复制 `process_operation_record`，保留已有记录和索引。


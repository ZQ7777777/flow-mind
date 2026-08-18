# SQLite Schema

# SQLite Schema

- 新建本地数据库：执行 `001_init_flow_platform.sql`。
- 由旧版 `001_init_flow_platform.sql` 创建、且尚未包含 M2 幂等动作的数据库：在部署 M2 运行时服务前，按顺序执行一次 `002_m2_runtime_operation_actions.sql`。该迁移仅重建并复制 `process_operation_record`，保留已有记录和索引。
- Existing databases must apply `003_attachment_operation_actions.sql` and then
  `004_attachment_replace_operation_action.sql`; both migrations preserve existing
  operation and audit records while extending their action constraints.
- `007_notice_node_config.sql` 在单一事务中重建 `process_node`，保留原节点数据和索引，
  同时增加 `NOTICE` 节点约束与 `notice_config` 字段；启动器会在检测到字段缺失时自动执行。

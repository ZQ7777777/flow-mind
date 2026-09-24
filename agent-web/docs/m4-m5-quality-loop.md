# M4-M5 质量闭环与安全落仓

## 范围

本文描述 `agent-web` 在既有 M3 目标契约、staging、ArtifactManifest、revision、diff、
Generator Session 和 Monaco 编辑器之上的 M4-M5 实现。M6 的真实流程实例联合验收不在本次范围内。

## 状态流

```text
CODE_GENERATING
  -> CODE_VERIFYING
  -> CODE_REVIEWING
  -> CODE_REPAIRING -> CODE_VERIFYING   (最多三轮)
  -> CODE_REVIEW | CODE_PIPELINE_FAILED
  -> WRITING_ARTIFACTS
  -> COMPLETED | ARTIFACT_WRITE_FAILED
```

服务启动时，未完成的生成、验证、Reviewer、Repair 和写入会标记为 `AGENT_INTERRUPTED`。
验证类中断不会消耗修复轮次，可由 owner 显式 reverify。写入中断只执行恢复，不自动重试写入。

## 数据与历史

SQLite migration 5 扩展 generation 质量与写入字段，并创建：

- `agent_verification_run`：按 revision 和 repair round 保存全部验证历史。
- `agent_code_review`：保存独立 Reviewer Session、结论和问题。
- `agent_quality_override`：保存当前 revision 的软门禁覆盖及失效时间。
- `agent_artifact_write`：保存写入请求、Manifest、备份目录和逐文件 journal。

Migration 6 增加 `agent_generation_action`，用于 reverify 和 quality override 的幂等重放。
相同 generation、动作和幂等键只能对应同一请求哈希。

## 质量阶段

| 阶段 | 执行方式 | 门禁 |
| --- | --- | --- |
| `STATIC_VALIDATION` | Java Parser、TypeScript Compiler API、Vue SFC Parser | 硬 |
| `BACKEND_COMPILE` | `mvn -q -DskipTests compile` | 硬 |
| `BACKEND_TESTS` | `mvn -q test` | 软 |
| `FRONTEND_TYPECHECK` | `npm run typecheck` | 硬 |
| `FRONTEND_TESTS` | `npm run test -- --run` | 软 |
| `FRONTEND_BUILD` | `npm run build` | 硬 |
| Reviewer | 独立只读 Pi Session；`BLOCKING` finding 必须修复或人工 override | 软阻断 |

静态检查覆盖精确文件集合、唯一 `startAndSubmit()`、平台动作边界、平台 HTTP、持久化/SQL、
Java 9+/Jakarta、字段和附件映射、路由注册及测试削弱。命令输出会归一化为带文件、行列和错误码
的诊断，并记录到独立日志目录。

## 隔离 Worker

Verification Worker 创建不包含符号链接的一次性目标副本，再 overlay 当前 staging Manifest。
命令使用参数数组、固定 cwd、受限环境变量和 `shell=false`。同一 generation 只允许一个质量任务；
单命令默认超时 5 分钟，输出上限 10 MiB，取消或超时会终止进程树。执行完成后删除副本，不修改真实目标。

## Reviewer 与 Repair

Reviewer 使用 `SessionManager.create()` 创建独立 Session，只注册 staged file、diff、quality
读取工具和 `submit_code_review`，没有写工具。WARNING/INFO 仅报告；`BLOCKING` finding 会令
`canWrite=false`，仅允许当前 generation revision 上记录操作者、原因和 scope 的人工 override。

Repair 使用 Generator 的 `pi_session_file` 调用 `SessionManager.open()`。每轮只能修改当前
Manifest 已管理的文件，不能新增或删除文件；完成后 revision 增加一并重新执行全部质量阶段。
Reviewer 问题和规范化诊断都会传回 Repair prompt。自动修复预算固定为三轮，人工编辑和 reverify
不会重置预算，基础设施失败不会递增预算。

## M5 接口

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| GET | `/api/agent/sessions/:sessionId/code-generations/:generationId/quality` | 当前质量报告 |
| POST | `.../:generationId/reverify` | 当前 revision 全量复验 |
| POST | `.../:generationId/quality-override` | 覆盖实际失败的软门禁 |
| POST | `.../:generationId/confirm-write` | 确认完整 Manifest 并安全写入 |
| GET | `/api/agent/management/process-definitions` | owner 隔离的流程定义清单 |
| GET | `/api/agent/management/code-generations` | owner 隔离的代码生成清单 |

所有修改操作校验 owner 和 generation revision。reverify、quality override 与 confirm-write 要求
`If-Match` 和 `Idempotency-Key`；重放相同请求时会在 rowVersion 检查前返回首次结果。

## 失效与覆盖

人工编辑保留 Manifest 冻结时的 `changeType/baseSha256`，只更新 staged hash、大小和编辑标识。
编辑后所有文件重新标记为 `PENDING`，旧质量、Reviewer、覆盖和 `canWrite` 失效。硬门禁失败的
revision 仍可人工编辑后 reverify。

覆盖仅接受 `BACKEND_TESTS`、`FRONTEND_TESTS` 或 `REVIEWER` 中当前实际失败的范围，原因
不少于 10 个字符。文件变化会使覆盖立即失效。

## Safe Writer

1. 校验 owner、状态、revision、完整 Manifest 请求和质量门禁。
2. 重新校验目标契约、protected hash、目标基线和 staged hash。
3. 先创建备份并持久化写入记录及完整 journal。
4. 每个文件写入前持久化 `APPLYING`，再执行同目录临时文件与 rename。
5. 失败时按逆序恢复；进程重启后对 `PREPARED/WRITING` 记录执行相同恢复。
6. 完整恢复后保留 `canWrite`，但失败幂等键不可复用；恢复不完整则关闭写入门禁。

该流程不创建 Git 分支、提交或推送，也不会写入 Manifest 白名单以外的文件。

## 验收

```powershell
npm run typecheck
npm test
npm run build
npm run test:e2e
```

E2E 使用 Fake Pi、受控 Worker 和临时目标工程，不执行 M6 联合验收。

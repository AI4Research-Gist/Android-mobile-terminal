# 项目开发日志

## v0.0.1 - 2026-03-15

### 变更主题
完成项目后端服务器迁移后的工程同步，统一更新 NocoDB 服务地址、运行配置、文档说明，并清理迁移阶段遗留的临时文件。

### 迁移背景
- 项目原有 NocoDB 服务说明中仍存在旧服务器地址 `47.109.158.254`。
- 代码当前实际使用的 NocoDB 地址已经切换为 `http://8.152.222.163:8080/api/v1/db/data/v1/p8bhzq1ltutm8zr/`。
- 文档、网络白名单和本地上下文配置需要和迁移后的真实环境保持一致。

### 本次完成内容
- 统一确认迁移后的 NocoDB 服务地址为 `8.152.222.163:8080`。
- 更新项目说明文档，补充迁移后的接口基址与配置说明。
- 修正 Android 明文网络访问白名单，使应用允许访问迁移后的服务器 IP。
- 同步更新本地上下文配置中的旧服务器探活命令。
- 补充技术说明，明确旧地址仅作为历史信息保留，不再作为当前运行配置。
- 删除迁移阶段产生且当前未被项目引用的 `tmp_*` 临时脚本和密钥中转文件。

### 受影响文件
- `README.md`
- `Gist.md`
- `app/src/main/res/xml/network_security_config.xml`
- `app/src/main/java/com/example/ai4research/data/remote/api/NocoApiService.kt`
- `.claude/settings.local.json`

### 清理文件
- `tmp_download_docker_rpms.py`
- `tmp_download_docker_rpms_local.py`
- `tmp_new_server_validate.sh`
- `tmp_old_server_final_backup.sh`
- `tmp_old_server_rehearsal_backup.sh`
- `tmp_old_to_new_pubkey.txt`
- `tmp_start_test_tunnel.sh`

### 当前状态
- App 侧 NocoDB 基址说明已与代码实际配置一致。
- 网络安全配置已从旧 IP 切换到新 IP。
- 项目主文档与技术说明文档已完成同步。
- 旧服务器地址目前仅在技术文档中保留为历史迁移说明，不再作为运行配置使用。

### 风险与后续建议
- 当前 NocoDB 仍通过明文 HTTP 访问，建议后续补齐 HTTPS/SSL。
- `NOCO_TOKEN` 与 `SiliconFlow API_KEY` 仍为硬编码，建议迁移到更安全的配置注入方案。
- 如果后续服务器再次变更，建议把服务地址抽离为环境配置，减少文档和代码重复维护成本。

### 版本备注
这是围绕“服务器迁移落地与文档收口”整理的首版开发日志，可作为后续版本日志的起点继续追加。

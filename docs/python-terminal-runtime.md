# Python 命令行实验环境支持说明

本次改动实现了 Python 命令行实验所需的后端基础能力，核心内容如下：

1. **依赖与基础设施**
   - 在 `pom.xml` 中引入 `docker-java` SDK 以及 `spring-boot-starter-websocket`，用于执行容器 exec 命令并处理 WebSocket 通讯。
   - 新增 `DockerClientConfiguration`，统一提供 `DockerClient` Bean。

2. **运行策略**
   - 新增 `PythonRuntimeStrategy`，强制使用 `tail -f /dev/null` 保活容器，默认端口 8000，工作目录 `/app/program`，并开启 `tty` 与 `stdin_open`。
   - 在 `RuntimeStrategy` / `TemplateManagerService` / `docker-compose.mustache` 中加入 `tty`、`stdin_open`、`working_dir` 的可选配置。

3. **终端通道**
   - 新增 `TerminalWebSocketHandler`：建立 `/ws/terminal` WebSocket 通道，解析 `containerId`，通过 Docker Exec 与容器内 `/bin/bash` 建立交互式会话，实现 stdin/stdout/stderr 的双向转发。
   - 新增 `WebSocketConfig`，注册终端 handler 并开放跨域。

4. **前端交互数据**
   - `EnvController` 原有返回体 `EnvInfo` 已包含 `containerId` 字段，可直接供前端在建立 WebSocket 时使用。

前端接入流程：

1. 调用 `/api/env/start`，从 `data.containerId` 取得容器 ID。
2. 创建 `ws://{server}/ws/terminal?containerId={containerId}` 的 WebSocket 连接。
3. 将 xterm.js 的输入写入 WebSocket，读取服务端下发的数据更新终端。

如需进一步的安全控制（鉴权、资源隔离等），可以基于现有 handler 增加用户身份校验逻辑。***


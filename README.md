# 虚拟实验环境框架

## 项目简介

虚拟实验环境框架是一个用于学习平台的Docker容器管理框架，支持为每个学生动态创建独立的实验环境。学生进入实验时，系统自动分配独立的Docker虚拟环境，部署实验程序并运行，学生之间完全隔离。

## 核心特性

- ✅ **动态环境创建** - 为每个学生+每个实验自动创建独立环境
- ✅ **自动端口分配** - 智能分配端口（18000-19999），并发安全，避免冲突
- ✅ **程序包自动部署** - 自动复制实验程序包到容器环境
- ✅ **Docker Compose集成** - 使用docker-compose管理容器生命周期
- ✅ **环境隔离** - 每个学生环境完全独立，互不影响
- ✅ **环境管理** - 支持启动、停止、重置、销毁操作（停止/启动保留容器，重置重建）
- ✅ **配置化部署** - 通过metadata.json配置，无需修改代码
- ✅ **数据库支持** - 支持共享MySQL、独立数据库等多种模式
- ✅ **统一异常处理** - 完善的异常体系，包含错误码，便于前端处理
- ✅ **资源自动清理** - 创建失败时自动清理容器、端口等资源
- ✅ **WebSocket终端** - 支持交互式终端访问（Python CLI等场景）

## 技术栈

- Spring Boot 2.6.13
- MyBatis Plus 3.5.2
- MySQL 8.0
- Docker & Docker Compose
- Mustache 模板引擎
- Java 8+

## 快速开始

### 1. 环境要求

- JDK 1.8+
- Maven 3.6+
- MySQL 8.0+
- Docker & Docker Compose

### 2. 数据库初始化

执行 `src/main/resources/db/schema.sql` 创建数据库和表。

### 3. 配置文件

修改 `src/main/resources/application.yml`：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/virtual_env?...
    username: your_username
    password: your_password

env:
  server:
    host: your_server_host  # 用于生成访问URL
  apps:
    base-path: /opt/apps    # 实验程序包存储路径
  user-envs:
    base-path: /opt/user_envs  # 用户环境目录路径
```

### 4. 准备实验程序包

在 `/opt/apps/{expId}/` 目录下放置实验程序包和元数据：

```
/opt/apps/exp-java-001/
├── app.jar
└── metadata.json
```

### 5. 启动应用

```bash
mvn spring-boot:run
```

或打包后运行：

```bash
mvn clean package
java -jar target/DockerEnvs-0.0.1-SNAPSHOT.jar
```

## API 接口

> 所有接口统一返回结构：
>
> **成功响应**：
> ```json
> {
>   "success": true,
>   "message": "操作成功",
>   "data": { ... }
> }
> ```
>
> **错误响应**：
> ```json
> {
>   "success": false,
>   "errorCode": "CONTAINER_START_FAILED",
>   "message": "容器启动失败: ..."
> }
> ```
>
> **常见错误码**：
> - `ENV_NOT_FOUND` - 环境不存在
> - `CONTAINER_START_FAILED` - 容器启动失败
> - `CONTAINER_STOP_FAILED` - 容器停止失败
> - `CONTAINER_NOT_FOUND` - 容器不存在
> - `NO_AVAILABLE_PORT` - 没有可用端口
> - `PORT_RELEASE_FAILED` - 端口释放失败
> - `DATABASE_INIT_FAILED` - 数据库初始化失败

### 1. 创建/启动环境

- **Endpoint**：`POST /api/env/start`
- **Request Body**

```json
{
  "userId": "student001",
  "systemId": "system001",
  "expId": "exp-python-001"
}
```

- **Response**

```json
{
  "success": true,
  "message": "环境启动成功",
  "data": {
    "envId": "env-681ff05c8510",
    "userId": "student001",
    "systemId": "system001",
    "expId": "exp-python-001",
    "port": 18000,
    "containerId": "91a67d164ba2...",
    "containerName": "env-env-681ff05c8510",
    "envDir": "D:/Code/.../user_envs/student001/system001/exp-python-001",
    "status": "RUNNING",
    "url": "http://localhost:18000",
    "createdTime": "2025-11-19 16:22:06",
    "updatedTime": "2025-11-19 16:22:06"
  }
}
```

### 2. 启动已停止的环境

- **Endpoint**：`POST /api/env/start-existing?envId={envId}`
- **说明**：启动已停止的容器（保留容器，不重建）
- **Response**

```json
{
  "success": true,
  "message": "环境启动成功"
}
```

### 3. 停止环境

- **Endpoint**：`POST /api/env/stop?envId={envId}`
- **说明**：停止容器但保留容器（不删除），可快速恢复
- **Response**

```json
{
  "success": true,
  "message": "环境停止成功"
}
```

### 4. 重置环境（重新部署）

- **Endpoint**：`POST /api/env/reset?envId={envId}`
- **说明**：删除并重建容器，确保配置变更生效
- **Response**

```json
{
  "success": true,
  "message": "环境重置成功"
}
```

### 5. 销毁环境（删除容器与目录）

- **Endpoint**：`DELETE /api/env/{envId}`
- **说明**：完全删除容器、数据卷和环境目录
- **Response**

```json
{
  "success": true,
  "message": "环境销毁成功"
}
```

### 6. 查询环境状态

- **Endpoint**：`GET /api/env/{envId}/status`
- **Response**

```json
{
  "success": true,
  "data": {
    "envId": "env-681ff05c8510",
    "status": "RUNNING",
    "containerId": "91a67d164ba2...",
    "port": 18000,
    "url": "http://localhost:18000",
    "updatedTime": "2025-11-19 16:22:06"
  }
}
```

### 7. 查询用户所有环境

- **Endpoint**：`GET /api/env/user/{userId}`
- **Response**

```json
{
  "success": true,
  "data": [
    {
      "envId": "env-681ff05c8510",
      "expId": "exp-python-001",
      "status": "RUNNING",
      "port": 18000,
      "url": "http://localhost:18000"
    },
    {
      "envId": "env-a1b2c3d4e5f6",
      "expId": "exp-java-001",
      "status": "STOPPED",
      "port": 18005,
      "url": "http://localhost:18005"
    }
  ]
}
```

### 8. 查询全部环境（管理用途）

- **Endpoint**：`GET /api/env/all`
- **Response**：同"查询用户所有环境"，但返回系统内全部环境列表。

## 添加新实验

添加新实验非常简单，只需：

1. 将程序包放到 `/opt/apps/{expId}/` 目录
2. 创建 `metadata.json` 文件
3. 无需修改框架代码

### metadata.json 示例（新格式）

```json
{
  "expId": "exp-java-001",
  "name": "Spring Boot应用",
  "runtimeType": "java",
  "baseImage": "eclipse-temurin:8-jre",
  "startCommand": "java -jar /app/program/app.jar",
  "containerPort": 8080,
  "database": {
    "enabled": true,
    "provider": "shared",
    "type": "mysql",
    "name": "test_db"
  }
}
```

详细配置说明请参考 [docs/add-new-experiment.md](docs/add-new-experiment.md)

## 项目结构

### 目录结构

```
src/main/java/org/dockerenvs/
├── DockerEnvsApplication.java  # Spring Boot 主启动类
├── config/                     # 配置层
│   └── MyBatisPlusConfig.java  # MyBatis Plus 配置
├── controller/                 # REST API控制器
│   ├── EnvController.java            # 环境管理 API
│   ├── SharedMysqlController.java     # 共享 MySQL 管理 API
│   ├── PageController.java            # 页面路由控制器
│   └── GlobalExceptionHandler.java    # 全局异常处理器（统一异常处理）
├── exception/                  # 异常类
│   ├── EnvException.java             # 环境异常基类
│   ├── ContainerException.java       # 容器操作异常
│   ├── PortException.java             # 端口管理异常
│   ├── DatabaseException.java         # 数据库操作异常
│   └── EnvNotFoundException.java      # 环境不存在异常
├── service/                    # 业务服务层
│   ├── EnvManagerService.java         # 环境管理（核心）
│   ├── PortManagerService.java        # 端口管理
│   ├── DockerOpsService.java          # Docker操作
│   ├── FileManagerService.java        # 文件管理
│   ├── TemplateManagerService.java    # 模板管理
│   └── SharedMysqlService.java         # 共享MySQL管理
├── provider/                   # 提供者模式（核心扩展点）
│   ├── ProviderManager.java           # 提供者管理器
│   ├── RuntimeStrategy.java           # 运行时策略接口
│   ├── AbstractRuntimeStrategy.java    # 运行时策略抽象基类
│   ├── JavaRuntimeStrategy.java        # Java 运行时策略
│   ├── NodeRuntimeStrategy.java        # Node.js 运行时策略
│   ├── PythonRuntimeStrategy.java      # Python CLI 运行时策略
│   ├── NginxRuntimeStrategy.java      # Nginx 运行时策略
│   ├── DatabaseProvider.java           # 数据库提供者接口
│   ├── MySqlSharedProvider.java       # MySQL 共享提供者
│   └── MySqlStandaloneProvider.java   # MySQL 独立提供者
├── dao/                        # 数据访问层
│   └── mapper/
│       ├── VirtualEnvMapper.java       # 虚拟环境数据库操作
│       └── PortUsageMapper.java        # 端口使用记录数据库操作
├── entity/                     # 实体类（数据库表映射）
│   ├── VirtualEnv.java                 # 虚拟环境实体
│   └── PortUsage.java                  # 端口使用记录实体
└── dto/                        # 数据传输对象
    ├── ExperimentMetadata.java         # 实验元数据
    ├── DatabaseConfig.java             # 数据库配置
    ├── VolumeConfig.java               # 数据卷配置
    ├── HealthCheckConfig.java          # 健康检查配置
    ├── ServiceConfig.java              # 附加服务配置
    ├── EnvInfo.java                    # 环境信息
    ├── StartEnvRequest.java            # 启动环境请求参数
    └── ApiResponse.java                # 统一API响应格式
```

### Provider 包详解（核心扩展点）

Provider 采用策略模式（Strategy Pattern）**设计，用于根据不同的运行时类型和数据库类型提供不同的配置和行为。这是系统的核心扩展点，使得添加新的运行时类型或数据库类型变得非常简单。

#### RuntimeStrategy（运行时策略）

根据不同的运行时类型（java/node/nginx/python）提供不同的默认配置：

- **JavaRuntimeStrategy** - Java 运行时
  
  - 默认端口: 8080
  - 挂载路径: `/app/program`（可写）
  - 启动命令: `java -jar /app/program/app.jar`
  - 健康检查: `curl http://localhost:8080/health`

- **NodeRuntimeStrategy** - Node.js 运行时
  
  - 默认端口: 3000
  - 挂载路径: `/app/program`（可写）
  - 启动命令: `cd /app/program && npm install --production && node server.js`

- **PythonRuntimeStrategy** - Python CLI 环境
  
  - 默认端口: 8000
  - 挂载路径: `/app/program`（可写）
  - 启动命令: `tail -f /dev/null`（保持容器运行）
  - 健康检查: 无（CLI 环境）
  - TTY/Stdin: 启用（支持交互式终端）

- **NginxRuntimeStrategy** - Nginx/静态文件服务
  
  - 默认端口: 80
  - 挂载路径: `/usr/share/nginx/html`（只读）
  - 启动命令: `nginx -g 'daemon off;'`
  - 健康检查: `wget http://localhost/`

#### DatabaseProvider（数据库提供者）

根据不同的数据库类型和提供方式（shared/standalone/custom）提供不同的数据库配置：

- **MySqlSharedProvider** - MySQL 共享提供者
  - 连接到共享 MySQL 网络
  - 生成环境变量（DB_HOST, DB_URL, DB_NAME 等）
  - 确保数据库容器存在且数据库已创建
  - 启动策略：等待应用健康检查，验证容器存在

- **MySqlStandaloneProvider** - MySQL 独立提供者
  - 每个环境拥有独立的 MySQL 容器
  - 数据完全隔离
  - 自动执行初始化 SQL 脚本
  - 启动策略：不等待应用健康检查（应用自动重试），跳过容器验证

**插件化设计**：
- 每个提供者完全封装自己的特殊逻辑
- 核心代码不感知具体提供者实现
- 通过 `shouldWaitForAppHealthCheck()` 和 `shouldVerifyContainerExists()` 控制启动策略

#### ProviderManager（提供者管理器）

统一管理和查找所有 Provider，启动时自动注册所有实现类。

**扩展新类型**：只需实现对应接口并添加 `@Component` 注解，系统会自动注册并使用，无需修改其他代码。

详细说明请参考 [项目结构说明](docs/项目结构说明.md)

## 工作流程

### 简要流程

```
学生进入实验
    ↓
前端调用 POST /api/env/start
    ↓
EnvManagerService.createEnv()
    ├── 检查环境是否已存在
    ├── PortManagerService.assignPort() - 分配端口（并发安全，数据库乐观锁）
    ├── FileManagerService.generateEnvDir() - 创建目录
    ├── DatabaseProvider.ensureDatabaseReady() - 初始化数据库（如需要）
    ├── TemplateManagerService.generateComposeFile() - 生成docker-compose.yml
    ├── DockerOpsService.startContainer() - 启动容器
    └── 保存环境信息到数据库
    ↓
返回环境URL (http://server:PORT)
    ↓
学生访问环境中的实验程序
    ↓
（如果创建失败，自动清理资源：容器、端口等）
```

### 详细流程

详细的技术文档请参考：[系统工作流程详解](docs/system-workflow.md)

该文档包含：
- 完整的流程图和步骤说明
- 数据库提供者工作流程
- 模板生成流程
- 端口分配并发安全机制
- 异常处理与资源清理
- 关键决策点说明
- 性能优化点
- 故障排查指南

## 目录说明

- `/opt/apps/` - 实验程序包存储目录
- `/opt/user_envs/` - 用户环境目录（自动创建）
  - `{userId}/{systemId}/{expId}/`
    - `logs/` - 日志目录
    - `docker-compose.yml` - 生成的compose文件（V2格式，使用Mustache模板）
    - 程序包通过共享挂载方式访问（不再复制）

## 注意事项

1. **目录权限** - 确保 `/opt/apps` 和 `/opt/user_envs` 目录有写权限
2. **Docker权限** - 确保应用有权限执行docker命令
3. **端口范围** - 默认端口范围18000-19999，可在配置文件中修改
4. **服务器地址** - 配置正确的服务器地址用于生成访问URL
5. **容器操作** - 停止/启动操作保留容器（快速恢复），重置操作会删除并重建容器（应用配置变更）
6. **错误处理** - 所有错误都包含错误码，前端可根据错误码进行相应处理
7. **资源清理** - 环境创建失败时会自动清理已分配的资源（容器、端口等）

## 生产部署指引

若将项目部署到正式服务器，建议按以下步骤准备：

1. **安装基础环境**
   - 安装 JDK 8+、Maven、MySQL 8.0、Docker / Docker Compose（Linux 推荐直接安装 Docker Engine）
   - 初始化 `virtual_env` 数据库（执行 `src/main/resources/db/schema.sql`）
2. **同步实验资源**
   - 将 `apps/` 目录中的实验包复制到服务器指定目录（例如 `/opt/docker-envs/apps`）
   - 确保目录具备读写权限
3. **修改 `application.yml`**
   - `spring.datasource.*`：改成服务器数据库的地址、账号、密码
   - `env.server.host`：改成服务器对外访问域名/IP
   - `env.apps.base-path`、`env.user-envs.base-path`：设置成服务器上的实际路径（如 `/opt/docker-envs/apps`、`/opt/docker-envs/user_envs`）
   - 如需调整端口段或其他参数，也可在此处修改
4. **网络与安全**
   - 开放 `server.port`（默认 8080）和 Docker 容器动态端口段（默认 18000-19999）
   - 如需通过反向代理（Nginx）访问，注意转发 WebSocket (`/ws/terminal`) 并保持 `Upgrade/Connection` 头
5. **启动服务**
   - `mvn spring-boot:run` 或 `mvn clean package && java -jar target/DockerEnvs-0.0.1-SNAPSHOT.jar`
   - 通过 `/api/env/start` 创建实验环境验证是否成功

部署完成后即可通过主页面或 API 管理实验容器；Python CLI 实验默认进入 `/app/program`，可直接在终端执行 `python main.py` 或其它脚本。

## 系统改进

### 最新改进（v2.0）

- ✅ **统一异常处理体系** - 自定义异常类，包含错误码，便于前端处理
- ✅ **统一API响应格式** - 所有接口使用 `ApiResponse<T>` 统一格式
- ✅ **完善资源清理** - 创建失败时自动清理容器、端口等资源
- ✅ **端口分配并发安全** - 使用数据库乐观锁，避免并发冲突
- ✅ **优化容器操作** - 停止/启动保留容器（快速恢复），重置重建容器（应用配置变更）
- ✅ **移除V1代码** - 完全使用V2格式（Mustache模板），代码更简洁
- ✅ **插件化架构** - 数据库提供者完全封装特殊逻辑，核心代码无需感知具体实现
- ✅ **独立数据库支持** - 每个环境可拥有独立的MySQL容器，数据完全隔离

### 架构优势

- **高扩展性** - 添加新的运行时类型或数据库类型只需实现接口，无需修改核心代码
- **低耦合** - 核心服务不依赖具体的Provider实现
- **易维护** - 特殊逻辑封装在对应的Provider中，代码结构清晰

### 改进详情

详细改进说明请参考 [docs/improvements.md](docs/improvements.md)

## 相关文档

- [添加新实验指南](docs/add-new-experiment.md) - 如何添加新的实验
- [系统工作流程详解](docs/system-workflow.md) - 详细的技术文档，说明系统的工作流程（面向技术人员）
- [系统改进建议](docs/improvements.md) - 系统改进和优化建议

## 许可证

MIT License

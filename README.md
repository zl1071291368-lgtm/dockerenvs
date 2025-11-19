# 虚拟实验环境框架

## 项目简介

虚拟实验环境框架是一个用于学习平台的Docker容器管理框架，支持为每个学生动态创建独立的实验环境。学生进入实验时，系统自动分配独立的Docker虚拟环境，部署实验程序并运行，学生之间完全隔离。

## 核心特性

- ✅ **动态环境创建** - 为每个学生+每个实验自动创建独立环境
- ✅ **自动端口分配** - 智能分配端口（18000-19999），避免冲突
- ✅ **程序包自动部署** - 自动复制实验程序包到容器环境
- ✅ **Docker Compose集成** - 使用docker-compose管理容器生命周期
- ✅ **环境隔离** - 每个学生环境完全独立，互不影响
- ✅ **环境管理** - 支持启动、停止、重置、销毁操作
- ✅ **配置化部署** - 通过metadata.json配置，无需修改代码
- ✅ **数据库支持** - 支持共享MySQL、独立数据库等多种模式

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
> ```json
> {
>   "success": true,
>   "message": "说明文本",
>   "data": { ... },
>   "detail": "可选，更多错误信息"
> }
> ```

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

### 2. 停止环境

- **Endpoint**：`POST /api/env/stop?envId={envId}`
- **Response**

```json
{
  "success": true,
  "message": "环境停止成功"
}
```

### 3. 重置环境（重新部署）

- **Endpoint**：`POST /api/env/reset?envId={envId}`
- **Response**

```json
{
  "success": true,
  "message": "环境重置成功"
}
```

### 4. 销毁环境（删除容器与目录）

- **Endpoint**：`DELETE /api/env/{envId}`
- **Response**

```json
{
  "success": true,
  "message": "环境销毁成功"
}
```

### 5. 查询环境状态

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

### 6. 查询用户所有环境

- **Endpoint**：`GET /api/env/user/{userId}`
- **Response**

```json
{
  "success": true,
  "total": 2,
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

### 7. 查询全部环境（管理用途）

- **Endpoint**：`GET /api/env/all`
- **Response**：同“查询用户所有环境”，但返回系统内全部环境列表。

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

### metadata.json 示例（旧格式，向后兼容）

```json
{
  "expId": "exp-java-001",
  "name": "Spring Boot实验",
  "type": "java",
  "baseImage": "java-base:latest",
  "startCommand": "java -jar /app/program/app.jar",
  "port": 8080,
  "needsDatabase": true,
  "databaseName": "test_db"
}
```

详细配置说明请参考 [docs/配置化部署指南.md](docs/配置化部署指南.md)

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
│   └── GlobalExceptionHandler.java    # 全局异常处理
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
│   ├── NginxRuntimeStrategy.java      # Nginx 运行时策略
│   ├── DatabaseProvider.java           # 数据库提供者接口
│   └── MySqlSharedProvider.java       # MySQL 共享提供者
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
    └── StartEnvRequest.java            # 启动环境请求参数
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

#### ProviderManager（提供者管理器）

统一管理和查找所有 Provider，启动时自动注册所有实现类。

**扩展新类型**：只需实现对应接口并添加 `@Component` 注解，系统会自动注册并使用，无需修改其他代码。

详细说明请参考 [项目结构说明](docs/项目结构说明.md)

## 工作流程

```
学生进入实验
    ↓
前端调用 POST /api/env/start
    ↓
EnvManagerService.createEnv()
    ├── 检查环境是否已存在
    ├── PortManagerService.assignPort() - 分配端口
    ├── FileManagerService.generateEnvDir() - 创建目录
    ├── FileManagerService.copyProgramPackage() - 复制程序包（已优化）
    ├── DatabaseProvider.ensureDatabaseReady() - 初始化数据库（如需要）
    ├── TemplateManagerService.generateComposeFileV2() - 生成docker-compose.yml
    ├── DockerOpsService.startContainer() - 启动容器
    └── 保存环境信息到数据库
    ↓
返回环境URL (http://server:PORT)
    ↓
学生访问环境中的实验程序
```

## 目录说明

- `/opt/apps/` - 实验程序包存储目录
- `/opt/user_envs/` - 用户环境目录（自动创建）
  - `{userId}/{systemId}/{expId}/`
    - `program/` - 复制的程序包（已优化）
    - `logs/` - 日志目录
    - `docker-compose.yml` - 生成的compose文件
    - `.env` - 环境变量文件

## 注意事项

1. **目录权限** - 确保 `/opt/apps` 和 `/opt/user_envs` 目录有写权限
2. **Docker权限** - 确保应用有权限执行docker命令
3. **端口范围** - 默认端口范围18000-19999，可在配置文件中修改
4. **服务器地址** - 配置正确的服务器地址用于生成访问URL

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

## 

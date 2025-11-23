# 系统工作流程详解

本文档详细说明 DockerEnvs 虚拟环境管理系统的核心工作流程，面向技术人员，包含详细的步骤说明、数据流转和关键决策点。

## 目录

1. [系统架构概览](#系统架构概览)
2. [环境创建流程](#环境创建流程)
3. [环境停止流程](#环境停止流程)
4. [环境启动流程](#环境启动流程)
5. [环境重置流程](#环境重置流程)
6. [环境销毁流程](#环境销毁流程)
7. [数据库提供者工作流程](#数据库提供者工作流程)
8. [模板生成流程](#模板生成流程)
9. [端口分配流程](#端口分配流程)
10. [异常处理与资源清理](#异常处理与资源清理)

---

## 系统架构概览

### 核心组件

```
┌─────────────────────────────────────────────────────────────┐
│                      Controller Layer                        │
│  EnvController, SharedMysqlController, GlobalExceptionHandler │
└───────────────────────┬─────────────────────────────────────┘
                        │
┌───────────────────────▼─────────────────────────────────────┐
│                     Service Layer                             │
│  ┌──────────────────┐  ┌──────────────────┐                │
│  │ EnvManagerService│  │TemplateManagerSvc│                │
│  └────────┬─────────┘  └────────┬─────────┘                │
│           │                     │                            │
│  ┌────────▼─────────┐  ┌────────▼─────────┐                │
│  │PortManagerService│  │DockerOpsService  │                │
│  └──────────────────┘  └──────────────────┘                │
│                                                               │
│  ┌──────────────────┐  ┌──────────────────┐                │
│  │FileManagerService│  │SharedMysqlService │                │
│  └──────────────────┘  └──────────────────┘                │
└───────────────────────┬─────────────────────────────────────┘
                        │
┌───────────────────────▼─────────────────────────────────────┐
│                    Provider Layer                             │
│  ┌──────────────────┐  ┌──────────────────┐                │
│  │RuntimeStrategy   │  │DatabaseProvider  │                │
│  │  - Java          │  │  - Shared        │                │
│  │  - Node          │  │  - Standalone    │                │
│  │  - Python        │  └──────────────────┘                │
│  │  - Nginx         │                                       │
│  └──────────────────┘                                       │
└───────────────────────┬─────────────────────────────────────┘
                        │
┌───────────────────────▼─────────────────────────────────────┐
│                      Data Layer                               │
│  MyBatis Plus + MySQL (virtual_env database)                  │
└─────────────────────────────────────────────────────────────┘
```

### 数据流转

```
API Request
    ↓
Controller (参数验证、异常捕获)
    ↓
Service (业务逻辑、事务管理)
    ↓
Provider (策略选择、配置生成)
    ↓
Docker/Database (实际操作)
    ↓
Response (统一格式返回)
```

---

## 环境创建流程

### 流程图

```
POST /api/env/start
    ↓
EnvController.startEnv()
    ↓
EnvManagerService.createEnv()
    │
    ├─→ [1] 检查环境是否已存在
    │   └─→ 如果存在且运行中 → 直接返回
    │   └─→ 如果存在但已停止 → 调用 startEnv() 启动它
    │       └─→ 如果启动失败（容器不存在）→ 释放端口 → 销毁旧环境 → 继续创建新环境
    │
    ├─→ [2] 生成环境ID (UUID)
    │
    ├─→ [3] 分配端口 (PortManagerService)
    │   ├─→ 查询已使用端口
    │   ├─→ 检查端口可用性 (Socket测试)
    │   ├─→ 数据库乐观锁分配
    │   └─→ 返回可用端口
    │
    ├─→ [4] 创建环境目录 (FileManagerService)
    │   └─→ {userEnvsBasePath}/{userId}/{systemId}/{expId}/
    │
    ├─→ [5] 读取实验元数据 (metadata.json)
    │   ├─→ 解析 JSON
    │   ├─→ 获取运行时类型 (runtimeType)
    │   └─→ 获取数据库配置 (database)
    │
    ├─→ [6] 获取程序包路径
    │   └─→ {appsBasePath}/{expId}/
    │
    ├─→ [7] 处理数据库配置 (DatabaseProvider)
    │   ├─→ 获取数据库提供者 (ProviderManager)
    │   ├─→ ensureDatabaseReady()
    │   │   ├─→ Shared: 检查共享容器 + 创建数据库
    │   │   └─→ Standalone: 记录日志（容器由docker-compose创建）
    │   └─→ 决定健康检查策略 (shouldWaitForAppHealthCheck)
    │
    ├─→ [8] 生成 docker-compose.yml (TemplateManagerService)
    │   ├─→ 获取运行时策略 (RuntimeStrategy)
    │   ├─→ 构建模板上下文 (Map<String, Object>)
    │   ├─→ 调用数据库提供者获取服务配置
    │   ├─→ Mustache 模板渲染
    │   └─→ 写入 docker-compose.yml
    │
    ├─→ [9] 启动容器 (DockerOpsService)
    │   ├─→ docker compose up -d
    │   ├─→ 获取容器ID (docker compose ps -q)
    │   ├─→ 等待健康检查 (可选，由策略决定)
    │   └─→ 返回容器ID
    │
    ├─→ [10] 验证容器存在 (可选，由数据库提供者决定)
    │
    ├─→ [11] 保存环境信息到数据库
    │   └─→ VirtualEnv 实体插入
    │
    └─→ [12] 返回环境信息 (EnvInfo)
```

### 详细步骤说明

#### 步骤 1: 环境存在性检查

```java
VirtualEnv existingEnv = findExistingEnv(userId, systemId, expId);
```

**查询条件**：
- `userId` = 请求的 userId
- `systemId` = 请求的 systemId  
- `expId` = 请求的 expId

**处理逻辑**：
- 如果环境存在且状态为 `RUNNING` → 直接返回现有环境信息
- 如果环境存在但状态为 `STOPPED` → 调用 `startEnv()` 启动已停止的环境
  - 如果启动成功 → 更新状态为 `RUNNING` 并返回环境信息
  - 如果启动失败（例如容器已被删除）→ 释放端口、销毁旧环境，然后创建新环境
- 如果环境不存在 → 直接创建新环境

#### 步骤 2: 环境ID生成

```java
String envId = generateEnvId(); // 格式: "env-" + UUID.randomUUID().toString().substring(0, 12)
```

**生成规则**：
- 前缀：`env-`
- 后缀：UUID 的前 12 位（去除连字符）
- 示例：`env-681ff05c8510`

#### 步骤 3: 端口分配（并发安全）

```java
Integer port = portManagerService.assignPort(envId);
```

**分配流程**：

1. **查询已使用端口**
   ```sql
   SELECT port FROM port_usage WHERE status = 'USED'
   ```

2. **遍历端口范围** (18000-19999)
   - 检查端口是否在已使用列表中
   - 检查端口是否被系统占用（`ServerSocket` 测试）

3. **数据库乐观锁分配**
   ```java
   // 如果端口记录存在且状态为 FREE
   UPDATE port_usage 
   SET status = 'USED', env_id = ?, allocated_time = NOW()
   WHERE port = ? AND status = 'FREE'
   ```
   - 使用 `WHERE status = 'FREE'` 作为乐观锁条件
   - 如果更新行数为 0，说明被其他线程抢占，尝试下一个端口
   - 如果端口记录不存在，直接插入新记录

4. **返回分配的端口**

**并发安全保证**：
- 数据库级别的乐观锁
- 端口范围足够大（2000个端口）
- 失败重试机制

#### 步骤 4: 环境目录创建

```java
String envDir = fileManagerService.generateEnvDir(userId, systemId, expId);
```

**目录结构**：
```
{userEnvsBasePath}/
  └── {userId}/
      └── {systemId}/
          └── {expId}/
              ├── logs/              # 日志目录
              └── docker-compose.yml # 生成的 compose 文件
```

**创建操作**：
- 使用 `Files.createDirectories()` 递归创建
- 同时创建 `logs/` 子目录

#### 步骤 5: 读取实验元数据

```java
ExperimentMetadata metadata = readExperimentMetadata(expId);
```

**文件路径**：
- `{appsBasePath}/{expId}/metadata.json`

**解析内容**：
- `runtimeType`: 运行时类型（java/node/python/nginx）
- `baseImage`: Docker 基础镜像
- `containerPort`: 容器内端口
- `startCommand`: 启动命令
- `database`: 数据库配置对象
- `volumes`: 数据卷配置
- `healthCheck`: 健康检查配置

#### 步骤 6: 获取程序包路径

```java
String programPath = fileManagerService.getAppSourcePath(expId);
```

**路径格式**：
- `{appsBasePath}/{expId}/`

**用途**：
- 作为 Docker 数据卷的挂载源
- 程序包通过共享挂载方式访问，不复制到环境目录

#### 步骤 7: 数据库配置处理

```java
DatabaseProvider dbProvider = providerManager.getDatabaseProvider(dbConfig);
dbProvider.ensureDatabaseReady(dbConfig);
```

**提供者选择**：
- `provider = "shared"` → `MySqlSharedProvider`
- `provider = "standalone"` → `MySqlStandaloneProvider`

**Shared 模式流程**：
1. 检查共享 MySQL 容器是否存在
2. 如果不存在，尝试自动创建（如果配置允许）
3. 确保数据库（schema）存在
4. 返回环境变量配置

**Standalone 模式流程**：
1. 记录日志（容器由 docker-compose 创建）
2. 返回环境变量配置

**健康检查策略**：
```java
boolean waitForHealth = (runtimeType != "python") && dbProvider.shouldWaitForAppHealthCheck();
```
- Python CLI 环境：不等待健康检查
- Standalone 数据库模式：不等待健康检查（应用自动重试连接）

#### 步骤 8: 生成 docker-compose.yml

```java
templateManagerService.generateComposeFile(envDir, programPath, metadata, envId, userId, port);
```

**模板文件**：
- `src/main/resources/templates/docker-compose.mustache`

**上下文构建**：

1. **基础信息**
   ```java
   context.put("baseImage", metadata.getBaseImage());
   context.put("containerName", "env-" + envId);
   context.put("hostPort", port);
   context.put("containerPort", metadata.getContainerPort());
   ```

2. **运行时策略配置**
   ```java
   RuntimeStrategy strategy = providerManager.getRuntimeStrategy(runtimeType);
   // 获取默认端口、挂载路径、启动命令等
   ```

3. **数据卷配置**
   ```java
   volumes.add({
     hostPath: programPath,
     containerPath: strategy.getDefaultMountPath(),
     options: "" // 可写
   });
   ```

4. **环境变量**
   ```java
   // 基础环境变量
   env.put("APP_PORT", port);
   env.put("CONTAINER_PORT", containerPort);
   
   // 数据库环境变量（由 DatabaseProvider 提供）
   if (dbProvider != null) {
     env.putAll(dbProvider.getEnvironmentVariables(dbConfig));
   }
   ```

5. **数据库服务配置**（Standalone 模式）
   ```java
   Map<String, Object> dbContext = new HashMap<>();
   dbContext.put("containerName", "env-" + envId);
   dbContext.put("networkName", "env-" + envId + "-net");
   dbContext.put("envDir", envDir);
   dbContext.put("initSqlPath", findInitSqlPath(expId, envDir));
   
   String serviceConfig = dbProvider.getServiceConfig(dbConfig, dbContext);
   // 添加到 additionalServices
   ```

6. **网络配置**
   ```java
   context.put("networkName", "env-" + envId + "-net");
   // 如果是 Shared 数据库，添加外部网络
   if (dbProvider != null) {
     String dbNetwork = dbProvider.getNetworkConfig();
     // 添加到 databaseNetwork
   }
   ```

7. **健康检查配置**
   ```java
   HealthCheckConfig healthCheck = metadata.getHealthCheck();
   if (healthCheck == null) {
     healthCheck = strategy.getDefaultHealthCheck(containerPort);
   }
   // 添加到 context
   ```

**模板渲染**：
```java
Mustache mustache = mustacheFactory.compile("templates/docker-compose.mustache");
StringWriter writer = new StringWriter();
mustache.execute(writer, context);
String yamlContent = writer.toString();
```

**文件写入**：
```java
Files.write(Paths.get(envDir, "docker-compose.yml"), yamlContent.getBytes(StandardCharsets.UTF_8));
```

#### 步骤 9: 启动容器

```java
String containerId = dockerOpsService.startContainer(envDir, waitForHealth);
```

**执行命令**：
```bash
docker compose -f {envDir}/docker-compose.yml -p {projectName} up -d
```

**项目名称提取**：
1. 优先从 `docker-compose.yml` 解析 `container_name`
2. 如果失败，从 `.env` 文件读取 `ENV_ID`
3. 最后使用路径 hash 值

**获取容器ID**：
```bash
docker compose -f {envDir}/docker-compose.yml -p {projectName} ps -q
```
- 返回第一个容器的 ID（通常是应用容器）

**健康检查等待**（可选）：
```java
if (waitForHealth) {
    waitForContainerHealthy(containerId, 30); // 最多等待30秒
}
```

**健康检查逻辑**：
```java
while (timeout not exceeded) {
    String status = docker inspect --format "{{.State.Health.Status}}" {containerId};
    if (status == "healthy") {
        return; // 健康检查通过
    }
    Thread.sleep(2000); // 等待2秒后重试
}
```

#### 步骤 10: 验证容器存在（可选）

```java
boolean shouldVerify = dbProvider == null || dbProvider.shouldVerifyContainerExists();
if (shouldVerify) {
    if (!dockerOpsService.containerExists(containerId)) {
        throw new ContainerException(...);
    }
}
```

**验证逻辑**：
```bash
docker ps --filter id={containerId} --format "{{.ID}}"
```
- 如果返回容器ID，说明容器存在
- Standalone 数据库模式通常跳过此验证（docker compose 已保证）

#### 步骤 11: 保存环境信息

```java
VirtualEnv virtualEnv = new VirtualEnv();
virtualEnv.setEnvId(envId);
virtualEnv.setUserId(userId);
virtualEnv.setSystemId(systemId);
virtualEnv.setExpId(expId);
virtualEnv.setPort(port);
virtualEnv.setContainerId(containerId);
virtualEnv.setEnvDir(envDir);
virtualEnv.setStatus("RUNNING");
virtualEnv.setUrl("http://" + serverHost + ":" + port);
virtualEnv.setCreatedTime(LocalDateTime.now());
virtualEnv.setUpdatedTime(LocalDateTime.now());

virtualEnvMapper.insert(virtualEnv);
```

**数据库表结构**：
- `virtual_env` 表
- 主键：`env_id`
- 索引：`(user_id, system_id, exp_id)` 用于快速查询

#### 步骤 12: 返回环境信息

```java
return convertToEnvInfo(virtualEnv);
```

**返回数据**：
```json
{
  "envId": "env-681ff05c8510",
  "userId": "student001",
  "systemId": "system001",
  "expId": "exp-java-001",
  "port": 18000,
  "containerId": "91a67d164ba2...",
  "containerName": "env-env-681ff05c8510",
  "status": "RUNNING",
  "url": "http://localhost:18000",
  "createdTime": "2025-11-19 16:22:06",
  "updatedTime": "2025-11-19 16:22:06"
}
```

---

## 环境停止流程

### 流程图

```
POST /api/env/stop?envId={envId}
    ↓
EnvController.stopEnv()
    ↓
EnvManagerService.stopEnv()
    │
    ├─→ [1] 查询环境记录
    │   └─→ 如果不存在 → 抛出 EnvNotFoundException
    │   └─→ 如果已停止 → 直接返回
    │
    ├─→ [2] 停止容器 (DockerOpsService)
    │   └─→ docker compose stop
    │   └─→ 保留容器和数据卷
    │
    └─→ [3] 更新数据库状态
        └─→ status = "STOPPED"
        └─→ updatedTime = NOW()
```

### 详细说明

**停止命令**：
```bash
docker compose -f {envDir}/docker-compose.yml -p {projectName} stop
```

**特点**：
- 只停止容器，不删除
- 保留数据卷
- 可以快速恢复（使用 `start-existing`）

---

## 环境启动流程

### 流程图

```
POST /api/env/start-existing?envId={envId}
    ↓
EnvController.startEnv()
    ↓
EnvManagerService.startEnv()
    │
    ├─→ [1] 查询环境记录
    │   └─→ 如果不存在 → 抛出 EnvNotFoundException
    │   └─→ 如果已运行 → 直接返回
    │
    ├─→ [2] 启动容器 (DockerOpsService)
    │   └─→ docker compose start
    │   └─→ 启动已存在的容器
    │
    ├─→ [3] 验证容器存在
    │
    └─→ [4] 更新数据库状态
        └─→ status = "RUNNING"
        └─→ updatedTime = NOW()
```

### 详细说明

**启动命令**：
```bash
docker compose -f {envDir}/docker-compose.yml -p {projectName} start
```

**特点**：
- 只启动已存在的容器
- 不创建新容器
- 如果容器不存在，会抛出异常（提示使用重置功能）

---

## 环境重置流程

### 流程图

```
POST /api/env/reset?envId={envId}
    ↓
EnvController.resetEnv()
    ↓
EnvManagerService.resetEnv()
    │
    ├─→ [1] 查询环境记录
    │
    ├─→ [2] 停止并删除容器 (DockerOpsService)
    │   └─→ docker compose down -v
    │   └─→ 删除容器和数据卷
    │
    ├─→ [3] 重新启动容器
    │   └─→ docker compose up -d
    │   └─→ 创建新容器
    │
    ├─→ [4] 更新容器ID
    │
    └─→ [5] 更新数据库状态
        └─→ status = "RUNNING"
        └─→ containerId = 新容器ID
        └─→ updatedTime = NOW()
```

### 详细说明

**重置命令**：
```bash
# 停止并删除
docker compose -f {envDir}/docker-compose.yml -p {projectName} down -v

# 重新创建
docker compose -f {envDir}/docker-compose.yml -p {projectName} up -d
```

**特点**：
- 完全重建容器
- 删除数据卷（`-v` 参数）
- 应用配置变更
- 适用于需要重新初始化数据的场景

---

## 环境销毁流程

### 流程图

```
DELETE /api/env/{envId}
    ↓
EnvController.destroyEnv()
    ↓
EnvManagerService.destroyEnv()
    │
    ├─→ [1] 查询环境记录
    │
    ├─→ [2] 停止并删除容器
    │   └─→ docker compose down -v
    │
    ├─→ [3] 释放端口 (PortManagerService)
    │   └─→ UPDATE port_usage SET status = 'FREE' WHERE port = ?
    │
    ├─→ [4] 删除环境目录 (FileManagerService)
    │   └─→ 递归删除 {envDir}
    │
    ├─→ [5] 强制删除容器（如果残留）
    │   └─→ docker rm -f {containerId}
    │
    └─→ [6] 更新数据库状态
        └─→ status = "DESTROYED"
        └─→ updatedTime = NOW()
```

### 详细说明

**销毁操作**：
- 完全删除容器和数据卷
- 释放端口资源
- 删除环境目录
- 更新数据库状态为 `DESTROYED`

**注意**：
- 销毁后无法恢复
- 数据卷中的数据会丢失

---

## 数据库提供者工作流程

### Shared 模式（MySqlSharedProvider）

```
ensureDatabaseReady()
    │
    ├─→ [1] 检查共享 MySQL 容器
    │   └─→ docker ps --filter name=shared-mysql
    │   └─→ 如果不存在 → 尝试自动创建
    │
    ├─→ [2] 确保数据库存在
    │   └─→ 连接到共享容器
    │   └─→ CREATE DATABASE IF NOT EXISTS {dbName}
    │
    └─→ [3] 返回环境变量
        └─→ DB_HOST = shared-mysql
        └─→ DB_URL = jdbc:mysql://shared-mysql:3306/{dbName}
```

**网络配置**：
- 应用容器连接到外部网络 `shared-mysql-net`
- 通过容器名 `shared-mysql` 访问数据库

### Standalone 模式（MySqlStandaloneProvider）

```
ensureDatabaseReady()
    │
    └─→ [1] 记录日志
        └─→ "使用独立MySQL容器，将在docker-compose中创建数据库服务"

getServiceConfig()
    │
    ├─→ [1] 构建服务配置
    │   ├─→ 镜像: mysql:8.0
    │   ├─→ 容器名: {containerName}-db
    │   ├─→ 数据卷: {envDir}/mysql-data:/var/lib/mysql
    │   ├─→ 初始化脚本: {initSqlPath}:/docker-entrypoint-initdb.d/init.sql
    │   └─→ 健康检查: mysqladmin ping
    │
    └─→ [2] 返回 YAML 字符串
```

**特点**：
- 每个环境拥有独立的 MySQL 容器
- 数据完全隔离
- 初始化脚本自动执行

**启动策略**：
- `shouldWaitForAppHealthCheck() = false` - 不等待应用健康检查
- `shouldVerifyContainerExists() = false` - 跳过容器存在验证

---

## 模板生成流程

### Mustache 模板渲染

```
TemplateManagerService.generateComposeFile()
    │
    ├─→ [1] 构建模板上下文 (Map<String, Object>)
    │   ├─→ 基础信息 (baseImage, containerName, ports)
    │   ├─→ 运行时策略配置 (volumes, environment, workingDir)
    │   ├─→ 数据库配置 (additionalServices, databaseNetwork)
    │   └─→ 健康检查配置
    │
    ├─→ [2] 加载 Mustache 模板
    │   └─→ templates/docker-compose.mustache
    │
    ├─→ [3] 渲染模板
    │   └─→ mustache.execute(writer, context)
    │
    └─→ [4] 写入文件
        └─→ {envDir}/docker-compose.yml
```

### 模板变量说明

| 变量名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| `baseImage` | String | Docker 基础镜像 | `eclipse-temurin:8-jre` |
| `containerName` | String | 容器名称 | `env-681ff05c8510` |
| `hostPort` | Integer | 主机端口 | `18000` |
| `containerPort` | Integer | 容器端口 | `8080` |
| `ports` | String | 端口映射 | `"18000:8080"` |
| `volumes` | List | 数据卷列表 | `[{hostPath, containerPath, options}]` |
| `environment` | List | 环境变量列表 | `[{key, value}]` |
| `workingDir` | String | 工作目录 | `/app/program` |
| `ttyEnabled` | Boolean | 启用 TTY | `true` |
| `stdinOpen` | Boolean | 启用标准输入 | `true` |
| `dependsOn` | Boolean | 是否有依赖 | `false` |
| `dependsOnList` | List | 依赖服务列表 | `["db"]` |
| `healthCheck` | Boolean | 是否有健康检查 | `true` |
| `healthCheckTest` | String | 健康检查命令 | `["CMD", "curl", "-f", "http://localhost:8080/health"]` |
| `additionalServices` | List | 附加服务（如独立数据库） | `["  db:\n    image: mysql:8.0\n..."]` |
| `networkName` | String | 网络名称 | `env-681ff05c8510-net` |
| `databaseNetwork` | String | 数据库网络名称 | `shared-mysql-net` |

---

## 端口分配流程

### 并发安全机制

```
PortManagerService.assignPort(envId)
    │
    ├─→ [1] 查询已使用端口
    │   └─→ SELECT port FROM port_usage WHERE status = 'USED'
    │
    ├─→ [2] 遍历端口范围 (18000-19999)
    │   │
    │   ├─→ [2.1] 检查是否已使用
    │   │   └─→ if (usedPorts.contains(port)) continue;
    │   │
    │   ├─→ [2.2] 检查系统占用
    │   │   └─→ try (ServerSocket socket = new ServerSocket(port)) { ... }
    │   │
    │   └─→ [2.3] 数据库乐观锁分配
    │       │
    │       ├─→ 如果端口记录存在
    │       │   └─→ UPDATE port_usage 
    │       │       SET status = 'USED', env_id = ?, allocated_time = NOW()
    │       │       WHERE port = ? AND status = 'FREE'
    │       │   └─→ 如果 updatedRows == 0 → 被抢占，继续下一个
    │       │   └─→ 如果 updatedRows > 0 → 分配成功
    │       │
    │       └─→ 如果端口记录不存在
    │           └─→ INSERT INTO port_usage (port, env_id, status, allocated_time)
    │           └─→ 如果插入失败（唯一键冲突）→ 被抢占，继续下一个
    │           └─→ 如果插入成功 → 分配成功
    │
    └─→ [3] 返回分配的端口
```

**并发安全保证**：
1. **数据库乐观锁**：使用 `WHERE status = 'FREE'` 作为条件
2. **唯一键约束**：`port` 字段有唯一索引
3. **事务隔离**：`@Transactional` 保证原子性
4. **失败重试**：如果被抢占，自动尝试下一个端口

---

## 异常处理与资源清理

### 异常体系

```
EnvException (基类)
    ├─→ ContainerException (容器操作异常)
    │   ├─→ ERROR_CODE_START_FAILED
    │   ├─→ ERROR_CODE_STOP_FAILED
    │   ├─→ ERROR_CODE_NOT_FOUND
    │   └─→ ERROR_CODE_DOCKER_UNAVAILABLE
    │
    ├─→ PortException (端口管理异常)
    │   ├─→ ERROR_CODE_NO_AVAILABLE_PORT
    │   ├─→ ERROR_CODE_PORT_NOT_FOUND
    │   └─→ ERROR_CODE_RELEASE_FAILED
    │
    ├─→ DatabaseException (数据库操作异常)
    │   └─→ ERROR_CODE_INIT_FAILED
    │
    └─→ EnvNotFoundException (环境不存在异常)
```

### 资源清理流程

```
createEnv() 失败时
    ↓
cleanupResources(envDir, port, containerId)
    │
    ├─→ [1] 停止容器（如果已启动）
    │   └─→ docker compose down
    │
    ├─→ [2] 释放端口（如果已分配）
    │   └─→ UPDATE port_usage SET status = 'FREE' WHERE port = ?
    │
    └─→ [3] 强制删除容器（如果残留）
        └─→ docker rm -f {containerId}
```

**清理时机**：
- 端口分配失败 → 无需清理
- 目录创建失败 → 无需清理
- 数据库初始化失败 → 清理端口
- 模板生成失败 → 清理端口
- 容器启动失败 → 清理端口、容器
- 容器验证失败 → 清理端口、容器

### 全局异常处理

```java
@ControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(ContainerException.class)
    public ResponseEntity<ApiResponse<?>> handleContainerException(ContainerException e) {
        return ResponseEntity.status(500)
            .body(ApiResponse.error(e.getErrorCode(), e.getMessage()));
    }
    // ... 其他异常处理
}
```

**统一响应格式**：
```json
{
  "success": false,
  "errorCode": "CONTAINER_START_FAILED",
  "message": "容器启动失败: ..."
}
```

---

## 关键决策点

### 1. 环境已存在时的处理

- **运行中** → 直接返回
- **已停止** → 销毁旧环境，创建新环境

### 2. 健康检查等待策略

- **Python CLI** → 不等待
- **Standalone 数据库** → 不等待（应用自动重试）
- **其他情况** → 等待最多 30 秒

### 3. 容器验证策略

- **Standalone 数据库** → 跳过验证（docker compose 已保证）
- **其他情况** → 验证容器存在

### 4. 端口分配策略

- **乐观锁** → 数据库级别并发控制
- **失败重试** → 自动尝试下一个端口
- **系统检查** → Socket 测试确保端口可用

### 5. 数据库提供者选择

- **Shared** → 连接到共享 MySQL 容器
- **Standalone** → 每个环境独立 MySQL 容器

---

## 性能优化点

1. **端口分配并发安全**：数据库乐观锁，避免锁竞争
2. **健康检查跳过**：独立数据库模式不等待，加快启动速度
3. **容器验证跳过**：减少 `docker inspect` 调用
4. **共享程序包**：使用数据卷挂载，不复制文件
5. **停止/启动保留容器**：快速恢复，无需重建

---

## 故障排查

### 常见问题

1. **端口分配失败**
   - 检查端口范围是否足够
   - 检查是否有端口泄漏（状态为 USED 但实际未使用）

2. **容器启动失败**
   - 检查 Docker 是否运行
   - 检查镜像是否存在
   - 查看容器日志：`docker logs {containerId}`

3. **数据库连接失败**
   - Shared 模式：检查共享容器是否运行
   - Standalone 模式：检查数据库容器是否启动完成

4. **健康检查超时**
   - 检查应用是否正常启动
   - 检查健康检查命令是否正确
   - 增加超时时间

---

## 扩展点

### 添加新的运行时类型

1. 实现 `RuntimeStrategy` 接口
2. 添加 `@Component` 注解
3. 系统自动注册

### 添加新的数据库提供者

1. 实现 `DatabaseProvider` 接口
2. 实现所有方法（包括策略方法）
3. 添加 `@Component` 注解
4. 系统自动注册

**无需修改核心代码！**

---

## 总结

DockerEnvs 系统采用**策略模式**和**提供者模式**，实现了高度的可扩展性。核心服务代码不依赖具体的运行时类型或数据库类型，所有特殊逻辑都封装在对应的 Provider 中。这使得添加新功能变得非常简单，只需实现接口并添加注解即可。

系统的并发安全性通过数据库乐观锁保证，资源清理机制确保失败时不会泄漏资源。统一的异常处理和响应格式使得前端可以方便地处理各种错误情况。


# 添加新实验流程说明

## 概述

本文档说明如何在 DockerEnvs 系统中添加新的实验。系统使用配置化的方式，通过 `metadata.json` 文件定义实验，无需修改代码。

## 前置条件

1. 实验程序包已准备好（JAR、WAR、Node.js 项目、Python 脚本等）
2. 了解实验的运行类型（Java、Node.js、Python、Nginx 等）
3. 确定实验是否需要数据库

## 步骤 1: 创建实验目录

在 `apps` 目录下创建实验目录，目录名即为实验ID（`expId`）：

```bash
apps/
└── exp-xxx-001/          # 实验目录，expId = "exp-xxx-001"
    ├── metadata.json      # 实验元数据（必需）
    ├── program/           # 程序文件目录（可选，根据实验类型）
    └── ...                # 其他文件
```

## 步骤 2: 准备实验程序包

根据实验类型，将程序文件放到合适的位置：

### Java 实验
```
apps/exp-java-xxx/
├── metadata.json
├── app.jar              # 或 app.war
├── application-container.yml  # 可选：容器内配置文件
└── lib/                 # 可选：依赖库
    └── *.jar
```

### Node.js 实验
```
apps/exp-node-xxx/
├── metadata.json
├── package.json
├── server.js            # 入口文件
└── ...                  # 其他 Node.js 文件
```

### Python 实验
```
apps/exp-python-xxx/
├── metadata.json
└── program/             # Python 脚本目录
    ├── main.py
    └── requirements.txt  # 可选：依赖列表
```

### Nginx/静态文件实验
```
apps/exp-nginx-xxx/
├── metadata.json
└── index.html           # 静态文件
```

## 步骤 3: 编写 metadata.json

创建 `metadata.json` 文件，使用 **V2 格式**（必须包含 `runtimeType` 字段）。

### 基础字段（必需）

```json
{
  "expId": "exp-xxx-001",           // 实验ID，与目录名一致
  "name": "实验名称",                 // 显示名称
  "runtimeType": "java",            // 运行时类型：java/node/python/nginx
  "baseImage": "eclipse-temurin:8-jre",  // Docker 基础镜像
  "containerPort": 8080,            // 容器内端口
  "startCommand": "java -jar /app/program/app.jar"  // 启动命令（可选）
}
```

### 数据库配置（可选）

如果实验需要数据库：

```json
{
  "database": {
    "enabled": true,
    "provider": "shared",           // shared: 共享MySQL / standalone: 独立MySQL
    "type": "mysql",
    "name": "test_db",              // 数据库名称
    "username": "root",
    "password": "123456"
  }
}
```

如果不需要数据库：

```json
{
  "database": {
    "enabled": false
  }
}
```

### 环境变量（可选）

```json
{
  "env": {
    "KEY1": "value1",
    "KEY2": "value2"
  }
}
```

**注意事项**：
- 环境变量值必须是字符串类型
- 如果 JSON 中某个环境变量值是对象（如 Map），系统会自动将其转换为 JSON 字符串
- 例如：`{"CONFIG": {"host": "localhost"}}` 会被转换为 `{"CONFIG": "{\"host\":\"localhost\"}"}`

### 完整示例

#### Java 实验（带数据库）

```json
{
  "expId": "exp-java-001",
  "name": "Spring Boot数据库实验",
  "runtimeType": "java",
  "baseImage": "eclipse-temurin:8-jre",
  "containerPort": 8080,
  "startCommand": "java -jar /app/program/app.jar --server.port=${CONTAINER_PORT}",
  "database": {
    "enabled": true,
    "provider": "shared",
    "type": "mysql",
    "name": "test_db",
    "username": "root",
    "password": "123456"
  }
}
```

#### Node.js 实验（无数据库）

```json
{
  "expId": "exp-node-001",
  "name": "Node.js Web应用实验",
  "runtimeType": "node",
  "baseImage": "node:16-alpine",
  "containerPort": 3000,
  "startCommand": "cd /app/program && npm install --production && node server.js",
  "database": {
    "enabled": false
  }
}
```

#### Python CLI 实验

```json
{
  "expId": "exp-python-001",
  "name": "Python 命令行交互实验",
  "runtimeType": "python",
  "baseImage": "python:3.11-slim",
  "containerPort": 8000,
  "env": {
    "TERM": "xterm-256color",
    "LANG": "C.UTF-8"
  },
  "database": {
    "enabled": false
  }
}
```

#### Nginx 静态文件实验

```json
{
  "expId": "test-nginx-001",
  "name": "Nginx 静态页面测试",
  "runtimeType": "nginx",
  "baseImage": "nginx:alpine",
  "containerPort": 80,
  "startCommand": "nginx -g 'daemon off;'",
  "database": {
    "enabled": false
  }
}
```

## 步骤 4: 验证配置

### 检查清单

- [ ] `metadata.json` 格式正确（JSON 语法）
- [ ] `expId` 与目录名一致
- [ ] `runtimeType` 字段存在（不是 `type`）
- [ ] `containerPort` 字段存在（不是 `port`）
- [ ] `database` 对象存在（即使 `enabled: false`）
- [ ] 程序文件已放置在正确位置
- [ ] 启动命令中的路径正确（如 `/app/program/app.jar`）

### 常见错误

1. **使用旧格式字段**：
   - ❌ `"type": "java"` → ✅ `"runtimeType": "java"`
   - ❌ `"port": 8080` → ✅ `"containerPort": 8080`
   - ❌ `"needsDatabase": true` → ✅ `"database": {"enabled": true, ...}`

2. **缺少必需字段**：
   - 必须包含 `runtimeType` 和 `containerPort`
   - 必须包含 `database` 对象（即使不需要数据库）

3. **路径错误**：
   - 程序文件路径应该是容器内路径：`/app/program/xxx`
   - 不是主机路径

## 步骤 5: 测试实验

1. 启动后端服务
2. 通过前端或 API 创建环境：
   ```json
   POST /api/env/start
   {
     "userId": "test",
     "systemId": "system001",
     "expId": "exp-xxx-001"
   }
   ```
3. 检查容器是否正常启动
4. 访问实验应用（Web 类型）或连接终端（CLI 类型）

**终端访问**：
- 每个环境（容器）都支持通过 WebSocket 终端访问
- 系统会自动尝试使用 `/bin/bash`，如果不存在则降级到 `/bin/sh` 或 `sh`
- 通过前端界面点击"打开终端"按钮，或直接访问 `/terminal.html?containerId={containerId}`
- 终端连接地址：`ws://{server}/ws/terminal?containerId={containerId}`

## 运行时类型说明

系统支持以下运行时类型，每种类型有默认配置：

| runtimeType | 默认端口 | 挂载路径 | 说明 |
|------------|---------|---------|------|
| `java` | 8080 | `/app/program` | Java 应用 |
| `node` | 3000 | `/app/program` | Node.js 应用 |
| `python` | 8000 | `/app/program` | Python CLI 环境 |
| `nginx` | 80 | `/usr/share/nginx/html` | Nginx 静态文件服务 |

## 高级配置

### 自定义数据卷

```json
{
  "volumes": [
    {
      "hostPath": "${ENV_DIR}/program",
      "containerPath": "/app/program",
      "options": ""
    },
    {
      "hostPath": "${ENV_DIR}/data",
      "containerPath": "/app/data",
      "options": ":ro"
    }
  ]
}
```

### 自定义健康检查

```json
{
  "healthCheck": {
    "test": "[\"CMD\", \"curl\", \"-f\", \"http://localhost:8080/health\"]",
    "interval": "30s",
    "timeout": "10s",
    "retries": 3,
    "startPeriod": "40s"
  }
}
```

### 多端口映射

```json
{
  "hostPorts": [
    "18000:8080",
    "18001:8081"
  ]
}
```

## 注意事项

1. **实验ID唯一性**：确保 `expId` 在系统中唯一
2. **镜像可用性**：确保 `baseImage` 在 Docker Hub 可用或已本地构建
3. **端口冲突**：系统会自动分配主机端口（18000-19999），无需手动指定
4. **数据库连接**：使用共享 MySQL 时，容器内通过容器名访问（如 `shared-mysql:3306`）
5. **环境变量**：
   - 启动命令中可使用 `${CONTAINER_PORT}`、`${APP_PORT}` 等变量
   - 环境变量值必须是字符串，对象值会自动转换为 JSON 字符串
6. **终端访问**：每个容器都支持通过 WebSocket 终端访问，系统会自动选择合适的 shell（bash/sh）

## 参考

- 示例实验：`apps/exp-java-001/`、`apps/exp-node-001/`、`apps/exp-python-001/`
- 运行时策略：`src/main/java/org/dockerenvs/provider/`
- 模板文件：`src/main/resources/templates/docker-compose.mustache`


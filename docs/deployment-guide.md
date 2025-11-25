# DockerEnvs 服务器部署指南

本文档详细说明如何将 DockerEnvs 虚拟实验环境框架部署到 Linux 生产服务器上，包括环境准备、配置修改、数据库初始化等完整流程。

## 📋 快速开始

### 部署流程概览

```
1. 准备服务器环境 (30-60分钟)
   ├── 安装 JDK、Maven、MySQL、Docker
   └── 配置防火墙和权限

2. 部署项目文件 (10-20分钟)
   ├── 上传项目到服务器
   ├── 创建必要目录
   └── 上传实验程序包

3. 修改配置文件 (5-10分钟) ⚠️ 重要
   ├── 修改服务器IP地址
   ├── 修改Windows路径为Linux路径
   └── 修改数据库密码

4. 初始化数据库 (5分钟)
   ├── 创建系统数据库
   └── 执行初始化脚本

5. 初始化共享MySQL容器 (5-10分钟)
   └── 创建并启动共享数据库容器

6. 启动应用服务 (5分钟)
   └── 使用systemd服务启动

7. 验证部署 (5分钟)
   └── 测试创建实验环境
```

**预计总时间**: 60-120分钟（取决于网络速度和服务器性能）

### ⚠️ 部署前必读

**重要提示**：
- 本文档针对 **Linux 生产环境**部署
- 所有路径均为 Linux 路径格式（使用 `/` 分隔符）
- 所有命令均为 Linux 命令
- 请将文档中所有 `YOUR_SERVER_IP` 替换为实际服务器IP地址

**必须修改的配置**：

项目中的 `src/main/resources/application.yml` 包含 **Windows 本地路径**，部署前**必须修改**：

| 配置项 | 当前值（Windows） | 需要改为（Linux） |
|--------|------------------|------------------|
| `env.apps.base-path` | `D:/Code/Java/DockerEnvs/DockerEnvs/apps` | `/opt/docker-envs/apps` |
| `env.user-envs.base-path` | `D:/Code/Java/DockerEnvs/DockerEnvs/user_envs` | `/opt/docker-envs/user_envs` |
| `env.server.host` | `localhost` | `YOUR_SERVER_IP` |

**如果不修改这些配置，应用将无法正常运行！**

### 配置信息准备

在开始部署前，请准备以下信息：

| 占位符 | 说明 | 示例值 |
|--------|------|--------|
| `YOUR_SERVER_IP` | 服务器公网IP或内网IP | `192.168.1.100` 或 `10.0.0.50` |
| `YOUR_SERVER_DOMAIN` | 服务器域名（如果有） | `docker-envs.example.com` |
| `YOUR_MYSQL_PASSWORD` | 系统数据库密码 | `YourSecurePassword123!` |
| `YOUR_SHARED_MYSQL_PASSWORD` | 共享MySQL容器root密码 | `SharedMySQLPass456!` |
| `YOUR_DB_USER` | 系统数据库用户名 | `dockerenvs` |

## 📑 目录

### 基础部署
1. [服务器环境要求](#服务器环境要求)
2. [基础环境安装](#基础环境安装)
3. [项目部署](#项目部署)
4. [配置文件修改](#配置文件修改) ⚠️ **重要**
5. [数据库初始化](#数据库初始化)
6. [Docker共享数据库初始化](#docker共享数据库初始化)
7. [启动服务](#启动服务)
8. [验证部署](#验证部署)

### 运维与优化
9. [常见问题排查](#常见问题排查)
10. [生产环境优化建议](#生产环境优化建议)
11. [生产环境配置示例](#生产环境配置示例)

### 快速参考
- [命令速查表](#命令速查表)
- [配置文件完整示例](#配置文件完整示例)

---

## 服务器环境要求

### 硬件要求

- **CPU**: 至少 2 核（推荐 4 核及以上）
- **内存**: 至少 4GB（推荐 8GB 及以上）
- **磁盘**: 至少 50GB 可用空间（推荐 100GB+，用于存储实验程序包和用户环境数据）
- **网络**: 稳定的网络连接，能够访问 Docker Hub 下载镜像

### 操作系统要求

- **推荐**: Linux（Ubuntu 20.04+ / CentOS 7+ / Debian 10+）
- **Windows Server**: 支持，但需要 Docker Desktop for Windows
- **macOS**: 支持开发环境，生产环境不推荐

### 软件依赖

- **JDK**: 1.8 或更高版本
- **Maven**: 3.6 或更高版本
- **MySQL**: 8.0 或更高版本（用于系统数据库）
- **Docker**: 20.10 或更高版本
- **Docker Compose**: 1.29 或更高版本（通常随 Docker 安装）

---

## 基础环境安装

### 1. 安装 JDK 8+

#### Ubuntu/Debian

```bash
# 更新包列表
sudo apt update

# 安装 OpenJDK 8
sudo apt install -y openjdk-8-jdk

# 验证安装
java -version
```

#### CentOS/RHEL

```bash
# 安装 OpenJDK 8
sudo yum install -y java-1.8.0-openjdk-devel

# 验证安装
java -version
```

### 2. 安装 Maven

#### Ubuntu/Debian

```bash
# 安装 Maven
sudo apt install -y maven

# 验证安装
mvn -version
```

#### CentOS/RHEL

```bash
# 安装 Maven
sudo yum install -y maven

# 验证安装
mvn -version
```

### 3. 安装 MySQL 8.0

#### Ubuntu/Debian

```bash
# 安装 MySQL Server
sudo apt install -y mysql-server

# 启动 MySQL 服务
sudo systemctl start mysql
sudo systemctl enable mysql

# 安全配置（设置 root 密码等）
sudo mysql_secure_installation
```

#### CentOS/RHEL

```bash
# 安装 MySQL Server
sudo yum install -y mysql-server

# 启动 MySQL 服务
sudo systemctl start mysqld
sudo systemctl enable mysqld

# 获取临时 root 密码
sudo grep 'temporary password' /var/log/mysqld.log

# 使用临时密码登录并修改密码
mysql -uroot -p
```

### 4. 安装 Docker

#### Ubuntu/Debian

```bash
# 卸载旧版本（如果有）
sudo apt remove docker docker-engine docker.io containerd runc

# 安装依赖
sudo apt update
sudo apt install -y ca-certificates curl gnupg lsb-release

# 添加 Docker 官方 GPG 密钥
sudo mkdir -p /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg

# 设置仓库
echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
  $(lsb_release -cs) stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

# 安装 Docker Engine
sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin

# 启动 Docker 服务
sudo systemctl start docker
sudo systemctl enable docker

# 将当前用户添加到 docker 组（避免每次使用 sudo）
sudo usermod -aG docker $USER

# 重新登录或执行以下命令使组权限生效
newgrp docker

# 验证安装
docker --version
docker compose version
```

#### CentOS/RHEL

```bash
# 卸载旧版本（如果有）
sudo yum remove docker docker-client docker-client-latest docker-common docker-latest docker-latest-logrotate docker-logrotate docker-engine

# 安装依赖
sudo yum install -y yum-utils

# 添加 Docker 仓库
sudo yum-config-manager --add-repo https://download.docker.com/linux/centos/docker-ce.repo

# 安装 Docker Engine
sudo yum install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin

# 启动 Docker 服务
sudo systemctl start docker
sudo systemctl enable docker

# 将当前用户添加到 docker 组
sudo usermod -aG docker $USER
newgrp docker

# 验证安装
docker --version
docker compose version
```

### 5. 配置 Docker（可选但推荐）

```bash
# 创建 Docker 配置目录
sudo mkdir -p /etc/docker

# 配置 Docker 守护进程（限制日志大小，避免磁盘占满）
sudo tee /etc/docker/daemon.json > /dev/null <<EOF
{
  "log-driver": "json-file",
  "log-opts": {
    "max-size": "10m",
    "max-file": "3"
  },
  "storage-driver": "overlay2"
}
EOF

# 重启 Docker 服务使配置生效
sudo systemctl restart docker
```

---

## 项目部署

### 1. 上传项目文件

将项目文件上传到服务器，可以使用以下方式：

#### 方式一：使用 Git（推荐）

```bash
# 在服务器上克隆项目
cd /opt
sudo git clone <your-repository-url> docker-envs
cd docker-envs
```

#### 方式二：使用 SCP

```bash
# 在本地执行
scp -r /path/to/DockerEnvs user@server:/opt/docker-envs
```

#### 方式三：使用压缩包

```bash
# 在本地打包
tar -czf docker-envs.tar.gz DockerEnvs/

# 上传到服务器
scp docker-envs.tar.gz user@server:/opt/

# 在服务器上解压
ssh user@server
cd /opt
tar -xzf docker-envs.tar.gz
mv DockerEnvs docker-envs
```

### 2. 创建必要的目录

```bash
# 创建实验程序包存储目录
sudo mkdir -p /opt/docker-envs/apps
sudo chmod 755 /opt/docker-envs/apps

# 创建用户环境目录
sudo mkdir -p /opt/docker-envs/user_envs
sudo chmod 755 /opt/docker-envs/user_envs

# 如果使用非 root 用户运行应用，需要设置目录所有者
# 假设使用 docker-envs 用户运行
sudo useradd -r -s /bin/bash docker-envs
sudo chown -R docker-envs:docker-envs /opt/docker-envs
```

### 3. 上传实验程序包

将实验程序包上传到 `/opt/docker-envs/apps/` 目录：

```bash
# 示例：上传 exp-java-001 实验包
scp -r apps/exp-java-001 user@server:/opt/docker-envs/apps/

# 确保目录权限正确
sudo chown -R docker-envs:docker-envs /opt/docker-envs/apps
```

### 4. 编译项目

```bash
cd /opt/docker-envs
mvn clean package -DskipTests
```

编译完成后，JAR 文件位于 `target/DockerEnvs-0.0.1-SNAPSHOT.jar`

---

## 配置文件修改

### ⚠️ 重要提示

**这是部署过程中最关键的一步！** 项目中的 `application.yml` 包含 Windows 本地路径和开发环境配置，部署到 Linux 服务器时**必须修改**。

**必须修改的配置项**：
1. ⚠️ **路径配置**（最重要）：Windows 路径 → Linux 路径
2. ⚠️ **服务器地址**：`localhost` → `YOUR_SERVER_IP`
3. ⚠️ **数据库密码**：开发密码 → 生产密码

### 1. 修改 application.yml

编辑 `src/main/resources/application.yml` 文件，找到以下配置项并修改：

```yaml
spring:
  application:
    name: DockerEnvs
  
  # 数据源配置 - 修改为服务器数据库信息
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    # 修改为服务器 MySQL 地址、端口、数据库名
    url: jdbc:mysql://localhost:3306/virtual_env?useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai
    # 修改为实际的数据库用户名
    username: root
    # 修改为实际的数据库密码
    password: your_mysql_password
  
  # Jackson 配置
  jackson:
    date-format: yyyy-MM-dd HH:mm:ss
    time-zone: GMT+8

# MyBatis Plus 配置
mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl
  mapper-locations: classpath*:/mapper/**/*.xml
  type-aliases-package: org.dockerenvs.entity

# 虚拟环境框架配置
env:
  # 服务器主机地址（用于生成访问URL）- 修改为服务器实际IP或域名
  server:
    host: YOUR_SERVER_IP  # 例如: 192.168.1.100 或 docker-envs.example.com
  # 实验程序包存储路径 - ⚠️ 必须修改！当前是Windows路径，需要改为Linux路径
  apps:
    base-path: /opt/docker-envs/apps  # 原值: D:/Code/Java/DockerEnvs/DockerEnvs/apps
  # 用户环境目录路径 - ⚠️ 必须修改！当前是Windows路径，需要改为Linux路径
  user-envs:
    base-path: /opt/docker-envs/user_envs  # 原值: D:/Code/Java/DockerEnvs/DockerEnvs/user_envs
  # 端口分配范围（可根据需要调整）
  port:
    min: 18000
    max: 19999

# 共享MySQL配置
shared:
  mysql:
    # MySQL root密码 - 修改为共享数据库容器的 root 密码
    root:
      password: shared_mysql_password  # 建议使用强密码
    # 是否允许在创建环境时自动创建数据库容器（默认false，需要手动管理）
    auto-create: false  # 生产环境建议设为 false，手动管理数据库容器

# 服务器配置
server:
  port: 8080  # 可根据需要修改端口
```

### 2. 配置项说明

#### 必须修改的配置项

| 配置项 | 说明 | 当前值（Windows） | 需要改为（Linux） |
|--------|------|------------------|------------------|
| `spring.datasource.url` | 系统数据库连接地址 | `jdbc:mysql://localhost:3306/virtual_env?...` | 保持不变或修改为远程数据库地址 |
| `spring.datasource.username` | 系统数据库用户名 | `root` | 建议改为专用用户（如：`dockerenvs`） |
| `spring.datasource.password` | 系统数据库密码 | `123456` | 修改为强密码 |
| `env.server.host` | 服务器对外访问地址 | `localhost` | `YOUR_SERVER_IP`（如：`192.168.1.100`）或 `YOUR_SERVER_DOMAIN` |
| **`env.apps.base-path`** | **实验程序包存储路径** | **`D:/Code/Java/DockerEnvs/DockerEnvs/apps`** | **`/opt/docker-envs/apps`** ⚠️ |
| **`env.user-envs.base-path`** | **用户环境目录路径** | **`D:/Code/Java/DockerEnvs/DockerEnvs/user_envs`** | **`/opt/docker-envs/user_envs`** ⚠️ |
| `shared.mysql.root.password` | 共享 MySQL 容器 root 密码 | `123456` | 修改为强密码 |

**⚠️ 特别注意**：`env.apps.base-path` 和 `env.user-envs.base-path` 这两个路径是 **Windows 本地路径**，在 Linux 服务器上不存在，**必须修改为 Linux 路径格式**！

#### 可选修改的配置项

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| `server.port` | 应用服务端口 | `8080` |
| `env.port.min` | 端口分配范围最小值 | `18000` |
| `env.port.max` | 端口分配范围最大值 | `19999` |
| `shared.mysql.auto-create` | 是否自动创建共享数据库容器 | `false` |

### 3. 配置修改检查清单

在部署前，请使用以下清单确认所有配置已正确修改：

#### ⚠️ 必须修改（否则应用无法运行）

- [ ] **`env.apps.base-path`** 
  - 原值：`D:/Code/Java/DockerEnvs/DockerEnvs/apps`
  - 改为：`/opt/docker-envs/apps`

- [ ] **`env.user-envs.base-path`**
  - 原值：`D:/Code/Java/DockerEnvs/DockerEnvs/user_envs`
  - 改为：`/opt/docker-envs/user_envs`

- [ ] **`env.server.host`**
  - 原值：`localhost`
  - 改为：`YOUR_SERVER_IP`（如：`192.168.1.100`）

- [ ] **`spring.datasource.password`**
  - 原值：`123456`
  - 改为：实际数据库密码

- [ ] **`shared.mysql.root.password`**
  - 原值：`123456`
  - 改为：共享MySQL容器密码（建议使用强密码）

#### 建议修改（安全考虑）

- [ ] **`spring.datasource.username`**
  - 原值：`root`
  - 建议改为：专用数据库用户（如：`dockerenvs`）

- [ ] **`mybatis-plus.configuration.log-impl`**
  - 原值：`org.apache.ibatis.logging.stdout.StdOutImpl`
  - 建议改为：`org.apache.ibatis.logging.slf4j.Slf4jImpl`（生产环境）

### 4. 重新编译（如果修改了配置文件）

```bash
cd /opt/docker-envs
mvn clean package -DskipTests
```

**注意**：如果配置文件在 `src/main/resources/application.yml`，修改后需要重新编译。如果使用外部配置文件（如 `application-prod.yml`），则不需要重新编译。

---

## 数据库初始化

### 1. 创建系统数据库

系统需要一个 MySQL 数据库来存储虚拟环境信息、端口使用记录等元数据。

```bash
# 登录 MySQL
mysql -uroot -p

# 执行数据库初始化脚本
source /opt/docker-envs/src/main/resources/db/schema.sql

# 或者直接执行 SQL 语句
mysql -uroot -p < /opt/docker-envs/src/main/resources/db/schema.sql
```

### 2. 验证数据库创建

```bash
# 登录 MySQL
mysql -uroot -p

# 查看数据库
SHOW DATABASES;

# 使用数据库
USE virtual_env;

# 查看表
SHOW TABLES;

# 应该看到以下表：
# - virtual_env（虚拟环境表）
# - port_usage（端口使用表）
```

### 3. 创建数据库用户（可选但推荐）

为了安全，建议创建专门的数据库用户，而不是使用 root：

```bash
# 登录 MySQL
mysql -uroot -p

# 创建用户
CREATE USER 'dockerenvs'@'localhost' IDENTIFIED BY 'your_password';

# 授予权限
GRANT ALL PRIVILEGES ON virtual_env.* TO 'dockerenvs'@'localhost';

# 刷新权限
FLUSH PRIVILEGES;

# 退出
EXIT;
```

然后在 `application.yml` 中使用新创建的用户：

```yaml
spring:
  datasource:
    username: dockerenvs
    password: your_password
```

---

## Docker共享数据库初始化

DockerEnvs 支持共享 MySQL 数据库模式，所有实验环境共享同一个 MySQL 容器。本节详细说明如何初始化共享数据库容器。

### 1. 初始化方式

共享 MySQL 数据库容器可以通过以下三种方式初始化：

#### 方式一：通过 Web 管理界面（推荐）

1. 启动应用后，访问 `http://YOUR_SERVER_IP:8080/shared-mysql.html`
2. 点击"确保共享 MySQL 存在"按钮
3. 系统会自动创建并启动共享 MySQL 容器

#### 方式二：通过 API 接口

```bash
# 确保共享 MySQL 容器存在并运行
curl -X POST http://YOUR_SERVER_IP:8080/api/shared-mysql/ensure

# 查看共享 MySQL 状态
curl http://YOUR_SERVER_IP:8080/api/shared-mysql/status
```

#### 方式三：手动创建（适合首次部署）

如果应用尚未启动，可以手动创建共享 MySQL 容器：

```bash
# 1. 创建 Docker 网络
docker network create shared-mysql-net

# 2. 创建共享 MySQL 容器
docker run -d \
  --name shared-mysql \
  --network shared-mysql-net \
  --restart unless-stopped \
  -e MYSQL_ROOT_PASSWORD=your_shared_mysql_password \
  -e MYSQL_ALLOW_EMPTY_PASSWORD=no \
  -v shared-mysql-data:/var/lib/mysql \
  mysql:8.0 \
  --character-set-server=utf8mb4 \
  --collation-server=utf8mb4_unicode_ci \
  --default-authentication-plugin=mysql_native_password \
  --bind-address=0.0.0.0 \
  --port=3306

# 注意：将 your_shared_mysql_password 替换为实际的密码
# 此密码需要与 application.yml 中的 shared.mysql.root.password 保持一致
```

### 2. 验证共享 MySQL 容器

```bash
# 检查容器是否运行
docker ps | grep shared-mysql

# 查看容器日志
docker logs shared-mysql

# 测试 MySQL 连接
docker exec -it shared-mysql mysql -uroot -p
# 输入密码后，如果能够连接，说明 MySQL 已就绪
```

### 3. 共享 MySQL 容器配置说明

共享 MySQL 容器使用以下配置：

| 配置项 | 值 | 说明 |
|--------|-----|------|
| 容器名称 | `shared-mysql` | 固定名称，应用通过此名称连接 |
| 网络名称 | `shared-mysql-net` | Docker 网络，所有需要数据库的实验容器会加入此网络 |
| 数据卷 | `shared-mysql-data` | 持久化存储 MySQL 数据 |
| 镜像 | `mysql:8.0` | MySQL 8.0 官方镜像 |
| 端口 | 3306（容器内） | 不映射到主机，通过 Docker 网络访问 |
| Root 密码 | 由配置决定 | 在 `application.yml` 中配置 |

### 4. 共享 MySQL 工作流程

1. **容器创建阶段**
   - 系统检查 `shared-mysql` 容器是否存在
   - 如果不存在，根据配置决定是否自动创建
   - 创建 Docker 网络 `shared-mysql-net`（如果不存在）
   - 创建并启动 MySQL 容器

2. **数据库创建阶段**
   - 当实验环境需要数据库时，系统会：
     - 检查共享 MySQL 容器是否运行
     - 在共享 MySQL 容器中创建对应的数据库（如果不存在）
     - 数据库名称由实验的 `metadata.json` 中的 `database.name` 指定

3. **环境连接阶段**
   - 实验容器通过 Docker 网络连接到 `shared-mysql` 容器
   - 使用容器名称 `shared-mysql` 作为数据库主机名
   - 连接信息通过环境变量传递给应用容器

### 5. 共享 MySQL 管理命令

#### 查看状态

```bash
# 通过 API
curl http://YOUR_SERVER_IP:8080/api/shared-mysql/status

# 通过 Docker
docker ps -a | grep shared-mysql
docker logs shared-mysql
```

#### 停止容器

```bash
# 通过 API
curl -X POST http://YOUR_SERVER_IP:8080/api/shared-mysql/stop

# 通过 Docker
docker stop shared-mysql
```

#### 启动容器

```bash
# 通过 Docker
docker start shared-mysql

# 通过 API（会自动启动）
curl -X POST http://YOUR_SERVER_IP:8080/api/shared-mysql/ensure
```

#### 完全删除容器和数据（谨慎操作）

```bash
# 通过 API
curl -X DELETE http://YOUR_SERVER_IP:8080/api/shared-mysql

# 通过 Docker（手动删除）
docker stop shared-mysql
docker rm -f shared-mysql
docker volume rm shared-mysql-data
docker network rm shared-mysql-net
```

### 6. 自动创建配置

如果希望系统在需要时自动创建共享 MySQL 容器，可以在 `application.yml` 中设置：

```yaml
shared:
  mysql:
    auto-create: true  # 启用自动创建
```

**注意**：生产环境建议设置为 `false`，手动管理数据库容器的生命周期，确保数据安全。

### 7. 数据库初始化脚本（可选）

如果需要为共享 MySQL 执行初始化脚本，可以在创建容器后执行：

```bash
# 将初始化 SQL 脚本复制到容器
docker cp /path/to/init.sql shared-mysql:/tmp/init.sql

# 执行初始化脚本
docker exec -i shared-mysql mysql -uroot -pyour_password < /tmp/init.sql

# 或者在容器内执行
docker exec -it shared-mysql mysql -uroot -p
# 然后执行 SQL 语句
```

---

## 启动服务

### 1. 使用 Maven 直接运行（开发/测试）

```bash
cd /opt/docker-envs
mvn spring-boot:run
```

### 2. 使用 JAR 文件运行（生产推荐）

```bash
cd /opt/docker-envs
java -jar target/DockerEnvs-0.0.1-SNAPSHOT.jar
```

### 3. 使用 systemd 服务（生产推荐）

创建 systemd 服务文件：

```bash
sudo nano /etc/systemd/system/docker-envs.service
```

添加以下内容：

```ini
[Unit]
Description=DockerEnvs Virtual Environment Framework
After=network.target mysql.service docker.service

[Service]
Type=simple
User=docker-envs
Group=docker-envs
WorkingDirectory=/opt/docker-envs
ExecStart=/usr/bin/java -jar /opt/docker-envs/target/DockerEnvs-0.0.1-SNAPSHOT.jar
Restart=always
RestartSec=10
StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
```

启动服务：

```bash
# 重新加载 systemd 配置
sudo systemctl daemon-reload

# 启动服务
sudo systemctl start docker-envs

# 设置开机自启
sudo systemctl enable docker-envs

# 查看服务状态
sudo systemctl status docker-envs

# 查看日志
sudo journalctl -u docker-envs -f
```

### 4. 使用 nohup 后台运行（简单方式）

```bash
cd /opt/docker-envs
nohup java -jar target/DockerEnvs-0.0.1-SNAPSHOT.jar > app.log 2>&1 &

# 查看进程
ps aux | grep DockerEnvs

# 查看日志
tail -f app.log
```

---

## 验证部署

### 1. 检查应用是否启动

```bash
# 检查端口是否监听
netstat -tlnp | grep 8080
# 或
ss -tlnp | grep 8080

# 检查进程
ps aux | grep DockerEnvs

# 访问主页
curl http://localhost:8080/
```

### 2. 检查共享 MySQL 容器

```bash
# 查看容器状态
docker ps | grep shared-mysql

# 查看容器日志
docker logs shared-mysql

# 通过 API 检查状态
curl http://localhost:8080/api/shared-mysql/status
```

### 3. 测试创建实验环境

```bash
# 创建测试环境
curl -X POST http://localhost:8080/api/env/start \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "test001",
    "systemId": "system001",
    "expId": "exp-java-001"
  }'

# 查看环境状态
curl http://localhost:8080/api/env/user/test001
```

### 4. 检查防火墙配置

确保以下端口已开放：

```bash
# Ubuntu/Debian
sudo ufw allow 8080/tcp
sudo ufw allow 18000:19999/tcp  # 实验环境端口范围

# CentOS/RHEL
sudo firewall-cmd --permanent --add-port=8080/tcp
sudo firewall-cmd --permanent --add-port=18000-19999/tcp
sudo firewall-cmd --reload
```

---

## 命令速查表

### 服务管理

```bash
# systemd 服务管理
sudo systemctl start docker-envs      # 启动服务
sudo systemctl stop docker-envs       # 停止服务
sudo systemctl restart docker-envs    # 重启服务
sudo systemctl status docker-envs     # 查看状态
sudo systemctl enable docker-envs    # 开机自启
sudo systemctl disable docker-envs   # 取消开机自启

# 查看日志
sudo journalctl -u docker-envs -f    # 实时日志
sudo journalctl -u docker-envs -n 100  # 最近100行
```

### Docker 管理

```bash
# 共享MySQL容器
docker ps | grep shared-mysql         # 查看容器状态
docker logs shared-mysql              # 查看容器日志
docker start shared-mysql             # 启动容器
docker stop shared-mysql              # 停止容器
docker restart shared-mysql           # 重启容器

# 实验环境容器
docker ps | grep env-                 # 查看所有实验环境容器
docker logs <container-name>          # 查看容器日志
docker exec -it <container-name> bash # 进入容器
```

### 应用检查

```bash
# 检查应用状态
curl http://YOUR_SERVER_IP:8080/                    # 访问主页
curl http://YOUR_SERVER_IP:8080/api/shared-mysql/status  # 检查共享MySQL
curl http://YOUR_SERVER_IP:8080/api/env/all          # 查看所有环境

# 检查端口
netstat -tlnp | grep 8080              # 检查应用端口
ss -tlnp | grep 8080                     # 使用ss命令
netstat -tlnp | grep 18000              # 检查实验环境端口
```

### 数据库管理

```bash
# 系统数据库
mysql -uroot -p                        # 登录MySQL
mysql -uroot -p < schema.sql           # 执行SQL脚本

# 共享MySQL容器
docker exec -it shared-mysql mysql -uroot -p  # 登录共享MySQL
docker exec shared-mysql mysql -uroot -p -e "SHOW DATABASES;"  # 查看数据库
```

### 文件与目录

```bash
# 检查目录
ls -la /opt/docker-envs/apps           # 查看实验程序包
ls -la /opt/docker-envs/user_envs     # 查看用户环境
du -sh /opt/docker-envs/*              # 查看目录大小

# 权限管理
sudo chown -R docker-envs:docker-envs /opt/docker-envs
sudo chmod -R 755 /opt/docker-envs
```

---

## 常见问题排查

> 💡 **快速诊断**：遇到问题时，首先查看应用日志：`sudo journalctl -u docker-envs -n 100`

### 1. 应用无法启动

**症状**：服务启动失败，无法访问应用

**快速检查**：
```bash
# 1. 查看错误日志
sudo journalctl -u docker-envs -n 50 --no-pager

# 2. 检查Java环境
java -version

# 3. 检查端口占用
sudo lsof -i :8080
# 或
sudo netstat -tlnp | grep 8080
```

**常见原因及解决方案**：

| 错误信息 | 原因 | 解决方案 |
|---------|------|---------|
| `Connection refused` 或 `Access denied` | 数据库连接失败 | 检查MySQL服务是否运行：`sudo systemctl status mysql`<br>验证用户名密码：`mysql -uroot -p`<br>检查配置文件中的数据库连接信息 |
| `Address already in use` | 端口被占用 | 查找占用进程：`sudo lsof -i :8080`<br>修改端口：在`application.yml`中修改`server.port` |
| `FileNotFoundException` 或路径错误 | 路径配置错误 | ⚠️ **检查路径配置**：确保`env.apps.base-path`和`env.user-envs.base-path`已从Windows路径改为Linux路径 |
| `YAML parse error` | 配置文件格式错误 | 检查YAML缩进（使用空格，不要使用Tab）<br>使用在线YAML验证工具检查语法 |
| `OutOfMemoryError` | 内存不足 | 增加JVM内存：在systemd服务中添加`-Xmx2048m`参数 |

### 2. Docker 权限问题

**症状**：执行Docker命令时提示 `permission denied while trying to connect to the Docker daemon socket`

**解决方案**：
```bash
# 1. 将运行用户添加到docker组
sudo usermod -aG docker docker-envs  # 替换为实际运行用户

# 2. 重新加载组权限（或重新登录）
newgrp docker

# 3. 验证权限
docker ps

# 如果仍有问题，检查Docker服务
sudo systemctl status docker
sudo systemctl start docker
```

**预防措施**：在创建systemd服务前，确保运行用户已加入docker组

### 3. 共享 MySQL 容器无法创建

**症状**：创建共享MySQL容器失败，或容器无法启动

**排查步骤**：
```bash
# 1. 检查Docker服务
sudo systemctl status docker

# 2. 检查网络和容器
docker network ls | grep shared-mysql-net
docker ps -a | grep shared-mysql

# 3. 查看容器日志（如果容器存在）
docker logs shared-mysql

# 4. 检查镜像
docker images | grep mysql
```

**常见原因及解决方案**：

| 错误信息 | 原因 | 解决方案 |
|---------|------|---------|
| `network already exists` | 网络已存在 | 删除旧网络：`docker network rm shared-mysql-net` |
| `container name already in use` | 容器名称冲突 | 删除旧容器：`docker rm -f shared-mysql` |
| `pull access denied` | 镜像下载失败 | 检查网络连接，手动拉取：`docker pull mysql:8.0` |
| `bind: address already in use` | 端口被占用 | 共享MySQL不映射端口，此错误通常不会出现 |
| 容器启动后立即退出 | 配置错误或资源不足 | 查看日志：`docker logs shared-mysql`<br>检查内存和磁盘空间 |

### 4. 实验环境无法创建

**症状**：调用API创建环境时返回错误，环境创建失败

**排查步骤**：
```bash
# 1. 查看应用日志（查找具体错误）
sudo journalctl -u docker-envs -n 100 | grep -i error

# 2. 检查实验程序包
ls -la /opt/docker-envs/apps/exp-java-001/
cat /opt/docker-envs/apps/exp-java-001/metadata.json

# 3. 检查目录权限
ls -ld /opt/docker-envs/apps
ls -ld /opt/docker-envs/user_envs

# 4. 检查端口使用情况
netstat -tlnp | grep -E "18000|18001"  # 检查端口范围

# 5. 检查共享MySQL
curl http://YOUR_SERVER_IP:8080/api/shared-mysql/status
```

**常见原因及解决方案**：

| 错误信息 | 原因 | 解决方案 |
|---------|------|---------|
| `实验程序包不存在` | 路径配置错误或程序包未上传 | ⚠️ **检查路径配置**：确认`env.apps.base-path`已正确配置<br>检查程序包是否存在：`ls -la /opt/docker-envs/apps/<expId>/` |
| `Permission denied` | 目录权限不足 | 设置正确权限：`sudo chown -R docker-envs:docker-envs /opt/docker-envs` |
| `NO_AVAILABLE_PORT` | 端口范围已用完 | 检查端口使用：`netstat -tlnp \| grep 18000`<br>扩大端口范围或清理未使用的环境 |
| `共享MySQL容器不可用` | 数据库容器未启动 | 启动共享MySQL：`curl -X POST http://YOUR_SERVER_IP:8080/api/shared-mysql/ensure` |
| `metadata.json not found` | 实验元数据文件缺失 | 检查实验程序包是否包含`metadata.json`文件 |

### 5. 数据库连接失败

**症状**：应用启动时提示数据库连接失败，或无法访问共享MySQL

**排查步骤**：
```bash
# 1. 检查系统MySQL服务
sudo systemctl status mysql
sudo systemctl start mysql  # 如果未启动

# 2. 测试系统数据库连接
mysql -uroot -p -e "SHOW DATABASES;"
mysql -uroot -p -e "USE virtual_env; SHOW TABLES;"

# 3. 检查共享MySQL容器
docker ps | grep shared-mysql
docker exec -it shared-mysql mysql -uroot -p

# 4. 检查网络配置
docker network inspect shared-mysql-net
```

**常见原因及解决方案**：

| 错误信息 | 原因 | 解决方案 |
|---------|------|---------|
| `Communications link failure` | MySQL服务未启动 | 启动服务：`sudo systemctl start mysql` |
| `Access denied for user` | 用户名或密码错误 | 检查配置文件中的用户名密码<br>测试连接：`mysql -u<username> -p` |
| `Unknown database 'virtual_env'` | 数据库不存在 | 执行初始化脚本：`mysql -uroot -p < src/main/resources/db/schema.sql` |
| `Can't connect to MySQL server` | 网络问题或防火墙 | 检查防火墙规则<br>检查MySQL是否监听正确端口：`sudo netstat -tlnp \| grep 3306` |
| 共享MySQL连接失败 | 容器未启动或网络问题 | 启动容器：`docker start shared-mysql`<br>检查网络：`docker network inspect shared-mysql-net` |

### 6. 快速故障排除检查清单

遇到问题时，按以下顺序检查：

```bash
# ✅ 1. 检查服务状态
sudo systemctl status docker-envs
sudo systemctl status docker
sudo systemctl status mysql

# ✅ 2. 检查日志（最重要！）
sudo journalctl -u docker-envs -n 100 --no-pager

# ✅ 3. 检查配置文件路径（最常见问题）
grep -E "base-path|host" src/main/resources/application.yml
# 确保路径不是Windows格式（D:/...），而是Linux格式（/opt/...）

# ✅ 4. 检查目录和权限
ls -ld /opt/docker-envs/apps
ls -ld /opt/docker-envs/user_envs
# 确保目录存在且有正确权限

# ✅ 5. 检查端口和网络
sudo netstat -tlnp | grep 8080
docker ps | grep shared-mysql

# ✅ 6. 检查数据库连接
mysql -uroot -p -e "SHOW DATABASES;"
curl http://YOUR_SERVER_IP:8080/api/shared-mysql/status
```

---

## 生产环境优化建议

### 1. 安全配置

- **使用非 root 用户运行应用**
- **数据库用户权限最小化**
- **使用强密码**
- **配置防火墙规则**
- **启用 HTTPS**（通过 Nginx 反向代理）

### 2. 性能优化

- **配置 JVM 参数**：

```bash
java -Xms512m -Xmx2048m -XX:+UseG1GC -jar target/DockerEnvs-0.0.1-SNAPSHOT.jar
```

- **配置 Docker 资源限制**：

在 `docker-compose.yml` 模板中添加资源限制（需要修改代码）

- **定期清理未使用的容器和镜像**：

```bash
# 清理停止的容器
docker container prune -f

# 清理未使用的镜像
docker image prune -a -f

# 清理未使用的数据卷
docker volume prune -f
```

### 3. 监控和日志

- **配置日志轮转**：使用 `logrotate` 管理应用日志
- **监控 Docker 资源使用**：使用 `docker stats` 或监控工具
- **监控应用健康状态**：定期检查 API 接口

### 4. 备份策略

- **数据库备份**：

```bash
# 系统数据库备份
mysqldump -uroot -p virtual_env > backup_$(date +%Y%m%d).sql

# 共享 MySQL 数据备份
docker exec shared-mysql mysqldump -uroot -p --all-databases > shared_mysql_backup_$(date +%Y%m%d).sql
```

- **数据卷备份**：

```bash
# 备份共享 MySQL 数据卷
docker run --rm -v shared-mysql-data:/data -v $(pwd):/backup alpine tar czf /backup/mysql_data_backup_$(date +%Y%m%d).tar.gz /data
```

### 5. 使用 Nginx 反向代理（可选）

```nginx
server {
    listen 80;
    server_name your-domain.com;

    location / {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # WebSocket 支持
    location /ws/ {
        proxy_pass http://localhost:8080;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
    }
}
```

---

## 总结

完成以上步骤后，DockerEnvs 系统应该已经成功部署到服务器上。主要步骤包括：

1. ✅ 安装基础环境（JDK、Maven、MySQL、Docker）
2. ✅ 部署项目文件
3. ✅ 修改配置文件
4. ✅ 初始化系统数据库
5. ✅ 初始化共享 MySQL 容器
6. ✅ 启动应用服务
7. ✅ 验证部署

如果遇到问题，请参考"常见问题排查"部分，或查看应用日志进行详细诊断。

---

## 生产环境配置示例

### 完整的生产环境配置

以下是针对 Linux 生产环境的完整配置示例，请根据实际情况修改占位符。

#### 1. application.yml 生产环境配置

创建或编辑 `src/main/resources/application.yml`：

```yaml
spring:
  application:
    name: DockerEnvs
  
  # 数据源配置 - 生产环境建议使用专用数据库用户
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    # 如果MySQL在本地，使用 localhost；如果在其他服务器，使用实际IP
    url: jdbc:mysql://localhost:3306/virtual_env?useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai
    # 生产环境建议创建专用用户，不要使用 root
    username: dockerenvs
    password: YOUR_MYSQL_PASSWORD
  
  # Jackson 配置
  jackson:
    date-format: yyyy-MM-dd HH:mm:ss
    time-zone: GMT+8

# MyBatis Plus 配置
mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true
    # 生产环境建议关闭 SQL 日志输出，改为文件日志
    log-impl: org.apache.ibatis.logging.slf4j.Slf4jImpl
  mapper-locations: classpath*:/mapper/**/*.xml
  type-aliases-package: org.dockerenvs.entity

# 虚拟环境框架配置
env:
  # 服务器主机地址 - 必须修改为实际服务器IP或域名
  # 用于生成实验环境的访问URL，学生将通过此地址访问实验环境
  server:
    host: YOUR_SERVER_IP  # 例如: 192.168.1.100 或 10.0.0.50
    # 如果使用域名，可以这样配置:
    # host: YOUR_SERVER_DOMAIN  # 例如: docker-envs.example.com
  
  # 实验程序包存储路径 - ⚠️ 必须修改！原值为Windows路径: D:/Code/Java/DockerEnvs/DockerEnvs/apps
  apps:
    base-path: /opt/docker-envs/apps
  
  # 用户环境目录路径 - ⚠️ 必须修改！原值为Windows路径: D:/Code/Java/DockerEnvs/DockerEnvs/user_envs
  user-envs:
    base-path: /opt/docker-envs/user_envs
  
  # 端口分配范围 - 根据服务器实际情况调整
  # 确保此端口范围未被其他服务占用
  port:
    min: 18000
    max: 19999

# 共享MySQL配置
shared:
  mysql:
    # MySQL root密码 - 必须使用强密码
    root:
      password: YOUR_SHARED_MYSQL_PASSWORD
    # 生产环境建议设为 false，手动管理数据库容器生命周期
    auto-create: false

# 服务器配置
server:
  port: 8080  # 应用服务端口，可根据需要修改
```

#### 2. 配置项说明

**必须修改的配置项**：

1. **`env.apps.base-path`** ⚠️ **最重要**：
   - **当前值**：`D:/Code/Java/DockerEnvs/DockerEnvs/apps`（Windows路径）
   - **必须改为**：`/opt/docker-envs/apps`（Linux路径）
   - 这是实验程序包的存储路径，路径错误会导致无法找到实验程序包

2. **`env.user-envs.base-path`** ⚠️ **最重要**：
   - **当前值**：`D:/Code/Java/DockerEnvs/DockerEnvs/user_envs`（Windows路径）
   - **必须改为**：`/opt/docker-envs/user_envs`（Linux路径）
   - 这是用户环境的存储路径，路径错误会导致无法创建用户环境

3. **`env.server.host`**: 
   - **当前值**：`localhost`
   - **必须改为**：服务器实际IP地址或域名
   - 学生将通过此地址访问实验环境
   - 示例：`192.168.1.100` 或 `docker-envs.example.com`

4. **`spring.datasource.password`**: 
   - **当前值**：`123456`
   - **必须改为**：系统数据库实际密码
   - 建议使用强密码

5. **`shared.mysql.root.password`**: 
   - **当前值**：`123456`
   - **必须改为**：共享MySQL容器的root密码
   - 必须使用强密码
   - 建议与系统数据库密码不同

**可选修改的配置项**：

- `server.port`: 应用服务端口（默认8080）
- `env.port.min/max`: 实验环境端口范围（默认18000-19999）
- `mybatis-plus.configuration.log-impl`: SQL日志输出方式

#### 3. 生产环境目录结构

确保以下目录存在且权限正确：

```bash
# 创建目录结构
sudo mkdir -p /opt/docker-envs/{apps,user_envs,logs}

# 设置目录权限（假设使用 docker-envs 用户运行）
sudo chown -R docker-envs:docker-envs /opt/docker-envs
sudo chmod -R 755 /opt/docker-envs
```

#### 4. systemd 服务配置（生产推荐）

创建 `/etc/systemd/system/docker-envs.service`：

```ini
[Unit]
Description=DockerEnvs Virtual Environment Framework
Documentation=https://github.com/your-repo/docker-envs
After=network.target mysql.service docker.service
Requires=docker.service

[Service]
Type=simple
User=docker-envs
Group=docker-envs
WorkingDirectory=/opt/docker-envs
ExecStart=/usr/bin/java -Xms512m -Xmx2048m -XX:+UseG1GC -jar /opt/docker-envs/target/DockerEnvs-0.0.1-SNAPSHOT.jar
Restart=always
RestartSec=10
StandardOutput=journal
StandardError=journal
SyslogIdentifier=docker-envs

# 环境变量（可选）
Environment="JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64"
Environment="PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"

# 安全设置
NoNewPrivileges=true
PrivateTmp=true

[Install]
WantedBy=multi-user.target
```

启用服务：

```bash
# 重新加载 systemd 配置
sudo systemctl daemon-reload

# 启用服务（开机自启）
sudo systemctl enable docker-envs

# 启动服务
sudo systemctl start docker-envs

# 查看状态
sudo systemctl status docker-envs

# 查看日志
sudo journalctl -u docker-envs -f
```

#### 5. 防火墙配置（Linux）

**Ubuntu/Debian (ufw)**:

```bash
# 允许应用端口
sudo ufw allow 8080/tcp comment 'DockerEnvs Application'

# 允许实验环境端口范围
sudo ufw allow 18000:19999/tcp comment 'DockerEnvs Experiment Ports'

# 启用防火墙（如果未启用）
sudo ufw enable

# 查看规则
sudo ufw status verbose
```

**CentOS/RHEL (firewalld)**:

```bash
# 允许应用端口
sudo firewall-cmd --permanent --add-port=8080/tcp --zone=public

# 允许实验环境端口范围
sudo firewall-cmd --permanent --add-port=18000-19999/tcp --zone=public

# 重载防火墙配置
sudo firewall-cmd --reload

# 查看规则
sudo firewall-cmd --list-all
```

#### 6. 生产环境检查清单

部署完成后，请检查以下项目：

- [ ] 应用服务已启动并运行
- [ ] 系统数据库已初始化
- [ ] 共享MySQL容器已创建并运行
- [ ] 防火墙端口已开放
- [ ] 目录权限正确
- [ ] 可以通过 `http://YOUR_SERVER_IP:8080` 访问应用
- [ ] 可以成功创建测试实验环境
- [ ] 日志输出正常
- [ ] systemd 服务配置正确（如果使用）

#### 7. 验证命令

```bash
# 检查应用是否运行
curl http://YOUR_SERVER_IP:8080/

# 检查共享MySQL状态
curl http://YOUR_SERVER_IP:8080/api/shared-mysql/status

# 测试创建环境（需要替换实际的实验ID）
curl -X POST http://YOUR_SERVER_IP:8080/api/env/start \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "test001",
    "systemId": "system001",
    "expId": "exp-java-001"
  }'
```

---

## 附录

### A. 配置文件完整示例

`application.yml` 完整配置示例（生产环境）：

```yaml
spring:
  application:
    name: DockerEnvs
  
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://localhost:3306/virtual_env?useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai
    username: dockerenvs
    password: YOUR_MYSQL_PASSWORD  # 替换为实际密码
  
  jackson:
    date-format: yyyy-MM-dd HH:mm:ss
    time-zone: GMT+8

mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true
    log-impl: org.apache.ibatis.logging.slf4j.Slf4jImpl  # 生产环境使用日志框架
  mapper-locations: classpath*:/mapper/**/*.xml
  type-aliases-package: org.dockerenvs.entity

env:
  server:
    host: YOUR_SERVER_IP  # 替换为实际服务器IP，如: 192.168.1.100
  apps:
    base-path: /opt/docker-envs/apps
  user-envs:
    base-path: /opt/docker-envs/user_envs
  port:
    min: 18000
    max: 19999

shared:
  mysql:
    root:
      password: YOUR_SHARED_MYSQL_PASSWORD  # 替换为实际密码
    auto-create: false

server:
  port: 8080
```

**配置说明**：
- 请将所有 `YOUR_SERVER_IP`、`YOUR_MYSQL_PASSWORD`、`YOUR_SHARED_MYSQL_PASSWORD` 替换为实际值
- 生产环境建议使用专用数据库用户，不要使用 root
- `env.server.host` 必须设置为服务器实际IP或域名，用于生成实验环境访问URL

### B. 快速部署脚本

可以创建一个部署脚本 `deploy.sh`：

```bash
#!/bin/bash

# DockerEnvs 生产环境快速部署脚本
# 适用于 Linux 系统

set -e

# 配置变量 - 请根据实际情况修改
SERVER_IP="YOUR_SERVER_IP"  # 替换为实际服务器IP
APP_USER="docker-envs"
APP_DIR="/opt/docker-envs"
JAVA_OPTS="-Xms512m -Xmx2048m -XX:+UseG1GC"

echo "=========================================="
echo "DockerEnvs 生产环境部署脚本"
echo "=========================================="

# 检查是否为 root 用户
if [ "$EUID" -eq 0 ]; then 
   echo "请不要使用 root 用户运行此脚本"
   exit 1
fi

# 1. 检查依赖
echo "[1/7] 检查依赖..."
command -v java >/dev/null 2>&1 || { echo "错误: 未安装 Java"; exit 1; }
command -v mvn >/dev/null 2>&1 || { echo "错误: 未安装 Maven"; exit 1; }
command -v docker >/dev/null 2>&1 || { echo "错误: 未安装 Docker"; exit 1; }
command -v mysql >/dev/null 2>&1 || { echo "错误: 未安装 MySQL 客户端"; exit 1; }
echo "✓ 依赖检查通过"

# 2. 编译项目
echo "[2/7] 编译项目..."
mvn clean package -DskipTests
if [ $? -ne 0 ]; then
    echo "错误: 项目编译失败"
    exit 1
fi
echo "✓ 项目编译完成"

# 3. 创建目录
echo "[3/7] 创建目录..."
sudo mkdir -p ${APP_DIR}/{apps,user_envs,logs}
sudo chown -R ${APP_USER}:${APP_USER} ${APP_DIR} 2>/dev/null || {
    echo "创建用户 ${APP_USER}..."
    sudo useradd -r -s /bin/bash ${APP_USER}
    sudo chown -R ${APP_USER}:${APP_USER} ${APP_DIR}
}
echo "✓ 目录创建完成"

# 4. 初始化数据库
echo "[4/7] 初始化数据库..."
read -sp "请输入 MySQL root 密码: " mysql_password
echo
mysql -uroot -p${mysql_password} < src/main/resources/db/schema.sql
if [ $? -ne 0 ]; then
    echo "错误: 数据库初始化失败"
    exit 1
fi
echo "✓ 数据库初始化完成"

# 5. 检查配置文件
echo "[5/7] 检查配置文件..."
if [ ! -f "src/main/resources/application.yml" ]; then
    echo "错误: 配置文件不存在"
    exit 1
fi
echo "✓ 配置文件检查通过"
echo "⚠️  请确保已修改 application.yml 中的配置项："
echo "   - env.server.host: ${SERVER_IP}"
echo "   - spring.datasource.password"
echo "   - shared.mysql.root.password"

# 6. 创建 systemd 服务
echo "[6/7] 创建 systemd 服务..."
sudo tee /etc/systemd/system/docker-envs.service > /dev/null <<EOF
[Unit]
Description=DockerEnvs Virtual Environment Framework
After=network.target mysql.service docker.service
Requires=docker.service

[Service]
Type=simple
User=${APP_USER}
Group=${APP_USER}
WorkingDirectory=${APP_DIR}
ExecStart=/usr/bin/java ${JAVA_OPTS} -jar ${APP_DIR}/target/DockerEnvs-0.0.1-SNAPSHOT.jar
Restart=always
RestartSec=10
StandardOutput=journal
StandardError=journal
SyslogIdentifier=docker-envs

[Install]
WantedBy=multi-user.target
EOF
sudo systemctl daemon-reload
echo "✓ systemd 服务创建完成"

# 7. 启动服务
echo "[7/7] 启动服务..."
read -p "是否现在启动服务? (y/n): " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    sudo systemctl enable docker-envs
    sudo systemctl start docker-envs
    sleep 3
    if sudo systemctl is-active --quiet docker-envs; then
        echo "✓ 服务启动成功"
    else
        echo "✗ 服务启动失败，请查看日志: sudo journalctl -u docker-envs -n 50"
        exit 1
    fi
fi

echo "=========================================="
echo "部署完成！"
echo "=========================================="
echo "访问地址: http://${SERVER_IP}:8080"
echo "查看日志: sudo journalctl -u docker-envs -f"
echo "服务管理:"
echo "  启动: sudo systemctl start docker-envs"
echo "  停止: sudo systemctl stop docker-envs"
echo "  重启: sudo systemctl restart docker-envs"
echo "  状态: sudo systemctl status docker-envs"
echo "=========================================="
```

使用方式：

```bash
# 赋予执行权限
chmod +x deploy.sh

# 运行部署脚本
./deploy.sh
```

**注意事项**：
- 脚本会检查必要的依赖
- 需要 sudo 权限来创建目录和 systemd 服务
- 请确保已修改脚本中的 `SERVER_IP` 变量
- 部署前请确保已修改 `application.yml` 配置文件

---

## 📝 部署检查清单

部署完成后，请确认以下所有项目：

### 基础配置
- [ ] 服务器环境已安装（JDK、Maven、MySQL、Docker）
- [ ] 项目文件已上传到服务器
- [ ] 实验程序包已上传到 `/opt/docker-envs/apps/`
- [ ] 目录权限已正确设置

### 配置文件
- [ ] `env.server.host` 已从 `localhost` 改为 `YOUR_SERVER_IP`
- [ ] `env.apps.base-path` 已从 Windows 路径改为 `/opt/docker-envs/apps`
- [ ] `env.user-envs.base-path` 已从 Windows 路径改为 `/opt/docker-envs/user_envs`
- [ ] `spring.datasource.password` 已修改为实际密码
- [ ] `shared.mysql.root.password` 已修改为实际密码

### 数据库
- [ ] 系统数据库 `virtual_env` 已创建
- [ ] 数据库表已初始化（`virtual_env` 和 `port_usage`）
- [ ] 共享MySQL容器已创建并运行
- [ ] 可以通过API访问共享MySQL状态

### 服务
- [ ] 应用服务已启动（systemd 或手动启动）
- [ ] 服务已设置为开机自启
- [ ] 可以通过 `http://YOUR_SERVER_IP:8080` 访问应用
- [ ] 防火墙端口已开放（8080 和 18000-19999）

### 功能验证
- [ ] 可以访问应用主页
- [ ] 可以查看共享MySQL状态
- [ ] 可以成功创建测试实验环境
- [ ] 实验环境可以正常访问

---

## 🎯 下一步

部署完成后，建议：

1. **配置监控**：设置应用和系统监控，及时发现问题
2. **定期备份**：配置数据库和数据的自动备份
3. **安全加固**：配置HTTPS、限制访问IP等安全措施
4. **性能优化**：根据实际使用情况调整JVM参数和资源限制
5. **文档维护**：记录服务器配置变更，便于后续维护

---

## 📚 相关文档

- [README.md](../README.md) - 项目说明和API文档
- [添加新实验指南](add-new-experiment.md) - 如何添加新的实验程序包
- [系统工作流程详解](system-workflow.md) - 系统技术文档

---

## 💬 获取帮助

如果遇到问题：

1. **查看日志**：`sudo journalctl -u docker-envs -n 100`
2. **检查配置**：确认所有配置项已正确修改
3. **参考故障排除**：查看"常见问题排查"章节
4. **联系支持**：提交Issue或联系开发团队

---

**文档版本**: 1.1  
**最后更新**: 2025-01-XX  
**维护者**: DockerEnvs 开发团队


# 快速上手指南

> 给小组成员的部署 & 运行说明。从零开始约 10 分钟能跑通。

---

## 前置要求

| 工具 | 最低版本 | 检查命令 |
|------|---------|---------|
| Docker Desktop | 4.x | `docker --version` |
| Docker Compose | 2.x（内置于 Docker Desktop） | `docker compose version` |
| Git | 任意 | `git --version` |
| （可选）DBeaver / MySQL Workbench | — | 查看结果用 |

> **Windows 用户**：使用 Git Bash 执行所有命令。PowerShell 也可以，但路径转换有时出问题。

---

## 第一步：克隆仓库

```bash
git clone https://github.com/non-contextual/user-action-analyzer-platform.git
cd user-action-analyzer-platform
```

✅ 成功标志：进入项目目录，能看到 `docker-compose.yml`。

---

## 第二步：准备数据

### 方式 A：Kaggle 真实数据（推荐，效果好）

1. 前往 [Kaggle 数据集页面](https://www.kaggle.com/datasets/mkechinov/ecommerce-behavior-data-from-multi-category-store) 下载 `2019-Oct.csv`（约 5.3 GB）
2. 将文件放入项目的 `data/` 目录：
   ```
   user-action-analyzer-platform/
   └── data/
       └── 2019-Oct.csv   ← 放这里
   ```
3. 确认 `spark-project/src/main/resources/config.properties` 中配置为：
   ```properties
   data.format=kaggle
   data.kaggle.sample=0.01   # 1% 采样，约 42 万行，运行时间约 1-2 分钟
   ```

### 方式 B：模拟数据（轻量，适合快速验证）

无需额外操作，容器启动后会自动生成。确认 `config.properties` 中：
```properties
data.format=generated
```

---

## 第三步：启动容器

```bash
# 在项目根目录执行
docker compose up -d --build
```

首次启动会拉取镜像并构建（约 3-5 分钟）。完成后验证：

```bash
docker ps
# 应看到三个容器：mysql、spark-master、spark-worker-1
```

等待 MySQL 健康检查通过（约 30 秒）：

```bash
docker exec mysql mysqladmin ping -h localhost -u root -proot123 --silent
```

✅ 成功标志：输出 `mysqld is alive`。

> **Docker Compose 服务名 vs 容器名**：`docker compose up/down/build` 使用**服务名** `spark-master`、`spark-worker`；`docker exec`、`docker logs` 使用**容器名** `spark-master`、`spark-worker-1`。

---

## 第四步：编译项目

```bash
docker exec spark-master bash /opt/scripts/build.sh
```

✅ 成功标志：最后输出 `=== 编译完成 ===` 以及 JAR 文件大小信息。

> 每次修改 Java 源码后都需要重新执行此命令。

---

## 第五步：运行 Spark 分析任务

使用 `submit_job.sh` 提交任务，用法：`submit_job.sh <JAR文件名> <taskId>`

```bash
# 任务 1：Session 聚合统计 + Top10 品类 + 页面单跳转化率（约 5-10 分钟）
docker exec spark-master bash /opt/scripts/submit_job.sh user-analyzer-1.0-SNAPSHOT.jar 1

# 任务 2：仅热门品类 Top10（约 2-3 分钟）
docker exec spark-master bash /opt/scripts/submit_job.sh user-analyzer-1.0-SNAPSHOT.jar 2

# 任务 3：随机抽取 1000 个 Session（约 2-3 分钟）
docker exec spark-master bash /opt/scripts/submit_job.sh user-analyzer-1.0-SNAPSHOT.jar 3
```

✅ 成功标志：日志末尾出现 `Job X finished` 且无 `ERROR` 字样。

> 运行期间可在 http://localhost:4040 实时查看 Spark 任务进度。

---

## 第六步：查看结果

### 命令行方式

```bash
# 连接 MySQL
docker exec -it mysql mysql -u spark -pspark123 user_action_db

# Session 统计（时长/步长分布）
SELECT session_count, visit_length_1s_3s, visit_length_10s_30s, step_length_1_3
FROM session_aggr_stat WHERE task_id=1;

# 热门品类 Top10（点击量排序）
SELECT category_id, click_count, order_count, pay_count
FROM top10_category WHERE task_id=1
ORDER BY click_count DESC LIMIT 10;

# 页面单跳转化率
SELECT page_flow, ROUND(convert_rate, 4) AS rate
FROM page_convert_rate WHERE task_id=1
ORDER BY page_flow;

# 随机抽取的 Session
SELECT session_id, start_time, end_time, click_category_ids
FROM session_random_extract WHERE task_id=3
LIMIT 10;
```

### DBeaver / Workbench 连接信息

| 字段 | 值 |
|------|-----|
| Host | `localhost` |
| Port | `3306` |
| Database | `user_action_db` |
| User | `spark` |
| Password | `spark123` |

---

## Web UI

| 页面 | 地址 |
|------|------|
| Spark Master | http://localhost:8080 |
| Spark Worker | http://localhost:8081 |
| Spark App UI（运行中） | http://localhost:4040 |

---

## 第七步：启动可视化后端（可选）

`backend/` 目录包含一个 Spring Boot 服务，读取 MySQL 中的分析结果并通过 REST API 对外提供数据，同时内嵌了可视化页面。

> 需要先完成第三步至第五步，MySQL 中有分析结果后页面才能正常展示数据。

### 前置要求

| 工具 | 最低版本 | 检查命令 |
|------|---------|---------|
| Java | 8 | `java -version` |
| Maven | 3.8 | `mvn -version` |

### 启动服务

```bash
cd backend
mvn spring-boot:run
```

✅ 成功标志：控制台出现 `Started UserActionBackendApplication`，端口 `8082`。

### 访问地址

| 页面 | 地址 |
|------|------|
| 可视化看板 | http://localhost:8082/api/ |
| Swagger API 文档 | http://localhost:8082/api/swagger-ui.html |

### 可用接口

| 接口 | 说明 | 图表类型 |
|------|------|---------|
| `GET /api/analytics/session/length-distribution` | Session 时长分布 | 折线图 |
| `GET /api/analytics/session/step-distribution` | Session 步长分布 | 柱状图 |
| `GET /api/analytics/session/summary` | Session 汇总占比 | 饼图 |
| `GET /api/analytics/category/top10` | 热门品类 Top10 | 柱状图 |
| `GET /api/analytics/page/conversion-rate` | 页面单跳转化率 | 折线图 |

所有接口均支持 `?taskId=` 参数（默认为 1），对应 Spark 分析时写入的任务 ID。

---

## 停止 & 清理

```bash
# 停止容器（保留数据）
docker compose stop

# 停止并删除容器（保留 MySQL 数据卷）
docker compose down

# 完全清理（删除所有数据，慎用）
docker compose down -v
```

### 重置旧环境（重新部署前推荐执行）

如果之前跑过本项目，再次启动前建议先清理干净，避免网络/卷冲突：

```bash
# 1. 删除本项目的容器、网络、数据卷
docker compose down -v

# 2. 清理残留的同名容器（报错可忽略）
docker rm -f spark-master spark-worker-1 mysql

# 3. 清理孤立网络（解决 subnet 冲突）
docker network prune -f

# 4. 重新启动
docker compose up -d --build
```

> 如果只报网络冲突但不想删数据卷，可跳过第 1 步，只执行第 2-3 步。

---

## 常见问题

### Q: `docker compose build` 报 `parent snapshot does not exist: not found`

Docker 本地构建缓存损坏。清理 builder 缓存后重建：

```bash
docker builder prune -f
docker compose build --no-cache spark-master spark-worker
```

如果仍然失败，执行更彻底的清理（会删除所有未使用的镜像和缓存）：

```bash
docker system prune -f
docker compose build --no-cache spark-master spark-worker
```

### Q: `docker compose up` 报 `network subnet overlaps with other one`

Docker 网络子网与已有网络冲突，通常是之前运行过本项目留下的残留网络。执行以下命令清理后重试：

```bash
docker compose down -v
docker network prune -f
docker compose up -d --build
```

### Q: `docker logs spark-master` 显示中文日志乱码（`????`）

镜像需要重建。新版 Dockerfile 已在构建时设置 UTF-8 环境变量，重建即可：

```bash
docker compose down
docker compose build --no-cache spark-master spark-worker
docker compose up -d
```

### Q: `WARN TaskSchedulerImpl: Initial job has not accepted any resources`

正常警告，不影响运行。任务会在几秒后继续。

### Q: 任务报 `ClassNotFoundException`

JAR 可能未编译或编译失败，重新执行第四步：

```bash
docker exec spark-master bash /opt/scripts/build.sh
```

### Q: 如何修改采样率

编辑 `spark-project/src/main/resources/config.properties`：

```properties
data.kaggle.sample=0.05   # 5% ≈ 2.1M 行，速度更快
data.kaggle.sample=1.0    # 全量 42M 行，需要更多内存
```

修改后需重新执行第四步编译部署 JAR。

### Q: MySQL 中文字符乱码

不要用 `docker exec mysql mysql -e "..."` 方式直接执行含中文的 SQL（Git Bash on Windows 会双重编码），改用 `-it` 进入交互式 shell 后再执行 SQL。

---

## 数据库表结构速查

| 表名 | 内容 | 写入任务 |
|------|------|---------|
| `task` | 任务参数（JSON） | 初始化时写入 |
| `session_aggr_stat` | Session 时长/步长分布 | task 1 (SESSION) |
| `top10_category` | 热门品类 Top10 | task 1, 2 |
| `page_convert_rate` | 页面单跳转化率 | task 1 (SESSION) |
| `session_random_extract` | 随机抽取 Session 摘要 | task 3 (RANDOM) |
| `session_detail` | 抽取 Session 行为明细 | task 3 (RANDOM) |

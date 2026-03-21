# 快速上手指南

> 给小组成员的部署 & 运行说明。从零开始约 15 分钟能跑通。

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

---

## 第二步：准备数据

### 方式 A：Kaggle 真实数据（推荐，效果好）

1. 前往 [Kaggle 数据集页面](https://www.kaggle.com/datasets/mkechinov/ecommerce-behavior-data-from-multi-category-store) 下载 `2019-Oct.csv`（约 5.3 GB）
2. 将文件放入项目的 `data/` 目录：
   ```
   UserActionAnalyzerPlatform/
   └── data/
       └── 2019-Oct.csv   ← 放这里
   ```
3. 确认 `spark-project/src/main/resources/config.properties` 中配置为：
   ```properties
   data.format=kaggle
   data.kaggle.sample=0.1    # 10% 采样，约 4.2M 行，运行时间约 5 分钟
   ```

### 方式 B：模拟数据（轻量，适合快速验证）

无需额外操作，容器启动后会自动生成。确认 `config.properties` 中：
```properties
data.format=generated
```

---

## 第三步：启动容器

```bash
# 在项目根目录执行（Git Bash / macOS Terminal）
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
# 输出 "mysqld is alive" 即可
```

---

## 第四步：编译项目

```bash
# 在 spark-master 容器内编译
MSYS_NO_PATHCONV=1 docker exec spark-master bash /opt/scripts/build.sh

# 将 JAR 部署到 spark-apps（重要！）
MSYS_NO_PATHCONV=1 docker exec spark-master bash -c \
  "cp /opt/spark-project/target/user-analyzer-1.0-SNAPSHOT.jar /opt/spark-apps/"
```

> ⚠️ **每次修改 Java 源码后都需要重新执行这两条命令**。

---

## 第五步：运行 Spark 分析任务

根据需要运行对应任务（使用 local 模式，无需 worker 可用）：

```bash
# 任务 1：Session 聚合统计 + Top10 品类 + 页面单跳转化率（运行时间约 5-10 min）
MSYS_NO_PATHCONV=1 docker exec spark-master bash -c \
  "/opt/spark/bin/spark-submit \
   --class com.useranalyzer.UserActionAnalyzerApp \
   --driver-memory 4g \
   /opt/spark-apps/user-analyzer-1.0-SNAPSHOT.jar 1"

# 任务 2：仅热门品类 Top10
MSYS_NO_PATHCONV=1 docker exec spark-master bash -c \
  "/opt/spark/bin/spark-submit \
   --class com.useranalyzer.UserActionAnalyzerApp \
   --driver-memory 4g \
   /opt/spark-apps/user-analyzer-1.0-SNAPSHOT.jar 2"

# 任务 3：随机抽取 1000 个 Session（运行时间约 2-3 min）
MSYS_NO_PATHCONV=1 docker exec spark-master bash -c \
  "/opt/spark/bin/spark-submit \
   --class com.useranalyzer.UserActionAnalyzerApp \
   --driver-memory 4g \
   /opt/spark-apps/user-analyzer-1.0-SNAPSHOT.jar 3"
```

---

## 第六步：查看结果

### 命令行方式

```bash
# 连接 MySQL
MSYS_NO_PATHCONV=1 docker exec -it mysql mysql -u spark -pspark123 user_action_db

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

## 停止 & 清理

```bash
# 停止容器（保留数据）
docker compose stop

# 停止并删除容器（保留 MySQL 数据卷）
docker compose down

# 完全清理（删除所有数据，慎用）
docker compose down -v
```

---

## 常见问题

### Q: `WARN TaskSchedulerImpl: Initial job has not accepted any resources`
Worker 资源暂时未分配给 local 模式作业，属于正常警告，不影响运行。任务会在几秒后继续。

### Q: Windows 下路径被转换（如 `/opt/data` → `C:/Program Files/Git/opt/data`）
在所有 `docker exec` 命令前加 `MSYS_NO_PATHCONV=1`，如本文档中所示。

### Q: 任务报 `ClassNotFoundException`
JAR 可能在复制过程中损坏。重新编译并复制：
```bash
MSYS_NO_PATHCONV=1 docker exec spark-master bash -c "cd /opt/spark-project && mvn clean package -DskipTests -q && cp target/user-analyzer-1.0-SNAPSHOT.jar /opt/spark-apps/"
```

### Q: MySQL 中文字符乱码（字段显示 `???`）
需要用 Python 写入含中文的 SQL 字段，不要用 `docker exec mysql -e "..."` 方式（Git Bash on Windows 会双重编码）。

### Q: 如何修改采样率
编辑 `spark-project/src/main/resources/config.properties`：
```properties
data.kaggle.sample=0.05   # 5% ≈ 2.1M 行，速度更快
data.kaggle.sample=1.0    # 全量 42M 行，需要更多内存
```
修改后需重新编译部署 JAR。

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

# 电商用户行为分析大数据平台

> 基于 Apache Spark 3.5 + MySQL 8.0 的电商用户行为离线分析系统，支持真实 Kaggle 数据集（4200万行）与模拟数据双模式。

## 项目简介

本项目是一个大数据工程实践课程项目，模拟工业级电商数据分析流水线：

- **数据接入**：支持 Kaggle 真实电商行为数据（2019-Oct.csv，42M 行）和 Python 生成的模拟数据
- **计算引擎**：Apache Spark 3.5.3（DataFrame / SparkSQL API，运行于 Docker 集群）
- **存储层**：MySQL 8.0，存储任务参数与分析结果
- **部署方式**：Docker Compose 三容器（spark-master + spark-worker + mysql）

## 技术栈

| 组件 | 版本 | 用途 |
|------|------|------|
| Java | 11 | 主开发语言 |
| Apache Spark | 3.5.3 | 大数据分析引擎 |
| MySQL | 8.0 | 任务参数 & 结果存储 |
| Maven | 3.x | 项目构建 |
| Docker Compose | 3.8 | 容器化部署 |
| Python | 3.10 | 模拟数据生成 |

## 分析功能

| 任务 ID | 类型 | 功能描述 | 结果表 |
|---------|------|----------|--------|
| 1 | SESSION | Session 聚合统计（时长/步长分布）+ Top10 品类 + 页面单跳转化率 | `session_aggr_stat`, `top10_category`, `page_convert_rate` |
| 2 | TOP10 | 热门品类 Top10（点击/下单/支付次数排序） | `top10_category` |
| 3 | RANDOM | 随机抽取 Session（附明细行为） | `session_random_extract`, `session_detail` |

## 项目结构

```
UserActionAnalyzerPlatform/
├── docker-compose.yml          # 三容器编排配置
├── Dockerfile                  # Spark 镜像（Ubuntu + Java 11 + Spark + Maven）
├── .env                        # 环境变量（数据库密码、Spark 配置）
├── start.sh                    # 一键启动脚本
├── data/                       # 数据目录（需自行放入 2019-Oct.csv）
├── init-sql/
│   └── init.sql                # MySQL 建表 + 初始任务参数
├── scripts/
│   ├── build.sh                # 容器内 Maven 编译脚本
│   ├── generate_data.py        # 模拟数据生成脚本
│   └── submit_job.sh           # Spark 作业提交脚本
├── spark-apps/                 # 编译后的 JAR（容器挂载）
└── spark-project/              # Maven 项目源码
    └── src/main/java/com/useranalyzer/
        ├── UserActionAnalyzerApp.java          # 主入口（按 task_type 路由）
        ├── constant/Constants.java             # 全局常量
        ├── domain/                             # 领域对象
        ├── spark/
        │   ├── session/
        │   │   ├── UserVisitSessionAnalyze.java    # Session 聚合统计
        │   │   └── RandomSessionExtract.java       # 随机抽取 Session
        │   ├── category/
        │   │   └── Top10CategoryAnalyze.java       # 热门品类 Top10
        │   └── page/
        │       └── PageConvertRate.java            # 页面单跳转化率
        └── util/
            ├── KaggleDataLoader.java           # Kaggle 数据加载 & 转换
            ├── JDBCHelper.java                 # MySQL JDBC 工具
            └── ParamUtils.java                 # 任务参数解析
```

## 验证结果（Kaggle 10% 采样）

| 指标 | 值 |
|------|----|
| 原始行为数据 | 4,246,484 条 |
| 合成用户数 | 1,389,941 |
| 过滤后 Session 数 | 62,405 |
| Top1 品类 | `electronics.smartphone`（点击 1,063,176 次） |
| 随机抽取 Session 数 | 1,000（from 2,790,949） |
| 页面 1→2 转化率 | 0.0084 |

## 快速开始

详见 [START.md](START.md)。

## 与计划书的主要变更

| 变更点 | 计划书 | 实际实现 |
|--------|--------|---------|
| 数据来源 | Python 模拟 CSV（5万行） | Kaggle 真实数据（4200万行），支持采样率 |
| 品类 ID | 数字 1-50（取模） | 真实英文品类名（`category_code`） |
| 步长定义 | 页面数 | 商品数（`page_id` 映射自 `product_id`） |
| 计算 API | RDD + Lambda | DataFrame / SparkSQL |
| 部署 | 本地运行 | Docker Compose 三容器集群 |

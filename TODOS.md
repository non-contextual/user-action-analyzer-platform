# 项目进度与变更跟踪

> 更新：2026-03-21 | 对照计划书（2026-03-14）

---

## 核心功能进度

| 功能 | 计划 | 实际状态 |
|------|------|---------|
| 环境搭建（Java/Spark/MySQL/Docker） | 第1周 | ✅ 完成 |
| Session 聚合统计（时长/步长分布） | 第2-3周 | ✅ 完成，已验证 |
| 热门品类 Top10 | 第4周 | ✅ 完成，已验证 |
| 随机抽取 Session | 第5周 | ✅ 完成，已验证 |
| 页面单跳转化率 | 第5周 | ✅ 完成，已验证 |
| Spring Boot 后端接口 | 第6周 | ⬜ 未开始 |
| ECharts 前端可视化 | 第6周 | ⬜ 未开始 |
| 答辩 PPT | 第7周 | ⬜ 未开始 |

---

## 与计划书的变更

### ✅ 已落地的变更

| 变更点 | 计划书原方案 | 实际实现 |
|--------|------------|---------|
| 数据来源 | Python 生成模拟 CSV（1000用户/5万行） | 新增 Kaggle 模式：2019-Oct.csv 真实数据（4200万行），支持采样率配置 |
| 用户画像来源 | user_info.csv 文件 | Kaggle 模式下基于 user_id 哈希在内存中合成（年龄/职业/城市/性别） |
| 步长定义 | 访问页面数 | 改为**访问商品数**（page_id 映射自 product_id） |
| 品类 ID | 数字 1-50（取模映射） | 改为真实英文品类名（直接使用 `category_code`，如 `electronics.smartphone`） |
| 数据读取方式 | `sc.textFile()` + RDD | 改为 `SparkSession.read().option("encoding","UTF-8").csv()`（解决中文乱码） |
| 计算 API | RDD + Lambda | 改为 DataFrame / SparkSQL（解决序列化异常） |
| 部署方式 | 本地直接运行 | Docker Compose 三容器（spark-master + spark-worker + mysql） |
| 任务路由 | 未区分任务类型 | 按 task_type（SESSION/TOP10/RANDOM）路由执行不同分析模块 |

### 📋 待计划书同步

- 第 3.2 节架构图已按 Kaggle 模式更新
- 第 5.1 节数据生成 → Kaggle 数据接入（`KaggleDataLoader.java`）
- 第 2.3 节非功能需求：数据量从"万级"改为"百万级（采样）/ 千万级（全量）"

---

## 当前验证结果

**Kaggle 模式（2019-Oct.csv，10% 采样）**

| 指标 | 值 |
|------|----|
| 原始行为数据 | 4,246,484 条 |
| 合成用户数 | 1,389,941 |
| 过滤后 Session 数（task 1） | 62,405 |
| Top1 品类 | electronics.smartphone（点击 1,063,176） |
| 随机抽取 Session 数（task 3） | 1,000（from 2,790,949 全量） |
| 页面转化率（1→2） | 0.0084 |

**模拟数据模式**

| 指标 | 值 |
|------|----|
| 行为数据 | 52,489 条 |
| 匹配 Session 数 | 212 |

---

## 运行方式

```bash
# 重新编译（新增功能后需要）
docker exec spark-master bash /opt/scripts/build.sh
docker exec spark-master bash -c "cp /opt/spark-project/target/user-analyzer-1.0-SNAPSHOT.jar /opt/spark-apps/"

# 任务 1：Session 聚合 + Top10 + 页面转化率
docker exec spark-master bash -c "/opt/spark/bin/spark-submit --class com.useranalyzer.UserActionAnalyzerApp --driver-memory 4g /opt/spark-apps/user-analyzer-1.0-SNAPSHOT.jar 1"

# 任务 2：热门品类 Top10
docker exec spark-master bash -c "/opt/spark/bin/spark-submit --class com.useranalyzer.UserActionAnalyzerApp --driver-memory 4g /opt/spark-apps/user-analyzer-1.0-SNAPSHOT.jar 2"

# 任务 3：随机抽取 Session
docker exec spark-master bash -c "/opt/spark/bin/spark-submit --class com.useranalyzer.UserActionAnalyzerApp --driver-memory 4g /opt/spark-apps/user-analyzer-1.0-SNAPSHOT.jar 3"
```

---

## 下一步

- [x] 随机抽取 Session ✅
- [x] 页面单跳转化率 ✅
- [ ] Spring Boot 接口 + ECharts 可视化
- [ ] 答辩 PPT

# Changelog

All notable changes to this project will be documented in this file.

## [0.2.0.0] - 2026-04-11

### Added
- **Spark/UserProfileAnalyze**: 用户行为分层分析（task 4, PROFILE 类型）。基于 kaggle_raw 真实事件数据计算每用户的购买次数、消费金额、加购次数、活跃天数，按 RFM 简化规则分为 VIP / 高价值 / 潜力 / 普通 / 沉默五级，汇总统计写入 `user_level_stat` 表（5行）
- **Spark/ProductAssociationAnalyze**: 商品关联规则挖掘（task 5, ASSOCIATION 类型）。用用户级品类购物篮（`collect_set(category_code)` where `event_type=purchase`）运行 Spark MLlib FP-Growth，Top-20 规则按置信度排序写入 `product_association` 表；篮子数量 < 10 时跳过保护
- **Spark/pom.xml**: 新增 `spark-mllib_2.12` 依赖（provided scope，不增大 fat jar）
- **Backend**: `UserLevelStat` + `ProductAssociation` JPA 实体（复合主键 `@IdClass`）
- **Backend**: `UserLevelStatRepository` + `ProductAssociationRepository`
- **Backend**: `AssociationRuleData` DTO（含嵌套 `RuleItem`：antecedent / consequent / support / confidence / lift）
- **Backend/AnalyticsController**: 新增两个 GET 接口——`/analytics/user/level-distribution?taskId=4` 和 `/analytics/association/rules?taskId=5`
- **Backend/AnalyticsService**: 新增 `getUserLevelStat` 和 `getAssociationRules` 方法，含 Caffeine 缓存
- **DB/init.sql**: 新增 `user_level_stat`、`product_association` 表定义，以及 task 4 / task 5 初始参数
- **Frontend**: 用户行为分层饼图（固定颜色映射，VIP=红）+ FP-Growth 关联规则表格（暗色表头，置信度/提升度/支持度）；两个接口无数据时显示友好占位提示

### Fixed
- **Backend/AnalyticsService**: `getSessionLengthDistribution` 会话时长折线图缺少"4-6秒"和"7-9秒"两个区间——xAxis 从 7 项补全为 9 项，序列数据同步对齐
- **Backend/LineChartData & BarChartData**: `xAxis` 字段经 Lombok getter 后被 Jackson 序列化为 `xaxis`（全小写），导致前端 `data.xAxis` 取值为 `undefined`，所有图表 x 轴显示数字序号而非实际标签。添加 `@JsonProperty("xAxis")` 修复
- **Frontend**: 用户分层饼图图例设置为 `orient: vertical, left: left`，在卡片高度有限时"沉默"等条目被截断不显示。改为 `horizontal + bottom + scroll` 滚动图例

### Changed
- **Spark/UserActionAnalyzerApp**: 新增 PROFILE 和 ASSOCIATION 两个 task_type 路由分支
- **Backend/AnalyticsService.clearCache**: evict 范围补充 `userLevelStats` 和 `associationRules`
- **Docs**: README.md、START.md、TODOS.md 同步更新，新增任务 4/5 说明、接口文档、DB 表结构

## [0.1.2.0] - 2026-03-28

### Performance
- **Spark/KaggleDataLoader**: 采样后立即 `.cache()`，9GB CSV 从被扫描 5-6 次降为 1 次，后续所有分析模块（uva、user_info、各任务）均从内存读取
- **Spark/Top10CategoryAnalyze**: 过滤后的 `uva_top10` 加 cache，点击/下单/支付三次统计查询不再重复扫描原始视图
- **Spark/UserVisitSessionAnalyze**: 移除两处纯日志用途的 `count()` 调用，减少 2 个冗余 Spark job
- **Spark/PageConvertRate**: `uva_page` 和 `page_transitions` 加 cache，N 对页面转化率查询从"每对重跑 LAG 窗口"变为内存查询
- **Spark/RandomSessionExtract**: 用 `orderBy(rand(42)).limit(N)` 替代 collect 全量 session ID 到 driver 再 shuffle，彻底消除 driver 侧 OOM 风险

### Fixed
- **Scripts**: `start.sh` 在提交 Spark 作业前新增清理步骤——`pkill` 容器内残留的 `SparkSubmit` / `UserActionAnalyzerApp` 进程，并轮询 Spark REST API 等待旧应用注销（最多 30s），彻底避免 Ctrl+C 中断后重启时双 driver 爆内存
- **Scripts**: `submit_job.sh` 同步加入上述清理逻辑，直接调用该脚本时同样受保护
- **Scripts**: `start.sh` spark-submit 恢复使用 `spark://spark-master:7077` 独立集群模式，executors 运行在 worker 节点而非 driver 进程内
- **Scripts**: `build.sh` Maven 构建参数由 `-q` 改为 `--batch-mode`，保留依赖下载进度输出，方便排查编译卡顿
- **Dockerfile**: Spark 基础镜像源切换为 `hub.rat.dev` 镜像站，解决国内网络环境下 DaoCloud TLS 超时问题

## [0.1.1.0] - 2026-03-24

### Fixed
- **Spark**: `Top10CategoryAnalyze` 不再覆盖共享 `uva` 视图，改用独立 `uva_top10` 视图，修复 SESSION 任务中 `PageConvertRate` 使用错误数据集的 bug
- **Backend**: 页面转化率精度丢失问题——原 `(int) Math.round(rate * 100)` 将 0.0084 截断为 1，改为返回保留两位小数的 Double 百分比（0.84）
- **Backend**: `AnalyticsController` 数据不存在时返回真实 HTTP 404，而非 HTTP 200 包裹错误码
- **Backend**: `GlobalExceptionHandler` 异常处理器顺序调整，避免子类异常被父类提前捕获
- **Scripts**: `submit_job.sh` 默认主类修正为 `com.useranalyzer.UserActionAnalyzerApp`（原引用不存在的类名）
- **Scripts**: `start.sh` driver memory 从 1g 提升至 4g；移除不存在的 task_id=4 引用

### Changed
- **Backend**: `LineChartData.series` 类型从 `List<Integer>` 改为 `List<? extends Number>`，支持转化率 Double 值
- **Spark**: 移除 `Top10CategoryAnalyze.analyze()` 中无用的 `userInfoPath` 参数
- **Spark**: 移除 `UserVisitSessionAnalyze` 中从未调用的死代码方法 `toSqlInListRaw`
- **Docs**: `START.md` 和 `TODOS.md` 移至 `docs/` 目录；`start.sh` 和 `entrypoint.sh` 统一收入 `scripts/` 目录
- **Docs**: 修正 `backend/README.md` 中 Spring Boot 版本（3.2.0 → 2.7.18）、Java 最低版本（17 → 8）、@Cacheable 说明层级错误
- **Docs**: 更新 `docs/START.md` CRLF 修复命令以反映脚本新路径

## [0.1.0.0] - 2026-03-24

### Added
- Spring Boot 后端服务 (`backend/`)，提供 5 个 REST API 接口，读取 MySQL 分析结果
- ECharts 可视化看板 (`/api/`)，展示 Session 时长/步长分布、热门品类 Top10、页面转化率、Session 汇总饼图
- Swagger UI API 文档 (`/api/swagger-ui.html`)
- Caffeine 缓存层（5 分钟 TTL，缓存所有分析接口响应）
- `backend/README.md` 详细说明后端架构与部署方式
- `START.md` 新增第七步：启动可视化后端

### Fixed
- `Top10Category.categoryId` 字段类型由 `Long` 改为 `String`，修复 Kaggle 品类数据（如 `electronics.smartphone`）加载时的 `NumberFormatException`，导致 `/category/top10` 接口 500 错误

### Changed
- `START.md` 简化 `docker exec` 命令格式，移除 `MSYS_NO_PATHCONV=1` 前缀，兼容 Git Bash 和 PowerShell
- `START.md` 补充 Docker 服务名 vs 容器名说明、CRLF 换行符修复指南、重置旧环境步骤
- `.gitignore` 新增 `backend/target/` 和 `backend/mvn_repo/` 排除规则
- `TODOS.md` 更新项目进度，标记后端与可视化任务完成

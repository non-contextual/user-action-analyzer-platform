# Changelog

All notable changes to this project will be documented in this file.

## [0.1.2.0] - 2026-03-28

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

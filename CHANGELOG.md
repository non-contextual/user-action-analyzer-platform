# Changelog

All notable changes to this project will be documented in this file.

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

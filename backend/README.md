# User Action Analytics Backend

基于 Spring Boot 3.2 的用户行为分析后端系统，支持 ECharts 可视化展示。

---

## 技术栈

- **Spring Boot**: 3.2.0
- **Java**: 8
- **Spring Data JPA**: 数据持久化
- **MySQL**: 8.0
- **Caffeine**: 缓存框架
- **Swagger/OpenAPI**: API 文档生成
- **Lombok**: 简化代码
- **JUnit 5**: 单元测试

---

## 项目结构

```
backend/                          # 后端项目根目录
├── src/                          # 源代码目录
│   ├── main/                     # 主代码目录
│   │   ├── java/                 # Java 源代码目录
│   │   │   └── com/useranalyzer/backend/  # 包路径
│   │   │       ├── UserActionBackendApplication.java  # 应用主类
│   │   │       ├── config/       # 配置类目录
│   │   │       │   └── SwaggerConfig.java  # Swagger 配置
│   │   │       ├── controller/   # 控制器目录
│   │   │       │   └── AnalyticsController.java  # 分析数据控制器
│   │   │       ├── dto/          # 数据传输对象目录
│   │   │       │   ├── ApiResponse.java  # API 响应格式
│   │   │       │   ├── LineChartData.java  # 折线图数据格式
│   │   │       │   ├── BarChartData.java  # 柱状图数据格式
│   │   │       │   └── PieChartData.java  # 饼图数据格式
│   │   │       ├── entity/       # 实体类目录
│   │   │       │   ├── SessionAggrStat.java  # 会话聚合统计实体
│   │   │       │   ├── Top10Category.java  # 热门品类 Top10 实体
│   │   │       │   └── PageConvertRate.java  # 页面转化率实体
│   │   │       ├── repository/   # 数据访问层目录
│   │   │       │   ├── SessionAggrStatRepository.java  # 会话聚合统计数据访问
│   │   │       │   ├── Top10CategoryRepository.java  # 热门品类 Top10 数据访问
│   │   │       │   └── PageConvertRateRepository.java  # 页面转化率数据访问
│   │   │       └── service/      # 业务逻辑层目录
│   │   │           └── AnalyticsService.java  # 分析服务
│   │   └── resources/            # 资源文件目录
│   │       ├── application.yml   # 应用配置文件
│   │       └── static/           # 静态资源目录
│   │           └── index.html    # 可视化界面
│   
├── pom.xml                       # Maven 项目配置文件
└── README.md                     # 项目说明文档
```

---

## 快速开始

### 前置要求

- JDK 8
- Maven 3.6+
- MySQL 8.0+（确保已运行 Spark 分析任务并生成数据）
- IDE（推荐 IntelliJ IDEA 或 Eclipse）

### 1. 克隆项目

```bash
cd user-action-analyzer-platform/backend
```

### 2. 配置数据库

编辑 `src/main/resources/application.yml`，确保数据库连接信息正确：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/user_action_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&characterEncoding=UTF-8
    username: spark
    password: spark123
```

### 3. 启动应用

确保已正确安装 JDK 8 和 Maven 3.6+ 环境
```bash
# 构建项目
mvn clean package -DskipTests
# 启动应用
mvn spring-boot:run
```

应用将在 `http://localhost:8082` 启动。


### 4. 访问 API 文档

打开浏览器访问：http://localhost:8082/api/swagger-ui.html

### 5. 查看可视化界面

打开浏览器访问：http://localhost:8082/api/index.html

---

## API 接口说明

### 1. 获取 Session 时长分布

**接口**: `GET /api/analytics/session/length-distribution`

**参数**:
- `taskId` (可选，默认: 1): 任务 ID

**响应格式**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "name": "Session Duration Distribution",
    "xAxis": ["1-3s", "10-30s", "30-60s", "1-3m", "3-10m", "10-30m", ">30m"],
    "series": [5, 1, 6, 27, 164, 9, 0]
  }
}
```

**ECharts 图表类型**: 折线图

---

### 2. 获取 Session 步长分布

**接口**: `GET /api/analytics/session/step-distribution`

**参数**:
- `taskId` (可选，默认: 1): 任务 ID

**响应格式**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "xAxis": ["1-3", "4-6", "7-9", "10-30", "30-60", ">60"],
    "series": [{
      "name": "Step Count",
      "data": [13, 38, 56, 105, 0, 0]
    }]
  }
}
```

**ECharts 图表类型**: 柱状图

---

### 3. 获取热门品类 Top10

**接口**: `GET /api/analytics/category/top10`

**参数**:
- `taskId` (可选，默认: 1): 任务 ID

**响应格式**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "xAxis": ["1", "43", "34", "16", "27", "35", "45", "24", "6"],
    "series": [
      {
        "name": "Clicks",
        "data": [567, 570, 560, 554, 551, 551, 550, 547, 547]
      },
      {
        "name": "Orders",
        "data": [306, 351, 322, 323, 299, 314, 331, 308, 300]
      },
      {
        "name": "Payments",
        "data": [79, 83, 85, 83, 92, 76, 73, 82, 88]
      }
    ]
  }
}
```

**ECharts 图表类型**: 多系列柱状图

---

### 4. 获取页面转化率

**接口**: `GET /api/analytics/page/conversion-rate`

**参数**:
- `taskId` (可选，默认: 1): 任务 ID

**响应格式**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "name": "Page Conversion Rate",
    "xAxis": ["1_2", "2_3", "3_4", "4_5", "5_6", "6_7"],
    "series": [5, 6, 5, 7, 6, 5]
  }
}
```

**ECharts 图表类型**: 折线图（带标记点）

---

### 5. 获取 Session 汇总

**接口**: `GET /api/analytics/session/summary`

**参数**:
- `taskId` (可选，默认: 1): 任务 ID

**响应格式**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "data": [
      {
        "name": "1-3s",
        "value": 5
      },
      {
        "name": "10-30s",
        "value": 1
      },
      {
        "name": "30-60s",
        "value": 6
      },
      {
        "name": "1-3m",
        "value": 27
      },
      {
        "name": "3-10m",
        "value": 164
      },
      {
        "name": "10-30m",
        "value": 9
      },
      {
        "name": ">30m",
        "value": 0
      }
    ]
  }
}
```

**ECharts 图表类型**: 饼图

---

### 6. 清除缓存

**接口**: `DELETE /api/analytics/cache/clear`

**响应格式**:
```json
{
  "code": 200,
  "message": "success",
  "data": "Cache cleared successfully"
}
```

---

## 性能优化

### 缓存机制

- 使用 Caffeine 缓存框架
- 缓存配置：最大 1000 条记录，5 分钟过期
- 缓存范围：
  - `sessionStats`: Session 统计数据
  - `top10Categories`: Top10 品类数据
  - `pageConvertRates`: 页面转化率数据

### 查询优化

- 使用 JPA 的 `@Cacheable` 注解自动缓存查询结果
- Repository 层方法使用自定义查询方法减少 N+1 查询
- 响应时间目标：< 300ms

---

## 错误处理

### 全局异常处理

- `GlobalExceptionHandler` 捕获所有未处理异常
- 统一错误响应格式：
  ```json
  {
    "code": 500,
    "message": "错误描述",
    "data": null
  }
  ```

### 错误码

| 错误码 | 说明 |
|--------|------|
| 200 | 成功 |
| 400 | 请求参数错误 |
| 404 | 数据不存在 |
| 500 | 服务器内部错误 |

---

## 测试

### 运行单元测试

```bash
mvn test
```

### 运行集成测试

```bash
mvn verify
```

### 测试覆盖率

- Service 层测试覆盖率目标：> 80%
- Controller 层测试覆盖率目标：> 90%

---

## 部署说明


#### 1. 构建 JAR 包

```bash
mvn clean package -DskipTests
```

#### 2. 运行 JAR

```bash
java -jar target/user-action-backend-1.0.0.jar
```

---

## 环境变量

| 变量名 | 说明 | 默认值 |
|--------|------|--------|
| `SERVER_PORT` | 服务端口 | 8082 |
| `SPRING_DATASOURCE_URL` | 数据库 URL | jdbc:mysql://localhost:3306/user_action_db |
| `SPRING_DATASOURCE_USERNAME` | 数据库用户名 | spark |
| `SPRING_DATASOURCE_PASSWORD` | 数据库密码 | spark123 |
| `SPRING_CACHE_TYPE` | 缓存类型 | caffeine |
| `SPRING_CACHE_CAFFEINE_SPEC` | 缓存配置 | maximumSize=1000,expireAfterWrite=5m |

---

## 常见问题

### Q: 数据库连接失败

**A**: 检查 MySQL 是否运行
```bash
docker ps | grep mysql
```

**B**: 检查数据库连接配置
```bash
# 确认 Spark 分析任务已执行并生成数据
docker exec mysql mysql -u spark -pspark123 user_action_db -e "SHOW TABLES;"
```

### Q: API 返回 404

**A**: 确认 taskId 参数正确
**B**: 检查数据库中是否存在对应任务数据

### Q: 缓存未生效

**A**: 检查 `@EnableCaching` 注解是否添加到主类
**B**: 确认 Repository 方法上有 `@Cacheable` 注解

### Q: Swagger UI 无法访问

**A**: 确认应用已启动
**B**: 检查端口是否被占用
```bash
netstat -ano | findstr :8082
```

---

## 开发指南

### 添加新的 API 接口

1. 在 `AnalyticsService` 中添加业务方法
2. 在 `AnalyticsController` 中添加 REST 端点
3. 在 `AnalyticsServiceTest` 中添加单元测试
4. 在 `AnalyticsControllerTest` 中添加集成测试
5. 更新 Swagger 注解

### 添加新的数据模型

1. 创建新的 Entity 类（继承 JPA 基类）
2. 创建对应的 Repository 接口
3. 在 Service 中实现业务逻辑
4. 在 Controller 中添加 API 端点

---

## 许可证

本项目仅供学习和演示使用。

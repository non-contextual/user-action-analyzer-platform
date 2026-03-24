#!/bin/bash
# ============================================================
# 电商用户行为分析大数据平台 - 一键启动 + 运行脚本
# 执行流程：启动容器 → 生成数据 → 编译 → 提交 Spark 作业
# ============================================================
set -e

TASK_ID="${1:-1}"
DATA_FORMAT="${2:-generated}"   # generated 或 kaggle
echo "============================================"
echo "  电商用户行为分析大数据平台"
echo "  taskId      = $TASK_ID"
echo "  data.format = $DATA_FORMAT"
echo "============================================"

# Kaggle 模式前置检查
if [ "$DATA_FORMAT" = "kaggle" ]; then
    if [ ! -f "./data/2019-Oct.csv" ] && [ ! -f "./data/2019-Nov.csv" ]; then
        echo "[ERROR] Kaggle 模式需要数据文件"
        echo "请从 Kaggle 下载 2019-Oct.csv 或 2019-Nov.csv 并放入 ./data/ 目录"
        echo "数据集: https://www.kaggle.com/datasets/mkechinov/ecommerce-behavior-data-from-multi-category-store"
        exit 1
    fi
    # 更新 config.properties 为 kaggle 模式
    KAGGLE_FILE=$(ls ./data/2019-*.csv 2>/dev/null | head -1 | xargs basename)
    echo "  Kaggle 文件 = $KAGGLE_FILE"
fi

# 1. 启动 Docker 服务
echo "[1/6] 启动 Docker 服务..."
docker compose up -d --build

# 2. 等待 MySQL 就绪
echo "[2/6] 等待 MySQL 启动..."
until docker exec mysql mysqladmin ping -h localhost -u root -proot123 --silent 2>/dev/null; do
    echo "  等待 MySQL..."
    sleep 3
done
echo "  MySQL 已就绪 ✓"

# 3. 等待 Spark Master 就绪
echo "[3/6] 等待 Spark Master 启动..."
until docker exec spark-master bash -c "curl -sf http://localhost:8080 > /dev/null 2>&1"; do
    echo "  等待 Spark Master..."
    sleep 3
done
echo "  Spark Master 已就绪 ✓"

# 4. 生成模拟数据（如果 CSV 不存在）
if [ ! -f "./data/user_info.csv" ]; then
    echo "[4/6] 生成模拟数据..."
    docker exec spark-master python3 /opt/scripts/generate_data.py
    echo "  数据生成完成 ✓"
else
    echo "[4/6] 数据已存在，跳过生成"
fi

# 5. 编译项目
echo "[5/6] 编译 Spark 项目..."
docker exec spark-master bash /opt/scripts/build.sh

# 6. 提交 Spark 作业
JAR_FILE=$(docker exec spark-master bash -c "ls /opt/spark-apps/*.jar 2>/dev/null | head -1")
if [ -z "$JAR_FILE" ]; then
    echo "[ERROR] 未找到 JAR 文件，编译失败"
    exit 1
fi
JAR_NAME=$(basename "$JAR_FILE")

echo ""
echo "============================================"
echo "[6/6] 提交 Spark 作业"
echo "  JAR: $JAR_NAME"
echo "  taskId: $TASK_ID"
echo "============================================"

docker exec spark-master \
    /opt/spark/bin/spark-submit \
    --master spark://spark-master:7077 \
    --deploy-mode client \
    --driver-memory 4g \
    --executor-memory 1g \
    --executor-cores 1 \
    --num-executors 1 \
    --class com.useranalyzer.UserActionAnalyzerApp \
    "/opt/spark-apps/$JAR_NAME" \
    "$TASK_ID"

echo ""
echo "============================================"
echo "  运行完成！"
echo "============================================"
echo "  查看结果:"
echo "    docker exec mysql mysql -u spark -pspark123 user_action_db"
echo "    SELECT * FROM session_aggr_stat WHERE task_id=$TASK_ID\\G"
echo "    SELECT * FROM top10_category WHERE task_id=$TASK_ID ORDER BY click_count DESC;"
echo ""
echo "  Web UI:"
echo "    Spark Master : http://localhost:8080"
echo "    Spark Worker : http://localhost:8081"
echo ""
echo "  Kaggle 模式运行方式:"
echo "    1. 下载 2019-Oct.csv 放入 ./data/ 目录"
echo "    2. 修改 config.properties: data.format=kaggle, data.kaggle.sample=0.1"
echo "    3. bash start.sh 2 kaggle   (task_id=2: 仅日期过滤 TOP10)"
echo "============================================"

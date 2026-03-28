#!/bin/bash
# ============================================================
# Spark 作业提交脚本
# 用法: ./submit_job.sh <jar文件名> <taskId>
# 示例: ./submit_job.sh user-analyzer-1.0-SNAPSHOT.jar 1
# ============================================================

set -e

JAR_NAME="${1:-user-analyzer-1.0-SNAPSHOT.jar}"
MAIN_CLASS="com.useranalyzer.UserActionAnalyzerApp"
TASK_ID="${2:-1}"
JAR_PATH="/opt/spark-apps/${JAR_NAME}"

if [ ! -f "$JAR_PATH" ]; then
    echo "[ERROR] JAR 文件不存在: $JAR_PATH"
    echo "请将编译好的 JAR 放入 ./spark-apps/ 目录"
    exit 1
fi

echo "=== 提交 Spark 作业 ==="
echo "  JAR: $JAR_PATH"
echo "  主类: $MAIN_CLASS"
echo "  任务ID: $TASK_ID"

${SPARK_HOME}/bin/spark-submit \
    --master local[*] \
    --driver-memory 4g \
    --conf "spark.driver.extraJavaOptions=-Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8" \
    --class "$MAIN_CLASS" \
    "$JAR_PATH" \
    "$TASK_ID"

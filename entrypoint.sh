#!/bin/bash
set -e

echo "=== Spark Mode: ${SPARK_MODE:-undefined} ==="
echo "=== Java Version ==="
java -version

case "$SPARK_MODE" in
  master)
    echo "Starting Spark Master..."
    exec ${SPARK_HOME}/bin/spark-class org.apache.spark.deploy.master.Master \
      --host "${SPARK_MASTER_HOST:-spark-master}" \
      --port "${SPARK_MASTER_PORT:-7077}" \
      --webui-port "${SPARK_MASTER_WEBUI_PORT:-8080}"
    ;;
  worker)
    MASTER_HOST="${SPARK_MASTER_HOST:-spark-master}"
    MASTER_PORT="${SPARK_MASTER_PORT:-7077}"
    echo "Waiting for Spark Master at ${MASTER_HOST}:${MASTER_PORT}..."
    until nc -z "$MASTER_HOST" "$MASTER_PORT" 2>/dev/null; do
      echo "  spark-master not ready, retrying in 2s..."
      sleep 2
    done
    echo "Spark Master is up. Starting Spark Worker..."
    exec ${SPARK_HOME}/bin/spark-class org.apache.spark.deploy.worker.Worker \
      --webui-port "${SPARK_WORKER_WEBUI_PORT:-8081}" \
      "${SPARK_MASTER_URL:-spark://spark-master:7077}"
    ;;
  history)
    echo "Starting Spark History Server..."
    mkdir -p /opt/spark-events
    exec ${SPARK_HOME}/bin/spark-class org.apache.spark.deploy.history.HistoryServer
    ;;
  *)
    echo "SPARK_MODE not set or unknown, executing: $@"
    exec "$@"
    ;;
esac

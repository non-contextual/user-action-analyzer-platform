#!/usr/bin/env bash
# Spark 环境变量配置

export JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64
export PYSPARK_PYTHON=python3

export SPARK_MASTER_HOST=spark-master
export SPARK_MASTER_PORT=7077
export SPARK_MASTER_WEBUI_PORT=8080

export SPARK_WORKER_MEMORY=2g
export SPARK_WORKER_CORES=2
export SPARK_WORKER_WEBUI_PORT=8081

export SPARK_LOG_DIR=/opt/logs
export SPARK_PID_DIR=/opt/spark/pid

export SPARK_LOCAL_DIRS=/tmp/spark-local

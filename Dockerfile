FROM ubuntu:22.04

ARG DEBIAN_FRONTEND=noninteractive
ENV TZ=Asia/Shanghai

# 安装基础依赖
RUN apt-get update && apt-get install -y \
    openjdk-11-jdk \
    python3 \
    python3-pip \
    maven \
    git \
    wget \
    curl \
    netcat-openbsd \
    procps \
    vim \
    && rm -rf /var/lib/apt/lists/*

# Java 环境变量
ENV JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64
ENV PATH=$PATH:$JAVA_HOME/bin

# 安装 Spark 3.5.3
ENV SPARK_VERSION=3.5.3
ENV SPARK_HOME=/opt/spark
ENV PATH=$PATH:$SPARK_HOME/bin:$SPARK_HOME/sbin
ENV PYSPARK_PYTHON=python3

RUN wget -q "https://archive.apache.org/dist/spark/spark-${SPARK_VERSION}/spark-${SPARK_VERSION}-bin-hadoop3.tgz" \
    -O /tmp/spark.tgz \
    && tar -xzf /tmp/spark.tgz -C /opt/ \
    && mv /opt/spark-${SPARK_VERSION}-bin-hadoop3 ${SPARK_HOME} \
    && rm /tmp/spark.tgz

# 下载 MySQL Connector/J (Spark 写入 MySQL 所需)
RUN wget -q "https://repo1.maven.org/maven2/com/mysql/mysql-connector-j/8.0.33/mysql-connector-j-8.0.33.jar" \
    -O ${SPARK_HOME}/jars/mysql-connector-j-8.0.33.jar

# 安装 Python 依赖
RUN pip3 install --no-cache-dir \
    faker==20.1.0 \
    pandas==2.1.4 \
    numpy==1.26.3 \
    mysql-connector-python==8.0.33

# 创建工作目录
RUN mkdir -p /opt/data /opt/spark-apps /opt/scripts /opt/logs /opt/spark/pid

# 复制配置文件
COPY conf/spark-defaults.conf ${SPARK_HOME}/conf/spark-defaults.conf
COPY conf/spark-env.sh ${SPARK_HOME}/conf/spark-env.sh

# 复制脚本
COPY scripts/ /opt/scripts/
COPY scripts/entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh \
    && find /opt/scripts -name "*.sh" -exec chmod +x {} \;

WORKDIR /opt/spark

EXPOSE 8080 7077 8081 4040 18080

ENTRYPOINT ["/entrypoint.sh"]

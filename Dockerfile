# 直接用官方 Spark 镜像：JDK 11 + Spark 3.5.3 + Python 3 全部内置
# 省去 apt 装 JDK（~10min）和 wget 下载 Spark（~15min）
FROM apache/spark:3.5.3-scala2.12-java11-python3-ubuntu

ARG DEBIAN_FRONTEND=noninteractive
ENV TZ=Asia/Shanghai

USER root

# 切换 apt 源为清华镜像，加速国内构建
RUN sed -i 's|http://archive.ubuntu.com/ubuntu|https://mirrors.tuna.tsinghua.edu.cn/ubuntu|g' /etc/apt/sources.list \
    && sed -i 's|http://security.ubuntu.com/ubuntu|https://mirrors.tuna.tsinghua.edu.cn/ubuntu|g' /etc/apt/sources.list

# 只装项目额外需要的工具，包数量从 290 降到 ~20
RUN apt-get update && apt-get install -y \
    maven \
    git \
    curl \
    netcat-openbsd \
    procps \
    vim \
    locales \
    && locale-gen en_US.UTF-8 \
    && rm -rf /var/lib/apt/lists/*

# Java 环境变量（官方镜像已设置 JAVA_HOME，这里保持兼容）
ENV JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64
ENV PATH=$PATH:$JAVA_HOME/bin
# 强制所有 JVM 进程使用 UTF-8，解决中文日志乱码
ENV JAVA_TOOL_OPTIONS="-Dfile.encoding=UTF-8"
ENV LANG=en_US.UTF-8
ENV LC_ALL=en_US.UTF-8

# Spark 路径（官方镜像已设置）
ENV SPARK_HOME=/opt/spark
ENV PATH=$PATH:$SPARK_HOME/bin:$SPARK_HOME/sbin
ENV PYSPARK_PYTHON=python3

# 下载 MySQL Connector/J，优先用阿里云 Maven 镜像，失败则回退官方源
RUN wget -q "https://maven.aliyun.com/repository/central/com/mysql/mysql-connector-j/8.0.33/mysql-connector-j-8.0.33.jar" \
    -O ${SPARK_HOME}/jars/mysql-connector-j-8.0.33.jar \
    || wget -q "https://repo1.maven.org/maven2/com/mysql/mysql-connector-j/8.0.33/mysql-connector-j-8.0.33.jar" \
    -O ${SPARK_HOME}/jars/mysql-connector-j-8.0.33.jar

# 安装 Python 依赖，使用清华 pip 镜像
RUN pip3 install --no-cache-dir \
    -i https://pypi.tuna.tsinghua.edu.cn/simple \
    --trusted-host pypi.tuna.tsinghua.edu.cn \
    faker==20.1.0 \
    pandas==2.0.3 \
    numpy==1.24.4 \
    mysql-connector-python==8.0.33

# 创建工作目录
RUN mkdir -p /opt/data /opt/spark-apps /opt/scripts /opt/logs /opt/spark/pid

# 复制配置文件
COPY conf/spark-defaults.conf ${SPARK_HOME}/conf/spark-defaults.conf
COPY conf/spark-env.sh ${SPARK_HOME}/conf/spark-env.sh

# 复制脚本
COPY scripts/ /opt/scripts/
COPY scripts/entrypoint.sh /entrypoint.sh
RUN sed -i 's/\r//' /entrypoint.sh \
    && find /opt/scripts -name "*.sh" -exec sed -i 's/\r//' {} \; \
    && chmod +x /entrypoint.sh \
    && find /opt/scripts -name "*.sh" -exec chmod +x {} \;

WORKDIR /opt/spark

EXPOSE 8080 7077 8081 4040 18080

ENTRYPOINT ["/entrypoint.sh"]

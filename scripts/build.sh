#!/bin/bash
# ============================================================
# 在 Docker 容器内编译 Spark 项目
# 用法（在宿主机执行）:
#   docker exec spark-master bash /opt/scripts/build.sh
# ============================================================
set -e

PROJECT_DIR="/opt/spark-project"
OUTPUT_DIR="/opt/spark-apps"

if [ ! -d "$PROJECT_DIR" ]; then
    echo "[ERROR] 项目目录不存在: $PROJECT_DIR"
    echo "请确认 docker-compose.yml 已挂载 ./spark-project:/opt/spark-project"
    exit 1
fi

echo "=== 开始编译 Maven 项目 ==="
echo "项目路径: $PROJECT_DIR"

cd "$PROJECT_DIR"

# 显示 Java 和 Maven 版本
java -version
mvn -version

# 写入阿里云 Maven 镜像配置（加速国内依赖下载）
MAVEN_SETTINGS=$(mktemp /tmp/maven-settings-XXXXXX.xml)
cat > "$MAVEN_SETTINGS" <<'SETTINGS'
<settings>
  <mirrors>
    <mirror>
      <id>aliyun</id>
      <mirrorOf>central</mirrorOf>
      <name>阿里云 Maven 镜像</name>
      <url>https://maven.aliyun.com/repository/central</url>
    </mirror>
  </mirrors>
</settings>
SETTINGS

# 编译并打包（跳过测试以加速）
mvn clean package -DskipTests -q -s "$MAVEN_SETTINGS"
rm -f "$MAVEN_SETTINGS"

# 找到编译后的 JAR（shade 插件生成的版本）
JAR=$(find target -name "*.jar" ! -name "*original*" | head -1)
if [ -z "$JAR" ]; then
    echo "[ERROR] 未找到编译后的 JAR 文件"
    exit 1
fi

echo "编译成功: $JAR"

# 复制到 spark-apps 目录
cp "$JAR" "$OUTPUT_DIR/"
echo "JAR 已复制到: $OUTPUT_DIR/$(basename $JAR)"
echo "=== 编译完成 ==="
ls -lh "$OUTPUT_DIR/"

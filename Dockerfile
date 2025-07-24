# 使用官方的 ARM64 OpenJDK 21 镜像作为基础镜像（适配 Mac M 系列芯片）
FROM --platform=linux/arm64 openjdk:21-jdk-slim

# 设置维护者信息
LABEL maintainer="sunyuan@word2pdf.com"
LABEL description="Word to PDF conversion service with LibreOffice"
LABEL version="1.0.1"

# 设置时区为中国时间
ENV TZ=Asia/Shanghai
ENV DEBIAN_FRONTEND=noninteractive
ENV LANG=C.UTF-8
ENV LC_ALL=C.UTF-8

# 配置 apt 超时和重试设置
RUN echo 'Acquire::http::Timeout "120";' > /etc/apt/apt.conf.d/99timeout && \
    echo 'Acquire::ftp::Timeout "120";' >> /etc/apt/apt.conf.d/99timeout && \
    echo 'Acquire::Retries "3";' >> /etc/apt/apt.conf.d/99timeout && \
    echo 'APT::Install-Recommends "false";' >> /etc/apt/apt.conf.d/99timeout && \
    echo 'APT::Install-Suggests "false";' >> /etc/apt/apt.conf.d/99timeout

# 切换到阿里云镜像源以加速下载
RUN sed -i 's/deb.debian.org/mirrors.aliyun.com/g' /etc/apt/sources.list.d/debian.sources

# 更新包列表并一次性安装所有依赖项，以优化缓存并解决中文乱码问题
RUN apt-get update -qq && \
    apt-get install -y --no-install-recommends \
    # 基础工具
    wget \
    curl \
    ca-certificates \
    # 虚拟显示器
    xvfb \
    # X11 和图形库 (LibreOffice 依赖)
    libx11-6 \
    libxrender1 \
    libxext6 \
    libxtst6 \
    libxi6 \
    libxss1 \
    libasound2 \
    libpangocairo-1.0-0 \
    libatk1.0-0 \
    libcairo-gobject2 \
    libgtk-3-0 \
    libgdk-pixbuf2.0-0 \
    # 字体工具和基础字体
    fontconfig \
    fonts-liberation \
    fonts-dejavu-core \
    # --- 中文字体 (解决乱码的关键) ---
    fonts-wqy-microhei \
    fonts-wqy-zenhei \
    fonts-noto-cjk \
    fonts-arphic-ukai \
    fonts-arphic-uming \
    # --- LibreOffice ---
    libreoffice-core \
    libreoffice-writer \
    libreoffice-common \
    libreoffice-java-common \
    # 清理APT缓存，减小镜像体积
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

# 更新字体缓存
RUN fc-cache -fv

# 创建应用用户
RUN groupadd -r appuser && useradd -r -g appuser appuser

# 下载 Arthas
RUN mkdir /arthas && \
    wget -O /arthas/arthas-boot.jar https://arthas.aliyun.com/arthas-boot.jar

# 创建应用目录
WORKDIR /app

# 创建必要的目录
RUN mkdir -p /app/logs \
    && mkdir -p /app/result \
    && mkdir -p /home/appuser/.config/libreoffice/4/user \
    && mkdir -p /tmp/.X11-unix \
    && chmod 1777 /tmp/.X11-unix \
    && chown -R appuser:appuser /app \
    && chown -R appuser:appuser /home/appuser

# 复制 Maven 构建文件（分层缓存优化）
COPY pom.xml ./
COPY mvnw ./
COPY .mvn ./.mvn

# 下载依赖（只有当pom.xml变化时才重新下载）
RUN chmod +x ./mvnw && ./mvnw dependency:go-offline -B

# 复制源代码（只有代码变化时才重新构建）
COPY src ./src

# 构建应用
RUN ./mvnw clean package -DskipTests -B

# 复制构建好的 JAR 文件
RUN cp target/word2pdf-*.jar app.jar

# 切换到应用用户
USER appuser

# 设置 DISPLAY 环境变量
ENV DISPLAY=:99

# 创建启动脚本
RUN echo '#!/bin/bash' > /app/start.sh && \
    echo 'export DISPLAY=:99' >> /app/start.sh && \
    echo 'Xvfb :99 -screen 0 1024x768x24 -ac +extension GLX +render -noreset &' >> /app/start.sh && \
    echo 'sleep 3' >> /app/start.sh && \
    echo 'exec java $JAVA_OPTS -jar app.jar --spring.config.additional-location=file:/app/application-docker.properties' >> /app/start.sh && \
    chmod +x /app/start.sh

# 切换回 root 设置权限
USER root
RUN chown -R appuser:appuser /app && chmod +x /app/start.sh

RUN mkdir -p /temp/libreoffice && chmod 777 /temp/libreoffice

# 切换到应用用户
USER appuser

# 暴露端口
EXPOSE 8080

# 启动应用
CMD ["/app/start.sh"] 
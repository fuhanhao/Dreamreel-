FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
# 使用阿里云 Maven 镜像加速依赖下载
RUN mkdir -p /root/.m2 && echo '<?xml version="1.0" encoding="UTF-8"?><settings><mirrors><mirror><id>aliyun</id><mirrorOf>central</mirrorOf><url>https://maven.aliyun.com/repository/central</url></mirror></mirrors></settings>' > /root/.m2/settings.xml
COPY pom.xml .
COPY src ./src
RUN mvn -q package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
# 使用阿里云镜像源加速 apk 下载
RUN sed -i 's/dl-cdn.alpinelinux.org/mirrors.aliyun.com/g' /etc/apk/repositories
# ffmpeg 烧录中文硬字幕需要 CJK 字体
RUN apk add --no-cache ffmpeg font-noto-cjk fontconfig
RUN addgroup -S app && adduser -S app -G app
COPY --from=build /app/target/*.jar app.jar
RUN mkdir -p /app/data/media /app/data/uploads && chown -R app:app /app
USER app
EXPOSE 7051
ENTRYPOINT ["java", "-jar", "app.jar"]

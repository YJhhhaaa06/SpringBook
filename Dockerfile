# syntax=docker/dockerfile:1
# 多阶段构建：Maven 编译 → JRE 运行。前端静态资源已打进 jar，容器只暴露 8080。

FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /build

# 先单独拷 pom 拉依赖，源码改动时不必重下依赖（利用层缓存）
COPY pom.xml .
RUN mvn -B -q -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:25-jre
WORKDIR /app

# 非 root 运行
RUN groupadd -r spring && useradd -r -g spring -d /app spring
COPY --from=build /build/target/SpringBook-0.0.1-SNAPSHOT.jar /app/app.jar
USER spring

EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]

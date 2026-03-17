# syntax=docker/dockerfile:1

FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app

COPY pom.xml ./
COPY .mvn ./.mvn
COPY mvnw mvnw
COPY mvnw.cmd mvnw.cmd
RUN chmod +x mvnw

# Evita logs silenciosos en CI y reutiliza cache Maven para acelerar deploys.
RUN --mount=type=cache,target=/root/.m2 \
	./mvnw -B -ntp -DskipTests -Dmaven.wagon.http.retryHandler.count=3 dependency:go-offline

COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
	./mvnw -B -ntp -DskipTests -Dmaven.wagon.http.retryHandler.count=3 clean package -Dspring-boot.repackage.skip=false

FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /app/target/backend-0.0.1-SNAPSHOT.jar app.jar

ENV JAVA_OPTS=""
EXPOSE 8080

CMD ["sh", "-c", "java $JAVA_OPTS -Dserver.port=${PORT:-8080} -jar /app/app.jar"]

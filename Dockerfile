FROM gradle:8.7-jdk17 AS builder

ENV GRADLE_OPTS="-Dfile.encoding=UTF-8 -Dconsole.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8"
ENV JAVA_TOOL_OPTIONS="-Dfile.encoding=UTF8"

WORKDIR /app

COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .

COPY src/main/proto src/main/proto

COPY src src
COPY web web

RUN chmod +x gradlew

RUN ./gradlew generateProto --no-daemon

RUN ./gradlew build --no-daemon -x test

RUN find /app/build -name "*.jar" -type f | xargs ls -la
RUN jar tf /app/build/libs/*.jar 2>/dev/null | head -20 || true

FROM eclipse-temurin:17-jre-alpine

ENV JAVA_OPTS="-Dfile.encoding=UTF-8 -Dconsole.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8"
ENV LANG=C.UTF-8
ENV LC_ALL=C.UTF-8

WORKDIR /app

RUN apk add --no-cache curl

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

RUN mkdir -p /run/secrets && chown appuser:appgroup /run/secrets

COPY --from=builder /app/build/libs/*.jar app.jar

USER appuser

EXPOSE 8090 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
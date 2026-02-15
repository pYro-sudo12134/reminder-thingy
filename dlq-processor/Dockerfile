FROM eclipse-temurin:17-jdk-alpine AS builder

WORKDIR /app

COPY gradlew .
COPY gradlew.bat .
COPY gradle gradle
COPY build.gradle .

RUN ./gradlew dependencies --no-daemon

COPY src src

RUN ./gradlew clean shadowJar -x test --no-daemon

FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

RUN apk add --no-cache curl

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

COPY --from=builder /app/build/libs/*-all.jar app.jar

USER appuser

EXPOSE 8085

ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-XX:+ExitOnOutOfMemoryError", \
    "-Dfile.encoding=UTF-8", \
    "-Dconsole.encoding=UTF-8", \
    "-Dsun.jnu.encoding=UTF-8", \
    "-jar", "app.jar"]
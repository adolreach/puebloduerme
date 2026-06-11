# ─── Stage 1: Build ──────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /app

COPY gradlew gradlew.bat ./
COPY gradle/ gradle/
COPY settings.gradle.kts build.gradle.kts gradle.properties ./
COPY engine/ engine/
COPY protocol/ protocol/
COPY host/ host/
COPY server/ server/

RUN chmod +x gradlew && ./gradlew :server:installDist --no-daemon -x test

# ─── Stage 2: Runtime ────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=builder /app/server/build/install/server/ /app/

EXPOSE 8080

ENV PORT=8080
ENV JAVA_OPTS="-Xmx256m -Xms128m"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -cp '/app/lib/*' com.puebloduerme.server.ApplicationKt"]

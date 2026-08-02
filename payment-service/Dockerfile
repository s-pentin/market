FROM eclipse-temurin:21-jdk AS builder
WORKDIR /app

COPY gradlew .
COPY gradle gradle
COPY settings.gradle.kts build.gradle.kts ./
COPY market-app/build.gradle.kts market-app/build.gradle.kts
COPY payment-service/build.gradle.kts payment-service/build.gradle.kts
COPY api-spec api-spec

RUN chmod +x ./gradlew
RUN ./gradlew :payment-service:dependencies --no-daemon

COPY payment-service/src payment-service/src

RUN ./gradlew :payment-service:bootJar --no-daemon

# ====================== Runtime ======================
FROM eclipse-temurin:21-jre

WORKDIR /app

RUN groupadd --system appuser && useradd --system --gid appuser appuser
USER appuser

COPY --from=builder /app/payment-service/build/libs/*.jar app.jar

EXPOSE 8082

ENTRYPOINT ["java", "-jar", "app.jar"]
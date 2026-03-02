# ---- Stage 1: Build ----
FROM maven:3.9.6-eclipse-temurin-17 AS builder

WORKDIR /app

# Copy pom.xml first for dependency caching
COPY pom.xml .
RUN mvn dependency:go-offline -q

# Copy source and build
COPY src ./src
RUN mvn package -DskipTests -q

# ---- Stage 2: Runtime (distroless Java 17) ----
FROM gcr.io/distroless/java17-debian12:nonroot

WORKDIR /app

# Copy the fat jar from builder stage
COPY --from=builder /app/target/payment-app-1.0.0.jar app.jar

# nonroot user is already set by the distroless base image (uid=65532)
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
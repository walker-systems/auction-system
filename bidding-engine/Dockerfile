# Build Stage
FROM maven:3-eclipse-temurin-25 AS builder
WORKDIR /app
COPY . .
RUN mvn clean package -pl bidding-engine -am -DskipTests

# Run Stage
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=builder /app/bidding-engine/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

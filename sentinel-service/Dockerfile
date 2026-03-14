# Build Stage
FROM maven:3-eclipse-temurin-25 AS builder
WORKDIR /app
COPY . .
RUN mvn clean package -pl sentinel-service -am -DskipTests

# Run Stage
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=builder /app/sentinel-service/target/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]

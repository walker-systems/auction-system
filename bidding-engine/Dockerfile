FROM eclipse-temurin:25-jre
WORKDIR /app
COPY bidding-engine/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-Xmx256m", "-Xms256m", "-jar", "app.jar"]

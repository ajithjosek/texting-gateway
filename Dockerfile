FROM eclipse-temurin:21-jre AS run
WORKDIR /app
COPY target/etg-app.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

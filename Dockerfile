FROM eclipse-temurin:21-jre-alpine
ARG JAR_FILE=build/libs/*-SNAPSHOT.jar
COPY ${JAR_FILE} /app.jar
USER 10001:10001
ENTRYPOINT ["java", "-jar", "/app.jar"]

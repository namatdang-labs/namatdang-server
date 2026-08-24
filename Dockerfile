FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache libwebp-tools
RUN cwebp -version >/dev/null
ARG JAR_FILE=build/libs/*-SNAPSHOT.jar
COPY ${JAR_FILE} /app.jar
USER 10001:10001
ENTRYPOINT ["java", "-jar", "/app.jar"]

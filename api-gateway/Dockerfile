FROM maven:3.9-eclipse-temurin-21 AS api-gateway-build
WORKDIR /api-gateway
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -Dmaven.test.skip=true -B

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S innowise && adduser -S valery -G innowise
COPY --from=api-gateway-build /api-gateway/target/*.jar app.jar
USER valery
EXPOSE 8091
ENTRYPOINT ["java", "-jar", "app.jar"]
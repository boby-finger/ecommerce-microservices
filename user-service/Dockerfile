FROM maven:3.9-eclipse-temurin-21 AS user-service-build
WORKDIR /user-service
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests -B

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S innowise && adduser -S valery -G innowise
COPY --from=user-service-build /user-service/target/*.jar app.jar
USER valery
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
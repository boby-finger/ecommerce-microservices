FROM maven:3.9-eclipse-temurin-21 AS auth-service-build
WORKDIR /authentication-service
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests -B

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S innowise && adduser -S valery -G innowise
COPY --from=auth-service-build /authentication-service/target/*.jar app.jar
USER valery
EXPOSE 8002
ENTRYPOINT ["java", "-jar", "app.jar"]
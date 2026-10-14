FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml designloop-backend
COPY src designloop-backend/src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
RUN useradd --system --create-home --uid 10001 appuser
COPY --from=build /build/target/designloop-backend-1.0.0.jar /app/app.jar
RUN mkdir -p /app/data/uploads && chown -R appuser:appuser /app/data
USER appuser
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]

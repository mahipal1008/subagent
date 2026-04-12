# Stage 1: build
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -q
COPY src/ src/
RUN mvn package -DskipTests -q

# Stage 2: run
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
COPY data/ /app/data-seed/
RUN mkdir -p /app/data
EXPOSE 8080
ENTRYPOINT ["java", "-Xmx1536m", "-jar", "app.jar"]

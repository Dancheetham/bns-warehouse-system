FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# postgres client tools (pg_dump/psql) for the Backup & Restore feature - see
# BackupService.java. Tries to match the postgres:17 image used in
# docker-compose.yml first (pg_dump generally can't dump FROM a server newer
# than itself, so getting the newest available client matters more than
# getting an exact match), falling back to whatever this Alpine base actually
# has available if the 17/16-specific package isn't in its repo yet.
RUN apk add --no-cache postgresql17-client \
    || apk add --no-cache postgresql16-client \
    || apk add --no-cache postgresql-client

COPY --from=build /app/target/warehouse-api.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

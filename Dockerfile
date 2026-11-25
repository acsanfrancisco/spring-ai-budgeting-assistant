FROM eclipse-temurin:25-jdk AS build
WORKDIR /build
COPY .mvn .mvn
COPY --chmod=755 mvnw ./
COPY pom.xml ./
COPY src/main src/main
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -Dmaven.test.skip=true package && cp target/*.jar /build/app.jar

FROM eclipse-temurin:25-jre
WORKDIR /app
RUN useradd --create-home --user-group budgeting
COPY --from=build /build/app.jar app.jar
USER budgeting
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

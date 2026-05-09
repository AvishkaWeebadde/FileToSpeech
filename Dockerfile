FROM maven:3.8.7-eclipse-temurin-17 AS build

WORKDIR /pdf-reader

COPY pom.xml .
RUN mvn dependency:go-offline

COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jdk-jammy

WORKDIR /pdf-reader

COPY --from=build /pdf-reader/target/aw.reader-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]

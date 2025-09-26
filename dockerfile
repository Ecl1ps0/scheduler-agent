FROM maven:3.9.8-eclipse-temurin-17 AS builder

WORKDIR /app

# Copy pom.xml
COPY pom.xml .

# Copy jade.jar into container
COPY ./lib/jade.jar /app/lib/jade.jar

# Install jade.jar into Maven local repo
RUN mvn install:install-file \
    -Dfile=./lib/jade.jar \
    -DgroupId=jade \
    -DartifactId=jade \
    -Dversion=4.5.0 \
    -Dpackaging=jar

# Download other dependencies (caching)
RUN mvn dependency:go-offline -B

# Copy source
COPY src ./src

# Build jar
RUN mvn package -DskipTests

FROM eclipse-temurin:17-jdk
WORKDIR /app

COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8080 1099

ENTRYPOINT ["java", "-jar", "app.jar"]
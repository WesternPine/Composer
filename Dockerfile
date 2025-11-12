# Build
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
COPY . .
RUN mvn -B clean package -DskipTests

# Runtime
FROM eclipse-temurin:17-jre
WORKDIR /opt/composer
COPY --from=build /app/target/composer-*-shaded.jar app.jar
CMD ["java","-Xms256m","-Xmx512m","-jar","/opt/composer/app.jar"]

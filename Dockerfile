FROM eclipse-temurin:21-jdk AS build
ARG MODULE
WORKDIR /src
COPY . .
RUN ./gradlew :${MODULE}:bootJar --no-daemon

FROM eclipse-temurin:21-jre
ARG MODULE
COPY --from=build /src/${MODULE}/build/libs/${MODULE}-1.0.0.jar /app.jar
ENTRYPOINT ["java","-jar","/app.jar"]

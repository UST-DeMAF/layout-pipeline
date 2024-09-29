FROM alpine:latest

RUN apk upgrade --no-cache \
    && apk add --no-cache curl graphviz maven openjdk17

RUN mkdir -p /app/target
WORKDIR /app

COPY pom.xml .
COPY src ./src

RUN mvn clean package

CMD java -jar target/visualization-service-0.1.0-SNAPSHOT.jar
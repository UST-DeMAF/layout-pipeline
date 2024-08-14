FROM debian:latest

RUN apt-get update \
    && apt-get upgrade -y \
    && apt-get install -y default-jdk graphviz maven \
    && apt-get autoremove -y \
    && apt-get autoclean -y

RUN mkdir -p /app/target
WORKDIR /app

COPY pom.xml .
COPY src ./src

RUN mvn clean package

CMD java -jar target/layout-pipeline-1.0-SNAPSHOT.jar
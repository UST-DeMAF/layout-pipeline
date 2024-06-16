FROM debian:latest

RUN apt-get update \
    && apt-get upgrade -y \
    && apt-get install -y default-jdk graphviz maven \
    && apt-get autoremove -y \
    && apt-get autoclean -y

COPY pom.xml .
COPY src src

RUN mvn clean package
CMD "mvn" "exec:java"
FROM debian:stable
MAINTAINER Leandro Tabares Martín <ltmartin198@gmail.com>
RUN apt-get update
RUN apt-get upgrade -y
RUN apt-get install -y openjdk-17-jdk
RUN mkdir sparqlear
COPY target/sparqlear-1.0.0.jar /sparqlear/
COPY src/main/resources/application.properties /sparqlear/
WORKDIR /sparqlear

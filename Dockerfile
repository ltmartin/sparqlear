FROM centos
MAINTAINER Leandro Tabares Martín <ltmartin198@gmail.com>
RUN yum update -y && yum upgrade -y
RUN yum install -y java-11-openjdk.x86_64
RUN mkdir sparqlear
#COPY target/sparqlear-0.1-SNAPSHOT.jar /sparqlear/
#COPY src/main/resources/application.properties /sparqlear/
#WORKDIR /sparqlear
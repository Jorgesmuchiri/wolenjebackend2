FROM java:8
WORKDIR /
COPY web/target/scala-2.12/web-assembly-0.1.0-SNAPSHOT.jar /web-assembly.jar
EXPOSE 8080
CMD java - jar web-assembly.jar
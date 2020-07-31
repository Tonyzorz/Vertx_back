# My First Vert.x 3 Application

This project is a very simple Vert.x 3 application and contains some explaination on how this application is built
and tested.

## Building

You build the project using:

```
mvn clean package
```

## Testing

The application is tested using [vertx-unit](http://vertx.io/docs/vertx-unit/java/).

## Packaging

The application is packaged as a _fat jar_, using the
[Maven Shade Plugin](https://maven.apache.org/plugins/maven-shade-plugin/).

## Running

Once packaged, just launch the _fat jar_ as follows:

```
java -jar target/my-first-app-db-1.0-SNAPSHOT-fat.jar -conf src/main/conf/my-application-conf.json

run io.vertx.blog.first.PreStarterVerticle -conf src/main/conf/my-application-conf.json
run io.vertx.blog.first.PreStarterVerticle -conf src/main/conf/my-application-conf.json -cluster -cluster-host 192.168.11.6 -ha -cluster-port 15701
```

java -jar target/my-first-app-db-1.0-SNAPSHOT-6-15-taewon.jar -conf src/main/conf/my-application-conf.json -cluster-host 192.168.11.6 -ha
run io.vertx.blog.first.MyStarterVerticle -conf src/main/conf/my-application-conf.json -cluster -cluster-host 192.168.11.6


java -jar target/my-first-app-db-1.0-SNAPSHOT-6-15-taewon.jar -conf src/main/conf/my-application-conf.json -cluster -cluster-host 192.168.11.6 -ha
java -jar target/my-first-app-db-1.0-SNAPSHOT-6-15-taewon.jar -conf src/main/conf/my-application-conf.json -cluster -cluster-host 192.168.11.6 -Dhazelcast.rest.enabled=true -ha


Then, open a browser to http://localhost:8080.



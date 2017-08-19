---
date: 2017-01-23T09:07:32-05:00
title: Restful Database Access Tutorial
---

# Introduction

Most microservices will have to access database in order to fulfill consumer requests. 
In this tutorial, we will walk through the following steps with Oracle/Postgres/Mysql
for light-rest-4j framework. If you use light-graphql-4j or light-hybrid-4j the steps
will be somewhat different but the concept is the same.

* How to setup database connection pool
* How to connect to the database instance
* How to do query database tables
* How to update database tables

# Preparation

In order to follow the steps below, please make sure you have the same working 
environment.

* A computer with MacOS or Linux (Windows should work but I never tried)
* Install git
* Install Docker
* Install JDK 8 and Maven
* Install Java IDE (Intellij IDEA Community Edition is recommended)
* Create a working directory under your user directory called networknt.

```
cd ~
mkdir networknt
```

# Create Database Demo Specification

First let's build an OpenAPI specification with several endpoints to demo database
access. You will need [swagger editor](https://networknt.github.io/light-4j/tools/swagger-editor/)
to create a specification. 

Here is the OpenAPI specification created and it can be found in 
[model-config](https://github.com/networknt/model-config/tree/master/rest/database) 

```
swagger: '2.0'

info:
  version: "1.0.0"
  title: light-rest-4j Database Tutorial
  description: A demo on how to connect, query and update Oracle/Mysql/Postgres. 
  contact:
    email: stevehu@gmail.com
  license:
    name: "Apache 2.0"
    url: "http://www.apache.org/licenses/LICENSE-2.0.html"
host: database.networknt.com
schemes:
  - http
  - https
basePath: /v1

consumes:
  - application/json
produces:
  - application/json

paths:
  /query:
    get:
      description: Single query to database table
      operationId: getQuery
      responses:
        200:
          description: "successful operation"
          schema:
            $ref: "#/definitions/RandomNumber"          
      security:
      - database_auth:
        - "database.r"
  /queries:
    get:
      description: Multiple queries to database table
      operationId: getQueries
      parameters:
      - name: "queries"
        in: "query"
        description: "Number of random numbers"
        required: false
        type: "integer"
        format: "int32"
      responses:
        200:
          description: "successful operation"
          schema:
            type: "array"
            items:
              $ref: "#/definitions/RandomNumber"
      security:
      - database_auth:
        - "database.r"
  /updates:
    get:
      description: Multiple updates to database table
      operationId: getUpdates
      parameters:
      - name: "queries"
        in: "query"
        description: "Number of random numbers"
        required: false
        type: "integer"
        format: "int32"
      responses:
        200:
          description: "successful operation"
          schema:
            type: "array"
            items:
              $ref: "#/definitions/RandomNumber"
      security:
      - database_auth:
        - "database.w"
securityDefinitions:
  database_auth:
    type: "oauth2"
    authorizationUrl: "http://localhost:8888/oauth2/code"
    flow: "implicit"
    scopes:
      database.w: "write database table"
      database.r: "read database table"
definitions:
  RandomNumber:
    type: "object"
    required:
    - "id"
    - "randomNumber"
    properties:
      id:
        type: "integer"
        format: "int32"
        description: "a unique id as primary key"
      randomNumber:
        type: "integer"
        format: "int32"
        description: "a random number"
```

Now let's clone the model-config repo to your working directory.

```
cd ~/networknt
git clone git@github.com:networknt/model-config.git
```

# Generate Demo Project

With the specification in place, we can generate the code with [light-codegen](https://github.com/networknt/light-codegen)

There are three different ways to generate the code:

* Local build
* Docker container
* Script with docker container

To learn how to use the tool, please refer to this the [README.md](https://github.com/networknt/light-codegen)

### Generate code with local build

Clone and build light-codegen

```
cd ~/networknt
git clone git@github.com:networknt/light-codegen.git
cd light-codegen
mvn clean install -DskipTests
```

For this demo, I am going to generate the code into light-example-4j/rest/database/generated
folder so that users can check the code later on from this repo. 

Let's checkout the light-example-4j repo and backup the existing database project.

```
cd ~/networknt
git clone git@github.com:networknt/light-example-4j.git
cd light-example-4j/rest
mv database database.bak
```

Before generating the project, we need to create a config.json to define packages,
artifactId, groupId and other options for the project.

Here is the content of the file and it can be found in ~/networknt/model-config/rest/database

```
{
  "name": "database",
  "version": "1.0.0",
  "groupId": "com.networknt",
  "artifactId": "database",
  "rootPackage": "com.networknt.database",
  "handlerPackage":"com.networknt.database.handler",
  "modelPackage":"com.networknt.database.model",
  "overwriteHandler": true,
  "overwriteHandlerTest": true,
  "overwriteModel": true,
  "httpPort": 8080,
  "enableHttp": true,
  "httpsPort": 8443,
  "enableHttps": true,
  "enableRegistry": false,
  "supportOracle": true,
  "supportMysql": true,
  "supportPostgresql": true,
  "supportH2ForTest": true,
  "supportClient": false
}
```

As you can see that we have enabled Oracle, Mysql, Postgres and H2 for test cases. 

Code generation

```
cd ~/networknt/light-codegen
java -jar codegen-cli/target/codegen-cli.jar -f light-rest-4j -o ../light-example-4j/rest/database/generated -m ../model-config/rest/database/swagger.json -c ../model-config/rest/database/config.json
```

Now you should have a project generated. Let's build it and run it.

```
cd ~/networknt/light-example-4j/rest/database/generated
mvn clean install exec:exec
```

Now you can access the service with curl following the step below.


### Generate code with docker container

Let's remove the generated folder from light-example-4j/rest/database folder and
generate the project again with docker container.

```
cd ~/networknt/light-example-4j/rest/database
rm -rf generated
```

Now let's generate the project again with docker.

```
cd ~/networknt
docker run -it -v ~/networknt/model-config/rest/database:/light-api/input -v ~/networknt/light-example-4j/rest/database:/light-api/out networknt/light-codegen -f light-rest-4j -m /light-api/input/swagger.json -c /light-api/input/config.json -o /light-api/out/generated
```

Node that on Linux, the generated folder in ~/networknt/light-example-4j/rest/database will be 
owned by root:root and it needs to be changed to your user:group before compile the project.

```
cd ~/networknt/light-example-4j/rest/database
sudo chown -R steve:steve .
```

Let's build and start the service

```
cd ~/networknt/light-example-4j/rest/database/generated
mvn clean install exec:exec
```

Now you can access the service with curl following the next step.


### Test the service

Now the service is up and running. Let's access it from curl

Single query

```
curl http://localhost:8080/v1/query
```

Result:

```json
 {
                                "id": 123,
                                "randomNumber": 456
                            }
```

Multiple queries with default number of object returned

```
curl http://localhost:8080/v1/queries
```

Result:

```json
 [
                                {
                                    "id": 123,
                                    "randomNumber": 456
                                },
                                {
                                    "id": 567,
                                    "randomNumber": 789
                                }
                            ]
```

Multiple queries with 10 numbers returned

```
curl http://localhost:8080/v1/queries?queries=10
```

Result: 

```json
 [
                                {
                                    "id": 123,
                                    "randomNumber": 456
                                },
                                {
                                    "id": 567,
                                    "randomNumber": 789
                                }
                            ]
```

Multiple updates with default number of object updated

```
curl http://localhost:8080/v1/updates
```

Result:

```json
 [
                                {
                                    "id": 123,
                                    "randomNumber": 456
                                },
                                {
                                    "id": 567,
                                    "randomNumber": 789
                                }
                            ]
```


Multiple updates with 10 numbers updated

```
curl http://localhost:8080/v1/updates?queries=10
```

Result:

```json
 [
                                {
                                    "id": 123,
                                    "randomNumber": 456
                                },
                                {
                                    "id": 567,
                                    "randomNumber": 789
                                }
                            ]
```

# Prepare Database Scripts

For database access, we are going to prepare three scripts for Oracle, Mysql and Postgres.

Oracle
```
DROP TABLE world CASCADE CONSTRAINTS;
CREATE TABLE  world (
  id int NOT NULL,
  randomNumber int NOT NULL,
  PRIMARY KEY  (id)
);

BEGIN
FOR loop_counter IN 1..10 LOOP
INSERT INTO world (id, randomNumber)
VALUES (loop_counter, dbms_random.value(1,10)
       );
END LOOP;
COMMIT;
END;

DROP TABLE fortune CASCADE CONSTRAINTS;
CREATE TABLE fortune (
  id int NOT NULL,
  message varchar2(2048) NOT NULL,
  PRIMARY KEY  (id)
);

INSERT INTO fortune (id, message) VALUES (1, 'fortune: No such file or directory');
INSERT INTO fortune (id, message) VALUES (2, 'A computer scientist is someone who fixes things that aren''t broken.');
INSERT INTO fortune (id, message) VALUES (3, 'After enough decimal places, nobody gives a damn.');
INSERT INTO fortune (id, message) VALUES (4, 'A bad random number generator: 1, 1, 1, 1, 1, 4.33e+67, 1, 1, 1');
INSERT INTO fortune (id, message) VALUES (5, 'A computer program does what you tell it to do, not what you want it to do.');
INSERT INTO fortune (id, message) VALUES (6, 'Emacs is a nice operating system, but I prefer UNIX. — Tom Christaensen');
INSERT INTO fortune (id, message) VALUES (7, 'Any program that runs right is obsolete.');
INSERT INTO fortune (id, message) VALUES (8, 'A list is only as strong as its weakest link. — Donald Knuth');
INSERT INTO fortune (id, message) VALUES (9, 'Feature: A bug with seniority.');
INSERT INTO fortune (id, message) VALUES (10, 'Computers make very fast, very accurate mistakes.');
INSERT INTO fortune (id, message) VALUES (11, '<script>alert("This should not be displayed in a browser alert box.");</script>');
INSERT INTO fortune (id, message) VALUES (12, 'フレームワークのベンチマーク');
```

Mysql
```
# modified from SO answer http://stackoverflow.com/questions/5125096/for-loop-in-mysql
DROP DATABASE IF EXISTS hello_world;
CREATE DATABASE hello_world;
USE hello_world;

DROP TABLE IF EXISTS world;
CREATE TABLE  world (
  id int(10) unsigned NOT NULL auto_increment,
  randomNumber int NOT NULL default 0,
  PRIMARY KEY  (id)
)
ENGINE=INNODB;

DROP PROCEDURE IF EXISTS load_data;

DELIMITER #
CREATE PROCEDURE load_data()
BEGIN

declare v_max int unsigned default 10;
declare v_counter int unsigned default 0;

  TRUNCATE TABLE world;
  START TRANSACTION;
  while v_counter < v_max do
    INSERT INTO world (randomNumber) VALUES ( floor(0 + (rand() * 10)) );
    SET v_counter=v_counter+1;
  end while;
  commit;
END #

DELIMITER ;

CALL load_data();

DROP TABLE IF EXISTS fortune;
CREATE TABLE  fortune (
  id int(10) unsigned NOT NULL auto_increment,
  message varchar(2048) CHARACTER SET 'utf8' NOT NULL,
  PRIMARY KEY  (id)
)
ENGINE=INNODB;

INSERT INTO fortune (message) VALUES ('fortune: No such file or directory');
INSERT INTO fortune (message) VALUES ('A computer scientist is someone who fixes things that aren''t broken.');
INSERT INTO fortune (message) VALUES ('After enough decimal places, nobody gives a damn.');
INSERT INTO fortune (message) VALUES ('A bad random number generator: 1, 1, 1, 1, 1, 4.33e+67, 1, 1, 1');
INSERT INTO fortune (message) VALUES ('A computer program does what you tell it to do, not what you want it to do.');
INSERT INTO fortune (message) VALUES ('Emacs is a nice operating system, but I prefer UNIX. — Tom Christaensen');
INSERT INTO fortune (message) VALUES ('Any program that runs right is obsolete.');
INSERT INTO fortune (message) VALUES ('A list is only as strong as its weakest link. — Donald Knuth');
INSERT INTO fortune (message) VALUES ('Feature: A bug with seniority.');
INSERT INTO fortune (message) VALUES ('Computers make very fast, very accurate mistakes.');
INSERT INTO fortune (message) VALUES ('<script>alert("This should not be displayed in a browser alert box.");</script>');
INSERT INTO fortune (message) VALUES ('フレームワークのベンチマーク');

```


Postgres

```

DROP TABLE IF EXISTS world;
CREATE TABLE  world (
  id integer NOT NULL,
  randomNumber integer NOT NULL default 0,
  PRIMARY KEY  (id)
);

INSERT INTO world (id, randomnumber)
SELECT x.id, random() * 10 + 1 FROM generate_series(1,10) as x(id);

DROP TABLE IF EXISTS fortune;
CREATE TABLE fortune (
  id integer NOT NULL,
  message varchar(2048) NOT NULL,
  PRIMARY KEY  (id)
);

INSERT INTO fortune (id, message) VALUES (1, 'fortune: No such file or directory');
INSERT INTO fortune (id, message) VALUES (2, 'A computer scientist is someone who fixes things that aren''t broken.');
INSERT INTO fortune (id, message) VALUES (3, 'After enough decimal places, nobody gives a damn.');
INSERT INTO fortune (id, message) VALUES (4, 'A bad random number generator: 1, 1, 1, 1, 1, 4.33e+67, 1, 1, 1');
INSERT INTO fortune (id, message) VALUES (5, 'A computer program does what you tell it to do, not what you want it to do.');
INSERT INTO fortune (id, message) VALUES (6, 'Emacs is a nice operating system, but I prefer UNIX. — Tom Christaensen');
INSERT INTO fortune (id, message) VALUES (7, 'Any program that runs right is obsolete.');
INSERT INTO fortune (id, message) VALUES (8, 'A list is only as strong as its weakest link. — Donald Knuth');
INSERT INTO fortune (id, message) VALUES (9, 'Feature: A bug with seniority.');
INSERT INTO fortune (id, message) VALUES (10, 'Computers make very fast, very accurate mistakes.');
INSERT INTO fortune (id, message) VALUES (11, '<script>alert("This should not be displayed in a browser alert box.");</script>');
INSERT INTO fortune (id, message) VALUES (12, 'フレームワークのベンチマーク');

```

Above scripts can be found in https://github.com/networknt/light-example-4j/tree/master/rest/database/dbscript
You can just copy the dbscript folder from database.bak to database folder as scripting for these three databases
are out of scope in this tutorial.

# Start Databases

In order to work on our service, we need to start database standalone for now. Depending
on which database you are working on, you can choose one of them below. For this demo 
use mysql and later on we can switch to Postgres and Oracle.


Oracle Database

```
docker run -v ~/networknt/light-example-4j/rest/database/dbscript/oracle:/docker-entrypoint-initdb.d -d -p 1522:1521 wnameless/oracle-xe-11g
```

 
Mysql Database

```
docker run -v ~/networknt/light-example-4j/rest/database/dbscript/mysql:/docker-entrypoint-initdb.d -e MYSQL_ROOT_PASSWORD=my-secret-pw -d -p 3306:3306 mysql

```

Postgres Database

```
docker run -v ~/networknt/light-example-4j/rest/database/dbscript/postgres:/docker-entrypoint-initdb.d -e POSTGRES_PASSWORD=my-secret-pw -e POSTGRES_DB=hello_world -d -p 5432:5432 postgres

```

# Setup Connection Pool

To connect to database we need to create service.yml that can inject connection pool
to the microservice you are building.

Now we have generated project, let's copy it and update with db connection pool

```
cd ~/networknt/light-example-4j/rest/database
cp -r generated connection
```

Add the following service.yml to ~/networknt/light-example-4j/rest/database/connection/src/main/resources/config

```
# Singleton service factory configuration
singletons: 
 - javax.sql.DataSource: 
   - com.zaxxer.hikari.HikariDataSource: 
     jdbcUrl: "jdbc:mysql://localhost:3306/hello_world?useSSL=false"
     username: root
     password: "my-secret-pw"
     maximumPoolSize: 95
     useServerPrepStmts: true
     cachePrepStmts: true
     cacheCallableStmts: true
     prepStmtCacheSize: 4096
     prepStmtCacheSqlLimit: 2048

```

The service.yaml will make sure the a Hikari DataSource will be created during server startup 
with the dependency injection module. You can find other database's service.yml in 
https://github.com/networknt/light-example-4j/tree/master/rest/database/dbscript

In the config.json for light-codegen, we've enabled multiple databases support so that pom.xml
should have all the dependencies included. 

Now you can add a line in each handler to get the DataSource as a static variable.


```
    private static final DataSource ds = (DataSource) SingletonServiceFactory.getBean(DataSource.class);

```
If you are using IDE, it will help you to add imports, otherwise, you have to add the following imports.

```
import com.networknt.service.SingletonServiceFactory;
import javax.sql.DataSource;

```

If you can build, start and access the server with curl, that means the database connection
is created. The next step we will try to query from database.

# Single Database Query

Let's copy connection to query

```
cd ~/networknt/light-example-4j/rest/database
cp -r connection query

```

And let's update QueryGetHandler.java

```
package com.networknt.database.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.config.Config;
import com.networknt.database.model.RandomNumber;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;

import com.networknt.service.SingletonServiceFactory;
import javax.sql.DataSource;

public class QueryGetHandler implements HttpHandler {
    private static final DataSource ds = (DataSource) SingletonServiceFactory.getBean(DataSource.class);
    private static final ObjectMapper mapper = Config.getInstance().getMapper();

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        if (exchange.isInIoThread()) {
            exchange.dispatch(this);
            return;
        }
        int queries = 1;

        RandomNumber[] randomNumbers = new RandomNumber[queries];
        try (final Connection connection = ds.getConnection()) {
            Map<Integer, Future<RandomNumber>> futureWorlds = new ConcurrentHashMap<>();
            for (int i = 0; i < queries; i++) {
                futureWorlds.put(i, Helper.EXECUTOR.submit(new Callable<RandomNumber>(){
                    @Override
                    public RandomNumber call() throws Exception {
                        try (PreparedStatement statement = connection.prepareStatement(
                                "SELECT * FROM world WHERE id = ?",
                                ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {

                            statement.setInt(1, Helper.randomWorld());
                            ResultSet resultSet = statement.executeQuery();
                            resultSet.next();
                            return new RandomNumber(
                                    resultSet.getInt("id"),
                                    resultSet.getInt("randomNumber"));
                        }
                    }
                }));
            }

            for (int i = 0; i < queries; i++) {
                randomNumbers[i] = futureWorlds.get(i).get();
            }
        }
        exchange.getResponseHeaders().put(
                Headers.CONTENT_TYPE, "application/json");

        exchange.getResponseSender().send(mapper.writeValueAsString(randomNumbers[0]));
    }
}

```

And add a helper class Helper.java

```
package com.networknt.database.handler;

import io.undertow.server.HttpServerExchange;

import java.util.Deque;
import java.util.concurrent.*;

/**
 * Created by stevehu on 2017-01-23.
 */
public class Helper {
    private Helper() {
        throw new AssertionError();
    }

    /**
     * Returns the value of the "queries" request parameter, which is an integer
     * bound between 1 and 5 with a default value of 1.
     *
     * @param exchange the current HTTP exchange
     * @return the value of the "queries" request parameter
     */
    static int getQueries(HttpServerExchange exchange) {
        Deque<String> values = exchange.getQueryParameters().get("queries");
        if (values == null) {
            return 1;
        }
        String textValue = values.peekFirst();
        if (textValue == null) {
            return 1;
        }
        try {
            int parsedValue = Integer.parseInt(textValue);
            return Math.min(5, Math.max(1, parsedValue));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    /**
     * Returns a random integer that is a suitable value for both the {@code id}
     * and {@code randomNumber} properties of a world object.
     *
     * @return a random world number
     */
    static int randomWorld() {
        return 1 + ThreadLocalRandom.current().nextInt(10);
    }

    private static final int cpuCount = Runtime.getRuntime().availableProcessors();

    // todo: parameterize multipliers
    public static ExecutorService EXECUTOR =
            new ThreadPoolExecutor(
                    cpuCount * 2, cpuCount * 25, 200, TimeUnit.MILLISECONDS,
                    new LinkedBlockingQueue<Runnable>(cpuCount * 100),
                    new ThreadPoolExecutor.CallerRunsPolicy());

}

```


And add a constructor that accept two integer as parameters for RandomNumber in model folder.

```
  public RandomNumber(int id, int randomNumber) {
    this.id = id;
    this.randomNumber = randomNumber;
  }

```

We are good to go.

```
cd ~/networknt/light-example-4j/rest/database/query
mvn clean install exec:exec
```

Access the query endpoint and you will result the random number as result.

Note that we need to make sure Mysql database docker container is up and running.

```
curl http://localhost:8080/v1/query
```

Result:

```
{"id":4495,"randomNumber":6569}
```


# Multiple Database Queries

Let's build multiple queries based on the codebase of single query.

```
cd ~/networknt/light-example-4j/rest/database
cp -r query queries
```

Now let's update queries project for QueriesGetHandler.java

```
package com.networknt.database.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.config.Config;
import com.networknt.database.model.RandomNumber;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import com.networknt.service.SingletonServiceFactory;
import io.undertow.util.Headers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;

public class QueriesGetHandler implements HttpHandler {
    private static final DataSource ds = (DataSource) SingletonServiceFactory.getBean(DataSource.class);
    private static final ObjectMapper mapper = Config.getInstance().getMapper();

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        if (exchange.isInIoThread()) {
            exchange.dispatch(this);
            return;
        }
        int queries = Helper.getQueries(exchange);

        RandomNumber[] randomNumbers = new RandomNumber[queries];
        try (final Connection connection = ds.getConnection()) {
            Map<Integer, Future<RandomNumber>> futureWorlds = new ConcurrentHashMap<>();
            for (int i = 0; i < queries; i++) {
                futureWorlds.put(i, Helper.EXECUTOR.submit(new Callable<RandomNumber>(){
                    @Override
                    public RandomNumber call() throws Exception {
                        try (PreparedStatement statement = connection.prepareStatement(
                                "SELECT * FROM world WHERE id = ?",
                                ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {

                            statement.setInt(1, Helper.randomWorld());
                            ResultSet resultSet = statement.executeQuery();
                            resultSet.next();
                            return new RandomNumber(
                                    resultSet.getInt("id"),
                                    resultSet.getInt("randomNumber"));
                        }
                    }
                }));
            }

            for (int i = 0; i < queries; i++) {
                randomNumbers[i] = futureWorlds.get(i).get();
            }
        }
        exchange.getResponseHeaders().put(
                Headers.CONTENT_TYPE, "application/json");
        exchange.getResponseSender().send(mapper.writeValueAsString(randomNumbers));
    }
}

```

Now let's build and test the server

```
cd ~/networknt/light-example-4j/rest/database/queries
mvn clean install exec:exec
```

Let's test it.

```
curl http://localhost:8080/v1/queries
```

Result:

```
[{"id":1480,"randomNumber":4720}]
```

Again with 10 random numbers

```
curl http://localhost:8080/v1/queries?queries=10
```
Result: 

```
[{"id":4473,"randomNumber":2370},{"id":1142,"randomNumber":3999},{"id":6022,"randomNumber":1683},{"id":159,"randomNumber":4017},{"id":8512,"randomNumber":3248},{"id":4291,"randomNumber":620},{"id":3238,"randomNumber":1257},{"id":8524,"randomNumber":256},{"id":7869,"randomNumber":1709},{"id":6410,"randomNumber":9362}]
```

# Update Database

Let's copy the queries to updates in order to work on updates

```
cd ~/networknt/light-example-4j/rest/database
cp -r queries updates
```

Now let's update UpdatesGetHandler.java in updates folder.

```
package com.networknt.database.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.config.Config;
import com.networknt.database.model.RandomNumber;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;

import com.networknt.service.SingletonServiceFactory;
import javax.sql.DataSource;

public class UpdatesGetHandler implements HttpHandler {
    private static final DataSource ds = (DataSource) SingletonServiceFactory.getBean(DataSource.class);
    private static final ObjectMapper mapper = Config.getInstance().getMapper();

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        if (exchange.isInIoThread()) {
            exchange.dispatch(this);
            return;
        }
        int queries = Helper.getQueries(exchange);
        RandomNumber[] worlds = new RandomNumber[queries];
        try (final Connection connection = ds.getConnection()) {
            Map<Integer, Future<RandomNumber>> futureWorlds = new ConcurrentHashMap<>();
            for (int i = 0; i < queries; i++) {
                futureWorlds.put(i, Helper.EXECUTOR.submit(new Callable<RandomNumber>() {
                    @Override
                    public RandomNumber call() throws Exception {
                        RandomNumber rn;
                        try (PreparedStatement update = connection.prepareStatement(
                                "UPDATE world SET randomNumber = ? WHERE id= ?")) {
                            try (PreparedStatement query = connection.prepareStatement(
                                    "SELECT * FROM world WHERE id = ?",
                                    ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {

                                query.setInt(1, Helper.randomWorld());
                                ResultSet resultSet = query.executeQuery();
                                resultSet.next();
                                rn = new RandomNumber(
                                        resultSet.getInt("id"),
                                        resultSet.getInt("randomNumber"));
                            }
                            rn.setRandomNumber(Helper.randomWorld());
                            update.setInt(1, rn.getRandomNumber());
                            update.setInt(2, rn.getId());
                            update.executeUpdate();
                            return rn;
                        }
                    }
                }));
            }
            for (int i = 0; i < queries; i++) {
                worlds[i] = futureWorlds.get(i).get();
            }
        }
        exchange.getResponseHeaders().put(
                Headers.CONTENT_TYPE, "application/json");
        exchange.getResponseSender().send(mapper.writeValueAsString(worlds));
    }
}

```


Let's build and start the server

```
cd ~/networknt/light-example-4j/rest/database/updates
mvn clean install exec:exec

```

Let's test it with one update

```
curl http://localhost:8080/v1/updates
```

Result: 

```
[{"id":4682,"randomNumber":1717}]
```

Let's test it with multiple updates

```
curl http://localhost:8080/v1/updates?queries=10
```

Result:

```
[{"id":6395,"randomNumber":938},{"id":4124,"randomNumber":4406},{"id":7694,"randomNumber":936},{"id":502,"randomNumber":5784},{"id":6992,"randomNumber":8037},{"id":3607,"randomNumber":3462},{"id":6910,"randomNumber":6195},{"id":7388,"randomNumber":9233},{"id":6235,"randomNumber":4825},{"id":4924,"randomNumber":1066}]
```

# Switch to Postgres

The first step is to start the postgres database in docker. The command
has shown above. 

To switch to Postgres database, you just need to replace service.yml from 
dbscript/postgres/config folder. First let's create a new folder from 
updates and copy the service.yml

```
cd ~/networknt/light-example-4j/rest/database
cp -r updates postgres
cp dbscript/postgres/config/service.yml postgres/src/main/resources/config
```

Now let's build the server from postgres folder.

```
cd postgres
mvn clean install exec:exec
```

Now you can test the server with curl to verify that the server is working with 
Postgres database.

```
curl http://localhost:8080/v1/query
```


# Switch to Oracle

The first step is to start Oracle database in docker. The command has
shown above.

Next we need to add manually install Oracle JDBC driver jar into your local maven
repository. You can search on Internet on the instructions. If you are not interested 
in Oracle, please just skip this step. The only reason I have Oracle in this tutorial
is because one of our customers only have Oracle database as approved. Downloading
Oracle XE Docker image will take a long time and that image is not stable and can be
broken at anytime. 

To switch to Oracle database, you just need to replace service.yml from
dbscript/oracle/config folder. First let's create a new folder from
updates and copy the service.yml

```
cd ~/networknt/light-example-4j/rest/database
cp -r updates oracle
cp dbscript/oracle/config/service.yml oracle/src/main/resources/config
```

Now let's build the server from postgres folder.

```
cd oracle
mvn clean install exec:exec
```

Now you can test the server with curl to verify that the server is working with 
Oracle database.

```
curl http://localhost:8080/v1/query
```

# End-to-End Test

In this step, we are going to create some end-to-end test cases. As these tests
are very important in ensuring the server you build works. They give us confidence
on changing the code and support continuous integration to production.
   
Given our code is based on a light weight Http framework, all our tests will be
using the real server. There is a TestServer.java in the generated code already
and each handler will have a generated test case. The only thing we need to do
is to add testing logic.

First let's create a new folder call test from updates which is using Mysql.


```
cd ~/networknt/light-example-4j/rest/database
cp -r updates test
```

Now let's go to IDE and navigate to the test folder under src. You can find there
are three test cases for each handler and there is an extra class called 
TestServer.java

Let's take a look at the generated QueryGetHandlerTest.java

```
package com.networknt.database.handler;

import com.networknt.client.Client;
import com.networknt.server.Server;
import com.networknt.exception.ClientException;
import com.networknt.exception.ApiException;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.*;
import org.apache.http.impl.client.CloseableHttpClient;
import org.junit.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;


public class QueryGetHandlerTest {
    @ClassRule
    public static TestServer server = TestServer.getInstance();

    static final Logger logger = LoggerFactory.getLogger(QueryGetHandlerTest.class);

    @Test
    public void testQueryGetHandlerTest() throws ClientException, ApiException {
        CloseableHttpClient client = Client.getInstance().getSyncClient();
        HttpGet httpGet = new HttpGet ("http://localhost:8080/v1/query");
        /*
        Client.getInstance().addAuthorization(httpGet);
        try {
            CloseableHttpResponse response = client.execute(httpGet);
            int statusCode = response.getStatusLine().getStatusCode();
            String body = IOUtils.toString(response.getEntity().getContent(), "utf8");
            Assert.assertEquals(200, statusCode);
            Assert.assertEquals("", body);
        } catch (Exception e) {
            e.printStackTrace();
        }
        */
    }
}

```

In this test file, we can see that a static TestServer instance is started 
with @ClassRule to be shared by all test cases in this test file. Also, there
is one test case with some of the logic commented out. This test is a positive
test generated based on swagger specification. The result checking code
is commented out because we don't know what is the exact object returned.

Let's modify this test to make it work in our service and connect to H2 database
for local testing without external database. The H2 dependency and service.yml
has been added in light-codegen config.json

Here is the modified test case. 

```java
package com.networknt.database.handler;

import com.networknt.client.Client;
import com.networknt.server.Server;
import com.networknt.exception.ClientException;
import com.networknt.exception.ApiException;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.*;
import org.apache.http.impl.client.CloseableHttpClient;
import org.junit.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;


public class QueryGetHandlerTest {
    @ClassRule
    public static TestServer server = TestServer.getInstance();

    static final Logger logger = LoggerFactory.getLogger(QueryGetHandlerTest.class);

    @Test
    public void testQueryGetHandlerTest() throws ClientException, ApiException {
        CloseableHttpClient client = Client.getInstance().getSyncClient();
        HttpGet httpGet = new HttpGet ("http://localhost:8080/v1/query");
        try {
            CloseableHttpResponse response = client.execute(httpGet);
            int statusCode = response.getStatusLine().getStatusCode();
            String body = IOUtils.toString(response.getEntity().getContent(), "utf8");
            Assert.assertEquals(200, statusCode);
            Assert.assertNotNull(body);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
```


Before run the test case, we need to prepare H2 database script. Here is the create_h2.sql
file that need to be created in src/test/resources

```sql
DROP table IF EXISTS world;

CREATE TABLE  world (
  id int auto_increment primary key,
  randomNumber int NOT NULL
);


INSERT INTO world(id, randomNumber) VALUES (1, 10);
INSERT INTO world(id, randomNumber) VALUES (2, 9);
INSERT INTO world(id, randomNumber) VALUES (3, 8);
INSERT INTO world(id, randomNumber) VALUES (4, 7);
INSERT INTO world(id, randomNumber) VALUES (5, 6);
INSERT INTO world(id, randomNumber) VALUES (6, 5);
INSERT INTO world(id, randomNumber) VALUES (7, 4);
INSERT INTO world(id, randomNumber) VALUES (8, 3);
INSERT INTO world(id, randomNumber) VALUES (9, 2);
INSERT INTO world(id, randomNumber) VALUES (10, 1);


```

And then we need to change TestServer.java to initialize H2 database. Here is the
updated file.

```java

package com.networknt.database.handler;

import com.networknt.server.Server;
import com.networknt.service.SingletonServiceFactory;
import org.h2.tools.RunScript;
import org.junit.rules.ExternalResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;

import com.networknt.server.Server;
import com.networknt.server.ServerConfig;

import javax.sql.DataSource;

import static java.nio.charset.StandardCharsets.UTF_8;

public class TestServer extends ExternalResource {
    static final Logger logger = LoggerFactory.getLogger(TestServer.class);

    private static final AtomicInteger refCount = new AtomicInteger(0);
    private static Server server;

    private static final TestServer instance  = new TestServer();

    public static TestServer getInstance () {
        return instance;
    }

    private TestServer() {
        DataSource ds = (DataSource) SingletonServiceFactory.getBean(DataSource.class);
        try (Connection connection = ds.getConnection()) {
            String schemaResourceName = "/create_h2.sql";
            InputStream in = TestServer.class.getResourceAsStream(schemaResourceName);

            if (in == null) {
                throw new RuntimeException("Failed to load resource: " + schemaResourceName);
            }
            InputStreamReader reader = new InputStreamReader(in, UTF_8);
            RunScript.execute(connection, reader);

        } catch (SQLException e) {
            e.printStackTrace();
        }

    }

    public ServerConfig getServerConfig() {
        return Server.config;
    }

    @Override
    protected void before() {
        try {
            if (refCount.get() == 0) {
                Server.start();
            }
        }
        finally {
            refCount.getAndIncrement();
        }
    }

    @Override
    protected void after() {
        refCount.getAndDecrement();
        if (refCount.get() == 0) {
            Server.stop();
        }
    }
}

```

Now you can run the test case and it should be passed. 

# Performance Test

To test the endpoint on your localhost, use the following command. You cannot
use localhost, so you have to find out your ip address. 

You can use ifconfig to find you local ip.

```
docker run --rm williamyeh/wrk -t4 -c50 -d30s --timeout 2s http://192.168.1.131:8080/v1/query
```

Here is the result.

```
Running 30s test @ http://192.168.1.131:8080/v1/query
  4 threads and 50 connections
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency    10.30ms    3.78ms  74.03ms   89.91%
    Req/Sec     1.18k   160.15     1.86k    72.50%
  141321 requests in 30.05s, 20.73MB read
Requests/sec:   4703.17
Transfer/sec:    706.30KB
```

As we are using Mysql docker container and its maximum connection can only reach 100.
If you can increase the connection pool size, you can get even better performance.


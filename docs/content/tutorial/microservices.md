---
date: 2016-10-09T08:01:56-04:00
title: Microservices
---

## Introduction

These days light weight container like Docker is getting traction, more and more 
API services are developed for docker container and deployed to the cloud. In this
environment, traditional heavy weight containers like Java EE and Spring are 
losing ground as it doesn't make sense to have a heavy weight container wrapped 
with a light weight docker container. Docker and container orchestration tools 
like Kubernetes and Docker Swarm are replacing all the functionalities Java EE 
provides without hogging resources.

There is an [article](https://www.gartner.com/doc/reprints?id=1-3N8E378&ct=161205&st=sb) 
published by Gartner indicates that both Java EE and .NET are declining and will
be replaced very soon. 


Another clear trend is standalone Gateway is phasing out in the cloud environment 
with docker containers as most of the traditional gateway features are replaced 
by container orchestration tool and docker container management tools. In addition, 
some of the cross cutting concerns gateway provided are addressed in API frameworks
like light-4j and light-rest-4j.

The following section shows you how to build and deploy multiple microservices that
interact with each other. 


## Prepare workspace

All specifications and code of the services are on github.com but we are going to
redo it again by following the steps in this tutorial. Let's first create a
workspace. I have created a directory named networknt under user directory.

Checkout related projects.

```
cd ~/networknt
git clone git@github.com:networknt/light-codegen.git
git clone git@github.com:networknt/light-example-4j.git
git clone git@github.com:networknt/model-config.git
git clone git@github.com:networknt/light-oauth2.git
git clone git@github.com:networknt/light-docker.git
```

As we are going to regenerate API A, B, C and D, let's rename these folders from
light-example-4j/rest. While you are working on the tutorial, you can compare what you
are doing with the final result for each service in the .bak directory.

```
cd ~/networknt/light-example-4j/rest
mv api_a api_a.bak
mv api_b api_b.bak
mv api_c api_c.bak
mv api_d api_d.bak
cd ..
```

## Specifications

Light-rest-4j microservices framework encourages Design Driven API building and 
[OpenAPI Specification](https://github.com/OAI/OpenAPI-Specification) is the central
piece to drive the runtime for security and validation. Also, the specification 
can be used to scaffold a running server project the first time so that developers 
can focus their efforts on the domain business logic implementation without 
worrying about how each component wired together.

During the service implementation phase, specification might be changed and you can
regenerate the service codebase again without overwriting your handlers and test
cases for handlers. If you have models, you can direct the light-codegen to not
overwrite them as well. This is a feature that allow you to upgrade the framework
to a new version without touching the user updated code. Of course, it is much safer
to generate a new project in another folder and do a folder comparison. 

To create OpenAPI specification(swagger specification), the best tool is
[swagger-editor](http://swagger.io/swagger-editor/) and I have an
[article](https://networknt.github.io/light-rest-4j/tool/swagger-editor/)
in tool section to describe how to use it.

By following the [instructions](https://networknt.github.io/light-rest-4j/tool/swagger-editor/)
on how to use the editor, let's create four API specifications in model-config repo.

API A will call API B and API C to fulfill its request. API B will call API D
to fulfill its request.

```
API A -> API B -> API D
          -> API C
```

Here is the API A swagger.yaml and others can be found at
[https://github.com/networknt/model-config](https://github.com/networknt/model-config) or 
model-config folder in your workspace. 

```
swagger: '2.0'

info:
  version: "1.0.0"
  title: API A for microservices demo
  description: API A is called by consumer directly and it will call API B and API C to get data
  contact:
    email: stevehu@gmail.com
  license:
    name: "Apache 2.0"
    url: "http://www.apache.org/licenses/LICENSE-2.0.html"
host: a.networknt.com
schemes:
  - http
basePath: /v1

consumes:
  - application/json
produces:
  - application/json

paths:
  /data:
    get:
      description: Return an array of strings collected from down stream APIs
      operationId: listData
      responses:
        200:
          description: Successful response
          schema:
            title: ArrayOfStrings
            type: array
            items:
              type: string
          examples: {
            "application/json": [
              "Message 1",
              "Message 2"
            ]
          }
      security:
        - a_auth:
          - api_a.w
          - api_a.r

securityDefinitions:
  a_auth:
    type: oauth2
    authorizationUrl: http://localhost:8080/oauth2/code
    flow: implicit
    scopes:
      api_a.w: write access
      api_a.r: read access
```

As defined in the specification, API A will return a list of stings and it requires
scope api_a.r or scope api_a.w to access the endpoint /v1/data. It also defines the
application/json example for the response which will be used in the generated handler.

OpenAPI specification has two different format: YAML for human and JSON for computer.

Along with the swagger.yaml, I have exported the swagger.json as the input for the
light-codegen model to drive the code generation and the same swagger.json will be
used by the service at runtime. It is copied to main/resources/config folder in the
generated project.


## light-codegen

Now we have four API swagger.json files available. Let's use light-codegen to start 
four projects in light-example-4j. In normal API build, you should create a repo for 
each API. To reduce the number of the repos in networknt organization, I put them all
in light-example-4j/rest folder. 


#### Build Light Java Generator

The project is cloned to the local already during the prepare stage. Let's build it.

```
cd light-codegen
mvn clean install -DskipTests
```

#### Prepare Generator Config

Each generator in light-codegen requires several parameters to control how the generator
works. For more information on how to use generator, please refer [here](https://networknt.github.io/light-codegen/generator/)

For API A, here is the config.json and a copy can be found in the folder 
model-config/rest/api_a/1.0.0 with swagger.yaml and swagger.json. 

```
{
  "rootPackage": "com.networknt.apia",
  "handlerPackage":"com.networknt.apia.handler",
  "modelPackage":"com.networknt.apia.model",
  "artifactId": "apia",
  "groupId": "com.networknt",
  "name": "apia",
  "version": "1.0.0",
  "overwriteHandler": true,
  "overwriteHandlerTest": true,
  "overwriteModel": true
}
```

#### Generate first project

Now you have your light-codegen built, let's generate a project. Assume that
model-config, light-example-4j and light-codegen are in the same working
directory ~/networknt and you are in ~/networknt/light-codegen now.

```
java -jar codegen-cli/target/codegen-cli.jar -f light-rest-4j -o ../light-example-4j/rest/api_a -m ../model-config/rest/api_a/1.0.0/swagger.json -c ../model-config/rest/api_a/1.0.0/config.json
```
Here is the generator output.

```
steve@joy:~/networknt/light-codegen$ java -jar codegen-cli/target/codegen-cli.jar -f light-rest-4j -o ../light-example-4j/rest/api_a -m ../model-config/rest/api_a/1.0.0/swagger.json -c ../model-config/rest/api_a/1.0.0/config.json
light-rest-4j ../model-config/rest/api_a/1.0.0/swagger.json ../model-config/rest/api_a/1.0.0/config.json ../light-example-4j/rest/api_a21:59:48.606 [main] INFO com.fizzed.rocker.runtime.RockerRuntime - Rocker version 0.16.0
21:59:48.609 [main] INFO com.fizzed.rocker.runtime.RockerRuntime - Rocker template reloading not activated

```

#### Build and run the mock API

And now you have a new project created in light-example-4j/rest. Let's build
it and run the test cases. If everything is OK, start the server.

```
cd ..
cd light-example-4j/rest/api_a
mvn clean install
mvn exec:exec
```

Let's test the API A by issuing the following command
```
curl localhost:8080/v1/data
```

By default the response example from swagger.json will be returned. 

```
["Message 1","Message 2"]
```

#### Generate other APIs

Let's kill the API A by Ctrl+C and move to the light-codegen terminal again. Follow 
the above steps to generate other APIs. Make sure you are in light_codegen directory.

```
java -jar codegen-cli/target/codegen-cli.jar -f light-rest-4j -o ../light-example-4j/rest/api_b -m ../model-config/rest/api_b/1.0.0/swagger.json -c ../model-config/rest/api_b/1.0.0/config.json
java -jar codegen-cli/target/codegen-cli.jar -f light-rest-4j -o ../light-example-4j/rest/api_c -m ../model-config/rest/api_c/1.0.0/swagger.json -c ../model-config/rest/api_c/1.0.0/config.json
java -jar codegen-cli/target/codegen-cli.jar -f light-rest-4j -o ../light-example-4j/rest/api_d -m ../model-config/rest/api_d/1.0.0/swagger.json -c ../model-config/rest/api_d/1.0.0/config.json
```

Now you have four APIs generated from four OpenAPI specifications. Let's check
them in. Note that you might not have write access to this repo, so you can ignore
this step. 

```
cd ../light-example-4j
git add .
git commit -m "checkin 4 apis"
```

## Handlers

Now these APIs are working if you start them and they will output the mock responses
generated based on the API specifications. Let's take a look at the API handler itself
and update it based on our business logic.

#### API D
Let's take a look at the generated PathHandlerProvider.java in
api_d/src/main/com/networknt/apid

```

package com.networknt.apid;

import com.networknt.config.Config;
import com.networknt.server.HandlerProvider;
import io.undertow.Handlers;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Methods;
import com.networknt.info.ServerInfoGetHandler;
import com.networknt.health.HealthGetHandler;
import com.networknt.apid.handler.*;

public class PathHandlerProvider implements HandlerProvider {
    @Override
    public HttpHandler getHandler() {
        return Handlers.routing()
        
            .add(Methods.GET, "/v1/data", new DataGetHandler())
        
            .add(Methods.GET, "/v1/health", new HealthGetHandler())
        
            .add(Methods.GET, "/v1/server/info", new ServerInfoGetHandler())
        
        ;
    }
}

```

This is the only class that routes each endpoint defined in specification to a handler 
instance. Because we only have one endpoint /v1/data@get there is only one route added
to the handler chain. And there is a handler generated in the handler subfolder to
handle request that has the url matched to this endpoint.
 
You might noticed that we have two additional endpoints that are not defined in the swagger.json
but in the generated PathHandlerProvider.java. These two endpoints were injected by the light-codegen
for health check and server info. 

The generated handler is named "DataGetHandler" and it returns example response defined in the 
swagger specification. Here is the generated handler DataGetHandler.java
 
```

package com.networknt.apid.handler;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;
import java.util.HashMap;
import java.util.Map;

public class DataGetHandler implements HttpHandler {
    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        
            exchange.getResponseHeaders().add(new HttpString("Content-Type"), "application/json");
             exchange.getResponseSender().send(" [\n                                \"Message 1\",\n                                \"Message 2\"\n                            ]");
        
    }
}
```

Let's update it to an array of strings that indicates the response comes from API D. 


```

package com.networknt.apid.handler;

import com.networknt.config.Config;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DataGetHandler implements HttpHandler {
    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        List<String> messages = new ArrayList<String>();
        messages.add("API D: Message 1");
        messages.add("API D: Message 2");
        exchange.getResponseSender().send(Config.getInstance().getMapper().writeValueAsString(messages));
    }
}

```

Now, let's build it and start the server. Make sure there is only one server
started at any time as all servers are listening to the same port by default.

```
cd api_d
mvn clean package exec:exec
```
Test it with curl.

```
curl localhost:8080/v1/data
```
And the result is

```
["API D: Message 1","API D: Message 2"]
```

You can also try the health check and server info endpoint. 

```
curl localhost:8080/v1/health
```

Which will return 200 response code with body of "OK" to indicate server is healthy. 

```
curl localhost:8080/v1/server/info
```

Will return runtime info and configurations for each enabled module.


#### API C
Let's shutdown API D and update API C DataGetHandler to

```
package com.networknt.apic.handler;

import com.networknt.config.Config;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DataGetHandler implements HttpHandler {
    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        List<String> messages = new ArrayList<String>();
        messages.add("API C: Message 1");
        messages.add("API C: Message 2");
        exchange.getResponseSender().send(Config.getInstance().getMapper().writeValueAsString(messages));
    }
}

```

Restart API C server and test the endpoint /v1/data

```
cd api_c
mvn clean install exec:exec
```
From another terminal window run:

```
curl localhost:8080/v1/data
```
And the result is

```
["API C: Message 1","API C: Message 2"]
```

#### API B

Let's shutdown API C and complete API B. API B will call API D to fulfill its
request so it needs to use light-4j Client module to call API D. 

Now let's update the generated DataGetHandler.java to this.

```

package com.networknt.apib.handler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.networknt.client.Client;
import com.networknt.config.Config;
import com.networknt.exception.ClientException;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DataGetHandler implements HttpHandler {
    static String CONFIG_NAME = "api_b";
    static String apidUrl = (String) Config.getInstance().getJsonMapConfig(CONFIG_NAME).get("api_d_endpoint");

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        List<String> list = new ArrayList<>();
        try {
            CloseableHttpClient client = Client.getInstance().getSyncClient();
            HttpGet httpGet = new HttpGet(apidUrl);
            CloseableHttpResponse response = client.execute(httpGet);
            int responseCode = response.getStatusLine().getStatusCode();
            if(responseCode != 200){
                throw new Exception("Failed to call API D: " + responseCode);
            }
            List<String> apidList = Config.getInstance().getMapper().readValue(response.getEntity().getContent(),
                    new TypeReference<List<String>>(){});
            list.addAll(apidList);
        } catch (ClientException e) {
            throw new Exception("Client Exception: ", e);
        } catch (IOException e) {
            throw new Exception("IOException:", e);
        }
        // now add API B specific messages
        list.add("API B: Message 1");
        list.add("API B: Message 2");
        exchange.getResponseSender().send(Config.getInstance().getMapper().writeValueAsString(list));
    }
}
```

As you can see API B need a config file to specify the url for API D. Here is the
content of the config file api_d_endpoint.yml

```
api_d_endpoint: http://localhost:7004/v1/data
```

This file is located at src/main/resources/config.

As API B only calls one API, here sync client is used. As API B will interact
with API D and it requires configuration changes to make it work. Let's wait
until the next step to test it.

#### API A

API A will call API B and API C to fulfill its request. Now let's update the 
generated DataGetHandler.java code to

```
package com.networknt.apia.handler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.networknt.client.Client;
import com.networknt.config.Config;
import com.networknt.exception.ClientException;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.CountDownLatch;

public class DataGetHandler implements HttpHandler {
    static String CONFIG_NAME = "api_a";
    static String apibUrl = (String)Config.getInstance().getJsonMapConfig(CONFIG_NAME).get("api_b_endpoint");
    static String apicUrl = (String) Config.getInstance().getJsonMapConfig(CONFIG_NAME).get("api_c_endpoint");

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        List<String> list = new Vector<String>();
        final HttpGet[] requests = new HttpGet[] {
                new HttpGet(apibUrl),
                new HttpGet(apicUrl),
        };
        try {
            CloseableHttpAsyncClient client = Client.getInstance().getAsyncClient();
            final CountDownLatch latch = new CountDownLatch(requests.length);
            for (final HttpGet request: requests) {
                client.execute(request, new FutureCallback<HttpResponse>() {
                    @Override
                    public void completed(final HttpResponse response) {
                        try {
                            List<String> apiList = Config.getInstance().getMapper().readValue(response.getEntity().getContent(),
                                    new TypeReference<List<String>>(){});
                            list.addAll(apiList);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        latch.countDown();
                    }

                    @Override
                    public void failed(final Exception ex) {
                        ex.printStackTrace();
                        latch.countDown();
                    }

                    @Override
                    public void cancelled() {
                        System.out.println("cancelled");
                        latch.countDown();
                    }
                });
            }
            latch.await();
        } catch (ClientException e) {
            e.printStackTrace();
            throw new Exception("ClientException:", e);
        }
        // now add API A specific messages
        list.add("API A: Message 1");
        list.add("API A: Message 2");
        exchange.getResponseSender().send(Config.getInstance().getMapper().writeValueAsString(list));
    }
}

```

There is a config file api_a.yml to specify the url for API B and API C in the resources/config 
folder. Here is the content.

```
"api_b_endpoint": "http://localhost:7002/v1/data",
"api_c_endpoint": "http://localhost:7003/v1/data"

```

At this moment, we have all four APIs completed but they are not going to be
started at the same time as generated server.yml uses the same port 8080 for http
and 8443 for https. The next step is to change the configuration and test it out.

## Configuration

Light-4j has a module called Config and it is responsible for reading config
files from environmental property specified directory, classpath, API
resources/config folder or module resources/config folder in that sequence as
default file system based configuration. It can be extended to other config like
config server, distributed cache and http server etc.

To make things simpler, let's update the server.yml in API A, B, C, D to bind to
different port in order to start them on the same localhost. The server.yml can
be found in src/main/resources/config folder of each project.


Find the server.yml at api_a/src/main/resources/config and update the ports to

API A 
```
httpPort: 7001
httpsPort: 7441
```


API B

```
httpPort: 7002
httpsPort: 7442
```

API C

```
httpPort: 7003
httpsPort: 7443
```
API D

```
httpPort: 7004
httpsPort: 7444
```


Now let's start all four servers on four terminals and test them out.

API D
```
cd api_d
mvn clean install exec:exec
```
API C

```
cd ../api_c
mvn clean install exec:exec
```

API B

```
cd ../api_b
mvn clean install exec:exec
```

API A

```
cd ../api_a
mvn clean install exec:exec
```

Now let's call API A and see what we can get.

Start another terminal and run

```
 curl localhost:7001/v1/data
```

And you should see the result like this.

```
["API C: Message 1","API C: Message 2","API D: Message 1","API D: Message 2","API B: Message 1","API B: Message 2","API A: Message 1","API A: Message 2"]
```

## Performance without security

Now let's see if these servers are performing with
[wrk](https://github.com/wg/wrk). To learn how to use it, please see my
article in tools [here](https://networknt.github.io/light-4j/tools/wrk-perf/)

Assume you have wrk installed, run the following command.

```
wrk -t4 -c800 -d30s http://localhost:7001/v1/data

```
And here is what I got on my i5 desktop

```
steve@joy:~/tool/wrk$ wrk -t4 -c800 -d30s http://localhost:7001/v1/data
Running 30s test @ http://localhost:7001/v1/data
  4 threads and 800 connections
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency   254.60ms  308.66ms   2.00s    84.11%
    Req/Sec     1.24k     1.19k    3.90k    80.27%
  147302 requests in 30.08s, 34.42MB read
  Socket errors: connect 0, read 0, write 0, timeout 251
Requests/sec:   4897.60
Transfer/sec:      1.14MB
```
Now let's push the server to its limit

```
 wrk -t4 -c128 -d30s http://localhost:7001 -s pipeline.lua --latency -- /v1/data 16
```

And here is the result without any tuning.

```
steve@joy:~/tool/wrk$ wrk -t4 -c128 -d30s http://localhost:7001 -s pipeline.lua --latency -- /v1/data 16
Running 30s test @ http://localhost:7001
  4 threads and 128 connections
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency    55.14ms  130.69ms   1.61s    13.96%
    Req/Sec     2.92k     1.24k    5.17k    64.86%
  Latency Distribution
     50%    0.00us
     75%    0.00us
     90%    0.00us
     99%    0.00us
  331488 requests in 30.03s, 77.45MB read
Requests/sec:  11039.99
Transfer/sec:      2.58MB
```

## Enable OAuth2 Security

So far, we've started four servers and tested them successfully; however,
these servers are not protected by OAuth2 JWT tokens as it is turned off
by default in the generated code.

Before we turn on the security, we need to have [light-oauth2](https://github.com/networknt/light-oauth2)
server up and running so that these servers can get JWT token in real
time.

The easiest way to run light-oauth2 server is through docker but let's
build it locally this time.

Go to your working directory and go to the light-oauth2 folder cloned in the prepare stage. 

```
cd light-oauth2
mvn clean package exec:exec
```

Now let's enable the jwt token verification and scope verification for all
APIs except API A. For API A we want to enableVerifyJwt to true but
enableVerifyScope to false so that we can use a long lived token to test from
curl without getting new tokens each time.

Open src/main/resources/config/security.json in api_b/api_c/api_d folder and 
update enableVerifyJwt and enableVerifyScope to true. The following is what 
looks like for API B, C and D.

```
{
  "description": "security configuration",
  "enableVerifyJwt": true,
  "enableVerifyScope": true,
  "enableMockJwt": false,
  "jwt": {
    "certificate": {
      "100": "oauth/primary.crt",
      "101": "oauth/secondary.crt"
    },
    "clockSkewInSeconds": 60
  },
  "logJwtToken": true,
  "logClientUserScope": false
}
```
This is what looks like for API A src/main/resources/config/security.json

```
{
  "description": "security configuration",
  "enableVerifyJwt": true,
  "enableVerifyScope": false,
  "enableMockJwt": false,
  "jwt": {
    "certificate": {
      "100": "oauth/primary.crt",
      "101": "oauth/secondary.crt"
    },
    "clockSkewInSeconds": 60
  },
  "logJwtToken": true,
  "logClientUserScope": false
}

```

Now make sure that client.json in both API A and API B are configured correctly
to get the right token from light-oauth2 server with the correct scopes. The generated code doesn't
have client.json so you need to create these files in src/main/resources/config folder in order to
overwrite the default client.json from Client module.

The client_id and client_secret are set up in clients.json in light-oauth2
already [here](https://github.com/networknt/light-oauth2/blob/master/src/main/resources/config/clients.json)

API A client
```
  "fe5dadd9-34ad-430f-a8f7-c75e81cc5d7b": {
    "client_secret":"GXkHy-1aSPyo4pst8WBWbg",
    "scope": "api_b.r api_b.w api_c.r api_c.w",
    "redirect_uri": "http://localhost:8080/oauth"
  },
```

API B client

```
  "10a0a743-4674-4a9d-8867-db63ad4c8b4e": {
    "client_secret":"tcahI1dvT1OsxXxg-IB_-w",
    "scope": "api_d.r api_d.w",
    "redirect_uri": "http://localhost:8080/oauth"
  }
```

Now let's create client.json on both API A and API B to reflect the above info.
This is what looks like in API A src/main/resources/config/client.json

```
{
  "description": "client configuration, all timing is milli-second",
  "sync": {
    "maxConnectionTotal": 100,
    "maxConnectionPerRoute": 10,
    "routes": {
      "api.google.com": 20,
      "api.facebook.com": 10
    },
    "timeout": 10000,
    "keepAlive": 15000
  },
  "async": {
    "maxConnectionTotal": 100,
    "maxConnectionPerRoute": 10,
    "routes": {
      "api.google.com": 20,
      "api.facebook.com": 10
    },
    "reactor": {
      "ioThreadCount": 1,
      "connectTimeout": 10000,
      "soTimeout": 10000
    },
    "timeout": 10000,
    "keepAlive": 15000
  },
  "tls": {
    "verifyHostname": false,
    "loadTrustStore": false,
    "trustStore": "trust.keystore",
    "trustPass": "password",
    "loadKeyStore": false,
    "keyStore": "key.jks",
    "keyPass": "password"
  },
  "oauth": {
    "tokenRenewBeforeExpired": 600000,
    "expiredRefreshRetryDelay": 5000,
    "earlyRefreshRetryDelay": 30000,
    "server_url": "http://localhost:8888",
    "timeout": 5000,
    "ignoreSSLErrors": false,
    "authorization_code": {
      "uri": "/oauth2/token",
      "client_id": "fe5dadd9-34ad-430f-a8f7-c75e81cc5d7b",
      "client_secret": "GXkHy-1aSPyo4pst8WBWbg",
      "redirect_uri": "https://localhost:8080/authorization_code",
      "scope": [
        "api_b.r",
        "api_b.w",
        "api_c.r",
        "api_c.w"
      ]
    },
    "client_credentials": {
      "uri": "/oauth2/token",
      "client_id": "fe5dadd9-34ad-430f-a8f7-c75e81cc5d7b",
      "client_secret": "GXkHy-1aSPyo4pst8WBWbg",
      "scope": [
        "api_b.r",
        "api_b.w",
        "api_c.r",
        "api_c.w"
      ]
    }
  }
}
```

And this is what looks like in API B client.json

```
{
  "description": "client configuration, all timing is milli-second",
  "sync": {
    "maxConnectionTotal": 100,
    "maxConnectionPerRoute": 10,
    "routes": {
      "api.google.com": 20,
      "api.facebook.com": 10
    },
    "timeout": 10000,
    "keepAlive": 15000
  },
  "async": {
    "maxConnectionTotal": 100,
    "maxConnectionPerRoute": 10,
    "routes": {
      "api.google.com": 20,
      "api.facebook.com": 10
    },
    "reactor": {
      "ioThreadCount": 1,
      "connectTimeout": 10000,
      "soTimeout": 10000
    },
    "timeout": 10000,
    "keepAlive": 15000
  },
  "tls": {
    "verifyHostname": false,
    "loadTrustStore": false,
    "trustStore": "trust.keystore",
    "trustPass": "password",
    "loadKeyStore": false,
    "keyStore": "key.jks",
    "keyPass": "password"
  },
  "oauth": {
    "tokenRenewBeforeExpired": 600000,
    "expiredRefreshRetryDelay": 5000,
    "earlyRefreshRetryDelay": 30000,
    "server_url": "http://localhost:8888",
    "timeout": 5000,
    "ignoreSSLErrors": false,
    "authorization_code": {
      "uri": "/oauth2/token",
      "client_id": "10a0a743-4674-4a9d-8867-db63ad4c8b4e",
      "client_secret": "tcahI1dvT1OsxXxg-IB_-w",
      "redirect_uri": "https://localhost:8080/authorization_code",
      "scope": [
        "api_d.r",
        "api_d.w"
      ]
    },
    "client_credentials": {
      "uri": "/oauth2/token",
      "client_id": "10a0a743-4674-4a9d-8867-db63ad4c8b4e",
      "client_secret": "tcahI1dvT1OsxXxg-IB_-w",
      "scope": [
        "api_d.r",
        "api_d.w"
      ]
    }
  }
}

```

With client.json updated in both API A and API B, we need to update the code
to assign scope token during runtime. 

Open API A DataGetHandler and one line before client.execute(...).

```
    Client.getInstance().propagateHeaders(request, exchange);
```

Here is the updated file

```
package io.swagger.handler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.networknt.client.Client;
import com.networknt.config.Config;
import com.networknt.exception.ClientException;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CountDownLatch;

import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;

public class DataGetHandler implements HttpHandler {

    static String CONFIG_NAME = "api_a";
    static String apibUrl = (String)Config.getInstance().getJsonMapConfig(CONFIG_NAME).get("api_b_endpoint");
    static String apicUrl = (String)Config.getInstance().getJsonMapConfig(CONFIG_NAME).get("api_c_endpoint");

    public void handleRequest(HttpServerExchange exchange) throws Exception {
        List<String> list = new Vector<String>();
        final HttpGet[] requests = new HttpGet[] {
                new HttpGet(apibUrl),
                new HttpGet(apicUrl),
        };
        try {
            CloseableHttpAsyncClient client = Client.getInstance().getAsyncClient();
            final CountDownLatch latch = new CountDownLatch(requests.length);
            for (final HttpGet request: requests) {
                Client.getInstance().propagateHeaders(request, exchange);
                client.execute(request, new FutureCallback<HttpResponse>() {
                    @Override
                    public void completed(final HttpResponse response) {
                        try {
                            List<String> apiList = (List<String>) Config.getInstance().getMapper().readValue(response.getEntity().getContent(),
                                    new TypeReference<List<String>>(){});
                            list.addAll(apiList);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        latch.countDown();
                    }

                    @Override
                    public void failed(final Exception ex) {
                        ex.printStackTrace();
                        latch.countDown();
                    }

                    @Override
                    public void cancelled() {
                        System.out.println("cancelled");
                        latch.countDown();
                    }
                });
            }
            latch.await();
        } catch (ClientException e) {
            e.printStackTrace();
            throw new Exception("ClientException:", e);
        }
        // now add API A specific messages
        list.add("API A: Message 1");
        list.add("API A: Message 2");
        exchange.getResponseSender().send(Config.getInstance().getMapper().writeValueAsString(list));
    }
}

```

Open API B DataGetHandler and add one line and the end result is:

```
package io.swagger.handler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.networknt.client.Client;
import com.networknt.config.Config;
import com.networknt.exception.ClientException;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;

public class DataGetHandler implements HttpHandler {

    static String CONFIG_NAME = "api_b";
    static String apidUrl = (String)Config.getInstance().getJsonMapConfig(CONFIG_NAME).get("api_d_endpoint");

    public void handleRequest(HttpServerExchange exchange) throws Exception {
        List<String> list = new ArrayList<String>();
        try {
            CloseableHttpClient client = Client.getInstance().getSyncClient();
            HttpGet httpGet = new HttpGet(apidUrl);
            Client.getInstance().propagateHeaders(httpGet, exchange);
            CloseableHttpResponse response = client.execute(httpGet);
            int responseCode = response.getStatusLine().getStatusCode();
            if(responseCode != 200){
                throw new Exception("Failed to call API D: " + responseCode);
            }
            List<String> apidList = (List<String>) Config.getInstance().getMapper().readValue(response.getEntity().getContent(),
                    new TypeReference<List<String>>(){});
            list.addAll(apidList);
        } catch (ClientException e) {
            throw new Exception("Client Exception: ", e);
        } catch (IOException e) {
            throw new Exception("IOException:", e);
        }
        // now add API B specific messages
        list.add("API B: Message 1");
        list.add("API B: Message 2");
        exchange.getResponseSender().send(Config.getInstance().getMapper().writeValueAsString(list));
    }
}
```

Let's start these servers and test it. Don't forget to start light-oauth2 server.

```
cd light-oauth2
mvn package exec:exec
```

Run the following command.

```
curl -H "Authorization: Bearer eyJraWQiOiIxMDAiLCJhbGciOiJSUzI1NiJ9.eyJpc3MiOiJ1cm46Y29tOm5ldHdvcmtudDpvYXV0aDI6djEiLCJhdWQiOiJ1cm46Y29tLm5ldHdvcmtudCIsImV4cCI6MTc5NDgwMDYzOSwianRpIjoiWFhlQmpJYXUwUk5ZSTl3dVF0MWxtUSIsImlhdCI6MTQ3OTQ0MDYzOSwibmJmIjoxNDc5NDQwNTE5LCJ2ZXJzaW9uIjoiMS4wIiwidXNlcl9pZCI6InN0ZXZlIiwidXNlcl90eXBlIjoiRU1QTE9ZRUUiLCJjbGllbnRfaWQiOiJmN2Q0MjM0OC1jNjQ3LTRlZmItYTUyZC00YzU3ODc0MjFlNzIiLCJzY29wZSI6WyJ3cml0ZTpwZXRzIiwicmVhZDpwZXRzIl19.f5XdkmhOoHT2lgTobqVGPp2aWUv_ItA0tqyLHC_CeMbmwzPvREqb5-oJ9T_m3VwRcJlPTh8xTdSjrLITXClaQFE4Y0bT8C-u6bb38uT-NQ5mjUjLrFQYHCF6GqwL7YkwQt_rshEqtrDFe1T4HoEL_9FHbOxf3MSJ39UKq0Ef_9mHXkn4Y-SHfdapeuUWc_4dDPdxzEdzbqmf1WSOOgTuM5O5F2fK4p_ix8LQl0H3AnMZIhIDyygQEnYPxEG-u35gwh503wfxio6buIf0b2Kku2PXPE36lethZwIVaPTncEcY5OPxfBxXuy-Wq-YQizd7NnpJTteHYbdQXupjK7NDvQ" localhost:7001/v1/data
```

And here is the result.

```
["API C: Message 1","API C: Message 2","API D: Message 1","API D: Message 2","API B: Message 1","API B: Message 2","API A: Message 1","API A: Message 2"]
```

At this moment, all four APIs are protected by JWT token and API B, C, D are projected by scope additionally. 

## Dockerization


## Integration

## Performance

## Production

## Conclusion


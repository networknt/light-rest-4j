---
date: 2016-10-09T08:01:56-04:00
title: Chain Pattern Microservices
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
like light-4j.


This tutorial shows you how to build 4 services chained together one by one. And it will
be the foundation for our microserives benchmarks.

API A -> API B -> API C -> API D

## Prepare workspace

All specifications and code of the services are on github.com but we are going to
redo it again by following the steps in the tutorial. Let's first create a
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

As we are going to regenerate API A, B, C and D, let's rename ms_chain folder from
light-example-4j.

```
cd ~/networknt/light-example-4j/rest
mv ms_chain ms_chain.bak
cd ~/networknt
```

## Specifications

Light-rest-4j microservices framework encourages Design Driven API building and 
[OpenAPI Specification](https://github.com/OAI/OpenAPI-Specification) is the central
piece to drive the runtime for security and validation. Also, the specification 
can be used to scaffold a running server project the first time so that developers 
can focus their efforts on the domain business logic implementation without 
worrying about how each component are wired together.

During the service implementation phase, specification might be changed and you can
regenerate the service codebase again without overwriting your handlers and test
cases for handlers. The regeneration is also useful if you want to upgrade to the
latest version of the frameworks for your project. 

To create OpenAPI(Swagger) specification, the best tool is
[swagger-editor](http://swagger.io/swagger-editor/) and I have an
[article](https://networknt.github.io/light-rest-4j/tools/swagger-editor/)
in tools section to describe how to use it.

By following the [instructions](https://networknt.github.io/light-rest-4j/tools/swagger-editor/)
on how to use the editor, let's create four API specifications in model-config repository.

API A will call API B, API B will call API C, API C will call API D

```
API A -> API B -> API C -> API D
```

Here is the API A swagger.yaml and others can be found at
[https://github.com/networknt/model-config](https://github.com/networknt/model-config/tree/master/rest) 
or model-config/rest folder in your workspace. 

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
            "application/json": ["Message 1","Message 2"]
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
scope api_a.r or scope api_a.w to access the endpoint /data.


## light-codegen

Now we have four API swagger.yaml files available. Let's use light-codegen
to start four projects in light-example-4j/rest/ms_chain. In normal API build, you 
should create a repo for each API. For us, we have to user light-example-4j for all the
examples and tutorial for easy management in networknt github organization.

#### Build light-codege

The project is cloned to the local already during the prepare stage. Let's build it.

```
cd light-codegen
mvn clean install -DskipTests
```

#### Prepare Generator Config

Each generator in light-codegen requires several parameters to control how the generator
works. For more information on how to use generator, please refer [here](https://networknt.github.io/light-codegen/generator/)

For API A, here is the config.json and a copy can be found in the folder 
model-config/rest/api_a/1.0.0 along with swagger.yaml and swagger.json. 

```
{
  "name": "apia",
  "version": "1.0.0",
  "groupId": "com.networknt",
  "artifactId": "apia",
  "rootPackage": "com.networknt.apia",
  "handlerPackage":"com.networknt.apia.handler",
  "modelPackage":"com.networknt.apia.model",
  "overwriteHandler": true,
  "overwriteHandlerTest": true,
  "overwriteModel": true,
  "httpPort": 7001,
  "enableHttp": true,
  "httpsPort": 7441,
  "enableHttps": true,
  "enableRegistry": false,
  "supportOracle": false,
  "supportMysql": false,
  "supportPostgresql": false,
  "supportH2ForTest": false,
  "supportClient": true
}
```
As you can see the generated project will use 7001 for http and 7441 for https and client
module will be included as it will call API B with it. DB dependencies are not required for
this tutorial.


#### Generate first project

Now you have your light-codegen built, let's generate a project. Assume that
model-config, light-example-4j and light-codegen are in the same working
directory ~/networknt and you are in ~/networknt/light-codegen now.

```
cd ~/networknt/light-codegen
java -jar codegen-cli/target/codegen-cli.jar -f light-rest-4j -o ../light-example-4j/rest/ms_chain/api_a/generated -m ../model-config/rest/api_a/1.0.0/swagger.json -c ../model-config/rest/api_a/1.0.0/config.json
```

#### Build and run the mock API

And now you have a new project created in light-example-4j/rest/ms_chain/api_a/generated. 
Let's build it and run the test cases. If everything is OK, start the server.

```
cd ..
cd light-example-4j/rest/ms_chain/api_a/generated
mvn clean install exec:exec
```

Let's test the API A by issuing the following command
```
curl localhost:7001/v1/data
```

By default the generated response example will be returned. 

```
 ["Message 1","Message 2"]
```

As https port is enable, let's test https connection and the result should be the same.

```
curl -k https://localhost:7441/v1/data
```

#### Generate other APIs

Let's kill the API A by Ctrl+C and move to the light-codegen terminal again. Follow 
the above steps to generate other APIs. Make sure you are in light_codegen
directory.

```
cd ~/networknt/light-codegen
java -jar codegen-cli/target/codegen-cli.jar -f light-rest-4j -o ../light-example-4j/rest/ms_chain/api_b/generated -m ../model-config/rest/api_b/1.0.0/swagger.json -c ../model-config/rest/api_b/1.0.0/config.json
java -jar codegen-cli/target/codegen-cli.jar -f light-rest-4j -o ../light-example-4j/rest/ms_chain/api_c/generated -m ../model-config/rest/api_c/1.0.0/swagger.json -c ../model-config/rest/api_c/1.0.0/config.json
java -jar codegen-cli/target/codegen-cli.jar -f light-rest-4j -o ../light-example-4j/rest/ms_chain/api_d/generated -m ../model-config/rest/api_d/1.0.0/swagger.json -c ../model-config/rest/api_d/1.0.0/config.json
```

Now you have four APIs generated from four OpenAPI specifications. Let's check
them in. Note that you might not have write access to this repo, so you can ignore
this step. 

```
cd ../light-example-4j
git add .
git commit -m "checkin 4 apis"
git push origin master
```

## ApiToApi Http

Now these APIs are working if you start them and they will output the mock responses
generated based on the API specifications. Let's take a look at the API handler itself
and update it based on our business logic, you can call API A and subsequently all other
APIs will be called in a chain.

#### Prepare Environment

Before starting this step, let's create a folder called httpchain in each sub folder under
ms_chain and copy everything from generated folder to the httpchain. We are going to update
httpchain folder to have business logic to call another api and change the configuration
to listen to different port. You can compare between generated and httpchain to see what has
been changed later on.

```
cd ~/networknt/light-example-4j/rest/ms_chain/api_a
cp -r generated httpchain
cd ~/networknt/light-example-4j/rest/ms_chain/api_b
cp -r generated httpchain
cd ~/networknt/light-example-4j/rest/ms_chain/api_c
cp -r generated httpchain
cd ~/networknt/light-example-4j/rest/ms_chain/api_d
cp -r generated httpchain

```

Now we have httpchain folder copied from generated and all updates in this step will be
in httpchain folder. 

#### API D
Let's take a look at the PathHandlerProvider.java in
ms_chain/api_d/httpchain/src/main/java/com/networknt/apid

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
handle request that has the url matched to this endpoint. The /server/info is injected 
to output the server runtime information on all the components and configurations. It
will be included in every API/service. /health is another injected endpoint to output
server health check info with 200 response code and "OK" as the body. It should be
disabled in most of the case, but Kubernetes needs it. 

The generated handler is named "DataGetHandler" and it returns example response defined
in swagger specification. Here is the generated handler code. 
 
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

Now, let's build it and start the server. 
```
cd ~/networknt/light-example-4j/rest/ms_chain/api_d/httpchain
mvn clean install exec:exec
```
Test it with curl.

```
curl localhost:7004/v1/data
```
And the result is

```
["API D: Message 1","API D: Message 2"]
```

Test with HTTPS port and you should have the same result.

```
curl -k https://localhost:7444/v1/data
```

Note that we have -k option to in the https command line as we are using self-signed
certificate and we don't want to verify the domain.


#### API C
Let's leave API D running and update API C DataGetHandler in 
~/networknt/light-example-4j/rest/ms_chain/api_c/httpchain


```

package com.networknt.apic.handler;

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
    static String CONFIG_NAME = "api_c";
    static String apidUrl = (String) Config.getInstance().getJsonMapConfig(CONFIG_NAME).get("api_d_endpoint");

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        List<String> list = new ArrayList<String>();
        try {
            CloseableHttpClient client = Client.getInstance().getSyncClient();
            HttpGet httpGet = new HttpGet(apidUrl);
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
        list.add("API C: Message 1");
        list.add("API C: Message 2");
        exchange.getResponseSender().send(Config.getInstance().getMapper().writeValueAsString(list));
    }
}
```

API C needs to have the url of API D in order to call it. Let's put it in a config file for
now and move to service discovery later.

Create api_c.yml in src/main/resources/config folder.

```
api_d_endpoint: "http://localhost:7004/v1/data"

```


Start API C server and test the endpoint /v1/data

```
cd ~/networknt/light-example-4j/rest/ms_chain/api_c/httpchain
mvn clean install exec:exec
```
From another terminal window run:

```
curl localhost:7003/v1/data
```
And the result is

```
["API D: Message 1","API D: Message 2","API C: Message 1","API C: Message 2"]
```

Access https port should have the same result.

```
curl -k https://localhost:7443/v1/data

```

#### API B

Let's keep API C and API D running. The next step is to complete API B. API B 
will call API C to fulfill its request. 

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
    static String apidUrl = (String) Config.getInstance().getJsonMapConfig(CONFIG_NAME).get("api_c_endpoint");

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        List<String> list = new ArrayList<String>();
        try {
            CloseableHttpClient client = Client.getInstance().getSyncClient();
            HttpGet httpGet = new HttpGet(apidUrl);
            CloseableHttpResponse response = client.execute(httpGet);
            int responseCode = response.getStatusLine().getStatusCode();
            if(responseCode != 200){
                throw new Exception("Failed to call API C: " + responseCode);
            }
            List<String> apicList = (List<String>) Config.getInstance().getMapper().readValue(response.getEntity().getContent(),
                    new TypeReference<List<String>>(){});
            list.addAll(apicList);
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

API B needs to have the url of API C in order to call it. Let's put it in a config file for
now and move to service discovery later.

Create api_b.yml in src/main/resources/config folder.

```
api_c_endpoint: "http://localhost:7003/v1/data"

```


Start API B server and test the endpoint /v1/data

```
cd ~/networknt/light-example-4j/rest/ms_chain/api_b/httpchain
mvn clean install exec:exec
```
From another terminal window run:

```
curl localhost:7002/v1/data
```
And the result is

```
["API D: Message 1","API D: Message 2","API C: Message 1","API C: Message 2","API B: Message 1","API B: Message 2"]
```

Here is the https port and the result is the same.

```
curl -k https://localhost:7442/v1/data

```


#### API A

API A will call API B to fulfill its request. Now let's update the 
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
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DataGetHandler implements HttpHandler {
    static String CONFIG_NAME = "api_a";
    static String apidUrl = (String) Config.getInstance().getJsonMapConfig(CONFIG_NAME).get("api_b_endpoint");

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        List<String> list = new ArrayList<String>();
        try {
            CloseableHttpClient client = Client.getInstance().getSyncClient();
            HttpGet httpGet = new HttpGet(apidUrl);
            CloseableHttpResponse response = client.execute(httpGet);
            int responseCode = response.getStatusLine().getStatusCode();
            if(responseCode != 200){
                throw new Exception("Failed to call API B: " + responseCode);
            }
            List<String> apicList = (List<String>) Config.getInstance().getMapper().readValue(response.getEntity().getContent(),
                    new TypeReference<List<String>>(){});
            list.addAll(apicList);
        } catch (ClientException e) {
            throw new Exception("Client Exception: ", e);
        } catch (IOException e) {
            throw new Exception("IOException:", e);
        }
        // now add API B specific messages
        list.add("API A: Message 1");
        list.add("API A: Message 2");
        exchange.getResponseSender().send(Config.getInstance().getMapper().writeValueAsString(list));
    }
}
```


API A needs to have the url of API in order to call it. Let's put it in a config file for
now and move to service discovery later.

Create api_a.yml in src/main/resources/config folder.

```
api_b_endpoint: "http://localhost:7002/v1/data"

```


Start API A server and test the endpoint /v1/data

```
cd ~/networknt/light-example-4j/rest/ms_chain/api_a/httpchain
mvn clean install exec:exec
```
From another terminal window run:

```
curl localhost:7001/v1/data
```
And the result is

```
["API D: Message 1","API D: Message 2","API C: Message 1","API C: Message 2","API B: Message 1","API B: Message 2","API A: Message 1","API A: Message 2"]
```

The https port and the result should be the same.

```
 curl -k https://localhost:7441/v1/data
```
At this moment, we have all four APIs completed and A is calling B, B is calling C and
C is calling D using Http connections.

## Performance with Http

Now let's see if these servers are performing with
[wrk](https://github.com/wg/wrk). To learn how to use it, please see my
article in tools [here](https://networknt.github.io/light-4j/tools/wrk-perf/)

Assume you have wrk installed, run the following command.

```
wrk -t4 -c128 -d30s http://localhost:7001 -s pipeline.lua --latency -- /v1/data 1024

```
And here is what I got on my i5 desktop

```
wrk -t4 -c128 -d30s http://localhost:7001 -s pipeline.lua --latency -- /v1/data 1024
Running 30s test @ http://localhost:7001
  4 threads and 128 connections
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     0.00us    0.00us   0.00us    -nan%
    Req/Sec     1.93k     1.00k    5.49k    65.65%
  Latency Distribution
     50%    0.00us
     75%    0.00us
     90%    0.00us
     99%    0.00us
  220696 requests in 30.06s, 50.72MB read
  Socket errors: connect 0, read 0, write 0, timeout 128
Requests/sec:   7342.71
Transfer/sec:      1.69MB
```

Before starting the next step, please kill all four instances by Ctrl+C. And check in
the httpchain folder we just created and updated. 


## ApiToApi Https

Now these APIs are working as a chain with Http connection. In this step, we are going to
change the connection to Https and see what is the performance difference. 

#### Prepare Environment

Before starting this step, let's create a folder called httpschain in each sub folder under
ms_chain and copy everything from httpchain folder to the httpschain. We are going to update
httpschain folder to have connections switched to Https by changing the urls in config files.

```
cd ~/networknt/light-example-4j/rest/ms_chain/api_a
cp -r httpchain httpschain
cd ~/networknt/light-example-4j/rest/ms_chain/api_b
cp -r httpchain httpschain
cd ~/networknt/light-example-4j/rest/ms_chain/api_c
cp -r httpchain httpschain
cd ~/networknt/light-example-4j/rest/ms_chain/api_d
cp -r httpchain httpschain

```

Now we have httpschain folder copied from httpchain and all updates in this step will be
in httpschain folder. 

#### API D

As we have the server.yml to enable both http and https so the server config for
API D is ready. However, we only tried to access the API D https port through curl with
security disabled. Java client access to API through https is not an easy task so we are
going to complete the test cases to test API D TLS connection with client module first.

This step also shows how to build end-to-end test case to test your API endpoints.

locate DataGetHandlerTest from src/test/com/networknt/apid/handler folder and change the
content to. 

```
package com.networknt.apid.handler;

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


public class DataGetHandlerTest {
    @ClassRule
    public static TestServer server = TestServer.getInstance();

    static final Logger logger = LoggerFactory.getLogger(DataGetHandlerTest.class);

    @Test
    public void testDataGetHandlerHttp() throws ClientException, ApiException {
        CloseableHttpClient client = Client.getInstance().getSyncClient();
        HttpGet httpGet = new HttpGet ("http://localhost:7004/v1/data");
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

    @Test
    public void testDataGetHandlerHttps() throws ClientException, ApiException {
        CloseableHttpClient client = Client.getInstance().getSyncClient();
        HttpGet httpGet = new HttpGet ("https://localhost:7444/v1/data");
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

As you have noticed, a new test case testDataGetHandlerHttps has been added and it access
the https url. If this test case works, that means API C should connect to API D with TLS
connection. 

As the server is started with self-signed key pair so we have to make sure client has a trust
store with server certificate in order to pass the server certificate verification. These keys
should be changed when you deploy to official environment.

In our test case, we are using our own client module to access the server and a client.yml is
already included as enableClient is true in config.json for the generator. client.yml file, 
it refers to client.truststore and client.keystore

These files are part of the client module and they are added automatically by light-codegen  

```
cd ~/networknt/light-example-4j/rest/ms_chain/api_d/httpschain
mvn clean install exec:exec
```

If above mvn command starts the server successfully, that means the test case we put in is
passed. 

#### API C
Let's leave API D running and update API C api_c.yml to use https url instead of
http. 

Locate api_c.yml in src/main/resources/config folder.

```
api_d_endpoint: "https://localhost:7444/v1/data"

```


Start API C server and test the endpoint /v1/data

```
cd ~/networknt/light-example-4j/rest/ms_chain/api_c/httpschain
mvn clean install exec:exec
```
From another terminal window run:

```
curl localhost:7003/v1/data
```
And the result is

```
["API D: Message 1","API D: Message 2","API C: Message 1","API C: Message 2"]
```

Access https port should have the same result.

```
curl -k https://localhost:7443/v1/data

```

#### API B

Let's keep API C and API D running and update api_b.yml in src/main/resources/config folder.

```
api_c_endpoint: "https://localhost:7443/v1/data"

```


Start API B server and test the endpoint /v1/data

```
cd ~/networknt/light-example-4j/rest/ms_chain/api_b/httpschain
mvn clean install exec:exec
```
From another terminal window run:

```
curl localhost:7002/v1/data
```
And the result is

```
["API D: Message 1","API D: Message 2","API C: Message 1","API C: Message 2","API B: Message 1","API B: Message 2"]
```

Here is the https port and the result is the same.

```
curl -k https://localhost:7442/v1/data

```


#### API A

API A will call API B to fulfill its request and we need to update api_a.yml in 
src/main/resources/config folder.

```
api_b_endpoint: "https://localhost:7442/v1/data"

```

Start API A server and test the endpoint /v1/data

```
cd ~/networknt/light-example-4j/rest/ms_chain/api_a/httpschain
mvn clean install exec:exec
```
From another terminal window run:

```
curl localhost:7001/v1/data
```
And the result is

```
["API D: Message 1","API D: Message 2","API C: Message 1","API C: Message 2","API B: Message 1","API B: Message 2","API A: Message 1","API A: Message 2"]
```

The https port and the result should be the same.

```
 curl -k https://localhost:7441/v1/data
```
At this moment, we have all four APIs completed and A is calling B, B is calling C and
C is calling D using Https connections.


## Performance with Https

Now let's see if these servers are performing with
[wrk](https://github.com/wg/wrk). To learn how to use it, please see my
article in tools [here](https://networknt.github.io/light-4j/tools/wrk-perf/)

Assume you have wrk installed, run the following command.

```
wrk -t4 -c128 -d30s http://localhost:7001 -s pipeline.lua --latency -- /v1/data 1024

```
And here is what I got on my i5 desktop

```
 wrk -t4 -c128 -d30s http://localhost:7001 -s pipeline.lua --latency -- /v1/data 1024
Running 30s test @ http://localhost:7001
  4 threads and 128 connections
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     0.00us    0.00us   0.00us    -nan%
    Req/Sec     1.06k   716.62     5.49k    73.45%
  Latency Distribution
     50%    0.00us
     75%    0.00us
     90%    0.00us
     99%    0.00us
  100572 requests in 30.06s, 23.12MB read
Requests/sec:   3345.62
Transfer/sec:    787.40KB
```

As you can see https connections are slower than http connections which is expected.

Before starting the next step, please kill all four instances by Ctrl+C. And check in
the httpschain folder we just created and updated. 


## Enable OAuth2 Security

So far, we've started four servers and tested them successfully with both Http and Https
connections; however, these servers are not protected by OAuth2 JWT tokens as it is turned off
by default in the generated code. Now let's turn on the security and check the performance with
JWT verification is on.

Before we turn on the security, we need to have [light-oauth2](https://github.com/networknt/light-oauth2)
server up and running so that these servers can get JWT token in real time.

When we enable security, the source code needs to be updated in order to leverage 
client module to get JWT token automatically. Let's prepare the environment. 

#### Update  APIs

Since we are going to change the code, let's copy each service into a new folder
called security from httpschain. 

```
cd ~/networknt/light-example-4j/rest/ms_chain/api_a
cp -r httpschain security
cd ~/networknt/light-example-4j/rest/ms_chain/api_b
cp -r httpschain security
cd ~/networknt/light-example-4j/rest/ms_chain/api_c
cp -r httpschain security
cd ~/networknt/light-example-4j/rest/ms_chain/api_d
cp -r httpschain security

```
Now for api_a, api_b and api_c we need to update DataGetHandler.java to add
a line before client.execute.

```
            Client.getInstance().propagateHeaders(httpGet, exchange);

```

Here is the updated file for api_a

```
package com.networknt.apia.handler;

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
    static String CONFIG_NAME = "api_a";
    static String apidUrl = (String) Config.getInstance().getJsonMapConfig(CONFIG_NAME).get("api_b_endpoint");

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        List<String> list = new ArrayList<String>();
        try {
            CloseableHttpClient client = Client.getInstance().getSyncClient();
            HttpGet httpGet = new HttpGet(apidUrl);
            Client.getInstance().propagateHeaders(httpGet, exchange);
            CloseableHttpResponse response = client.execute(httpGet);
            int responseCode = response.getStatusLine().getStatusCode();
            if(responseCode != 200){
                throw new Exception("Failed to call API B: " + responseCode);
            }
            List<String> apicList = (List<String>) Config.getInstance().getMapper().readValue(response.getEntity().getContent(),
                    new TypeReference<List<String>>(){});
            list.addAll(apicList);
        } catch (ClientException e) {
            throw new Exception("Client Exception: ", e);
        } catch (IOException e) {
            throw new Exception("IOException:", e);
        }
        // now add API B specific messages
        list.add("API A: Message 1");
        list.add("API A: Message 2");
        exchange.getResponseSender().send(Config.getInstance().getMapper().writeValueAsString(list));
    }
}
```

Follow api_a, update api_b and api_c.


#### Update Config

Now let's update security.yml to enable JWT verification and scope verification
for each service. This file is located at src/main/resources/config folder.

API A

old file

```
# Security configuration in light framework.
---
# Enable JWT verification flag.
enableVerifyJwt: false

# Enable JWT scope verification. Only valid when enableVerifyJwt is true.
enableVerifyScope: true

# User for test only. should be always be false on official environment.
enableMockJwt: false

# JWT signature public certificates. kid and certificate path mappings.
jwt:
  certificate:
    '100': oauth/primary.crt
    '101': oauth/secondary.crt
  clockSkewInSeconds: 60

# Enable or disable JWT token logging
logJwtToken: true

# Enable or disable client_id, user_id and scope logging.
logClientUserScope: false
```

Update to 

```
# Security configuration in light framework.
---
# Enable JWT verification flag.
enableVerifyJwt: true

# Enable JWT scope verification. Only valid when enableVerifyJwt is true.
enableVerifyScope: true

# User for test only. should be always be false on official environment.
enableMockJwt: false

# JWT signature public certificates. kid and certificate path mappings.
jwt:
  certificate:
    '100': oauth/primary.crt
    '101': oauth/secondary.crt
  clockSkewInSeconds: 60

# Enable or disable JWT token logging
logJwtToken: true

# Enable or disable client_id, user_id and scope logging.
logClientUserScope: false
```

Update the security.yml for api_b, api_c and api_d in security folder.


#### Start OAuth2 Services

The easiest way to run light-oauth2 services is through docker-compose. In the preparation
step, we have cloned light-docker repo. 

Let's start the light-oauth2 services from a docker compose.

```
cd ~/networknt/light-docker
docker-compose -f docker-compose-oauth2-mysql.yml up
```
Now the OAuth2 services are up and running. 

#### Register Client

Before we start integrate with OAuth2 services, we need to register clients for api_a,
api_b, api_c and api_d. This step should be done from light-portal for official environment.
After client registration, we need to remember the client_id and client_secret for each in
order to update client.yml for each service. Please note, API A, B and C are service and client
at the same time.

For more details on how to use the command line tool or script to access oauth2 services,
please see this [tutorial](https://networknt.github.io/light-oauth2/tutorials/enterprise/)

Register a client that calls api_a.

```
curl -H "Content-Type: application/json" -X POST -d '{"clientType":"public","clientProfile":"mobile","clientName":"Consumer","clientDesc":"A client that calls API A","scope":"api_a.r api_a.w","redirectUri": "http://localhost:8080/authorization","ownerId":"admin"}' http://localhost:6884/oauth2/client

```

The return value is something like this. You returned object should be different and you need to write down the clientId and clientSecret. 
```
{"clientId":"f0439841-fbe7-43a4-843e-ae0c51971a5e","clientSecret":"pu9aCVwmQjK2PET0_vOl9A","clientType":"public","clientProfile":"mobile","clientName":"Consumer","clientDesc":"A client that calls API A","ownerId":"admin","scope":"api_a.r api_a.w","redirectUri":"http://localhost:8080/authorization","createDt":"2017-03-30","updateDt":null}
```

To make it convenient, I have created a long lived token that can access api_a with api_a scopes. Here is token and you can verify that in jwt.io
 
```
eyJraWQiOiIxMDAiLCJhbGciOiJSUzI1NiJ9.eyJpc3MiOiJ1cm46Y29tOm5ldHdvcmtudDpvYXV0aDI6djEiLCJhdWQiOiJ1cm46Y29tLm5ldHdvcmtudCIsImV4cCI6MTgxMzAwNTAzNiwianRpIjoiN2daaHY2TS14UXpvVDhuNVMxODNodyIsImlhdCI6MTQ5NzY0NTAzNiwibmJmIjoxNDk3NjQ0OTE2LCJ2ZXJzaW9uIjoiMS4wIiwidXNlcl9pZCI6IlN0ZXZlIiwidXNlcl90eXBlIjoiRU1QTE9ZRUUiLCJjbGllbnRfaWQiOiJmN2Q0MjM0OC1jNjQ3LTRlZmItYTUyZC00YzU3ODc0MjFlNzIiLCJzY29wZSI6WyJhcGlfYS53IiwiYXBpX2IudyIsImFwaV9jLnciLCJhcGlfZC53Iiwic2VydmVyLmluZm8uciJdfQ.FkFbPTRXZf045_7fBlEPQTn7rNoib54TYQeFzSjLmMkUjrfDsJZD6EnrsAquDpHt8GKQNqGbyPzgiNWAIYHgwPZvM-lHw_dv0KUKii3D0woaFBkqu4vYxqyImROBii0B38evxPAZVONWqUncL21592bFPHsxGCz5oHL2unLv-oIQklWxcILpMrSL_tf7nhXHSu1RkRhshxAiAHSSpBZnluu4-jqZdEFtc5U_YApToUrKkmI_An1op5-6rS_I-fMbSnSctUoDgg3RT4Zvw1HC-ZLJlXWRF5-FD4uQOAOgy_T7PI75pNiuh4wgOGgdIf48X-7-fDkEbla-cVLiuj3z4g
```


Register a client for api_a to call api_b

```
curl -H "Content-Type: application/json" -X POST -d '{"clientType":"public","clientProfile":"service","clientName":"api_a","clientDesc":"API A service","scope":"api_b.r api_b.w","redirectUri": "http://localhost:8080/authorization","ownerId":"admin"}' http://localhost:6884/oauth2/client

```

And the result is something like this.

```
{"clientId":"9380563c-1598-4499-a01c-4abb013d3a49","clientSecret":"Cz0mNzDmQYuKIJNiUBh9nQ","clientType":"public","clientProfile":"service","clientName":"api_a","clientDesc":"API A service","ownerId":"admin","scope":"api_b.r api_b.w","redirectUri":"http://localhost:8080/authorization","createDt":"2017-06-16","updateDt":null}
```

Now we need to update client.yml and secret.yml with this clientId and clientSecret for
api_a. These config files are located in src/main/resources/config folder. 


Here is the client.yml for api_a. You can see that client_id and scope are updated based
on the result of the client registration. 


```
sync:
  maxConnectionTotal: 100
  maxConnectionPerRoute: 10
  routes:
    api.google.com: 20
    api.facebook.com: 10
  timeout: 10000
  keepAlive: 15000
async:
  maxConnectionTotal: 100
  maxConnectionPerRoute: 10
  routes:
    api.google.com: 20
    api.facebook.com: 10
  reactor:
    ioThreadCount: 1
    connectTimeout: 10000
    soTimeout: 10000
  timeout: 10000
  keepAlive: 15000
tls:
  # if the server is using self-signed certificate, this need to be false. If true, you have to use CA signed certificate
  # or load truststore that contains the self-signed cretificate.
  verifyHostname: true
  # trust store contains certifictes that server needs. Enable if tls is used.
  loadTrustStore: true
  # trust store location can be specified here or system properties javax.net.ssl.trustStore and password javax.net.ssl.trustStorePassword
  trustStore: tls/client.truststore
  # key store contains client key and it should be loaded if two-way ssl is uesed.
  loadKeyStore: false
  # key store location
  keyStore: tls/client.keystore
oauth:
  tokenRenewBeforeExpired: 600000
  expiredRefreshRetryDelay: 5000
  earlyRefreshRetryDelay: 30000
  # token server url. The default port number for token service is 6882.
  server_url: http://localhost:6882
  authorization_code:
    # token endpoint for authorization code grant
    uri: "/oauth2/token"
    # client_id for authorization code grant flow. client_secret is in secret.yml
    client_id: 9380563c-1598-4499-a01c-4abb013d3a49
    redirect_uri: https://localhost:8080/authorization_code
    scope:
    - api_b.r
    - api_b.w
  client_credentials:
    # token endpoint for client credentials grant
    uri: "/oauth2/token"
    # client_id for client credentials grant flow. client_secret is in secret.yml
    client_id: 9380563c-1598-4499-a01c-4abb013d3a49
    scope:
    - api_b.r
    - api_b.w
```

And here is the secret.yml for api_a and you can see that authorizationCodeClientSecret
and clientCredentialsClientSecret are updated.

```
# This file contains all the secrets for the server and client in order to manage and
# secure all of them in the same place. In Kubernetes, this file will be mapped to
# Secrets and all other config files will be mapped to mapConfig

---

# Sever section

# Key store password, the path of keystore is defined in server.yml
serverKeystorePass: password

# Key password, the key is in keystore
serverKeyPass: password

# Trust store password, the path of truststore is defined in server.yml
serverTruststorePass: password


# Client section

# Key store password, the path of keystore is defined in server.yml
clientKeystorePass: password

# Key password, the key is in keystore
clientKeyPass: password

# Trust store password, the path of truststore is defined in server.yml
clientTruststorePass: password

# Authorization code client secret for OAuth2 server
authorizationCodeClientSecret: Cz0mNzDmQYuKIJNiUBh9nQ

# Client credentials client secret for OAuth2 server
clientCredentialsClientSecret: Cz0mNzDmQYuKIJNiUBh9nQ


```


Register a client for api_b to call api_c

```
curl -H "Content-Type: application/json" -X POST -d '{"clientType":"public","clientProfile":"service","clientName":"api_b","clientDesc":"API B service","scope":"api_c.r api_c.w","redirectUri": "http://localhost:8080/authorization","ownerId":"admin"}' http://localhost:6884/oauth2/client

```

And the result is

```
{"clientId":"75e196ab-21c2-467b-9f2c-7d2f9720c1df","clientSecret":"zzO34AhITwWcuvAkPxW4HA","clientType":"public","clientProfile":"service","clientName":"api_b","clientDesc":"API B service","ownerId":"admin","scope":"api_c.r api_c.w","redirectUri":"http://localhost:8080/authorization","createDt":"2017-06-16","updateDt":null}
```

Now let's update client.yml for the client_id and scope like api_a and update secrets for
secret.yml

client.yml

```
sync:
  maxConnectionTotal: 100
  maxConnectionPerRoute: 10
  routes:
    api.google.com: 20
    api.facebook.com: 10
  timeout: 10000
  keepAlive: 15000
async:
  maxConnectionTotal: 100
  maxConnectionPerRoute: 10
  routes:
    api.google.com: 20
    api.facebook.com: 10
  reactor:
    ioThreadCount: 1
    connectTimeout: 10000
    soTimeout: 10000
  timeout: 10000
  keepAlive: 15000
tls:
  # if the server is using self-signed certificate, this need to be false. If true, you have to use CA signed certificate
  # or load truststore that contains the self-signed cretificate.
  verifyHostname: true
  # trust store contains certifictes that server needs. Enable if tls is used.
  loadTrustStore: true
  # trust store location can be specified here or system properties javax.net.ssl.trustStore and password javax.net.ssl.trustStorePassword
  trustStore: tls/client.truststore
  # key store contains client key and it should be loaded if two-way ssl is uesed.
  loadKeyStore: false
  # key store location
  keyStore: tls/client.keystore
oauth:
  tokenRenewBeforeExpired: 600000
  expiredRefreshRetryDelay: 5000
  earlyRefreshRetryDelay: 30000
  # token server url. The default port number for token service is 6882.
  server_url: http://localhost:6882
  authorization_code:
    # token endpoint for authorization code grant
    uri: "/oauth2/token"
    # client_id for authorization code grant flow. client_secret is in secret.yml
    client_id: 75e196ab-21c2-467b-9f2c-7d2f9720c1df
    redirect_uri: https://localhost:8080/authorization_code
    scope:
    - api_c.r
    - api_c.w
  client_credentials:
    # token endpoint for client credentials grant
    uri: "/oauth2/token"
    # client_id for client credentials grant flow. client_secret is in secret.yml
    client_id: 75e196ab-21c2-467b-9f2c-7d2f9720c1df
    scope:
    - api_c.r
    - api_c.w
```

secret.yml

```
# This file contains all the secrets for the server and client in order to manage and
# secure all of them in the same place. In Kubernetes, this file will be mapped to
# Secrets and all other config files will be mapped to mapConfig

---

# Sever section

# Key store password, the path of keystore is defined in server.yml
serverKeystorePass: password

# Key password, the key is in keystore
serverKeyPass: password

# Trust store password, the path of truststore is defined in server.yml
serverTruststorePass: password


# Client section

# Key store password, the path of keystore is defined in server.yml
clientKeystorePass: password

# Key password, the key is in keystore
clientKeyPass: password

# Trust store password, the path of truststore is defined in server.yml
clientTruststorePass: password

# Authorization code client secret for OAuth2 server
authorizationCodeClientSecret: zzO34AhITwWcuvAkPxW4HA

# Client credentials client secret for OAuth2 server
clientCredentialsClientSecret: zzO34AhITwWcuvAkPxW4HA

```

Register a client for api_c to call api_d

```
curl -H "Content-Type: application/json" -X POST -d '{"clientType":"public","clientProfile":"service","clientName":"api_c","clientDesc":"API C service","scope":"api_d.r api_d.w","redirectUri": "http://localhost:8080/authorization","ownerId":"admin"}' http://localhost:6884/oauth2/client

```

And the result is

```
{"clientId":"a366d5c1-d5c1-4ade-8b45-7db5e29859fb","clientSecret":"R16idrozRduTyLh6cysYKQ","clientType":"public","clientProfile":"service","clientName":"api_c","clientDesc":"API C service","ownerId":"admin","scope":"api_d.r api_d.w","redirectUri":"http://localhost:8080/authorization","createDt":"2017-06-16","updateDt":null}
```

We need to update client.yml and secret.yml according to the result of the registration

client.yml

```
sync:
  maxConnectionTotal: 100
  maxConnectionPerRoute: 10
  routes:
    api.google.com: 20
    api.facebook.com: 10
  timeout: 10000
  keepAlive: 15000
async:
  maxConnectionTotal: 100
  maxConnectionPerRoute: 10
  routes:
    api.google.com: 20
    api.facebook.com: 10
  reactor:
    ioThreadCount: 1
    connectTimeout: 10000
    soTimeout: 10000
  timeout: 10000
  keepAlive: 15000
tls:
  # if the server is using self-signed certificate, this need to be false. If true, you have to use CA signed certificate
  # or load truststore that contains the self-signed cretificate.
  verifyHostname: true
  # trust store contains certifictes that server needs. Enable if tls is used.
  loadTrustStore: true
  # trust store location can be specified here or system properties javax.net.ssl.trustStore and password javax.net.ssl.trustStorePassword
  trustStore: tls/client.truststore
  # key store contains client key and it should be loaded if two-way ssl is uesed.
  loadKeyStore: false
  # key store location
  keyStore: tls/client.keystore
oauth:
  tokenRenewBeforeExpired: 600000
  expiredRefreshRetryDelay: 5000
  earlyRefreshRetryDelay: 30000
  # token server url. The default port number for token service is 6882.
  server_url: http://localhost:6882
  authorization_code:
    # token endpoint for authorization code grant
    uri: "/oauth2/token"
    # client_id for authorization code grant flow. client_secret is in secret.yml
    client_id: a366d5c1-d5c1-4ade-8b45-7db5e29859fb
    redirect_uri: https://localhost:8080/authorization_code
    scope:
    - api_d.r
    - api_d.w
  client_credentials:
    # token endpoint for client credentials grant
    uri: "/oauth2/token"
    # client_id for client credentials grant flow. client_secret is in secret.yml
    client_id: a366d5c1-d5c1-4ade-8b45-7db5e29859fb
    scope:
    - api_d.r
    - api_d.w

```

secret.yml

```
# This file contains all the secrets for the server and client in order to manage and
# secure all of them in the same place. In Kubernetes, this file will be mapped to
# Secrets and all other config files will be mapped to mapConfig

---

# Sever section

# Key store password, the path of keystore is defined in server.yml
serverKeystorePass: password

# Key password, the key is in keystore
serverKeyPass: password

# Trust store password, the path of truststore is defined in server.yml
serverTruststorePass: password


# Client section

# Key store password, the path of keystore is defined in server.yml
clientKeystorePass: password

# Key password, the key is in keystore
clientKeyPass: password

# Trust store password, the path of truststore is defined in server.yml
clientTruststorePass: password

# Authorization code client secret for OAuth2 server
authorizationCodeClientSecret: R16idrozRduTyLh6cysYKQ

# Client credentials client secret for OAuth2 server
clientCredentialsClientSecret: R16idrozRduTyLh6cysYKQ

```

API D is not calling any other API so it is not a client and doesn't need to be registered. However,
in the previous step httpschain, we have updated the test cases for api_d to and here we have enabled
the security and these two tests will fail as there is no security token in the test request. Let's
update the test case to put long lived token in it. 

Here is the updated test class. 

```
package com.networknt.apid.handler;

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


public class DataGetHandlerTest {
    @ClassRule
    public static TestServer server = TestServer.getInstance();

    static final Logger logger = LoggerFactory.getLogger(DataGetHandlerTest.class);

    @Test
    public void testDataGetHandlerHttp() throws ClientException, ApiException {
        CloseableHttpClient client = Client.getInstance().getSyncClient();
        HttpGet httpGet = new HttpGet ("http://localhost:7004/v1/data");
        httpGet.setHeader("Authorization", "Bearer eyJraWQiOiIxMDAiLCJhbGciOiJSUzI1NiJ9.eyJpc3MiOiJ1cm46Y29tOm5ldHdvcmtudDpvYXV0aDI6djEiLCJhdWQiOiJ1cm46Y29tLm5ldHdvcmtudCIsImV4cCI6MTgxMzAwNTAzNiwianRpIjoiN2daaHY2TS14UXpvVDhuNVMxODNodyIsImlhdCI6MTQ5NzY0NTAzNiwibmJmIjoxNDk3NjQ0OTE2LCJ2ZXJzaW9uIjoiMS4wIiwidXNlcl9pZCI6IlN0ZXZlIiwidXNlcl90eXBlIjoiRU1QTE9ZRUUiLCJjbGllbnRfaWQiOiJmN2Q0MjM0OC1jNjQ3LTRlZmItYTUyZC00YzU3ODc0MjFlNzIiLCJzY29wZSI6WyJhcGlfYS53IiwiYXBpX2IudyIsImFwaV9jLnciLCJhcGlfZC53Iiwic2VydmVyLmluZm8uciJdfQ.FkFbPTRXZf045_7fBlEPQTn7rNoib54TYQeFzSjLmMkUjrfDsJZD6EnrsAquDpHt8GKQNqGbyPzgiNWAIYHgwPZvM-lHw_dv0KUKii3D0woaFBkqu4vYxqyImROBii0B38evxPAZVONWqUncL21592bFPHsxGCz5oHL2unLv-oIQklWxcILpMrSL_tf7nhXHSu1RkRhshxAiAHSSpBZnluu4-jqZdEFtc5U_YApToUrKkmI_An1op5-6rS_I-fMbSnSctUoDgg3RT4Zvw1HC-ZLJlXWRF5-FD4uQOAOgy_T7PI75pNiuh4wgOGgdIf48X-7-fDkEbla-cVLiuj3z4g");
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

    @Test
    public void testDataGetHandlerHttps() throws ClientException, ApiException {
        CloseableHttpClient client = Client.getInstance().getSyncClient();
        HttpGet httpGet = new HttpGet ("https://localhost:7444/v1/data");
        httpGet.setHeader("Authorization", "Bearer eyJraWQiOiIxMDAiLCJhbGciOiJSUzI1NiJ9.eyJpc3MiOiJ1cm46Y29tOm5ldHdvcmtudDpvYXV0aDI6djEiLCJhdWQiOiJ1cm46Y29tLm5ldHdvcmtudCIsImV4cCI6MTgxMzAwNTAzNiwianRpIjoiN2daaHY2TS14UXpvVDhuNVMxODNodyIsImlhdCI6MTQ5NzY0NTAzNiwibmJmIjoxNDk3NjQ0OTE2LCJ2ZXJzaW9uIjoiMS4wIiwidXNlcl9pZCI6IlN0ZXZlIiwidXNlcl90eXBlIjoiRU1QTE9ZRUUiLCJjbGllbnRfaWQiOiJmN2Q0MjM0OC1jNjQ3LTRlZmItYTUyZC00YzU3ODc0MjFlNzIiLCJzY29wZSI6WyJhcGlfYS53IiwiYXBpX2IudyIsImFwaV9jLnciLCJhcGlfZC53Iiwic2VydmVyLmluZm8uciJdfQ.FkFbPTRXZf045_7fBlEPQTn7rNoib54TYQeFzSjLmMkUjrfDsJZD6EnrsAquDpHt8GKQNqGbyPzgiNWAIYHgwPZvM-lHw_dv0KUKii3D0woaFBkqu4vYxqyImROBii0B38evxPAZVONWqUncL21592bFPHsxGCz5oHL2unLv-oIQklWxcILpMrSL_tf7nhXHSu1RkRhshxAiAHSSpBZnluu4-jqZdEFtc5U_YApToUrKkmI_An1op5-6rS_I-fMbSnSctUoDgg3RT4Zvw1HC-ZLJlXWRF5-FD4uQOAOgy_T7PI75pNiuh4wgOGgdIf48X-7-fDkEbla-cVLiuj3z4g");
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


Now we need to rebuild and restart API A, B, C and D in four terminals. 

```
cd ~/networknt/light-example-4j/rest/ms_chain/api_a/security
mvn clean install exec:exec
```

```
cd ~/networknt/light-example-4j/rest/ms_chain/api_b/security
mvn clean install exec:exec
```

```
cd ~/networknt/light-example-4j/rest/ms_chain/api_c/security
mvn clean install exec:exec
```

```
cd ~/networknt/light-example-4j/rest/ms_chain/api_d/security
mvn clean install exec:exec

```


Now let's test it with a JWT token for the https port on api_a. For this moment on, 
we are going to use https all the time as it is required if OAuth2 is enabled.

```
curl -k -H "Authorization: Bearer eyJraWQiOiIxMDAiLCJhbGciOiJSUzI1NiJ9.eyJpc3MiOiJ1cm46Y29tOm5ldHdvcmtudDpvYXV0aDI6djEiLCJhdWQiOiJ1cm46Y29tLm5ldHdvcmtudCIsImV4cCI6MTgxMzAwNTAzNiwianRpIjoiN2daaHY2TS14UXpvVDhuNVMxODNodyIsImlhdCI6MTQ5NzY0NTAzNiwibmJmIjoxNDk3NjQ0OTE2LCJ2ZXJzaW9uIjoiMS4wIiwidXNlcl9pZCI6IlN0ZXZlIiwidXNlcl90eXBlIjoiRU1QTE9ZRUUiLCJjbGllbnRfaWQiOiJmN2Q0MjM0OC1jNjQ3LTRlZmItYTUyZC00YzU3ODc0MjFlNzIiLCJzY29wZSI6WyJhcGlfYS53IiwiYXBpX2IudyIsImFwaV9jLnciLCJhcGlfZC53Iiwic2VydmVyLmluZm8uciJdfQ.FkFbPTRXZf045_7fBlEPQTn7rNoib54TYQeFzSjLmMkUjrfDsJZD6EnrsAquDpHt8GKQNqGbyPzgiNWAIYHgwPZvM-lHw_dv0KUKii3D0woaFBkqu4vYxqyImROBii0B38evxPAZVONWqUncL21592bFPHsxGCz5oHL2unLv-oIQklWxcILpMrSL_tf7nhXHSu1RkRhshxAiAHSSpBZnluu4-jqZdEFtc5U_YApToUrKkmI_An1op5-6rS_I-fMbSnSctUoDgg3RT4Zvw1HC-ZLJlXWRF5-FD4uQOAOgy_T7PI75pNiuh4wgOGgdIf48X-7-fDkEbla-cVLiuj3z4g" https://localhost:7441/v1/data
```

And you should have the normal result. 


## Performance with Security

Since we have both https and OAuth2 involved, we are going to test the performance again. 

```
wrk -t4 -c128 -d30s -H "Authorization: Bearer eyJraWQiOiIxMDAiLCJhbGciOiJSUzI1NiJ9.eyJpc3MiOiJ1cm46Y29tOm5ldHdvcmtudDpvYXV0aDI6djEiLCJhdWQiOiJ1cm46Y29tLm5ldHdvcmtudCIsImV4cCI6MTgxMzAwNTAzNiwianRpIjoiN2daaHY2TS14UXpvVDhuNVMxODNodyIsImlhdCI6MTQ5NzY0NTAzNiwibmJmIjoxNDk3NjQ0OTE2LCJ2ZXJzaW9uIjoiMS4wIiwidXNlcl9pZCI6IlN0ZXZlIiwidXNlcl90eXBlIjoiRU1QTE9ZRUUiLCJjbGllbnRfaWQiOiJmN2Q0MjM0OC1jNjQ3LTRlZmItYTUyZC00YzU3ODc0MjFlNzIiLCJzY29wZSI6WyJhcGlfYS53IiwiYXBpX2IudyIsImFwaV9jLnciLCJhcGlfZC53Iiwic2VydmVyLmluZm8uciJdfQ.FkFbPTRXZf045_7fBlEPQTn7rNoib54TYQeFzSjLmMkUjrfDsJZD6EnrsAquDpHt8GKQNqGbyPzgiNWAIYHgwPZvM-lHw_dv0KUKii3D0woaFBkqu4vYxqyImROBii0B38evxPAZVONWqUncL21592bFPHsxGCz5oHL2unLv-oIQklWxcILpMrSL_tf7nhXHSu1RkRhshxAiAHSSpBZnluu4-jqZdEFtc5U_YApToUrKkmI_An1op5-6rS_I-fMbSnSctUoDgg3RT4Zvw1HC-ZLJlXWRF5-FD4uQOAOgy_T7PI75pNiuh4wgOGgdIf48X-7-fDkEbla-cVLiuj3z4g" https://localhost:7441 -s pipeline.lua --latency -- /v1/data 1024
```

And result

```
Running 30s test @ https://localhost:7441
  4 threads and 128 connections
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     0.00us    0.00us   0.00us    -nan%
    Req/Sec   484.87    476.28     3.43k    88.89%
  Latency Distribution
     50%    0.00us
     75%    0.00us
     90%    0.00us
     99%    0.00us
  22576 requests in 30.10s, 5.19MB read
Requests/sec:    750.14
Transfer/sec:    176.55KB

```

As you can see the performance dropped a lot due to JWT verification. We will explore some
optimization later on with JWT verification. But is it out of scope for this tutorial.



## Enable Metrics

All services can be configed to enable metrics collection and report metrics info to Influxdb 
and subsequently viewed from Grafana dashboard. 

If InfluxDB is not available the report will be a noop.

In order to output to the right Influxdb instance, we need to create metrics.yml in
src/main/resources/config folder to overwrite the default configuration. 


Let's create a new folder metrics by copying the security folder.

```
cd ~/networknt/light-example-4j/rest/ms_chain/api_a
cp -r security metrics
cd ~/networknt/light-example-4j/rest/ms_chain/api_b
cp -r security metrics
cd ~/networknt/light-example-4j/rest/ms_chain/api_c
cp -r security metrics
cd ~/networknt/light-example-4j/rest/ms_chain/api_d
cp -r security metrics

```

Now we need to add the following metrics.yml to each api resources/config.

```
# Metrics handler configuration

# If metrics handler is enabled or not
enabled: true

# influxdb protocal can be http, https
influxdbProtocol: http
# influxdb hostname
influxdbHost: localhost
# influxdb port number
influxdbPort: 8086
# influxdb database name
influxdbName: metrics
# influxdb user
influxdbUser: root
# influx db password
influxdbPass: root
# report and reset metrics in minutes.
reportInMinutes: 1

```

Now let's start Influxdb and Grafana from docker-compose-metrics.yml in light-docker.
The light-docker repo should have been checked out at preparation step.

```
cd ~/networknt/light-docker
docker-compose -f docker-compose-metrics.yml up
```

Now let's start four APIs from four terminals. 

```
cd ~/networknt/light-example-4j/rest/ms_chain/api_a/metrics
mvn clean install exec:exec
```

```
cd ~/networknt/light-example-4j/rest/ms_chain/api_b/metrics
mvn clean install exec:exec
```

```
cd ~/networknt/light-example-4j/rest/ms_chain/api_c/metrics
mvn clean install exec:exec
```

```
cd ~/networknt/light-example-4j/rest/ms_chain/api_d/metrics
mvn clean install exec:exec

```


Let's use curl to access API A, this time I am using a long lived token I generated 
from a utility.

```
eyJraWQiOiIxMDAiLCJhbGciOiJSUzI1NiJ9.eyJpc3MiOiJ1cm46Y29tOm5ldHdvcmtudDpvYXV0aDI6djEiLCJhdWQiOiJ1cm46Y29tLm5ldHdvcmtudCIsImV4cCI6MTgwNjIwMDY2MSwianRpIjoibmQtb2ZZbWRIY0JZTUlEYU50MUFudyIsImlhdCI6MTQ5MDg0MDY2MSwibmJmIjoxNDkwODQwNTQxLCJ2ZXJzaW9uIjoiMS4wIiwidXNlcl9pZCI6IlN0ZXZlIiwidXNlcl90eXBlIjoiRU1QTE9ZRUUiLCJjbGllbnRfaWQiOiJmN2Q0MjM0OC1jNjQ3LTRlZmItYTUyZC00YzU3ODc0MjFlNzIiLCJzY29wZSI6WyJhcGlfYS53IiwiYXBpX2IudyIsImFwaV9jLnciLCJhcGlfZC53Iiwic2VydmVyLmluZm8uciJdfQ.SPHICXRY4SuUvWf0NYtwUrQ2-N-NeYT3b4CvxbzNl7D7GL5CF91G3siECrRBVexe0smBHHeiP3bq65rnCVFtwlYYqH6ZS5P7-AFiNcLBzSI9-OhV8JSf5sv381nk2f41IE4av2YUlgY0_mcIDo24ItnuPCxj0l49CAaLb7b1SHZJBQJANJTeQj-wgFsEqwafA-2wH2gehtH8CmOuuYfWO5t5IehP-zJNVT66E4UTRfvvZaJIvNTEQBWPpaZeeK6e56SyBqaLOR7duqJZ8a2UQZRWsDdIVt2Y5jGXQu1gyenIvCQbYLS6iglg6Xaco9emnYFopd2i3psathuX367fvw

```

```
curl -k -H "Authorization: Bearer eyJraWQiOiIxMDAiLCJhbGciOiJSUzI1NiJ9.eyJpc3MiOiJ1cm46Y29tOm5ldHdvcmtudDpvYXV0aDI6djEiLCJhdWQiOiJ1cm46Y29tLm5ldHdvcmtudCIsImV4cCI6MTgwNjIwMDY2MSwianRpIjoibmQtb2ZZbWRIY0JZTUlEYU50MUFudyIsImlhdCI6MTQ5MDg0MDY2MSwibmJmIjoxNDkwODQwNTQxLCJ2ZXJzaW9uIjoiMS4wIiwidXNlcl9pZCI6IlN0ZXZlIiwidXNlcl90eXBlIjoiRU1QTE9ZRUUiLCJjbGllbnRfaWQiOiJmN2Q0MjM0OC1jNjQ3LTRlZmItYTUyZC00YzU3ODc0MjFlNzIiLCJzY29wZSI6WyJhcGlfYS53IiwiYXBpX2IudyIsImFwaV9jLnciLCJhcGlfZC53Iiwic2VydmVyLmluZm8uciJdfQ.SPHICXRY4SuUvWf0NYtwUrQ2-N-NeYT3b4CvxbzNl7D7GL5CF91G3siECrRBVexe0smBHHeiP3bq65rnCVFtwlYYqH6ZS5P7-AFiNcLBzSI9-OhV8JSf5sv381nk2f41IE4av2YUlgY0_mcIDo24ItnuPCxj0l49CAaLb7b1SHZJBQJANJTeQj-wgFsEqwafA-2wH2gehtH8CmOuuYfWO5t5IehP-zJNVT66E4UTRfvvZaJIvNTEQBWPpaZeeK6e56SyBqaLOR7duqJZ8a2UQZRWsDdIVt2Y5jGXQu1gyenIvCQbYLS6iglg6Xaco9emnYFopd2i3psathuX367fvw" https://localhost:7441/v1/data
```

Send several request from curl and let's take a look at influxdb console at
http://localhost:8083

On the console, select the database to metrics and select from Query templates with 
"SHOW MEASUREMENTS" and you can see something like the following picture.

![influx_console](/images/influx_console.png)

Pick one of the measurement and create a query and you can see the content of the table.

```
select * from "com.networknt.apia-1.0.0.request.count"
```

![api_a_metrics](/images/api_a_metrics.png)


If you want to take a look at metrics from one of client's perspective, issue the following query

```
select * from "f7d42348-c647-4efb-a52d-4c5787421e72.request.count"
```

![client_metrics](/images/client_metrics.png)


## Docker Compose

So far, we have four servers running and they can talk to each other as standalone
services. In this step, we are going to dockerize all of them and start them with a
docker-compose up command. 

Before doing that, let's create two folders under ms_chain for different version of
compose files and externalized config files.

```
cd ~/networknt/light-example-4j/rest/ms_chain
mkdir compose
mkdir etc
```

Now in the etc folder, we need to create sub folders for each api and config sub folder


```
cd ~/networknt/light-example-4j/rest/ms_chain/etc
mkdir api_a
mkdir api_b
mkdir api_c
mkdir api_d

cd api_a
mkdir config
cd ../api_b
mkdir config
cd ../api_c
mkdir config
cd ../api_d
mkdir config
```


Let's create a rest/ms_chain/compose/docker-compose-app.yml.

```
#
# docker-compose-app.yml
#

version: '2'

#
# Services
#
services:


    #
    # Microservice: API A
    #
    apia:
        build: ~/networknt/light-example-4j/rest/ms_chain/api_a/metrics/
        ports:
            - "7441:7441"
        networks:
            - localnet
        volumes:
            - ~/networknt/light-example-4j/rest/ms_chain/etc/api_a/config:/config

    #
    # Microservice: API B
    #
    apib:
        build: ~/networknt/light-example-4j/rest/ms_chain/api_b/metrics/
        ports:
            - "7002:7002"
        networks:
            - localnet
        volumes:
            - ~/networknt/light-example-4j/rest/ms_chain/etc/api_b/config:/config

    #
    # Microservice: API C
    #
    apic:
        build: ~/networknt/light-example-4j/rest/ms_chain/api_c/metrics/
        ports:
            - "7003:7003"
        networks:
            - localnet
        volumes:
            - ~/networknt/light-example-4j/rest/ms_chain/etc/api_c/config:/config

    #
    # Microservice: API D
    #
    apid:
        build: ~/networknt/light-example-4j/rest/ms_chain/api_d/metrics/
        ports:
            - "7004:7004"
        networks:
            - localnet
        volumes:
            - ~/networknt/light-example-4j/rest/ms_chain/etc/api_d/config:/config

#
# Networks
#
networks:
    localnet:
        external: true
        
```


From above docker compose file you can see we have a volume for each api with externalized
configuration folder under ms_chain/etc/api_x/config. Since we are using docker compose
we cannot use localhost:port to call services and we have to use service name in 
docker-compose-app.yml for the hostname. To resolve the issue without rebuilding the services
we are going to externalize api_a.yml, api_b.yml and api_c.yml to their externalized config
folder.

Let's create api_a.yml in ms_chain/etc/api_a/config folder. 

```
api_b_endpoint: "https://apib:7442/v1/data"

```
Please note that apib is the name of the service in docker-compose-app.yml

Create api_b.yml in ms_chain/etc/api_b/config folder

```
api_c_endpoint: "https://apic:7443/v1/data"

```

Create api_c.yml in ms_chain/etc/api_c/config folder

```
api_d_endpoint: "https://apid:7444/v1/data"

```

As each API needs to access OAuth2 server to get access token, so we need to update
client.yml to point to the service name in docker-compose instead of localhost. 

Now, let's copy the client.yml from metrics folder for each API into ms_chain/etc/api_x/config
folder and update the url for OAuth2 server. 

```
cp ~/networknt/light-example-4j/rest/ms_chain/api_a/metrics/src/main/resources/config/client.yml ~/networknt/light-example-4j/rest/ms_chain/etc/api_a/config
cp ~/networknt/light-example-4j/rest/ms_chain/api_b/metrics/src/main/resources/config/client.yml ~/networknt/light-example-4j/rest/ms_chain/etc/api_b/config
cp ~/networknt/light-example-4j/rest/ms_chain/api_c/metrics/src/main/resources/config/client.yml ~/networknt/light-example-4j/rest/ms_chain/etc/api_c/config
```

The api_a client.yml in ms_chain/etc/api_a/config folder looks like this after modification. 

```
sync:
  maxConnectionTotal: 100
  maxConnectionPerRoute: 10
  routes:
    api.google.com: 20
    api.facebook.com: 10
  timeout: 10000
  keepAlive: 15000
async:
  maxConnectionTotal: 100
  maxConnectionPerRoute: 10
  routes:
    api.google.com: 20
    api.facebook.com: 10
  reactor:
    ioThreadCount: 1
    connectTimeout: 10000
    soTimeout: 10000
  timeout: 10000
  keepAlive: 15000
tls:
  # if the server is using self-signed certificate, this need to be false. If true, you have to use CA signed certificate
  # or load truststore that contains the self-signed cretificate.
  verifyHostname: false
  # trust store contains certifictes that server needs. Enable if tls is used.
  loadTrustStore: true
  # trust store location can be specified here or system properties javax.net.ssl.trustStore and password javax.net.ssl.trustStorePassword
  trustStore: tls/client.truststore
  # key store contains client key and it should be loaded if two-way ssl is uesed.
  loadKeyStore: false
  # key store location
  keyStore: tls/client.keystore
oauth:
  tokenRenewBeforeExpired: 600000
  expiredRefreshRetryDelay: 5000
  earlyRefreshRetryDelay: 30000
  # token server url. The default port number for token service is 6882.
  server_url: http://oauth2-token:6882
  authorization_code:
    # token endpoint for authorization code grant
    uri: "/oauth2/token"
    # client_id for authorization code grant flow. client_secret is in secret.yml
    client_id: 9380563c-1598-4499-a01c-4abb013d3a49
    redirect_uri: https://localhost:8080/authorization_code
    scope:
    - api_b.r
    - api_b.w
  client_credentials:
    # token endpoint for client credentials grant
    uri: "/oauth2/token"
    # client_id for client credentials grant flow. client_secret is in secret.yml
    client_id: 9380563c-1598-4499-a01c-4abb013d3a49
    scope:
    - api_b.r
    - api_b.w

```
As you can see the server_url has been change to "http://oauth2-token:6882" and
tls/verifyHostname is changed to false as we are using self-signed certificate and hostname
has been changed to something other than localhost.

Now in order to make the metrics works, we need to copy the metrics.yml to the config folder
and modify it for the influxdb hostname.

```
cp ~/networknt/light-example-4j/rest/ms_chain/api_a/metrics/src/main/resources/config/metrics.yml ~/networknt/light-example-4j/rest/ms_chain/etc/api_a/config
cp ~/networknt/light-example-4j/rest/ms_chain/api_b/metrics/src/main/resources/config/metrics.yml ~/networknt/light-example-4j/rest/ms_chain/etc/api_b/config
cp ~/networknt/light-example-4j/rest/ms_chain/api_c/metrics/src/main/resources/config/metrics.yml ~/networknt/light-example-4j/rest/ms_chain/etc/api_c/config
cp ~/networknt/light-example-4j/rest/ms_chain/api_d/metrics/src/main/resources/config/metrics.yml ~/networknt/light-example-4j/rest/ms_chain/etc/api_d/config
```

The api_a metrics.yml in ms_chain/etc/api_a/config folder looks like this after modification. 

```
# Metrics handler configuration

# If metrics handler is enabled or not
enabled: true

# influxdb protocal can be http, https
influxdbProtocol: http
# influxdb hostname
influxdbHost: influxdb
# influxdb port number
influxdbPort: 8086
# influxdb database name
influxdbName: metrics
# influxdb user
influxdbUser: root
# influx db password
influxdbPass: root
# report and reset metrics in minutes.
reportInMinutes: 1

```


Now let's start the docker compose. 

```
cd ~/networknt/light-example-4j/rest/ms_chain/compose
docker-compose -f docker-compose-app.yml up
```

Let's test if the servers are working.

```
curl -k -H "Authorization: Bearer eyJraWQiOiIxMDAiLCJhbGciOiJSUzI1NiJ9.eyJpc3MiOiJ1cm46Y29tOm5ldHdvcmtudDpvYXV0aDI6djEiLCJhdWQiOiJ1cm46Y29tLm5ldHdvcmtudCIsImV4cCI6MTgwNjIwMDY2MSwianRpIjoibmQtb2ZZbWRIY0JZTUlEYU50MUFudyIsImlhdCI6MTQ5MDg0MDY2MSwibmJmIjoxNDkwODQwNTQxLCJ2ZXJzaW9uIjoiMS4wIiwidXNlcl9pZCI6IlN0ZXZlIiwidXNlcl90eXBlIjoiRU1QTE9ZRUUiLCJjbGllbnRfaWQiOiJmN2Q0MjM0OC1jNjQ3LTRlZmItYTUyZC00YzU3ODc0MjFlNzIiLCJzY29wZSI6WyJhcGlfYS53IiwiYXBpX2IudyIsImFwaV9jLnciLCJhcGlfZC53Iiwic2VydmVyLmluZm8uciJdfQ.SPHICXRY4SuUvWf0NYtwUrQ2-N-NeYT3b4CvxbzNl7D7GL5CF91G3siECrRBVexe0smBHHeiP3bq65rnCVFtwlYYqH6ZS5P7-AFiNcLBzSI9-OhV8JSf5sv381nk2f41IE4av2YUlgY0_mcIDo24ItnuPCxj0l49CAaLb7b1SHZJBQJANJTeQj-wgFsEqwafA-2wH2gehtH8CmOuuYfWO5t5IehP-zJNVT66E4UTRfvvZaJIvNTEQBWPpaZeeK6e56SyBqaLOR7duqJZ8a2UQZRWsDdIVt2Y5jGXQu1gyenIvCQbYLS6iglg6Xaco9emnYFopd2i3psathuX367fvw" https://localhost:7441/v1/data

```

And we will have the result.

```
["API D: Message 1","API D: Message 2","API C: Message 1","API C: Message 2","API B: Message 1","API B: Message 2","API A: Message 1","API A: Message 2"]
```


## Performance with Docker Compose

Let's run a load test now.

```
wrk -t4 -c128 -d30s -H "Authorization: Bearer eyJraWQiOiIxMDAiLCJhbGciOiJSUzI1NiJ9.eyJpc3MiOiJ1cm46Y29tOm5ldHdvcmtudDpvYXV0aDI6djEiLCJhdWQiOiJ1cm46Y29tLm5ldHdvcmtudCIsImV4cCI6MTgxMzAwNTAzNiwianRpIjoiN2daaHY2TS14UXpvVDhuNVMxODNodyIsImlhdCI6MTQ5NzY0NTAzNiwibmJmIjoxNDk3NjQ0OTE2LCJ2ZXJzaW9uIjoiMS4wIiwidXNlcl9pZCI6IlN0ZXZlIiwidXNlcl90eXBlIjoiRU1QTE9ZRUUiLCJjbGllbnRfaWQiOiJmN2Q0MjM0OC1jNjQ3LTRlZmItYTUyZC00YzU3ODc0MjFlNzIiLCJzY29wZSI6WyJhcGlfYS53IiwiYXBpX2IudyIsImFwaV9jLnciLCJhcGlfZC53Iiwic2VydmVyLmluZm8uciJdfQ.FkFbPTRXZf045_7fBlEPQTn7rNoib54TYQeFzSjLmMkUjrfDsJZD6EnrsAquDpHt8GKQNqGbyPzgiNWAIYHgwPZvM-lHw_dv0KUKii3D0woaFBkqu4vYxqyImROBii0B38evxPAZVONWqUncL21592bFPHsxGCz5oHL2unLv-oIQklWxcILpMrSL_tf7nhXHSu1RkRhshxAiAHSSpBZnluu4-jqZdEFtc5U_YApToUrKkmI_An1op5-6rS_I-fMbSnSctUoDgg3RT4Zvw1HC-ZLJlXWRF5-FD4uQOAOgy_T7PI75pNiuh4wgOGgdIf48X-7-fDkEbla-cVLiuj3z4g" https://localhost:7441 -s pipeline.lua --latency -- /v1/data 1024
```

And the result.

```
Running 30s test @ https://localhost:7441
  4 threads and 128 connections
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     0.00us    0.00us   0.00us    -nan%
    Req/Sec   482.32    418.96     3.40k    83.98%
  Latency Distribution
     50%    0.00us
     75%    0.00us
     90%    0.00us
     99%    0.00us
  21556 requests in 30.05s, 4.95MB read
Requests/sec:    717.22
Transfer/sec:    168.80KB
```

## Kubernetes

## Performance with Kubernetes

## Conclusion


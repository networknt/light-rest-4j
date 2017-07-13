---
date: 2017-07-12T16:37:05-04:00
title: Branch Pattern Microservices
---

This is another pattern that is very useful which is a hybrid of chain pattern and aggregate
pattern.

This tutorial shows you how to build 4 services in branch pattern. 

```

API A -> API B -> API D
          -> API C
```

API A calls API B and API C. API B calls API D.

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

As we are going to regenerate API A, B, C and D, let's rename ms_branch folder from
light-example-4j.

```
cd ~/networknt/light-example-4j/rest
mv ms_branch ms_branch.bak
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

API A will call API B, API C and API D

```
API A -> API B -> API D 
          -> API C 
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
to start four projects in light-example-4j/rest/ms_branch. In normal API build, you 
should create a repo for each API. For us, we have to user light-example-4j for all the
examples and tutorial for easy management in networknt github organization.

#### Build light-codege

The project is cloned to the local already during the prepare stage. Let's build it.

```
cd ~/networknt/light-codegen
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
module will be included as it will call API B, C, D with it. DB dependencies are not required for
this tutorial.


#### Generate first project

Now you have your light-codegen built, let's generate a project. Assume that
model-config, light-example-4j and light-codegen are in the same working
directory ~/networknt and you are in ~/networknt/light-codegen now.

```
cd ~/networknt/light-codegen
java -jar codegen-cli/target/codegen-cli.jar -f light-rest-4j -o ../light-example-4j/rest/ms_branch/api_a/generated -m ../model-config/rest/api_a/1.0.0/swagger.json -c ../model-config/rest/api_a/1.0.0/config.json
```

#### Build and run the mock API

And now you have a new project created in light-example-4j/rest/ms_branch/api_a/generated. 
Let's build it and run the test cases. If everything is OK, start the server.

```
cd ..
cd light-example-4j/rest/ms_branch/api_a/generated
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
java -jar codegen-cli/target/codegen-cli.jar -f light-rest-4j -o ../light-example-4j/rest/ms_branch/api_b/generated -m ../model-config/rest/api_b/1.0.0/swagger.json -c ../model-config/rest/api_b/1.0.0/config.json
java -jar codegen-cli/target/codegen-cli.jar -f light-rest-4j -o ../light-example-4j/rest/ms_branch/api_c/generated -m ../model-config/rest/api_c/1.0.0/swagger.json -c ../model-config/rest/api_c/1.0.0/config.json
java -jar codegen-cli/target/codegen-cli.jar -f light-rest-4j -o ../light-example-4j/rest/ms_branch/api_d/generated -m ../model-config/rest/api_d/1.0.0/swagger.json -c ../model-config/rest/api_d/1.0.0/config.json
```

Now you have four APIs generated from four OpenAPI specifications. Let's check
them in. Note that you might not have write access to this repo, so you can ignore
this step. 

```
cd ../light-example-4j
git add .
git commit -m "checkin 4 apis in ms_branch"
git push origin master
```

## ApiToApi Http

Now these APIs are working if you start them and they will output the mock responses
generated based on the API specifications. Let's take a look at the API handler itself
and update it based on our business logic, you can call API A and subsequently all API B and
API C will be called by API A. And API D will be called by API B.

#### Prepare Environment

Before starting this step, let's create a folder called httpbranch in each sub folder under
ms_branch and copy everything from generated folder to the httpbranch. We are going to update
httpbranch folder to have business logic to call other API(s) and change the configuration
to listen to different port. You can compare between generated and httpbranch to see what has
been changed later on.

```
cd ~/networknt/light-example-4j/rest/ms_branch/api_a
cp -r generated httpbranch
cd ~/networknt/light-example-4j/rest/ms_branch/api_b
cp -r generated httpbranch
cd ~/networknt/light-example-4j/rest/ms_branch/api_c
cp -r generated httpbranch
cd ~/networknt/light-example-4j/rest/ms_branch/api_d
cp -r generated httpbranch

```

Now we have httpbranch folder copied from generated and all updates in this step will be
in httpbranch folder. 

#### API D
Let's take a look at the PathHandlerProvider.java in
ms_branch/api_d/httpbranch/src/main/java/com/networknt/apid

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
cd ~/networknt/light-example-4j/rest/ms_branch/api_d/httpbranch
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
~/networknt/light-example-4j/rest/ms_branch/api_c/httpbranch


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

Start API C server and test the endpoint /v1/data

```
cd ~/networknt/light-example-4j/rest/ms_branch/api_c/httpbranch
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

Let's keep API C and API D running. The next step is to complete API B. 

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
        List<String> list = new ArrayList<String>();
        try {
            CloseableHttpClient client = Client.getInstance().getSyncClient();
            HttpGet httpGet = new HttpGet(apidUrl);
            //Client.getInstance().propagateHeaders(httpGet, exchange);
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
Configuration file for API D url.

api_b.yml

```
api_d_endpoint: http://localhost:7004/v1/data

```

Start API B server and test the endpoint /v1/data

```
cd ~/networknt/light-example-4j/rest/ms_branch/api_b/httpbranch
mvn clean install exec:exec
```
From another terminal window run:

```
curl localhost:7002/v1/data
```
And the result is

```
["API D: Message 1","API D: Message 2","API B: Message 1","API B: Message 2"]
```

Here is the https port and the result is the same.

```
curl -k https://localhost:7442/v1/data

```


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
                //Client.getInstance().propagateHeaders(request, exchange);
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


API A needs to have the urls of API B, and API C in order to call them. Let's put it in a config 
file for now and move to service discovery later.

Create api_a.yml in src/main/resources/config folder.

```
api_b_endpoint: http://localhost:7002/v1/data
api_c_endpoint: http://localhost:7003/v1/data
```


Start API A server and test the endpoint /v1/data

```
cd ~/networknt/light-example-4j/rest/ms_branch/api_a/httpbranch
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
At this moment, we have all four APIs completed and A is calling B, C and D using Http connections.

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
    Req/Sec     5.31k     7.02k   21.29k    80.30%
  Latency Distribution
     50%    0.00us
     75%    0.00us
     90%    0.00us
     99%    0.00us
  157184 requests in 30.05s, 36.13MB read
  Socket errors: connect 0, read 0, write 0, timeout 128
Requests/sec:   5230.92
Transfer/sec:      1.20MB
```

Before starting the next step, please kill all four instances by Ctrl+C. And check in
the httpaggregate folder we just created and updated. 


For more steps, please refer to 

https://networknt.github.io/light-rest-4j/tutorial/ms-chain/


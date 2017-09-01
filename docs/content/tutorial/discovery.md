---
date: 2017-01-27T20:57:14-05:00
title: Registry and Discovery
---

# Introduction

This is a tutorial to show you how to use service registry and discovery
for microservices. The example services are implemented in RESTful style but
they can be implemented in graphql or hybrid as well. We are going to use 
api_a, api_b, api_c and api_d as our examples. To simply the tutorial, I am 
going to disable the security all the time. There are some details that might
not be shown in this tutorial, for example, walking through light-codegen config
files etc. It is recommended to go through [ms-chain](ms-chain/) before this
tutorial. 

There are two situations that you need service discovery: 

* A client application to consume services and it needs to locate the service
instance by serviceId

* A service that has to call another service to fulfill its request in request/
response style.  

The specifications for above APIs can be found at 
https://github.com/networknt/model-config rest folder.

# Preparation

In order to follow the steps below, please make sure you have the same 
working environment.

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

# Clone the specifications

In order to generate the initial projects, we need to call light-codegen
and we need the specifications for these services.

```
cd ~/networknt
git clone git@github.com:networknt/model-config.git
```

In this repo, you have a generate.sh in the root folder to use docker
container of light-codegen to generate the code and there are api_a,
api_b, api_c and api_d in rest folder for swagger.json files and config.json
files for each API.

# Code generation

We are going to generate the code into light-example-4j repo, so let's
clone this repo into our working directory.

```
cd ~/networknt
git clone git@github.com:networknt/light-example-4j.git
```

In the above repo, there is a folder discovery contains all the projects
for this tutorial. In order to start from scratch, let's change the existing
folder to discovery.bak as a backup so that you can compare if your code is
not working in each step.

```
cd ~/networknt/light-example-4j
mv discovery discovery.bak
```

In order to generate the four projects, let's clone and build the light-codegen 
into the workspace. 

```
cd ~/networknt
git clone git@github.com:networknt/light-codegen.git
mvn clean install
```

Now let's generate the four APIs.

```
cd ~/networknt/light-codegen
java -jar codegen-cli/target/codegen-cli.jar -f light-rest-4j -o ../light-example-4j/discovery/api_a/generated -m ../model-config/rest/api_a/1.0.0/swagger.json -c ../model-config/rest/api_a/1.0.0/config.json
java -jar codegen-cli/target/codegen-cli.jar -f light-rest-4j -o ../light-example-4j/discovery/api_b/generated -m ../model-config/rest/api_b/1.0.0/swagger.json -c ../model-config/rest/api_b/1.0.0/config.json
java -jar codegen-cli/target/codegen-cli.jar -f light-rest-4j -o ../light-example-4j/discovery/api_c/generated -m ../model-config/rest/api_c/1.0.0/swagger.json -c ../model-config/rest/api_c/1.0.0/config.json
java -jar codegen-cli/target/codegen-cli.jar -f light-rest-4j -o ../light-example-4j/discovery/api_d/generated -m ../model-config/rest/api_d/1.0.0/swagger.json -c ../model-config/rest/api_d/1.0.0/config.json

```

We have four projects generated and compiled under generated folder under each 
project folder. 

# Test generated code

Now you can test the generated projects to make sure they are working with mock
data. We will pick up one project to test it but you can test them all.

```
cd ~/networknt/light-example-4j/discovery/api_a/generated
mvn clean install exec:exec
```

From another terminal, access the server with curl command and check the result.

```
curl http://localhost:7001/v1/data
["Message 1","Message 2"]
```

Based on the config files for each project, the generated service will listen to
7001, 7002, 7003 and 7004 for http connections. You can start other services to test
them on their corresponding port.


# Static

Now we have four standalone services and the next step is to connect them together.

Here is the call tree for these services.

API A will call API B and API C to fulfill its request. API B will call API D
to fulfill its request.

```
API A -> API B -> API D
      -> API C
```

Before we change the code, let's copy the generated projects to new folders so
that we can compare the changes later on.

```
cd ~/networknt/light-example-4j/discovery/api_a
cp -r generated static
cd ~/networknt/light-example-4j/discovery/api_b
cp -r generated static
cd ~/networknt/light-example-4j/discovery/api_c
cp -r generated static
cd ~/networknt/light-example-4j/discovery/api_d
cp -r generated static
```

Let's start update the code in static folders for each project. If you are
using Intellij IDEA Community Edition, you need to open light-example-4j
repo and then import each project by right click pom.xml in each static folder.

As indicated from the title, here we are going to hard code urls in API to API
calls in configuration files. That means these services will be deployed on the 
known hosts with known ports. And we will have a config file for each project to 
define the calling service urls.

### API A

For API A, as it is calling API B and API C, its handler needs to be changed to
call two other APIs and needs to load a configuration file that define the 
urls for API B and API C.

DataGetHandler.java

```
package com.networknt.apia.handler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.networknt.client.Http2Client;
import com.networknt.config.Config;
import com.networknt.exception.ClientException;
import com.networknt.security.JwtHelper;
import io.undertow.UndertowOptions;
import io.undertow.client.ClientConnection;
import io.undertow.client.ClientRequest;
import io.undertow.client.ClientResponse;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;
import io.undertow.util.Methods;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnio.OptionMap;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

public class DataGetHandler implements HttpHandler {
    static String CONFIG_NAME = "api_a";
    static Logger logger = LoggerFactory.getLogger(DataGetHandler.class);
    static String apibHost = (String) Config.getInstance().getJsonMapConfig(CONFIG_NAME).get("api_b_host");
    static String apibPath = (String) Config.getInstance().getJsonMapConfig(CONFIG_NAME).get("api_b_path");
    static String apicHost = (String) Config.getInstance().getJsonMapConfig(CONFIG_NAME).get("api_c_host");
    static String apicPath = (String) Config.getInstance().getJsonMapConfig(CONFIG_NAME).get("api_c_path");
    static Map<String, Object> securityConfig = (Map)Config.getInstance().getJsonMapConfig(JwtHelper.SECURITY_CONFIG);
    static boolean securityEnabled = (Boolean)securityConfig.get(JwtHelper.ENABLE_VERIFY_JWT);
    static Http2Client client = Http2Client.getInstance();
    static ClientConnection connectionB;
    static ClientConnection connectionC;

    public DataGetHandler() {
        try {
            connectionB = client.connect(new URI(apibHost), Http2Client.WORKER, Http2Client.SSL, Http2Client.POOL, OptionMap.create(UndertowOptions.ENABLE_HTTP2, true)).get();
            connectionC = client.connect(new URI(apicHost), Http2Client.WORKER, Http2Client.SSL, Http2Client.POOL, OptionMap.create(UndertowOptions.ENABLE_HTTP2, true)).get();
        } catch (Exception e) {
            logger.error("Exeption:", e);
        }
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        List<String> list = new ArrayList<>();
        if(connectionB == null || !connectionB.isOpen()) {
            try {
                connectionB = client.connect(new URI(apibHost), Http2Client.WORKER, Http2Client.SSL, Http2Client.POOL, OptionMap.create(UndertowOptions.ENABLE_HTTP2, true)).get();
            } catch (Exception e) {
                logger.error("Exeption:", e);
                throw new ClientException(e);
            }
        }
        if(connectionC == null || !connectionC.isOpen()) {
            try {
                connectionC = client.connect(new URI(apicHost), Http2Client.WORKER, Http2Client.SSL, Http2Client.POOL, OptionMap.create(UndertowOptions.ENABLE_HTTP2, true)).get();
            } catch (Exception e) {
                logger.error("Exeption:", e);
                throw new ClientException(e);
            }
        }
        final CountDownLatch latch = new CountDownLatch(2);
        final AtomicReference<ClientResponse> referenceB = new AtomicReference<>();
        final AtomicReference<ClientResponse> referenceC = new AtomicReference<>();
        try {
            ClientRequest requestB = new ClientRequest().setMethod(Methods.GET).setPath(apibPath);
            if(securityEnabled) client.propagateHeaders(requestB, exchange);
            connectionB.sendRequest(requestB, client.createClientCallback(referenceB, latch));

            ClientRequest requestC = new ClientRequest().setMethod(Methods.GET).setPath(apicPath);
            if(securityEnabled) client.propagateHeaders(requestC, exchange);
            connectionC.sendRequest(requestB, client.createClientCallback(referenceC, latch));

            latch.await();

            int statusCodeB = referenceB.get().getResponseCode();
            if(statusCodeB >= 300){
                throw new Exception("Failed to call API B: " + statusCodeB);
            }
            List<String> apibList = Config.getInstance().getMapper().readValue(referenceB.get().getAttachment(Http2Client.RESPONSE_BODY),
                    new TypeReference<List<String>>(){});
            list.addAll(apibList);

            int statusCodeC = referenceC.get().getResponseCode();
            if(statusCodeC >= 300){
                throw new Exception("Failed to call API C: " + statusCodeC);
            }
            List<String> apicList = Config.getInstance().getMapper().readValue(referenceC.get().getAttachment(Http2Client.RESPONSE_BODY),
                    new TypeReference<List<String>>(){});
            list.addAll(apicList);
        } catch (Exception e) {
            logger.error("Exception:", e);
            throw new ClientException(e);
        }
        list.add("API A: Message 1");
        list.add("API A: Message 2");
        exchange.getResponseSender().send(Config.getInstance().getMapper().writeValueAsString(list));
    }
}

```

The following is the config file that define the url for API B and API C. This is hard
coded and can only be changed in this config file and restart the server. For now, I
am just changing the file in src/main/resources/config folder, but it should be 
externalized on official environment.

api_a.yml

```
api_b_host: https://localhost:7442
api_b_path: /v1/data
api_c_host: https://localhost:7443
api_c_path: /v1/data
```

### API B

Change the handler to call API D and load configuration for API D url.

DataGetHandler.java

```
package com.networknt.apib.handler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.networknt.client.Http2Client;
import com.networknt.config.Config;
import com.networknt.exception.ClientException;
import com.networknt.security.JwtHelper;
import io.undertow.UndertowOptions;
import io.undertow.client.ClientConnection;
import io.undertow.client.ClientRequest;
import io.undertow.client.ClientResponse;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Methods;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnio.OptionMap;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

public class DataGetHandler implements HttpHandler {
    static String CONFIG_NAME = "api_b";
    static Logger logger = LoggerFactory.getLogger(DataGetHandler.class);
    static String apidHost = (String) Config.getInstance().getJsonMapConfig(CONFIG_NAME).get("api_d_host");
    static String apidPath = (String) Config.getInstance().getJsonMapConfig(CONFIG_NAME).get("api_d_path");
    static Map<String, Object> securityConfig = (Map)Config.getInstance().getJsonMapConfig(JwtHelper.SECURITY_CONFIG);
    static boolean securityEnabled = (Boolean)securityConfig.get(JwtHelper.ENABLE_VERIFY_JWT);
    static Http2Client client = Http2Client.getInstance();
    static ClientConnection connection;

    public DataGetHandler() {
        try {
            connection = client.connect(new URI(apidHost), Http2Client.WORKER, Http2Client.SSL, Http2Client.POOL, OptionMap.create(UndertowOptions.ENABLE_HTTP2, true)).get();
        } catch (Exception e) {
            logger.error("Exeption:", e);
        }
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        List<String> list = new ArrayList<>();
        final CountDownLatch latch = new CountDownLatch(1);
        if(connection == null || !connection.isOpen()) {
            try {
                connection = client.connect(new URI(apidHost), Http2Client.WORKER, Http2Client.SSL, Http2Client.POOL, OptionMap.create(UndertowOptions.ENABLE_HTTP2, true)).get();
            } catch (Exception e) {
                logger.error("Exeption:", e);
                throw new ClientException(e);
            }
        }
        final AtomicReference<ClientResponse> reference = new AtomicReference<>();
        try {
            ClientRequest request = new ClientRequest().setMethod(Methods.GET).setPath(apidPath);
            // this is to ask client module to pass through correlationId and traceabilityId as well as
            // getting access token from oauth2 server automatically and attatch authorization headers.
            if(securityEnabled) client.propagateHeaders(request, exchange);
            connection.sendRequest(request, client.createClientCallback(reference, latch));
            latch.await();
            int statusCode = reference.get().getResponseCode();
            if(statusCode >= 300){
                throw new Exception("Failed to call API D: " + statusCode);
            }
            List<String> apidList = Config.getInstance().getMapper().readValue(reference.get().getAttachment(Http2Client.RESPONSE_BODY),
                    new TypeReference<List<String>>(){});
            list.addAll(apidList);
        } catch (Exception e) {
            logger.error("Exception:", e);
            throw new ClientException(e);
        }
        list.add("API B: Message 1");
        list.add("API B: Message 2");
        exchange.getResponseSender().send(Config.getInstance().getMapper().writeValueAsString(list));
    }
}
```

Configuration file for API D url.

api_b.yml

```
api_d_host: https://localhost:7444
api_d_path: /v1/data
```

### API C


Update API C handler to return information that associates with API C.

DataGetHandler.java

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
        List<String> messages = new ArrayList<>();
        messages.add("API C: Message 1");
        messages.add("API C: Message 2");
        exchange.getResponseSender().send(Config.getInstance().getMapper().writeValueAsString(messages));
    }
}

```


### API D

Update Handler for API D to return messages related to API D.

DataGetHandler.java

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
        List<String> messages = new ArrayList<>();
        messages.add("API D: Message 1");
        messages.add("API D: Message 2");
        exchange.getResponseSender().send(Config.getInstance().getMapper().writeValueAsString(messages));
    }
}

```


### Start Servers

Now let's start all four servers from four terminals.

API A

```
cd ~/networknt/light-example-4j/discovery/api_a/static
mvn clean install exec:exec
```

API B

```
cd ~/networknt/light-example-4j/discovery/api_b/static
mvn clean install exec:exec

```

API C

```
cd ~/networknt/light-example-4j/discovery/api_c/static
mvn clean install exec:exec

```

API D

```
cd ~/networknt/light-example-4j/discovery/api_d/static
mvn clean install exec:exec

```

When starting servers in above sequence, you can see some errors in API A and B as
the target server are not up yet. However, once you issue a call to API A, the
connections will be established immediately and then cached.

### Test Servers

Let's access API A and see if we can get messages from all four servers.

```
curl http://localhost:7001/v1/data

```
The result is 

```
["API C: Message 1","API C: Message 2","API D: Message 1","API D: Message 2","API B: Message 1","API B: Message 2","API A: Message 1","API A: Message 2"]
```


# Dynamic

The above step uses static urls defined in configuration files. It won't work in a
dynamic clustered environment as there are more instances of each service. In this
step, we are going to use cluster component with direct registry so that we don't
need to start external consul or zookeeper instances. We still go through registry
for service discovery but the registry is defined in service.yml. Next step we
will use consul server for the discovery to mimic real production environment.


First let's create a folder from static to dynamic.

```
cd ~/networknt/light-example-4j/discovery/api_a
cp -r static dynamic
cd ~/networknt/light-example-4j/discovery/api_b
cp -r static dynamic
cd ~/networknt/light-example-4j/discovery/api_c
cp -r static dynamic
cd ~/networknt/light-example-4j/discovery/api_d
cp -r static dynamic
```


### API A

Let's update API A Handler to use Cluster instance instead of using static config
files. 

DataGetHandler.java

```
package com.networknt.apia.handler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.networknt.client.Http2Client;
import com.networknt.cluster.Cluster;
import com.networknt.config.Config;
import com.networknt.exception.ClientException;
import com.networknt.security.JwtHelper;
import com.networknt.service.SingletonServiceFactory;
import io.undertow.UndertowOptions;
import io.undertow.client.ClientConnection;
import io.undertow.client.ClientRequest;
import io.undertow.client.ClientResponse;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;
import io.undertow.util.Methods;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnio.OptionMap;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

public class DataGetHandler implements HttpHandler {
    static Logger logger = LoggerFactory.getLogger(DataGetHandler.class);
    static Cluster cluster = SingletonServiceFactory.getBean(Cluster.class);
    static String apibHost;
    static String apicHost;
    static String path = "/v1/data";
    static Map<String, Object> securityConfig = (Map)Config.getInstance().getJsonMapConfig(JwtHelper.SECURITY_CONFIG);
    static boolean securityEnabled = (Boolean)securityConfig.get(JwtHelper.ENABLE_VERIFY_JWT);

    static Http2Client client = Http2Client.getInstance();
    static ClientConnection connectionB;
    static ClientConnection connectionC;

    public DataGetHandler() {
        try {
            apibHost = cluster.serviceToUrl("https", "com.networknt.apib-1.0.0", null);
            apicHost = cluster.serviceToUrl("https", "com.networknt.apic-1.0.0", null);
            connectionB = client.connect(new URI(apibHost), Http2Client.WORKER, Http2Client.SSL, Http2Client.POOL, OptionMap.create(UndertowOptions.ENABLE_HTTP2, true)).get();
            connectionC = client.connect(new URI(apicHost), Http2Client.WORKER, Http2Client.SSL, Http2Client.POOL, OptionMap.create(UndertowOptions.ENABLE_HTTP2, true)).get();
        } catch (Exception e) {
            logger.error("Exeption:", e);
        }
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        List<String> list = new ArrayList<>();
        if(connectionB == null || !connectionB.isOpen()) {
            try {
                apibHost = cluster.serviceToUrl("https", "com.networknt.apib-1.0.0", null);
                connectionB = client.connect(new URI(apibHost), Http2Client.WORKER, Http2Client.SSL, Http2Client.POOL, OptionMap.create(UndertowOptions.ENABLE_HTTP2, true)).get();
            } catch (Exception e) {
                logger.error("Exeption:", e);
                throw new ClientException(e);
            }
        }
        if(connectionC == null || !connectionC.isOpen()) {
            try {
                apicHost = cluster.serviceToUrl("https", "com.networknt.apic-1.0.0", null);
                connectionC = client.connect(new URI(apicHost), Http2Client.WORKER, Http2Client.SSL, Http2Client.POOL, OptionMap.create(UndertowOptions.ENABLE_HTTP2, true)).get();
            } catch (Exception e) {
                logger.error("Exeption:", e);
                throw new ClientException(e);
            }
        }
        final CountDownLatch latch = new CountDownLatch(2);
        final AtomicReference<ClientResponse> referenceB = new AtomicReference<>();
        final AtomicReference<ClientResponse> referenceC = new AtomicReference<>();
        try {
            ClientRequest requestB = new ClientRequest().setMethod(Methods.GET).setPath(path);
            if(securityEnabled) client.propagateHeaders(requestB, exchange);
            connectionB.sendRequest(requestB, client.createClientCallback(referenceB, latch));

            ClientRequest requestC = new ClientRequest().setMethod(Methods.GET).setPath(path);
            if(securityEnabled) client.propagateHeaders(requestC, exchange);
            connectionC.sendRequest(requestB, client.createClientCallback(referenceC, latch));

            latch.await();

            int statusCodeB = referenceB.get().getResponseCode();
            if(statusCodeB >= 300){
                throw new Exception("Failed to call API B: " + statusCodeB);
            }
            List<String> apibList = Config.getInstance().getMapper().readValue(referenceB.get().getAttachment(Http2Client.RESPONSE_BODY),
                    new TypeReference<List<String>>(){});
            list.addAll(apibList);

            int statusCodeC = referenceC.get().getResponseCode();
            if(statusCodeC >= 300){
                throw new Exception("Failed to call API C: " + statusCodeC);
            }
            List<String> apicList = Config.getInstance().getMapper().readValue(referenceC.get().getAttachment(Http2Client.RESPONSE_BODY),
                    new TypeReference<List<String>>(){});
            list.addAll(apicList);
        } catch (Exception e) {
            logger.error("Exception:", e);
            throw new ClientException(e);
        }
        list.add("API A: Message 1");
        list.add("API A: Message 2");
        exchange.getResponseSender().send(Config.getInstance().getMapper().writeValueAsString(list));
    }
}

```

Also, we need service.yml to inject several singleton implementations of
Cluster, LoadBanlance, URL and Registry. Please note that the key in parameters
is serviceId of your calling APIs


```
singletons:
- com.networknt.registry.URL:
  - com.networknt.registry.URLImpl:
      protocol: https
      host: localhost
      port: 8080
      path: direct
      parameters:
        com.networknt.apib-1.0.0: https://localhost:7442
        com.networknt.apic-1.0.0: https://localhost:7443
- com.networknt.registry.Registry:
  - com.networknt.registry.support.DirectRegistry
- com.networknt.balance.LoadBalance:
  - com.networknt.balance.RoundRobinLoadBalance
- com.networknt.cluster.Cluster:
  - com.networknt.cluster.LightCluster

```

As we don't need api_a.yml anymore, it can be removed.


### API B

DataGetHandler.java

```
package com.networknt.apib.handler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.networknt.client.Http2Client;
import com.networknt.cluster.Cluster;
import com.networknt.config.Config;
import com.networknt.exception.ClientException;
import com.networknt.security.JwtHelper;
import com.networknt.service.SingletonServiceFactory;
import io.undertow.UndertowOptions;
import io.undertow.client.ClientConnection;
import io.undertow.client.ClientRequest;
import io.undertow.client.ClientResponse;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Methods;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnio.OptionMap;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

public class DataGetHandler implements HttpHandler {
    static Logger logger = LoggerFactory.getLogger(DataGetHandler.class);
    static Cluster cluster = SingletonServiceFactory.getBean(Cluster.class);
    static String apidHost;
    static String path = "/v1/data";
    static Map<String, Object> securityConfig = (Map)Config.getInstance().getJsonMapConfig(JwtHelper.SECURITY_CONFIG);
    static boolean securityEnabled = (Boolean)securityConfig.get(JwtHelper.ENABLE_VERIFY_JWT);
    static Http2Client client = Http2Client.getInstance();
    static ClientConnection connection;

    public DataGetHandler() {
        try {
            apidHost = cluster.serviceToUrl("https", "com.networknt.apid-1.0.0", null);
            connection = client.connect(new URI(apidHost), Http2Client.WORKER, Http2Client.SSL, Http2Client.POOL, OptionMap.create(UndertowOptions.ENABLE_HTTP2, true)).get();
        } catch (Exception e) {
            logger.error("Exeption:", e);
        }
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        List<String> list = new ArrayList<>();
        final CountDownLatch latch = new CountDownLatch(1);
        if(connection == null || !connection.isOpen()) {
            try {
                apidHost = cluster.serviceToUrl("https", "com.networknt.apid-1.0.0", null);
                connection = client.connect(new URI(apidHost), Http2Client.WORKER, Http2Client.SSL, Http2Client.POOL, OptionMap.create(UndertowOptions.ENABLE_HTTP2, true)).get();
            } catch (Exception e) {
                logger.error("Exeption:", e);
                throw new ClientException(e);
            }
        }
        final AtomicReference<ClientResponse> reference = new AtomicReference<>();
        try {
            ClientRequest request = new ClientRequest().setMethod(Methods.GET).setPath(path);
            // this is to ask client module to pass through correlationId and traceabilityId as well as
            // getting access token from oauth2 server automatically and attatch authorization headers.
            if(securityEnabled) client.propagateHeaders(request, exchange);
            connection.sendRequest(request, client.createClientCallback(reference, latch));
            latch.await();
            int statusCode = reference.get().getResponseCode();
            if(statusCode >= 300){
                throw new Exception("Failed to call API D: " + statusCode);
            }
            List<String> apidList = Config.getInstance().getMapper().readValue(reference.get().getAttachment(Http2Client.RESPONSE_BODY),
                    new TypeReference<List<String>>(){});
            list.addAll(apidList);
        } catch (Exception e) {
            logger.error("Exception:", e);
            throw new ClientException(e);
        }
        list.add("API B: Message 1");
        list.add("API B: Message 2");
        exchange.getResponseSender().send(Config.getInstance().getMapper().writeValueAsString(list));
    }
}
```

Inject interface implementations and define the API D url.

```
singletons:
- com.networknt.registry.URL:
  - com.networknt.registry.URLImpl:
      protocol: https
      host: localhost
      port: 8080
      path: direct
      parameters:
        com.networknt.apid-1.0.0: https://localhost:7444
- com.networknt.registry.Registry:
  - com.networknt.registry.support.DirectRegistry
- com.networknt.balance.LoadBalance:
  - com.networknt.balance.RoundRobinLoadBalance
- com.networknt.cluster.Cluster:
  - com.networknt.cluster.LightCluster

```

As we don't need api_b.yml anymore, it can be removed.

### API C

API C is not calling any other APIs, so there is no change to its handler.




### API D

API D is not calling any other APIs, so there is no change to its handler.


### Start Servers

Now let's start all four servers from four terminals.

API A

```
cd ~/networknt/light-example-4j/discovery/api_a/dynamic
mvn clean install exec:exec
```

API B

```
cd ~/networknt/light-example-4j/discovery/api_b/dynamic
mvn clean install exec:exec

```

API C

```
cd ~/networknt/light-example-4j/discovery/api_c/dynamic
mvn clean install exec:exec

```

API D

```
cd ~/networknt/light-example-4j/discovery/api_d/dynamic
mvn clean install exec:exec

```

### Test Servers

Let's access API A and see if we can get messages from all four servers.

```
curl http://localhost:7001/v1/data

```
The result is 

```
["API C: Message 1","API C: Message 2","API D: Message 1","API D: Message 2","API B: Message 1","API B: Message 2","API A: Message 1","API A: Message 2"]
```

# Multiple API D Instances

In this step, we are going to start two API D instances that listening to 7444 and 7445.

Now let's copy from dynamic to multiple for each API.
 

```
cd ~/networknt/light-example-4j/discovery/api_a
cp -r dynamic multiple
cd ~/networknt/light-example-4j/discovery/api_b
cp -r dynamic multiple
cd ~/networknt/light-example-4j/discovery/api_c
cp -r dynamic multiple
cd ~/networknt/light-example-4j/discovery/api_d
cp -r dynamic multiple
```

 
### API B 

Let's modify API B service.yml to have two API D instances that listen to 7444
and 7445. 

service.yml

```
singletons:
- com.networknt.registry.URL:
  - com.networknt.registry.URLImpl:
      protocol: https
      host: localhost
      port: 8080
      path: direct
      parameters:
        com.networknt.apid-1.0.0: https://localhost:7444,https://localhost:7445
- com.networknt.registry.Registry:
  - com.networknt.registry.support.DirectRegistry
- com.networknt.balance.LoadBalance:
  - com.networknt.balance.RoundRobinLoadBalance
- com.networknt.cluster.Cluster:
  - com.networknt.cluster.LightCluster

```

### API D

In order to start two instances with the same code base, we need to modify the
server.yml before starting the server. 

Also, let's update the handler so that we know which port serves the request.

DataGetHandler.java

```
package com.networknt.apid.handler;

import com.networknt.config.Config;
import com.networknt.server.Server;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;

import java.util.ArrayList;
import java.util.List;

public class DataGetHandler implements HttpHandler {
    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        int port = Server.config.getHttpPort();
        List<String> messages = new ArrayList<String>();
        messages.add("API D: Message 1 from port " + port);
        messages.add("API D: Message 2 from port " + port);
        exchange.getResponseSender().send(Config.getInstance().getMapper().writeValueAsString(messages));
    }
}

```

### Start Servers
Now let's start all five servers from five terminals. API D has two instances.

API A

```
cd ~/networknt/light-example-4j/discovery/api_a/multiple
mvn clean install exec:exec
```

API B

```
cd ~/networknt/light-example-4j/discovery/api_b/multiple
mvn clean install exec:exec

```

API C

```
cd ~/networknt/light-example-4j/discovery/api_c/multiple
mvn clean install exec:exec

```

API D


And start the first instance that listen to 7444.

```
cd ~/networknt/light-example-4j/discovery/api_d/multiple
mvn clean install exec:exec

```
 
Now let's start the second instance of API D. Before starting the serer, let's update
server.yml with https port 7445 and disable HTTP.

```

# Server configuration
---
# This is the default binding address if the service is dockerized.
ip: 0.0.0.0

# Http port if enableHttp is true.
httpPort:  7004

# Enable HTTP should be false on official environment.
enableHttp: false

# Https port if enableHttps is true.
httpsPort:  7445

# Enable HTTPS should be true on official environment.
enableHttps: true

# Http/2 is enabled by default.
enableHttp2: true

# Keystore file name in config folder. KeystorePass is in secret.yml to access it.
keystoreName: tls/server.keystore

# Flag that indicate if two way TLS is enabled. Not recommended in docker container.
enableTwoWayTls: false

# Truststore file name in config folder. TruststorePass is in secret.yml to access it.
truststoreName: tls/server.truststore

# Unique service identifier. Used in service registration and discovery etc.
serviceId: com.networknt.apid-1.0.0

# Flag to enable service registration. Only be true if running as standalone Java jar.
enableRegistry: false

```

And start the second instance

```
cd ~/networknt/light-example-4j/discovery/api_d/multiple
mvn clean install exec:exec

```

### Test Servers

```
curl http://localhost:7001/v1/data
```

And the result can be the following alternatively as two instances of API D are load balanced.

```
["API C: Message 1","API C: Message 2","API D: Message 1 from port 7445","API D: Message 2 from port 7445","API B: Message 1","API B: Message 2","API A: Message 1","API A: Message 2"]
```

If you send multiple requests, the result should be same as it is using the same cached connection.
Now let's kill the last started 7445 instance by CTRL+C and run the curl command again. You will see
the following result as it failed over to the other server. 

```
["API C: Message 1","API C: Message 2","API D: Message 1 from port 7444","API D: Message 2 from port 7444","API B: Message 1","API B: Message 2","API A: Message 1","API A: Message 2"]
```

# Consul

Above step multiple demonstrates how to use direct registry to enable load balance and
it works the same way as Consul and Zookeeper registry. In this step, we are going to
use Consul for registry to enable cluster.

Now let's copy from multiple to consul for each API.
 

```
cd ~/networknt/light-example-4j/discovery/api_a
cp -r multiple consul
cd ~/networknt/light-example-4j/discovery/api_b
cp -r multiple consul
cd ~/networknt/light-example-4j/discovery/api_c
cp -r multiple consul
cd ~/networknt/light-example-4j/discovery/api_d
cp -r multiple consul
```


### API A

In order to switch from direct registry to consul registry, we just need to update
service.yml configuration to inject the consul implementation to the registry interface.

service.yml

```
singletons:
- com.networknt.registry.URL:
  - com.networknt.registry.URLImpl:
      protocol: light
      host: localhost
      port: 8080
      path: consul
      parameters:
        registryRetryPeriod: '30000'
- com.networknt.consul.client.ConsulClient:
  - com.networknt.consul.client.ConsulEcwidClient:
    - java.lang.String: localhost
    - int: 8500
- com.networknt.registry.Registry:
  - com.networknt.consul.ConsulRegistry
- com.networknt.balance.LoadBalance:
  - com.networknt.balance.RoundRobinLoadBalance
- com.networknt.cluster.Cluster:
  - com.networknt.cluster.LightCluster
```

Although in our case, there is no caller service for API A, we still need to register
it to consul by enable it in server.yml. Note that enableRegsitry is turn on and enableHttp
is turned off. 

If you don't turn off the http port, both ports will be registered on Consul. 

```

# Server configuration
---
# This is the default binding address if the service is dockerized.
ip: 0.0.0.0

# Http port if enableHttp is true.
httpPort:  7001

# Enable HTTP should be false on official environment.
enableHttp: false

# Https port if enableHttps is true.
httpsPort:  7441

# Enable HTTPS should be true on official environment.
enableHttps: true

# Http/2 is enabled by default.
enableHttp2: true

# Keystore file name in config folder. KeystorePass is in secret.yml to access it.
keystoreName: tls/server.keystore

# Flag that indicate if two way TLS is enabled. Not recommended in docker container.
enableTwoWayTls: false

# Truststore file name in config folder. TruststorePass is in secret.yml to access it.
truststoreName: tls/server.truststore

# Unique service identifier. Used in service registration and discovery etc.
serviceId: com.networknt.apia-1.0.0

# Flag to enable service registration. Only be true if running as standalone Java jar.
enableRegistry: true

```


### API B

Let's update service.yml to inject consul registry instead of direct registry used in
the previous step.

service.yml

```
singletons:
- com.networknt.registry.URL:
  - com.networknt.registry.URLImpl:
      protocol: light
      host: localhost
      port: 8080
      path: consul
      parameters:
        registryRetryPeriod: '30000'
- com.networknt.consul.client.ConsulClient:
  - com.networknt.consul.client.ConsulEcwidClient:
    - java.lang.String: localhost
    - int: 8500
- com.networknt.registry.Registry:
  - com.networknt.consul.ConsulRegistry
- com.networknt.balance.LoadBalance:
  - com.networknt.balance.RoundRobinLoadBalance
- com.networknt.cluster.Cluster:
  - com.networknt.cluster.LightCluster
```

As API B will be called by API A, it needs to register itself to consul registry so
that API A can discover it through the same consul registry. To do that you only need
to enable server registry in config file.

server.yml

```


# Server configuration
---
# This is the default binding address if the service is dockerized.
ip: 0.0.0.0

# Http port if enableHttp is true.
httpPort:  7002

# Enable HTTP should be false on official environment.
enableHttp: false

# Https port if enableHttps is true.
httpsPort:  7442

# Enable HTTPS should be true on official environment.
enableHttps: true

# Http/2 is enabled by default.
enableHttp2: true

# Keystore file name in config folder. KeystorePass is in secret.yml to access it.
keystoreName: tls/server.keystore

# Flag that indicate if two way TLS is enabled. Not recommended in docker container.
enableTwoWayTls: false

# Truststore file name in config folder. TruststorePass is in secret.yml to access it.
truststoreName: tls/server.truststore

# Unique service identifier. Used in service registration and discovery etc.
serviceId: com.networknt.apib-1.0.0

# Flag to enable service registration. Only be true if running as standalone Java jar.
enableRegistry: true
```

### API C

Although API C is not calling any other APIs, it needs to register itself to consul
so that API A can discovery it from the same consul registry. Let's create service.yml

service.yml

```
singletons:
- com.networknt.registry.URL:
  - com.networknt.registry.URLImpl:
      protocol: light
      host: localhost
      port: 8080
      path: consul
      parameters:
        registryRetryPeriod: '30000'
- com.networknt.consul.client.ConsulClient:
  - com.networknt.consul.client.ConsulEcwidClient:
    - java.lang.String: localhost
    - int: 8500
- com.networknt.registry.Registry:
  - com.networknt.consul.ConsulRegistry
- com.networknt.balance.LoadBalance:
  - com.networknt.balance.RoundRobinLoadBalance
- com.networknt.cluster.Cluster:
  - com.networknt.cluster.LightCluster
```


server.yml

```

# Server configuration
---
# This is the default binding address if the service is dockerized.
ip: 0.0.0.0

# Http port if enableHttp is true.
httpPort:  7003

# Enable HTTP should be false on official environment.
enableHttp: false

# Https port if enableHttps is true.
httpsPort:  7443

# Enable HTTPS should be true on official environment.
enableHttps: true

# Http/2 is enabled by default.
enableHttp2: true

# Keystore file name in config folder. KeystorePass is in secret.yml to access it.
keystoreName: tls/server.keystore

# Flag that indicate if two way TLS is enabled. Not recommended in docker container.
enableTwoWayTls: false

# Truststore file name in config folder. TruststorePass is in secret.yml to access it.
truststoreName: tls/server.truststore

# Unique service identifier. Used in service registration and discovery etc.
serviceId: com.networknt.apic-1.0.0

# Flag to enable service registration. Only be true if running as standalone Java jar.
enableRegistry: true
```

### API D

Although API D is not calling any other APIs, it needs to register itself to consul
so that API B can discovery it from the same consul registry. Let's create service.yml

service.yml

```
singletons:
- com.networknt.registry.URL:
  - com.networknt.registry.URLImpl:
      protocol: light
      host: localhost
      port: 8080
      path: consul
      parameters:
        registryRetryPeriod: '30000'
- com.networknt.consul.client.ConsulClient:
  - com.networknt.consul.client.ConsulEcwidClient:
    - java.lang.String: localhost
    - int: 8500
- com.networknt.registry.Registry:
  - com.networknt.consul.ConsulRegistry
- com.networknt.balance.LoadBalance:
  - com.networknt.balance.RoundRobinLoadBalance
- com.networknt.cluster.Cluster:
  - com.networknt.cluster.LightCluster
```

server.yml

```

# Server configuration
---
# This is the default binding address if the service is dockerized.
ip: 0.0.0.0

# Http port if enableHttp is true.
httpPort:  7004

# Enable HTTP should be false on official environment.
enableHttp: false

# Https port if enableHttps is true.
httpsPort:  7444

# Enable HTTPS should be true on official environment.
enableHttps: true

# Http/2 is enabled by default.
enableHttp2: true

# Keystore file name in config folder. KeystorePass is in secret.yml to access it.
keystoreName: tls/server.keystore

# Flag that indicate if two way TLS is enabled. Not recommended in docker container.
enableTwoWayTls: false

# Truststore file name in config folder. TruststorePass is in secret.yml to access it.
truststoreName: tls/server.truststore

# Unique service identifier. Used in service registration and discovery etc.
serviceId: com.networknt.apid-1.0.0

# Flag to enable service registration. Only be true if running as standalone Java jar.
enableRegistry: true

```


### Start Consul

Here we are starting consul server in docker to serve as a centralized registry. To make it
simpler, the ACL in consul is disable by setting acl_default_policy=allow.

```
docker run -d -p 8400:8400 -p 8500:8500/tcp -p 8600:53/udp -e 'CONSUL_LOCAL_CONFIG={"acl_datacenter":"dc1","acl_default_policy":"allow","acl_down_policy":"extend-cache","acl_master_token":"the_one_ring","bootstrap_expect":1,"datacenter":"dc1","data_dir":"/usr/local/bin/consul.d/data","server":true}' consul agent -server -ui -bind=127.0.0.1 -client=0.0.0.0
```


### Start four servers

Now let's start four terminals to start servers.  

API A

```
cd ~/networknt/light-example-4j/discovery/api_a/consul
mvn clean install -DskipTests
mvn exec:exec
```

API B

```
cd ~/networknt/light-example-4j/discovery/api_b/consul
mvn clean install -DskipTests
mvn exec:exec

```

API C

```
cd ~/networknt/light-example-4j/discovery/api_c/consul
mvn clean install -DskipTests 
mvn exec:exec

```

API D


And start the first instance that listen to 7444 as default

```
cd ~/networknt/light-example-4j/discovery/api_d/consul
mvn clean install -DskipTests 
mvn exec:exec

```

If you follow the above sequence to start servers, then you will find that API A and 
API B show some error messages as they cannot establish connections to target servers. 

You can ignore these errors and as the connections will be recreated on the first request. 

Now you can see the registered service from Consul UI.

```
http://localhost:8500/ui
```


### Test four servers

```
curl -k https://localhost:7441/v1/data
```

And the result will be

```
["API C: Message 1","API C: Message 2","API D: Message 1 from port 7444","API D: Message 2 from port 7444","API B: Message 1","API B: Message 2","API A: Message 1","API A: Message 2"]
```
 
### Start another API D
 
Now let's start the second instance of API D. Before starting the serer, let's update
server.yml with port 7445.

```


# Server configuration
---
# This is the default binding address if the service is dockerized.
ip: 0.0.0.0

# Http port if enableHttp is true.
httpPort:  7004

# Enable HTTP should be false on official environment.
enableHttp: false

# Https port if enableHttps is true.
httpsPort:  7445

# Enable HTTPS should be true on official environment.
enableHttps: true

# Http/2 is enabled by default.
enableHttp2: true

# Keystore file name in config folder. KeystorePass is in secret.yml to access it.
keystoreName: tls/server.keystore

# Flag that indicate if two way TLS is enabled. Not recommended in docker container.
enableTwoWayTls: false

# Truststore file name in config folder. TruststorePass is in secret.yml to access it.
truststoreName: tls/server.truststore

# Unique service identifier. Used in service registration and discovery etc.
serviceId: com.networknt.apid-1.0.0

# Flag to enable service registration. Only be true if running as standalone Java jar.
enableRegistry: true

```

And start the second instance

```
cd ~/networknt/light-example-4j/discovery/api_d/consul
mvn clean install -DskipTests 
mvn exec:exec

```

### Test Servers

As we cache the connection in our application, so even there is a new server added to
consul, it won't be used unless we create a new connection for each request. 

Let's issue the same command again. 

```
curl -k https://localhost:7441/v1/data
```

And the result should be the same.

```
["API C: Message 1","API C: Message 2","API D: Message 1 from port 7444","API D: Message 2 from port 7444","API B: Message 1","API B: Message 2","API A: Message 1","API A: Message 2"]
```

Shutdown the server 7444 and wait for 10 seconds. Now when you call the same curl command, 
you will see 7445 server picks up the request. 

```
["API C: Message 1","API C: Message 2","API D: Message 1 from port 7445","API D: Message 2 from port 7445","API B: Message 1","API B: Message 2","API A: Message 1","API A: Message 2"]
```

# Docker

In this step, we are going to dockerize all the APIs and then use [registrator](https://github.com/gliderlabs/registrator) 
for service registry. To make it easier, we are going to use docker-compose to put everything together.

Now let's copy from consul to docker for each API.
 

```
cd ~/networknt/light-example-4j/discovery/api_a
cp -r consul consuldocker
cd ~/networknt/light-example-4j/discovery/api_b
cp -r consul consuldocker
cd ~/networknt/light-example-4j/discovery/api_c
cp -r consul consuldocker
cd ~/networknt/light-example-4j/discovery/api_d
cp -r consul consuldocker
```

### API A

As we will be using docker-compose to start consul, registrator and APIs altogether
and there is no easy way to control the start sequence. We are going to update the 
handler to remove the connection creation in constructor. 

```
package com.networknt.apia.handler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.networknt.client.Http2Client;
import com.networknt.cluster.Cluster;
import com.networknt.config.Config;
import com.networknt.exception.ClientException;
import com.networknt.security.JwtHelper;
import com.networknt.service.SingletonServiceFactory;
import io.undertow.UndertowOptions;
import io.undertow.client.ClientConnection;
import io.undertow.client.ClientRequest;
import io.undertow.client.ClientResponse;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;
import io.undertow.util.Methods;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnio.OptionMap;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

public class DataGetHandler implements HttpHandler {
    static Logger logger = LoggerFactory.getLogger(DataGetHandler.class);
    static Cluster cluster = SingletonServiceFactory.getBean(Cluster.class);
    static String apibHost;
    static String apicHost;
    static String path = "/v1/data";
    static Map<String, Object> securityConfig = (Map)Config.getInstance().getJsonMapConfig(JwtHelper.SECURITY_CONFIG);
    static boolean securityEnabled = (Boolean)securityConfig.get(JwtHelper.ENABLE_VERIFY_JWT);

    static Http2Client client = Http2Client.getInstance();
    static ClientConnection connectionB;
    static ClientConnection connectionC;

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        List<String> list = new ArrayList<>();
        if(connectionB == null || !connectionB.isOpen()) {
            try {
                apibHost = cluster.serviceToUrl("https", "com.networknt.apib-1.0.0", null);
                connectionB = client.connect(new URI(apibHost), Http2Client.WORKER, Http2Client.SSL, Http2Client.POOL, OptionMap.create(UndertowOptions.ENABLE_HTTP2, true)).get();
            } catch (Exception e) {
                logger.error("Exeption:", e);
                throw new ClientException(e);
            }
        }
        if(connectionC == null || !connectionC.isOpen()) {
            try {
                apicHost = cluster.serviceToUrl("https", "com.networknt.apic-1.0.0", null);
                connectionC = client.connect(new URI(apicHost), Http2Client.WORKER, Http2Client.SSL, Http2Client.POOL, OptionMap.create(UndertowOptions.ENABLE_HTTP2, true)).get();
            } catch (Exception e) {
                logger.error("Exeption:", e);
                throw new ClientException(e);
            }
        }
        final CountDownLatch latch = new CountDownLatch(2);
        final AtomicReference<ClientResponse> referenceB = new AtomicReference<>();
        final AtomicReference<ClientResponse> referenceC = new AtomicReference<>();
        try {
            ClientRequest requestB = new ClientRequest().setMethod(Methods.GET).setPath(path);
            if(securityEnabled) client.propagateHeaders(requestB, exchange);
            connectionB.sendRequest(requestB, client.createClientCallback(referenceB, latch));

            ClientRequest requestC = new ClientRequest().setMethod(Methods.GET).setPath(path);
            if(securityEnabled) client.propagateHeaders(requestC, exchange);
            connectionC.sendRequest(requestB, client.createClientCallback(referenceC, latch));

            latch.await();

            int statusCodeB = referenceB.get().getResponseCode();
            if(statusCodeB >= 300){
                throw new Exception("Failed to call API B: " + statusCodeB);
            }
            List<String> apibList = Config.getInstance().getMapper().readValue(referenceB.get().getAttachment(Http2Client.RESPONSE_BODY),
                    new TypeReference<List<String>>(){});
            list.addAll(apibList);

            int statusCodeC = referenceC.get().getResponseCode();
            if(statusCodeC >= 300){
                throw new Exception("Failed to call API C: " + statusCodeC);
            }
            List<String> apicList = Config.getInstance().getMapper().readValue(referenceC.get().getAttachment(Http2Client.RESPONSE_BODY),
                    new TypeReference<List<String>>(){});
            list.addAll(apicList);
        } catch (Exception e) {
            logger.error("Exception:", e);
            throw new ClientException(e);
        }
        list.add("API A: Message 1");
        list.add("API A: Message 2");
        exchange.getResponseSender().send(Config.getInstance().getMapper().writeValueAsString(list));
    }
}

```

Since we are using registrator to register the service, we need to disable the application service registration.

server.yml

```

# Server configuration
---
# This is the default binding address if the service is dockerized.
ip: 0.0.0.0

# Http port if enableHttp is true.
httpPort:  7001

# Enable HTTP should be false on official environment.
enableHttp: false

# Https port if enableHttps is true.
httpsPort:  7441

# Enable HTTPS should be true on official environment.
enableHttps: true

# Http/2 is enabled by default.
enableHttp2: true

# Keystore file name in config folder. KeystorePass is in secret.yml to access it.
keystoreName: tls/server.keystore

# Flag that indicate if two way TLS is enabled. Not recommended in docker container.
enableTwoWayTls: false

# Truststore file name in config folder. TruststorePass is in secret.yml to access it.
truststoreName: tls/server.truststore

# Unique service identifier. Used in service registration and discovery etc.
serviceId: com.networknt.apia-1.0.0

# Flag to enable service registration. Only be true if running as standalone Java jar.
enableRegistry: false
```

Please note that enableRegistry is set to false as we don't need the server to register itself
as it is running inside the Docker container and doesn't know the Container IP address and port.

In order to discover services from client module, we need to update service.yml to setup properties
of URLImpl and properties of ConsulEcwidClient with hostname Consul and port 8500.

service.yml

```
singletons:
- com.networknt.registry.URL:
  - com.networknt.registry.URLImpl:
      protocol: light
      host: localhost
      port: 8080
      path: consul
      parameters:
        registryRetryPeriod: '30000'
- com.networknt.consul.client.ConsulClient:
  - com.networknt.consul.client.ConsulEcwidClient:
    - java.lang.String: consul
    - int: 8500
- com.networknt.registry.Registry:
  - com.networknt.consul.ConsulRegistry
- com.networknt.balance.LoadBalance:
  - com.networknt.balance.RoundRobinLoadBalance
- com.networknt.cluster.Cluster:
  - com.networknt.cluster.LightCluster

```

The Dockerfile generated from light-codegen should expose 7441 based on the config.json

Dockerfile

```
FROM openjdk:8-jre-alpine
EXPOSE  7441
ADD /target/apia-1.0.0.jar server.jar
CMD ["/bin/sh","-c","java -Dlight-4j-config-dir=/config -Dlogback.configurationFile=/config/logback.xml -jar /server.jar"]
```


Let's build a docker image for this service and push it to docker hub. For you own testing
you can ignore the last command to push the image to docker hub or you can change is to
your own docker hub account. 

```
cd ~/networknt/light-example-4j/discovery/api_a/consuldocker
mvn clean install -DskipTests
docker build -t networknt/com.networknt.apia-1.0.0 .
docker push networknt/com.networknt.apia-1.0.0
```

### API B

Similar with API A, we are going to remove the connection creation in constructor for
the handler. 

```
package com.networknt.apib.handler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.networknt.client.Http2Client;
import com.networknt.cluster.Cluster;
import com.networknt.config.Config;
import com.networknt.exception.ClientException;
import com.networknt.security.JwtHelper;
import com.networknt.service.SingletonServiceFactory;
import io.undertow.UndertowOptions;
import io.undertow.client.ClientConnection;
import io.undertow.client.ClientRequest;
import io.undertow.client.ClientResponse;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Methods;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnio.OptionMap;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

public class DataGetHandler implements HttpHandler {
    static Logger logger = LoggerFactory.getLogger(DataGetHandler.class);
    static Cluster cluster = SingletonServiceFactory.getBean(Cluster.class);
    static String apidHost;
    static String path = "/v1/data";
    static Map<String, Object> securityConfig = (Map)Config.getInstance().getJsonMapConfig(JwtHelper.SECURITY_CONFIG);
    static boolean securityEnabled = (Boolean)securityConfig.get(JwtHelper.ENABLE_VERIFY_JWT);
    static Http2Client client = Http2Client.getInstance();
    static ClientConnection connection;

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        List<String> list = new ArrayList<>();
        final CountDownLatch latch = new CountDownLatch(1);
        if(connection == null || !connection.isOpen()) {
            try {
                apidHost = cluster.serviceToUrl("https", "com.networknt.apid-1.0.0", null);
                connection = client.connect(new URI(apidHost), Http2Client.WORKER, Http2Client.SSL, Http2Client.POOL, OptionMap.create(UndertowOptions.ENABLE_HTTP2, true)).get();
            } catch (Exception e) {
                logger.error("Exeption:", e);
                throw new ClientException(e);
            }
        }
        final AtomicReference<ClientResponse> reference = new AtomicReference<>();
        try {
            ClientRequest request = new ClientRequest().setMethod(Methods.GET).setPath(path);
            // this is to ask client module to pass through correlationId and traceabilityId as well as
            // getting access token from oauth2 server automatically and attatch authorization headers.
            if(securityEnabled) client.propagateHeaders(request, exchange);
            connection.sendRequest(request, client.createClientCallback(reference, latch));
            latch.await();
            int statusCode = reference.get().getResponseCode();
            if(statusCode >= 300){
                throw new Exception("Failed to call API D: " + statusCode);
            }
            List<String> apidList = Config.getInstance().getMapper().readValue(reference.get().getAttachment(Http2Client.RESPONSE_BODY),
                    new TypeReference<List<String>>(){});
            list.addAll(apidList);
        } catch (Exception e) {
            logger.error("Exception:", e);
            throw new ClientException(e);
        }
        list.add("API B: Message 1");
        list.add("API B: Message 2");
        exchange.getResponseSender().send(Config.getInstance().getMapper().writeValueAsString(list));
    }
}

```

server.yml

```
# Server configuration
---
# This is the default binding address if the service is dockerized.
ip: 0.0.0.0

# Http port if enableHttp is true.
httpPort:  7002

# Enable HTTP should be false on official environment.
enableHttp: false

# Https port if enableHttps is true.
httpsPort:  7442

# Enable HTTPS should be true on official environment.
enableHttps: true

# Http/2 is enabled by default.
enableHttp2: true

# Keystore file name in config folder. KeystorePass is in secret.yml to access it.
keystoreName: tls/server.keystore

# Flag that indicate if two way TLS is enabled. Not recommended in docker container.
enableTwoWayTls: false

# Truststore file name in config folder. TruststorePass is in secret.yml to access it.
truststoreName: tls/server.truststore

# Unique service identifier. Used in service registration and discovery etc.
serviceId: com.networknt.apib-1.0.0

# Flag to enable service registration. Only be true if running as standalone Java jar.
enableRegistry: false

```

service.yml

```
singletons:
- com.networknt.registry.URL:
  - com.networknt.registry.URLImpl:
      protocol: light
      host: localhost
      port: 8080
      path: consul
      parameters:
        registryRetryPeriod: '30000'
- com.networknt.consul.client.ConsulClient:
  - com.networknt.consul.client.ConsulEcwidClient:
    - java.lang.String: consul
    - int: 8500
- com.networknt.registry.Registry:
  - com.networknt.consul.ConsulRegistry
- com.networknt.balance.LoadBalance:
  - com.networknt.balance.RoundRobinLoadBalance
- com.networknt.cluster.Cluster:
  - com.networknt.cluster.LightCluster

```

Dockerfile should be generated correctly based on the config.json for light-codegen


```
FROM openjdk:8-jre-alpine
EXPOSE  7442
ADD /target/apib-1.0.0.jar server.jar
CMD ["/bin/sh","-c","java -Dlight-4j-config-dir=/config -Dlogback.configurationFile=/config/logback.xml -jar /server.jar"]
```

Let's build a docker image for this service and push it to docker hub.

```
cd ~/networknt/light-example-4j/discovery/api_b/consuldocker
mvn clean install -DskipTests
docker build -t networknt/com.networknt.apib-1.0.0 .
docker push networknt/com.networknt.apib-1.0.0

```

### API C

server.yml

```
# Server configuration
---
# This is the default binding address if the service is dockerized.
ip: 0.0.0.0

# Http port if enableHttp is true.
httpPort:  7003

# Enable HTTP should be false on official environment.
enableHttp: false

# Https port if enableHttps is true.
httpsPort:  7443

# Enable HTTPS should be true on official environment.
enableHttps: true

# Http/2 is enabled by default.
enableHttp2: true

# Keystore file name in config folder. KeystorePass is in secret.yml to access it.
keystoreName: tls/server.keystore

# Flag that indicate if two way TLS is enabled. Not recommended in docker container.
enableTwoWayTls: false

# Truststore file name in config folder. TruststorePass is in secret.yml to access it.
truststoreName: tls/server.truststore

# Unique service identifier. Used in service registration and discovery etc.
serviceId: com.networknt.apic-1.0.0

# Flag to enable service registration. Only be true if running as standalone Java jar.
enableRegistry: false
```

service.yml

```
singletons:
- com.networknt.registry.URL:
  - com.networknt.registry.URLImpl:
      protocol: light
      host: localhost
      port: 8080
      path: consul
      parameters:
        registryRetryPeriod: '30000'
- com.networknt.consul.client.ConsulClient:
  - com.networknt.consul.client.ConsulEcwidClient:
    - java.lang.String: consul
    - int: 8500
- com.networknt.registry.Registry:
  - com.networknt.consul.ConsulRegistry
- com.networknt.balance.LoadBalance:
  - com.networknt.balance.RoundRobinLoadBalance
- com.networknt.cluster.Cluster:
  - com.networknt.cluster.LightCluster
```

Dockerfile should be

```
FROM openjdk:8-jre-alpine
EXPOSE  7443
ADD /target/apic-1.0.0.jar server.jar
CMD ["/bin/sh","-c","java -Dlight-4j-config-dir=/config -Dlogback.configurationFile=/config/logback.xml -jar /server.jar"]
```

Let's build a docker image for this service and push it to docker hub.

```
cd ~/networknt/light-example-4j/discovery/api_c/consuldocker
mvn clean install
docker build -t networknt/com.networknt.apic-1.0.0 .
docker push networknt/com.networknt.apic-1.0.0
```


### API D

server.yml

```
# Server configuration
---
# This is the default binding address if the service is dockerized.
ip: 0.0.0.0

# Http port if enableHttp is true.
httpPort:  7004

# Enable HTTP should be false on official environment.
enableHttp: false

# Https port if enableHttps is true.
httpsPort:  7445

# Enable HTTPS should be true on official environment.
enableHttps: true

# Http/2 is enabled by default.
enableHttp2: true

# Keystore file name in config folder. KeystorePass is in secret.yml to access it.
keystoreName: tls/server.keystore

# Flag that indicate if two way TLS is enabled. Not recommended in docker container.
enableTwoWayTls: false

# Truststore file name in config folder. TruststorePass is in secret.yml to access it.
truststoreName: tls/server.truststore

# Unique service identifier. Used in service registration and discovery etc.
serviceId: com.networknt.apid-1.0.0

# Flag to enable service registration. Only be true if running as standalone Java jar.
enableRegistry: false

```

service.yml

```
singletons:
- com.networknt.registry.URL:
  - com.networknt.registry.URLImpl:
      protocol: light
      host: localhost
      port: 8080
      path: consul
      parameters:
        registryRetryPeriod: '30000'
- com.networknt.consul.client.ConsulClient:
  - com.networknt.consul.client.ConsulEcwidClient:
    - java.lang.String: consul
    - int: 8500
- com.networknt.registry.Registry:
  - com.networknt.consul.ConsulRegistry
- com.networknt.balance.LoadBalance:
  - com.networknt.balance.RoundRobinLoadBalance
- com.networknt.cluster.Cluster:
  - com.networknt.cluster.LightCluster

```

Dockerfile

```
FROM openjdk:8-jre-alpine
EXPOSE  7444
ADD /target/apid-1.0.0.jar server.jar
CMD ["/bin/sh","-c","java -Dlight-4j-config-dir=/config -Dlogback.configurationFile=/config/logback.xml -jar /server.jar"]
```


Let's build a docker image for this service and push it to docker hub.

```
cd ~/networknt/light-example-4j/discovery/api_d/consuldocker
mvn clean install
docker build -t networknt/com.networknt.apid-1.0.0 .
docker push networknt/com.networknt.apid-1.0.0

```

### Start docker-compose

Now we have all four API built, dockerized and pushed to docker hub. Let's start the docker-compose.
Before we start the new docker-compose, make sure we stop the current consul docker container. 


```
cd ~/networknt
git clone git@github.com:networknt/light-docker.git
cd light-docker
docker-compose -f docker-compose-consul-registrator.yml up

```

This compose will start Consul, Registrator and four services together.


### Test Servers

```
curl -k https://localhost:7441/v1/data
```

And here is the result.

```
["API C: Message 1","API C: Message 2","API D: Message 1 from port 7444","API D: Message 2 from port 7444","API B: Message 1","API B: Message 2","API A: Message 1","API A: Message 2"]
```


Note: with the latest registrator, it registers apia, b, c and d into address 172.25.0.4 - 7
and these IPs are not reachable. It is broken and need further investigation. 

# Kubernetes

Kubernetes should be used for production service scheduling with registrator like Docker 
compose.  
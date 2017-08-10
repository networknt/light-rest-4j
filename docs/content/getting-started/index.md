---
date: 2017-08-10T09:41:21-04:00
title: Getting Started
---

To start a microservice with light-rest-4j, the easier way is to use [light-codegen](https://github.com/networknt/light-codegen)
to scaffold a running application based on your OpenAPI specification and a config.json that
controls how code generator works.


The other way to start your project if you don't want to follow design driven approach is to
copy one of our examples and extend it based on your use cases.

## Start project with light-codegen

light-*-4j frameworks encourage design driven approach to build your services which means you
have to create your contract before start coding. This makes sense as the contract is the most
important thing for services and it should be finalized before coding is started. Once the spec.
is ready, light-codegen can be utilized to generate the project. The generated project can be
used by client to start their development immediately.

Here we are going to generate petstore project from the publicly available OpenAPI specification.

Let's assume you have a workspace under your home directory call networknt and you have Java, Git
and Maven installed on your computer.

First we need to clone the light-codegen and model-config which contains all specifications and
configurations for our projects.

```
cd ~/networknt
git clone git@github.com:networknt/light-codegen.git
git clone git@github.com:networknt/model-config.git
cd light-codegen
mvn clean install -DskipTests
cd ~/networknt
java -jar light-codegen/codegen-cli/target/codegen-cli.jar -f light-rest-4j -o petstore -m model-config/rest/petstore/2.0.0/swagger.json -c model-config/rest/petstore/2.0.0/config.json
cd petstore
mvn clean install exec:exec
```

Now you petstore service should be started. Let's test it with curl from another terminal.

```
curl localhost:8080/v2/pet/111
```

And you will see the result like this.

```
{
                "photoUrls" : [ "aeiou" ],
                "name" : "doggie",
                "id" : 123456789,
                "category" : {
                  "name" : "aeiou",
                  "id" : 123456789
                },
                "tags" : [
                  {
                    "name" : "aeiou",
                    "id" : 123456789
                  }
                ],
                "status" : "aeiou"
              }
```

For more information on how to use the light-codegen, please refer to [light-codegen document](https://networknt.github.io/light-codegen/generator/)

For other tutorials on Restful services, please refer to [tutorial](https://networknt.github.io/light-rest-4j/tutorial/)

## Start project from examples

You can find several Restful API examples at https://github.com/networknt/light-example-4j/tree/master/rest
and majority of them has a [tutorial](https://networknt.github.io/light-rest-4j/tutorial/) to
walk you through step by step.

The applications that have a matching tutorial are usually multiple services interact with each
other. There is a separate example petstore which is described above.



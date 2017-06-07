---
date: 2017-06-07T19:03:07-04:00
title: Middleware Handlers
---

The light-rest-4j framework has the following middleware handlers designed around OpenAPI specification
(previously named swagger specification).

* [swagger-meta](https://networknt.github.io/light-rest-4j/middleware/swagger-meta/) is a 
middleware that load swagger at runtime and parse it based on the request uri and method. The
result map is attached to the exchange for subsequent handlers to use.

* [swagger-security](https://networknt.github.io/light-rest-4j/middleware/swagger-security/) 
Oauth2 JWT token verification distributed in every microservice. Also, there is an OAuth2 
server based on light-4j released [here](https://github.com/networknt/light-oauth2)

* [swagger-validator](https://networknt.github.io/light-rest-4j/middleware/swagger-validator/) 
validates request based on the swagger.json for uri parameters, query parameters and body 
which is based on [json-schema-validator](https://github.com/networknt/json-schema-validator)


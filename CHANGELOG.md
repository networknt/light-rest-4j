# Change Log

## [1.5.19](https://github.com/networknt/light-rest-4j/tree/1.5.19) (2018-09-22)
[Full Changelog](https://github.com/networknt/light-rest-4j/compare/1.5.18...1.5.19)

**Closed issues:**

- openapi-validator doesn't validate enum values from the specification [\#56](https://github.com/networknt/light-rest-4j/issues/56)
- upgrade to the new open-parser api for openapi-validator [\#54](https://github.com/networknt/light-rest-4j/issues/54)
- switch to http-string module for HttpString headers constants [\#53](https://github.com/networknt/light-rest-4j/issues/53)
- Error parsing openapi spec when objects depend on eachother [\#52](https://github.com/networknt/light-rest-4j/issues/52)
- support YAML format for the specification in OpenAPI framework [\#51](https://github.com/networknt/light-rest-4j/issues/51)

**Merged pull requests:**

- Implements `OpenApiEndpointSource` and `SwaggerEndpointSource` which … [\#55](https://github.com/networknt/light-rest-4j/pull/55) ([logi](https://github.com/logi))

## [1.5.18](https://github.com/networknt/light-rest-4j/tree/1.5.18) (2018-08-16)
[Full Changelog](https://github.com/networknt/light-rest-4j/compare/1.5.17...1.5.18)

**Closed issues:**

- Fix issue causing null pointer exception when `required` field missing from `requestBody` [\#48](https://github.com/networknt/light-rest-4j/issues/48)
- flatten the config files into the same directory for k8s [\#47](https://github.com/networknt/light-rest-4j/issues/47)
- support specific swagger or openapi security and validator configuration [\#45](https://github.com/networknt/light-rest-4j/issues/45)
- upgrade to undertow 2.0.11.Final [\#44](https://github.com/networknt/light-rest-4j/issues/44)

**Merged pull requests:**

- Fixing issue causing required field to be required in requestBody [\#49](https://github.com/networknt/light-rest-4j/pull/49) ([NicholasAzar](https://github.com/NicholasAzar))
- fixes \#45 support specific swagger or openapi security and validator … [\#46](https://github.com/networknt/light-rest-4j/pull/46) ([stevehu](https://github.com/stevehu))

## [1.5.17](https://github.com/networknt/light-rest-4j/tree/1.5.17) (2018-07-15)
[Full Changelog](https://github.com/networknt/light-rest-4j/compare/1.5.16...1.5.17)

## [1.5.16](https://github.com/networknt/light-rest-4j/tree/1.5.16) (2018-06-19)
[Full Changelog](https://github.com/networknt/light-rest-4j/compare/1.5.15...1.5.16)

## [1.5.15](https://github.com/networknt/light-rest-4j/tree/1.5.15) (2018-06-18)
[Full Changelog](https://github.com/networknt/light-rest-4j/compare/1.5.14...1.5.15)

**Closed issues:**

- remove basic security for this release as it has some issues with Maven Central [\#43](https://github.com/networknt/light-rest-4j/issues/43)
- switch to default setExchangeStatus method for errors [\#42](https://github.com/networknt/light-rest-4j/issues/42)
- add basic security module for basic authentication [\#41](https://github.com/networknt/light-rest-4j/issues/41)
- remove version for jose4j in sub modules [\#40](https://github.com/networknt/light-rest-4j/issues/40)

## [1.5.14](https://github.com/networknt/light-rest-4j/tree/1.5.14) (2018-05-19)
[Full Changelog](https://github.com/networknt/light-rest-4j/compare/1.5.13...1.5.14)

**Closed issues:**

- update VerifyJwt signature and security.yml [\#39](https://github.com/networknt/light-rest-4j/issues/39)
- populate subject claims and access claims in openapi security [\#38](https://github.com/networknt/light-rest-4j/issues/38)
- Log the validation error in logs In error Level [\#37](https://github.com/networknt/light-rest-4j/issues/37)

## [1.5.13](https://github.com/networknt/light-rest-4j/tree/1.5.13) (2018-04-20)
[Full Changelog](https://github.com/networknt/light-rest-4j/compare/1.5.12...1.5.13)

**Fixed bugs:**

- openapi schema validator resolves references incorrectly [\#36](https://github.com/networknt/light-rest-4j/issues/36)

## [1.5.12](https://github.com/networknt/light-rest-4j/tree/1.5.12) (2018-04-08)
[Full Changelog](https://github.com/networknt/light-rest-4j/compare/1.5.11...1.5.12)

**Closed issues:**

- remove consul and zookeeper dependencies [\#35](https://github.com/networknt/light-rest-4j/issues/35)
- upgrade jackson to 2.9.5 and swagger-parser to 1.0.34 [\#34](https://github.com/networknt/light-rest-4j/issues/34)

## [1.5.11](https://github.com/networknt/light-rest-4j/tree/1.5.11) (2018-03-31)
[Full Changelog](https://github.com/networknt/light-rest-4j/compare/1.5.10...1.5.11)

**Implemented enhancements:**

- support parameters validation in path item object  [\#33](https://github.com/networknt/light-rest-4j/issues/33)

**Closed issues:**

- remove docs folder as all documents moved to light-doc [\#32](https://github.com/networknt/light-rest-4j/issues/32)

## [1.5.10](https://github.com/networknt/light-rest-4j/tree/1.5.10) (2018-03-02)
[Full Changelog](https://github.com/networknt/light-rest-4j/compare/1.5.9...1.5.10)

**Closed issues:**

- add subject\_claims and access\_claims in auditInfo attachment [\#31](https://github.com/networknt/light-rest-4j/issues/31)

## [1.5.9](https://github.com/networknt/light-rest-4j/tree/1.5.9) (2018-02-21)
[Full Changelog](https://github.com/networknt/light-rest-4j/compare/1.5.8...1.5.9)

**Closed issues:**

- update travis CI to only build master branch [\#29](https://github.com/networknt/light-rest-4j/issues/29)
- upgrade json-schema-validator to 0.1.15 [\#28](https://github.com/networknt/light-rest-4j/issues/28)

## [1.5.8](https://github.com/networknt/light-rest-4j/tree/1.5.8) (2018-02-03)
[Full Changelog](https://github.com/networknt/light-rest-4j/compare/1.5.7...1.5.8)

## [1.5.7](https://github.com/networknt/light-rest-4j/tree/1.5.7) (2018-01-09)
[Full Changelog](https://github.com/networknt/light-rest-4j/compare/1.5.6...1.5.7)

## [1.5.6](https://github.com/networknt/light-rest-4j/tree/1.5.6) (2017-12-28)
[Full Changelog](https://github.com/networknt/light-rest-4j/compare/1.5.4...1.5.6)

**Closed issues:**

- Update default security.yml in openapi-security and swagger-security [\#26](https://github.com/networknt/light-rest-4j/issues/26)
- Maven build warnings  [\#25](https://github.com/networknt/light-rest-4j/issues/25)

## [1.5.4](https://github.com/networknt/light-rest-4j/tree/1.5.4) (2017-11-21)
[Full Changelog](https://github.com/networknt/light-rest-4j/compare/1.5.1...1.5.4)

**Closed issues:**

- Switch OpenAPI 3.0 specification file from yaml to json for framework input [\#24](https://github.com/networknt/light-rest-4j/issues/24)
- Support OpenAPI 3.0 specification format [\#23](https://github.com/networknt/light-rest-4j/issues/23)

## [1.5.1](https://github.com/networknt/light-rest-4j/tree/1.5.1) (2017-11-09)
[Full Changelog](https://github.com/networknt/light-rest-4j/compare/1.5.0...1.5.1)

**Closed issues:**

- com.fizzed.rocker.runtime.RockerRuntime - Rocker template reloading not activated [\#22](https://github.com/networknt/light-rest-4j/issues/22)

**Merged pull requests:**

- Fix for case when swagger.getBasePath is "/" [\#21](https://github.com/networknt/light-rest-4j/pull/21) ([chaudhryfaisal](https://github.com/chaudhryfaisal))

## [1.5.0](https://github.com/networknt/light-rest-4j/tree/1.5.0) (2017-10-21)
[Full Changelog](https://github.com/networknt/light-rest-4j/compare/1.4.6...1.5.0)

**Closed issues:**

- Upgrade dependencies and add maven-version [\#20](https://github.com/networknt/light-rest-4j/issues/20)

## [1.4.6](https://github.com/networknt/light-rest-4j/tree/1.4.6) (2017-09-22)
[Full Changelog](https://github.com/networknt/light-rest-4j/compare/1.4.5...1.4.6)

**Closed issues:**

- Make swagger-validator aware of body parser enabled or not [\#19](https://github.com/networknt/light-rest-4j/issues/19)

## [1.4.5](https://github.com/networknt/light-rest-4j/tree/1.4.5) (2017-09-22)
[Full Changelog](https://github.com/networknt/light-rest-4j/compare/1.4.4...1.4.5)

## [1.4.4](https://github.com/networknt/light-rest-4j/tree/1.4.4) (2017-09-21)
[Full Changelog](https://github.com/networknt/light-rest-4j/compare/1.4.3...1.4.4)

**Closed issues:**

- The swagger-validator has hard dependency on BodyHandler to parse the body to object into attachement [\#18](https://github.com/networknt/light-rest-4j/issues/18)

## [1.4.3](https://github.com/networknt/light-rest-4j/tree/1.4.3) (2017-09-10)
[Full Changelog](https://github.com/networknt/light-rest-4j/compare/1.4.2...1.4.3)

## [1.4.2](https://github.com/networknt/light-rest-4j/tree/1.4.2) (2017-08-31)
[Full Changelog](https://github.com/networknt/light-rest-4j/compare/1.4.1...1.4.2)

## [1.4.1](https://github.com/networknt/light-rest-4j/tree/1.4.1) (2017-08-30)
[Full Changelog](https://github.com/networknt/light-rest-4j/compare/1.4.0...1.4.1)

**Closed issues:**

- Upgrade Undertow and Jackson to the newer version [\#17](https://github.com/networknt/light-rest-4j/issues/17)

## [1.4.0](https://github.com/networknt/light-rest-4j/tree/1.4.0) (2017-08-22)
[Full Changelog](https://github.com/networknt/light-rest-4j/compare/1.3.5...1.4.0)

**Closed issues:**

- Switch from Client to Http2Client in test cases and remove dependency for apache httpclient [\#16](https://github.com/networknt/light-rest-4j/issues/16)
- Merge petstore example from light-4j to light-rest-4j document site [\#15](https://github.com/networknt/light-rest-4j/issues/15)
- Upgrade to Undertow 1.4.18.Final and remove dependency on JsonPath [\#14](https://github.com/networknt/light-rest-4j/issues/14)
- Fix HTTP 404 on documentation [\#13](https://github.com/networknt/light-rest-4j/issues/13)

## [1.3.5](https://github.com/networknt/light-rest-4j/tree/1.3.5) (2017-08-01)
[Full Changelog](https://github.com/networknt/light-rest-4j/compare/1.3.4...1.3.5)

## [1.3.4](https://github.com/networknt/light-rest-4j/tree/1.3.4) (2017-07-08)
[Full Changelog](https://github.com/networknt/light-rest-4j/compare/1.3.3...1.3.4)

**Closed issues:**

- Log error in JwtVerifyHandler if error response goes back to consumer. [\#12](https://github.com/networknt/light-rest-4j/issues/12)

## [1.3.3](https://github.com/networknt/light-rest-4j/tree/1.3.3) (2017-06-14)
[Full Changelog](https://github.com/networknt/light-rest-4j/compare/1.3.2...1.3.3)

**Fixed bugs:**

- client\_id and user\_id are not populated when auditInfo is not null [\#11](https://github.com/networknt/light-rest-4j/issues/11)

## [1.3.2](https://github.com/networknt/light-rest-4j/tree/1.3.2) (2017-06-14)
[Full Changelog](https://github.com/networknt/light-rest-4j/compare/1.3.1...1.3.2)

**Closed issues:**

- Upgrade json-schema-validator to 0.1.7 [\#10](https://github.com/networknt/light-rest-4j/issues/10)
- Remove response validator and description in validator.yml and ValidatorConfig object [\#9](https://github.com/networknt/light-rest-4j/issues/9)
- Populate auditInfo map object in exchange from swagger-meta and swagger-security for metrics [\#8](https://github.com/networknt/light-rest-4j/issues/8)

## [1.3.1](https://github.com/networknt/light-rest-4j/tree/1.3.1) (2017-06-03)
[Full Changelog](https://github.com/networknt/light-rest-4j/compare/1.3.0...1.3.1)

**Closed issues:**

- Add comments and update docs [\#7](https://github.com/networknt/light-rest-4j/issues/7)

## [1.3.0](https://github.com/networknt/light-rest-4j/tree/1.3.0) (2017-05-06)
[Full Changelog](https://github.com/networknt/light-rest-4j/compare/1.2.8...1.3.0)

**Closed issues:**

- Change project name to light-rest-4j from light-java-rest as java is a trademark of Oracle [\#6](https://github.com/networknt/light-rest-4j/issues/6)

## [1.2.8](https://github.com/networknt/light-rest-4j/tree/1.2.8) (2017-05-02)
[Full Changelog](https://github.com/networknt/light-rest-4j/compare/1.2.7...1.2.8)

**Closed issues:**

- Bump up scope mismatch log from debug to warn as it is security violation [\#5](https://github.com/networknt/light-rest-4j/issues/5)
- Upgrade dependencies to the latest version [\#4](https://github.com/networknt/light-rest-4j/issues/4)

## [1.2.7](https://github.com/networknt/light-rest-4j/tree/1.2.7) (2017-03-28)
[Full Changelog](https://github.com/networknt/light-rest-4j/compare/1.2.6...1.2.7)

**Closed issues:**

- Upgrade undertow to 1.4.11.Final [\#3](https://github.com/networknt/light-rest-4j/issues/3)

## [1.2.6](https://github.com/networknt/light-rest-4j/tree/1.2.6) (2017-03-18)
[Full Changelog](https://github.com/networknt/light-rest-4j/compare/1.2.5...1.2.6)

**Implemented enhancements:**

- Token scope and spec scope mismatch error is not clear in logs [\#2](https://github.com/networknt/light-rest-4j/issues/2)

## [1.2.5](https://github.com/networknt/light-rest-4j/tree/1.2.5) (2017-03-04)
[Full Changelog](https://github.com/networknt/light-rest-4j/compare/1.2.4...1.2.5)

## [1.2.4](https://github.com/networknt/light-rest-4j/tree/1.2.4) (2017-02-20)
[Full Changelog](https://github.com/networknt/light-rest-4j/compare/1.2.3...1.2.4)

## [1.2.3](https://github.com/networknt/light-rest-4j/tree/1.2.3) (2017-02-09)
[Full Changelog](https://github.com/networknt/light-rest-4j/compare/1.2.2...1.2.3)

## [1.2.2](https://github.com/networknt/light-rest-4j/tree/1.2.2) (2017-02-04)
[Full Changelog](https://github.com/networknt/light-rest-4j/compare/1.2.1...1.2.2)

## [1.2.1](https://github.com/networknt/light-rest-4j/tree/1.2.1) (2017-01-25)
[Full Changelog](https://github.com/networknt/light-rest-4j/compare/1.2.0...1.2.1)

## [1.2.0](https://github.com/networknt/light-rest-4j/tree/1.2.0) (2017-01-22)
**Closed issues:**

- Move swagger, security and validator handlers from light-java [\#1](https://github.com/networknt/light-rest-4j/issues/1)



\* *This Change Log was automatically generated by [github_changelog_generator](https://github.com/skywinder/Github-Changelog-Generator)*
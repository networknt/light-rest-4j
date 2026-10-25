A RESTful API or service framework built on top of light-4j

[Stack Overflow](https://stackoverflow.com/questions/tagged/light-4j) |
[Google Group](https://groups.google.com/forum/#!forum/light-4j) |
[Gitter Chat](https://gitter.im/networknt/light-rest-4j) |
[Subreddit](https://www.reddit.com/r/lightapi/) |
[Youtube Channel](https://www.youtube.com/channel/UCHCRMWJVXw8iB7zKxF55Byw) |
[Documentation](https://doc.networknt.com/style/light-rest-4j/) |
[Contribution Guide](https://doc.networknt.com/contribute/) |

[![Build Status](https://travis-ci.org/networknt/light-rest-4j.svg?branch=master)](https://travis-ci.org/networknt/light-rest-4j) [![codecov.io](https://codecov.io/github/networknt/light-rest-4j/coverage.svg?branch=master)](https://codecov.io/github/networknt/light-rest-4j?branch=master)

This repository contains middleware handlers that work with either Swagger 2.0 or OpenAPI 3.0 specification. If you are starting a brand new project, it is highly recommended using the OpenAPI 3.0 specification for your design.

To get started your first project, please visit the [getting started](https://doc.networknt.com/getting-started/light-rest-4j/) to walk through the process.

For documentation on the handlers, please visit the [reference document](https://doc.networknt.com/style/light-rest-4j/).

There are also numeric [tutorials](https://doc.networknt.com/tutorial/rest/) to show users how to build Restful services.


### Jackson 3 development migration

This development line uses Jackson 3.2.3 and json-schema-validator 3.0.8.
Jackson core/databind types in public APIs now use `tools.jackson`; annotations
remain under `com.fasterxml.jackson.annotation`. This is a source and binary
API change. The existing project version is unchanged during implementation;
coordinate a next-major release before publishing these artifacts.

Build against the migrated light-4j, http-client, json-overlay, openapi-parser
and yaml-rule sources/artifacts together. Old Jackson 2 snapshots under the same
coordinates are incompatible. yaml-rule now resolves to the migrated 4.0.0-SNAPSHOT
coordinate. Framework JSON policies come from the migrated Config mapper.
Handled malformed response JSON and injected-spec serialization errors retain
their previous error paths with unchecked JacksonException handling. The #448
routing publication and validator reload behavior is retained.

OpenAPI YAML uses the upstream Jackson 3 YAML resolver. Use canonical `true`,
`false`, `null` and decimal numbers for typed values; quote ambiguous string
scalars. Examples such as yes/no/on/off, True/FALSE, Null/NULL, 012, 0x1F,
0b11, 1_000 and ~ have different scalar types or values from Jackson 2.
The checked-in YAML corpus has no scalar differences; deployed specifications
need a separate audit. Classic SnakeYAML pins remain for the final dependency
cleanup phase.

The source-build workflow resolves yaml-rule #36 and light-4j #2815 to their
latest heads while open, then follows their base branches after merge. Other
prerequisites use master. Each run logs the exact source commits tested and uses
a fresh Maven repository. Rerun this check after prerequisite changes and before
merging: an earlier green run qualifies only its recorded commits. Manual
workflow dispatch is available. Source-build success does not qualify published
artifact resolution.

Prerequisites are resolved to full commit SHAs before checkout. To replay a
failed dependency combination, manually dispatch the source-build workflow with
its uploaded `source-refs.json` contents as the `source_refs` input. Omitted
repositories resolve their current source head; supplied values must be full
commit SHAs. Each green result qualifies only the recorded combination.

GitHub manual dispatch requires this workflow to exist on the default branch.
Before merge, use the recorded commits to reproduce with local source checkouts.

HTTP tests in openapi-meta, openapi-security and openapi-validator depend in test
scope on the `light-rest-test-support` reactor module. Its JUnit-dependent helper
classes are compiled once into that fixture jar; production consumers do not
inherit them at runtime. `HttpClientFixture` owns I/O-thread sends and bounded
response waits. Restoring a token keeps its socket open for pool reuse, and each
fixture closes its tracked sockets at teardown before stopping its listener.
Each consumer module runs a two-request test asserting that the second borrow
returns the same socket. Direct Undertow fixtures bind ephemeral ports; JWKS tests
temporarily point client config at their ephemeral key server and restore the
prior map afterward. The configuration-driven UnifiedSecurityHandler fixture
retains its checked-in server port, with tracked sockets closed at its teardown.

The fixture module is installed only for reactor/local test use: it sets both
`maven.deploy.skip` and Central Publishing `skipPublishing` to true. Its inherited
reuse tests assert the token URI equals the listener URI and that closing tracked
connections actually closes the reused socket, before stopping the server.

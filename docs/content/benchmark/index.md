---
date: 2017-08-10T09:42:38-04:00
title: Benchmark
---

One of the major goals to design light-*-4j frameworks is to provide a Java EE
alternative with lower latency, higher throughput and smaller memory footprint.

We basically achieved this goal and beyond. Internally, we maintain a [microservices
benchmark](https://github.com/networknt/microservices-framework-benchmark) with all
the popular microservices frameworks.

There are third party test from TechEmpower from [Round 14](https://www.techempower.com/benchmarks/)
with a version that is not even optimized. Note that it is called light-java in that
benchmark and later we have changed name to light-4j.

Now we are working with TechEmpower to optimize the light-4j implementation and I am
confident that Round 15 will be better than 14. [Here](https://www.techempower.com/benchmarks/previews/round15/)
is a preview of Round 15 with multiple query and multiple update not optimized yet.

All the implementations for TechEmpower are following RESTful style although there
are a lot of middleware handlers are removed. The source code can be used as an
example on how to implement high performance microservices.




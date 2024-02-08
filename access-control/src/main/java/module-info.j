module com.networknt.openapi-control {
    exports com.networknt.openapi;

    requires com.networknt.config;
    requires com.networknt.handler;
    requires com.networknt.utility;

    requires com.fasterxml.jackson.core;
    requires org.slf4j;
    requires java.logging;
}

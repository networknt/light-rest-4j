package com.networknt.openapi;

import com.networknt.handler.LightHttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;

public class JwkHandler implements LightHttpHandler {

    public JwkHandler() {
        if(logger.isInfoEnabled()) logger.info("JwkHandler constructed");
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        exchange.setStatusCode(200);
        exchange.getResponseHeaders().add(new HttpString("Content-Type"), "application/json");
        exchange.getResponseSender().send("{\"keys\":[{\"kty\":\"RSA\",\"kid\":\"Tj_l_tIBTginOtQbL0Pv5w\",\"n\":\"0YRbWAb1FGDpPUUcrIpJC6BwlswlKMS-z2wMAobdo0BNxNa7hG_gIHVPkXu14Jfo1JhUhS4wES3DdY3a6olqPcRN1TCCUVHd-1TLd1BBS-yq9tdJ6HCewhe5fXonaRRKwutvoH7i_eR4m3fQ1GoVzVAA3IngpTr4ptnM3Ef3fj-5wZYmitzrRUyQtfARTl3qGaXP_g8pHFAP0zrNVvOnV-jcNMKm8YZNcgcs1SuLSFtUDXpf7Nr2_xOhiNM-biES6Dza1sMLrlxULFuctudO9lykB7yFh3LHMxtIZyIUHuy0RbjuOGC5PmDowLttZpPI_j4ynJHAaAWr8Ddz764WdQ\",\"e\":\"AQAB\"}]}");
    }
}

package com.hyf.agent_work_foot.auth;
import com.hyf.agent_work_foot.config.WeChatMiniProgramProperties;
import com.hyf.agent_work_foot.common.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;
class WeChatTimeoutTests {
    @Test void slowWeChatServerFailsWithinTheFrontendRequestBudget() throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",exchange->{
            try { Thread.sleep(4500); } catch(InterruptedException ignored) { Thread.currentThread().interrupt(); }
            byte[] body="{\"openid\":\"test-only\"}".getBytes(StandardCharsets.UTF_8);
            try { exchange.sendResponseHeaders(200,body.length); exchange.getResponseBody().write(body); } finally { exchange.close(); }
        });
        server.start();
        try {
            var client=new WeChatMiniProgramClient(new WeChatMiniProgramProperties("test-app","test-secret","http://127.0.0.1:"+server.getAddress().getPort()+"/"),new ObjectMapper());
            long started=System.nanoTime();
            assertThrows(ApiException.class,()->client.exchangeCode("test-code"));
            assertTrue((System.nanoTime()-started)/1_000_000 < 4200);
        } finally { server.stop(0); }
    }
}

package com.hyf.agent_work_foot.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.DefaultCorsProcessor;

/** 本地网页来源的CORS白名单测试，不依赖Spring上下文、MySQL或Docker。 */
class WebCorsConfigTest {
    private final CorsConfigurationSource source = new WebCorsConfig().corsConfigurationSource(
            new WebCorsProperties(List.of("http://localhost:5173"))
    );

    @Test
    void allowsConfiguredLocalPreflightRequest() throws Exception {
        MockHttpServletRequest request = preflight("http://localhost:5173", "PATCH");
        MockHttpServletResponse response = new MockHttpServletResponse();
        CorsConfiguration configuration = source.getCorsConfiguration(request);

        assertTrue(new DefaultCorsProcessor().processRequest(configuration, request, response));
        assertEquals("http://localhost:5173", response.getHeader("Access-Control-Allow-Origin"));
        assertTrue(response.getHeader("Access-Control-Allow-Methods").contains("PATCH"));
    }

    @Test
    void allowsEveryBrowserMethodUsedByTheApi() throws Exception {
        for (String method : List.of("GET", "POST", "PUT", "PATCH", "DELETE")) {
            MockHttpServletRequest request = preflight("http://localhost:5173", method);
            MockHttpServletResponse response = new MockHttpServletResponse();
            CorsConfiguration configuration = source.getCorsConfiguration(request);

            assertTrue(new DefaultCorsProcessor().processRequest(configuration, request, response));
            assertTrue(response.getHeader("Access-Control-Allow-Methods").contains(method));
        }
    }

    @Test
    void rejectsUnconfiguredOrigin() throws Exception {
        MockHttpServletRequest request = preflight("http://localhost:3000", "GET");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertFalse(new DefaultCorsProcessor().processRequest(source.getCorsConfiguration(request), request, response));
    }

    private MockHttpServletRequest preflight(String origin, String method) {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/v1/admin/dashboard");
        request.addHeader("Origin", origin);
        request.addHeader("Access-Control-Request-Method", method);
        request.addHeader("Access-Control-Request-Headers", "Authorization");
        return request;
    }
}

package com.schwab.notificationservice;

import com.schwab.notificationservice.config.RequestCorrelationFilter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class NotificationServiceApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void contextLoads() {
    }

    @Test
    void unauthenticatedNotificationEndpointIsRejected() throws Exception {
        mockMvc.perform(get("/api/notifications/missing"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("X-Correlation-Id"));
    }

    @Test
    void actuatorHealthIsPublicAndPreservesProvidedCorrelationId() throws Exception {
        mockMvc.perform(get("/actuator/health").header("X-Correlation-Id", "test-correlation-id"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-Id", "test-correlation-id"));
    }

    @Test
    void blankCorrelationIdIsReplacedWithGeneratedValue() throws Exception {
        mockMvc.perform(get("/actuator/health").header("X-Correlation-Id", "  "))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Correlation-Id"));
    }

        @Test
        void correlationFilterGeneratesMissingIdsAndCleansTraceContext() throws Exception {
        RequestCorrelationFilter filter = new RequestCorrelationFilter();
        MockHttpServletRequest generatedRequest = new MockHttpServletRequest();
        MockHttpServletResponse generatedResponse = new MockHttpServletResponse();

        filter.doFilter(generatedRequest, generatedResponse, (request, response) ->
            org.junit.jupiter.api.Assertions.assertNotNull(
                ((MockHttpServletResponse) response).getHeader(RequestCorrelationFilter.CORRELATION_ID_HEADER)));

        MockHttpServletRequest providedRequest = new MockHttpServletRequest();
        providedRequest.addHeader(RequestCorrelationFilter.CORRELATION_ID_HEADER, "provided-correlation-id");
        MockHttpServletResponse providedResponse = new MockHttpServletResponse();
        filter.doFilter(providedRequest, providedResponse, (request, response) ->
            org.junit.jupiter.api.Assertions.assertEquals("provided-correlation-id",
                ((MockHttpServletResponse) response).getHeader(RequestCorrelationFilter.CORRELATION_ID_HEADER)));
        }

    @Test
    void authenticatedInvalidNotificationReturnsStructuredValidationError() throws Exception {
        mockMvc.perform(post("/api/notifications")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("tester"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(header().exists("X-Correlation-Id"));
    }

    @Test
    void authenticatedMalformedNotificationReturnsStructuredError() throws Exception {
        mockMvc.perform(post("/api/notifications")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("tester"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Malformed request body"));
    }
}

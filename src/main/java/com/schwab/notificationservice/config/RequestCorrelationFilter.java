package com.schwab.notificationservice.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestCorrelationFilter extends OncePerRequestFilter {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    public static final String TRACE_ID_KEY = "traceId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String correlationId = Optional.ofNullable(request.getHeader(CORRELATION_ID_HEADER))
                .filter(value -> !value.isBlank())
                .orElseGet(() -> UUID.randomUUID().toString());

        response.setHeader(CORRELATION_ID_HEADER, correlationId);
        MDC.put(TRACE_ID_KEY, correlationId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(TRACE_ID_KEY);
        }
    }
}

package com.devsuperior.ingestor.filter;

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
import java.util.UUID;

/**
 * Captura o header X-Correlation-ID da request (gera UUID se ausente),
 * coloca no MDC para aparecer nos logs, salva no atributo da request para
 * o controller propagar adiante via header SQS, e devolve no response
 * para o cliente saber qual id rastrear.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Correlation-ID";
    public static final String MDC_KEY = "correlationId";
    public static final String REQUEST_ATTR = "correlationId";
    private static final String SOURCE_MDC_KEY = "source";
    private static final String SOURCE = "ms-ticket-ingestor";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String cid = request.getHeader(HEADER);
        if (cid == null || cid.isBlank()) {
            cid = UUID.randomUUID().toString();
        }
        try {
            MDC.put(MDC_KEY, cid);
            MDC.put(SOURCE_MDC_KEY, SOURCE);
            request.setAttribute(REQUEST_ATTR, cid);
            response.setHeader(HEADER, cid);
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
            MDC.remove(SOURCE_MDC_KEY);
        }
    }
}

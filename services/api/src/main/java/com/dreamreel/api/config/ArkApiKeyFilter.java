package com.dreamreel.api.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class ArkApiKeyFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        try {
            var header = ArkApiKeyResolver.sanitize(request.getHeader(ArkApiKeyContext.HEADER));
            if (header != null) {
                ArkApiKeyContext.set(header);
            }
            filterChain.doFilter(request, response);
        } finally {
            ArkApiKeyContext.clear();
        }
    }
}

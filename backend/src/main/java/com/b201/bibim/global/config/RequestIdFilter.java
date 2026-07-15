package com.b201.bibim.global.config;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String ATTRIBUTE_NAME = RequestIdFilter.class.getName() + ".requestId";
    public static final String HEADER_NAME = "X-Request-Id";

    private static final Pattern SAFE_REQUEST_ID = Pattern.compile("[A-Za-z0-9-]{1,100}");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String requestId = resolveRequestId(request.getHeader(HEADER_NAME));
        request.setAttribute(ATTRIBUTE_NAME, requestId);
        response.setHeader(HEADER_NAME, requestId);
        filterChain.doFilter(request, response);
    }

    private String resolveRequestId(String candidate) {
        if (StringUtils.hasText(candidate) && SAFE_REQUEST_ID.matcher(candidate).matches()) {
            return candidate;
        }
        return UUID.randomUUID().toString();
    }
}


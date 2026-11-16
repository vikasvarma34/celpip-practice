package com.celpip.practice.imports;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.net.URI;
import java.util.Set;

@Component
class LocalRequestFilter extends OncePerRequestFilter {
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        if (!request.getRequestURI().startsWith("/api/")) { chain.doFilter(request,response); return; }
        response.setHeader("Cache-Control", "no-store"); response.setHeader("X-Content-Type-Options", "nosniff");
        boolean allowed = Set.of("127.0.0.1", "localhost", "[::1]", "::1").contains(request.getServerName());
        if (!Set.of("GET", "HEAD", "OPTIONS").contains(request.getMethod())) {
            String origin = request.getHeader("Origin");
            if (origin != null) {
                try { URI uri = URI.create(origin); allowed &= "http".equals(uri.getScheme()) && Set.of("127.0.0.1", "localhost").contains(uri.getHost()) && Set.of(47321,47322).contains(uri.getPort()) && uri.getRawUserInfo() == null && (uri.getPath() == null || uri.getPath().isEmpty()); }
                catch (Exception ex) { allowed = false; }
            }
            allowed &= !"cross-site".equals(request.getHeader("Sec-Fetch-Site"));
        }
        if (!allowed) {
            response.setStatus(403); response.setContentType("application/json");
            response.getWriter().write("{\"code\":\"LOCAL_REQUEST_REQUIRED\",\"message\":\"Use the local application origin.\",\"fieldErrors\":{}}"); return;
        }
        chain.doFilter(request,response);
    }
}

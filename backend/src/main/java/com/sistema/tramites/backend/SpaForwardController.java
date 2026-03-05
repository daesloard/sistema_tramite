package com.sistema.tramites.backend;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class SpaForwardController {

    // Forward SPA client-side routes to index.html while excluding API and technical paths.
    @GetMapping({"/", "/{*path}"})
    public String forwardSpaRoutes(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null || uri.isBlank()) {
            return "forward:/index.html";
        }

        if (uri.startsWith("/api")
            || uri.startsWith("/h2-console")
            || uri.startsWith("/actuator")
            || uri.startsWith("/v3")
            || uri.startsWith("/swagger-ui")
            || uri.contains(".")) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        return "forward:/index.html";
    }
}
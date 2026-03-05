package com.sistema.tramites.backend;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaForwardController {

    // Forward SPA client-side routes to index.html, but keep /api/** and static assets untouched.
    @GetMapping(value = {
        "/",
        "/{path:(?!api$)[^\\.]*}",
        "/{path:(?!api$)[^\\.]*}/**/{subpath:[^\\.]*}"
    })
    public String forwardSpaRoutes() {
        return "forward:/index.html";
    }
}
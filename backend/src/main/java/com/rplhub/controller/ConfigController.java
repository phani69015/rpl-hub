package com.rplhub.controller;

import com.rplhub.ai.AiClient;
import com.rplhub.model.AppConfig;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public feature flags the frontend needs before sign-in. */
@RestController
@RequestMapping("/api/config")
class ConfigController {

    private final AiClient ai;

    ConfigController(AiClient ai) {
        this.ai = ai;
    }

    @GetMapping
    AppConfig config() {
        return new AppConfig(ai.enabled());
    }
}

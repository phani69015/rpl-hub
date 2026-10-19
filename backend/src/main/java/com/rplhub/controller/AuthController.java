package com.rplhub.controller;

import com.rplhub.model.AppUser;
import com.rplhub.model.Me;
import com.rplhub.model.OtpRequest;
import com.rplhub.model.TokenResponse;
import com.rplhub.model.VerifyRequest;
import com.rplhub.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
class AuthController {

    private final AuthService auth;

    AuthController(AuthService auth) {
        this.auth = auth;
    }

    @GetMapping("/me")
    Me me(@AuthenticationPrincipal AppUser u) {
        return Me.of(u);
    }

    @PostMapping("/otp/request")
    ResponseEntity<Void> request(@Valid @RequestBody OtpRequest req) {
        auth.requestOtp(req.phone());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/otp/verify")
    TokenResponse verify(@Valid @RequestBody VerifyRequest req) {
        return auth.verify(req.phone(), req.otp());
    }
}

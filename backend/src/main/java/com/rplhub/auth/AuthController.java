package com.rplhub.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import com.rplhub.user.AppUser;
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

    private static final String PHONE = "^\\+?[0-9]{10,15}$";

    record OtpRequest(@Pattern(regexp = PHONE, message = "invalid phone number") String phone) {}
    record VerifyRequest(@Pattern(regexp = PHONE, message = "invalid phone number") String phone, @NotBlank String otp) {}
    record TokenResponse(String token, String role) {}

    record Me(String phone, String role, boolean canDecide, boolean canPublish) {}

    private final AuthService auth;

    AuthController(AuthService auth) {
        this.auth = auth;
    }

    @GetMapping("/me")
    Me me(@AuthenticationPrincipal AppUser u) {
        return new Me(u.phone(), u.role(), u.canDecide(), u.canPublish());
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

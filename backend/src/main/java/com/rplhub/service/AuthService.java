package com.rplhub.service;

import com.rplhub.model.AppUser;
import com.rplhub.model.TokenResponse;
import com.rplhub.repository.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** OTP is mocked: a fixed code is accepted. Replace requestOtp/verify check when an SMS provider is chosen. */
@Service
public class AuthService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository users;
    private final JwtEncoder encoder;
    private final String mockCode;
    private final long ttlHours;
    private final String adminPhone;

    AuthService(UserRepository users, JwtEncoder encoder,
                @Value("${app.otp.mock-code}") String mockCode,
                @Value("${app.jwt.ttl-hours}") long ttlHours,
                @Value("${app.bootstrap-admin-phone:}") String adminPhone) {
        this.users = users;
        this.encoder = encoder;
        this.mockCode = mockCode;
        this.ttlHours = ttlHours;
        this.adminPhone = adminPhone;
    }

    /** Seeds the bootstrap administrator so someone can sign in to import data. */
    @Override
    public void run(ApplicationArguments args) {
        if (!adminPhone.isBlank()) users.saveAdmin(adminPhone);
    }

    public void requestOtp(String phone) {
        log.info("MOCK OTP for {} is {}", phone, mockCode);
    }

    public TokenResponse verify(String phone, String otp) {
        if (!mockCode.equals(otp)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid OTP");
        AppUser user = users.findOrCreateWorker(phone);
        Instant now = Instant.now();
        var claims = JwtClaimsSet.builder()
            .subject(user.id().toString()).claim("role", user.role()).claim("phone", phone)
            .issuedAt(now).expiresAt(now.plus(ttlHours, ChronoUnit.HOURS)).build();
        String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new TokenResponse(token, user.role());
    }
}

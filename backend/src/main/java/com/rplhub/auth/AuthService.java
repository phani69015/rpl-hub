package com.rplhub.auth;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** OTP is mocked: a fixed code is accepted. Replace requestOtp/verify check when an SMS provider is chosen. */
@Service
class AuthService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final JdbcClient jdbc;
    private final JwtEncoder encoder;
    private final String mockCode;
    private final long ttlHours;
    private final String adminPhone;

    AuthService(JdbcClient jdbc, JwtEncoder encoder,
                @Value("${app.otp.mock-code}") String mockCode,
                @Value("${app.jwt.ttl-hours}") long ttlHours,
                @Value("${app.bootstrap-admin-phone:}") String adminPhone) {
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.mockCode = mockCode;
        this.ttlHours = ttlHours;
        this.adminPhone = adminPhone;
    }

    /** Seeds the bootstrap administrator so someone can sign in to import data. */
    @Override
    public void run(ApplicationArguments args) {
        if (adminPhone.isBlank()) return;
        jdbc.sql("""
                insert into app_user(id, phone, role, can_publish, can_decide) values (?,?, 'ADMIN', true, true)
                on conflict (phone) do update set role = 'ADMIN', can_publish = true, can_decide = true""")
            .params(UUID.randomUUID(), adminPhone).update();
    }

    void requestOtp(String phone) {
        log.info("MOCK OTP for {} is {}", phone, mockCode);
    }

    AuthController.TokenResponse verify(String phone, String otp) {
        if (!mockCode.equals(otp)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid OTP");
        jdbc.sql("insert into app_user(id, phone, role) values (?,?, 'WORKER') on conflict (phone) do nothing")
            .params(UUID.randomUUID(), phone).update();
        var user = jdbc.sql("select id, role from app_user where phone = ?").param(phone)
            .query((rs, i) -> new String[] {rs.getString("id"), rs.getString("role")}).single();

        Instant now = Instant.now();
        var claims = JwtClaimsSet.builder()
            .subject(user[0]).claim("role", user[1]).claim("phone", phone)
            .issuedAt(now).expiresAt(now.plus(ttlHours, ChronoUnit.HOURS)).build();
        String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
            .getTokenValue();
        return new AuthController.TokenResponse(token, user[1]);
    }
}

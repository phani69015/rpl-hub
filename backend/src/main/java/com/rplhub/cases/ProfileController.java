package com.rplhub.cases;

import com.rplhub.user.AppUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/profile")
class ProfileController {

    record Profile(@NotBlank @Size(max = 120) String fullName, @Size(max = 120) String primaryTrade,
                   @Min(0) @Max(60) Integer yearsExperience, @Size(max = 4000) String skills) {}

    private final JdbcClient jdbc;

    ProfileController(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    Optional<Profile> get(@AuthenticationPrincipal AppUser u) {
        requireWorker(u);
        return jdbc.sql("select full_name, primary_trade, years_experience, skills from worker_profile where user_id = ?")
            .param(u.id()).query(Profile.class).optional();
    }

    @PutMapping
    Profile put(@AuthenticationPrincipal AppUser u, @Valid @RequestBody Profile p) {
        requireWorker(u);
        jdbc.sql("""
                insert into worker_profile(user_id, full_name, primary_trade, years_experience, skills) values (?,?,?,?,?)
                on conflict (user_id) do update set full_name = excluded.full_name, primary_trade = excluded.primary_trade,
                  years_experience = excluded.years_experience, skills = excluded.skills, updated_at = now()""")
            .params(u.id(), p.fullName().trim(), p.primaryTrade(), p.yearsExperience(), p.skills()).update();
        return p;
    }

    private static void requireWorker(AppUser u) {
        if (!u.isWorker()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Workers only");
    }
}

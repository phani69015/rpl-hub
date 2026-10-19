package com.rplhub.controller;

import com.rplhub.model.AppUser;
import com.rplhub.model.Profile;
import com.rplhub.service.ProfileService;
import jakarta.validation.Valid;
import java.util.Optional;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/profile")
class ProfileController {

    private final ProfileService profiles;

    ProfileController(ProfileService profiles) {
        this.profiles = profiles;
    }

    @GetMapping
    Optional<Profile> get(@AuthenticationPrincipal AppUser u) {
        return profiles.get(u);
    }

    @PutMapping
    Profile put(@AuthenticationPrincipal AppUser u, @Valid @RequestBody Profile p) {
        return profiles.save(u, p);
    }
}

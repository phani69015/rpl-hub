package com.rplhub.service;

import com.rplhub.model.AppUser;
import com.rplhub.model.Profile;
import com.rplhub.repository.ProfileRepository;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProfileService {

    private final ProfileRepository profiles;

    ProfileService(ProfileRepository profiles) {
        this.profiles = profiles;
    }

    public Optional<Profile> get(AppUser u) {
        requireWorker(u);
        return profiles.find(u.id());
    }

    public Profile save(AppUser u, Profile p) {
        requireWorker(u);
        profiles.save(u.id(), p);
        return p;
    }

    private static void requireWorker(AppUser u) {
        if (!u.isWorker()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Workers only");
    }
}

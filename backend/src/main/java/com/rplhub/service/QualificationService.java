package com.rplhub.service;

import com.rplhub.exception.InvalidException;
import com.rplhub.model.AppUser;
import com.rplhub.model.LockedVersion;
import com.rplhub.model.QualificationContent;
import com.rplhub.model.VersionDetail;
import com.rplhub.model.VersionSummary;
import com.rplhub.repository.AuditRepository;
import com.rplhub.repository.QualificationRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Draft review (edit) and publication. Published versions are immutable; only PUBLISHED may be used downstream. */
@Service
public class QualificationService {

    private final QualificationRepository repo;
    private final AuditRepository audit;

    QualificationService(QualificationRepository repo, AuditRepository audit) {
        this.repo = repo;
        this.audit = audit;
    }

    public List<VersionSummary> list(String status) {
        return repo.list(status);
    }

    public VersionDetail get(UUID id) {
        return repo.detail(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Version not found"));
    }

    @Transactional
    public VersionDetail update(UUID actor, UUID id, QualificationContent content) {
        LockedVersion v = lock(id);
        if (!v.status().equals("DRAFT")) throw new ResponseStatusException(HttpStatus.CONFLICT, "Only draft versions can be edited");
        var codes = new HashSet<String>();
        for (var c : content.competencies()) {
            if (!codes.add(c.code().trim())) throw new InvalidException("Duplicate competency code '" + c.code() + "'");
        }
        String hash = content.contentHash();
        if (repo.hashTakenByOther(v.qualificationId(), hash, id))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Identical to another version of this qualification");
        repo.updateDraftFields(id, content);
        repo.replaceCompetencies(id, content.competencies());
        audit.record(actor, "QUALIFICATION_DRAFT_EDITED", "qualification_version", id,
            Map.of("previousHash", v.contentHash(), "newHash", hash));
        return get(id);
    }

    @Transactional
    public VersionDetail publish(AppUser actor, UUID id) {
        if (!actor.isAdmin() || !actor.canPublish()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not authorised to publish qualifications");
        LockedVersion v = lock(id);
        if (v.status().equals("PUBLISHED")) return get(id); // retry-safe
        if (!v.status().equals("DRAFT")) throw new ResponseStatusException(HttpStatus.CONFLICT, "Version is " + v.status());
        if (repo.newerVersionPublished(v.qualificationId(), v.version()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A newer version has already been published");
        repo.publish(id, v.qualificationId(), actor.id());
        audit.record(actor.id(), "QUALIFICATION_PUBLISHED", "qualification_version", id, Map.of("version", v.version(), "contentHash", v.contentHash()));
        return get(id);
    }

    private LockedVersion lock(UUID id) {
        return repo.lockVersion(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Version not found"));
    }
}

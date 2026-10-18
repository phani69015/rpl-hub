package com.rplhub.qualification;

import com.rplhub.audit.AuditService;
import com.rplhub.qualification.QualificationRepository.LockedVersion;
import com.rplhub.qualification.QualificationRepository.VersionDetail;
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
class QualificationService {

    private final QualificationRepository repo;
    private final AuditService audit;

    QualificationService(QualificationRepository repo, AuditService audit) {
        this.repo = repo;
        this.audit = audit;
    }

    List<QualificationRepository.VersionSummary> list(String status) {
        return repo.list(status);
    }

    VersionDetail get(UUID id) {
        return repo.detail(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Version not found"));
    }

    @Transactional
    VersionDetail update(UUID actor, UUID id, QualificationContent content) {
        LockedVersion v = lock(id);
        if (!v.status().equals("DRAFT")) throw new ResponseStatusException(HttpStatus.CONFLICT, "Only draft versions can be edited");
        var codes = new HashSet<String>();
        for (var c : content.competencies()) {
            if (!codes.add(c.code().trim())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Duplicate competency code '" + c.code() + "'");
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
    VersionDetail publish(UUID actor, UUID id) {
        if (!repo.canPublish(actor)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not authorised to publish qualifications");
        LockedVersion v = lock(id);
        if (v.status().equals("PUBLISHED")) return get(id); // retry-safe
        if (!v.status().equals("DRAFT")) throw new ResponseStatusException(HttpStatus.CONFLICT, "Version is " + v.status());
        if (repo.newerVersionPublished(v.qualificationId(), v.version()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A newer version has already been published");
        repo.publish(id, v.qualificationId(), actor);
        audit.record(actor, "QUALIFICATION_PUBLISHED", "qualification_version", id, Map.of("version", v.version(), "contentHash", v.contentHash()));
        return get(id);
    }

    private LockedVersion lock(UUID id) {
        return repo.lockVersion(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Version not found"));
    }
}

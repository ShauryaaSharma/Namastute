package com.namastute.verification;

import com.namastute.notifications.PushService;
import com.namastute.notifications.PushService.Push;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// The automatic first pass on each new ID check (Learning Notes 82), in the background so uploads stay instant:
// the face score and suggestions read from the ID, saved for the reviewer. Only "no face at all" is decided here (an
// automatic rejection); everything else waits for a person. One check at a time: the server has 2 cores.
@Component
class IdChecks {

    record Next(UUID id, String docType, String docKey, String selfieKey) {}

    static final String NO_FACE_ON_ID =
            "We couldn't find a face on your ID photo. Retake it flat, in good light, with the whole card visible.";
    static final String NO_FACE_IN_SELFIE =
            "We couldn't find your face in the selfie. Retake it facing the camera, in good light, without sunglasses or a mask.";

    private static final Logger log = LoggerFactory.getLogger(IdChecks.class);

    private final JdbcClient db;
    private final PhotoStore photos;
    private final FaceMatch faces;
    private final Ocr ocr;
    private final PushService push;
    private final VerificationService verification;
    private final boolean enabled;

    IdChecks(JdbcClient db, PhotoStore photos, FaceMatch faces, Ocr ocr, PushService push, VerificationService verification,
             @Value("${namastute.verification.checks-enabled}") boolean enabled) {
        this.db = db;
        this.photos = photos;
        this.faces = faces;
        this.ocr = ocr;
        this.push = push;
        this.verification = verification;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelay = 30_000, initialDelay = 30_000)
    void job() {
        if (enabled) {
            while (checkNext()) {
                // until nothing is waiting
            }
        }
    }

    // The oldest waiting check nobody has run the machine checks on. false = none left.
    boolean checkNext() {
        Optional<Next> next = db.sql("""
                        select id, doc_type, doc_key, selfie_key from verifications
                        where status = 'pending' and checked_at is null and doc_key is not null and selfie_key is not null
                        order by created_at limit 1""")
                .query(Next.class).optional();
        next.ifPresent(this::check);
        return next.isPresent();
    }

    // One specific check (tests use this, so they never touch anyone else's)
    void check(UUID verificationId) {
        db.sql("""
                        select id, doc_type, doc_key, selfie_key from verifications
                        where id = ? and status = 'pending' and checked_at is null and doc_key is not null and selfie_key is not null""")
                .param(verificationId).query(Next.class).optional().ifPresent(this::check);
    }

    // Every failure is caught and noted, and the check is marked as checked anyway: a photo that crashes a model must
    // not be retried every 30 seconds forever. The reviewer still sees both photos.
    private void check(Next n) {
        List<String> notes = new ArrayList<>();
        byte[] id, selfie;
        try {
            id = photos.get(n.docKey());
            selfie = photos.get(n.selfieKey());
        } catch (RuntimeException e) {
            log.warn("ID check {}: couldn't load the photos: {}", n.id(), e.getMessage());
            save(n.id(), null, null, null, List.of("Automatic checks skipped: the photos couldn't be loaded"));
            return;
        }

        FaceMatch.Result face = null;
        try {
            face = faces.compare(id, selfie);
        } catch (RuntimeException | UnsatisfiedLinkError e) {
            log.warn("ID check {}: face check failed", n.id(), e);
            notes.add("Face check failed: compare the faces yourself");
        }
        if (face != null && (!face.idFace() || !face.selfieFace())) {
            autoReject(n.id(), face.idFace() ? NO_FACE_IN_SELFIE : NO_FACE_ON_ID);
            return;
        }

        IdText.Reading reading = null;
        try {
            reading = IdText.read(n.docType(), ocr.read(id));
            notes.addAll(reading.notes());
        } catch (Exception | UnsatisfiedLinkError e) {
            log.warn("ID check {}: reading the ID failed", n.id(), e);
            notes.add("Couldn't read the ID's text: fill in the gender and last 4 yourself");
        }
        save(n.id(), face == null ? null : face.score(), reading == null ? null : reading.gender(),
                reading == null ? null : reading.last4(), notes);
    }

    private void save(UUID id, Double score, String gender, String last4, List<String> notes) {
        db.sql("""
                        update verifications set face_similarity = ?, suggested_gender = ?, suggested_last4 = ?, check_notes = ?,
                          checked_at = now()
                        where id = ? and status = 'pending'""")
                .params(score, gender, last4, notes.isEmpty() ? null : String.join("\n", notes), id).update();
    }

    // The only decision a machine makes: there is no face to compare. Same steps as a person's rejection (chapter 80):
    // the decision first, then the person is told and the photos are deleted.
    private void autoReject(UUID id, String reason) {
        Optional<UUID> person = db.sql("""
                        update verifications set status = 'rejected', reject_reason = ?, reviewed_at = now(), checked_at = now()
                        where id = ? and status = 'pending' returning user_id""")
                .params(reason, id).query(UUID.class).optional();
        if (person.isEmpty()) {
            return;   // a person decided it in the meantime
        }
        push.send(List.of(person.get()), new Push("Your ID check wasn't approved",
                "Open Namastute to see why and try again. Your photos have been deleted.", Duration.ofDays(7)));
        try {
            verification.deletePhotos(verification.photosOf(id));
        } catch (RuntimeException e) {
            log.warn("Couldn't delete the photos of verification {} yet; the purge job will retry: {}", id, e.getMessage());
        }
    }
}

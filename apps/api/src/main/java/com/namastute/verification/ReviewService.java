package com.namastute.verification;

import com.namastute.notifications.PushService;
import com.namastute.notifications.PushService.Push;
import com.namastute.shared.ApiException;
import com.namastute.users.UserService;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// The admin review of ID checks (Learning Notes 80). Every method checks the role first: being logged in isn't enough.
// The controller saves a decision (approve/reject commit when they return) and only then calls deletePhotos, so a
// failure can never leave a check that's still pending but has no photos to review.
@Service
class ReviewService {

    // checked: the machine checks have run (Learning Notes 82); their results are suggestions for the reviewer
    record PendingView(UUID id, String firstName, String docType, String gesture, OffsetDateTime submittedAt, boolean checked,
                       @Schema(nullable = true) Double faceScore,
                       @Schema(nullable = true, allowableValues = {"female", "male", "other"}) String suggestedGender,
                       @Schema(nullable = true) String suggestedLast4, @Schema(nullable = true) String checkNotes) {}
    record PendingRow(UUID id, UUID userId, String docType, String gesture, OffsetDateTime createdAt, OffsetDateTime checkedAt,
                      Double faceSimilarity, String suggestedGender, String suggestedLast4, String checkNotes) {}

    private static final Logger log = LoggerFactory.getLogger(ReviewService.class);

    private final JdbcClient db;
    private final PhotoStore photos;
    private final UserService users;
    private final PushService push;
    private final VerificationService verification;

    ReviewService(JdbcClient db, PhotoStore photos, UserService users, PushService push, VerificationService verification) {
        this.db = db;
        this.photos = photos;
        this.users = users;
        this.push = push;
        this.verification = verification;
    }

    // Oldest first: whoever waited longest is reviewed first
    List<PendingView> pending(UUID admin) {
        requireAdmin(admin);
        List<PendingRow> rows = db.sql("""
                        select id, user_id, doc_type, gesture, created_at, checked_at, face_similarity, suggested_gender,
                          suggested_last4, check_notes from verifications
                        where status = 'pending' and doc_key is not null order by created_at""")
                .query(PendingRow.class).list();
        Map<UUID, String> names = users.firstNames(rows.stream().map(PendingRow::userId).toList());
        return rows.stream().map(r -> new PendingView(r.id(), names.get(r.userId()), r.docType(), r.gesture(), r.createdAt(),
                r.checkedAt() != null, r.faceSimilarity(), r.suggestedGender(), r.suggestedLast4(), r.checkNotes())).toList();
    }

    // "id" or "selfie", only while the check is waiting. Anything else looks like nothing exists.
    byte[] photo(UUID admin, UUID verificationId, String which) {
        requireAdmin(admin);
        String column = switch (which) {
            case "id" -> "doc_key";
            case "selfie" -> "selfie_key";
            default -> throw notFound();
        };
        String key = db.sql("select " + column + " from verifications where id = ? and status = 'pending'")   // column: one of two constants
                .param(verificationId).query(String.class).optional().orElseThrow(ReviewService::notFound);
        return photos.get(key);
    }

    // gender as printed on the ID; last4: the last 4 letters or digits of the document number (all we keep of it)
    @Transactional
    void approve(UUID admin, UUID verificationId, String gender, String last4) {
        requireAdmin(admin);
        UUID person = decide(admin, verificationId, "status = 'verified', doc_last4 = :extra", last4.toUpperCase());
        users.markVerified(person, gender);
        push.send(List.of(person), new Push("You're ID verified ✓", "Your photos have been deleted.", Duration.ofDays(7)));
    }

    @Transactional
    void reject(UUID admin, UUID verificationId, String reason) {
        requireAdmin(admin);
        UUID person = decide(admin, verificationId, "status = 'rejected', reject_reason = :extra", reason.strip());
        push.send(List.of(person), new Push("Your ID check wasn't approved",
                "Open Namastute to see why and try again. Your photos have been deleted.", Duration.ofDays(7)));
    }

    // Only a check that's still pending, and never your own: a double click, or two admins at once, get "already
    // decided" instead of a second decision (a conditional update, like the last seat in Learning Notes 40)
    private UUID decide(UUID admin, UUID verificationId, String set, String extra) {
        UUID person = db.sql("update verifications set " + set + ", reviewed_by = :admin, reviewed_at = now() " +
                        "where id = :id and status = 'pending' and user_id <> :admin returning user_id")
                .param("extra", extra).param("admin", admin).param("id", verificationId)
                .query(UUID.class).optional()
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "ALREADY_DECIDED",
                        "This check was already decided, or it's your own."));
        return person;
    }

    // After a decision is committed. If S3 is unreachable right now, the photos stay listed on the row and the
    // 6-hourly purge deletes them later.
    void deletePhotos(UUID verificationId) {
        try {
            verification.deletePhotos(verification.photosOf(verificationId));
        } catch (RuntimeException e) {
            log.warn("Couldn't delete the photos of verification {} yet; the purge job will retry: {}", verificationId, e.getMessage());
        }
    }

    // Not an admin: the same 404 as a page that doesn't exist, so the admin area isn't even confirmed to be there
    private void requireAdmin(UUID me) {
        if (!users.isAdmin(me)) {
            throw notFound();
        }
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Not found");
    }
}

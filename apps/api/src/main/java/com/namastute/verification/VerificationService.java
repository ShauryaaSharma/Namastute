package com.namastute.verification;

import com.namastute.notifications.PushService;
import com.namastute.notifications.PushService.Push;
import com.namastute.shared.ApiException;
import com.namastute.shared.RateLimiter;
import com.namastute.users.UserService;
import io.swagger.v3.oas.annotations.media.Schema;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

// ID verification, slice 1 (Learning Notes 78): submit an ID photo and a selfie showing a random gesture. A person
// reviews them (slice 2); the photos never outlive the check: 7 days at most, even if nobody reviews them.
@Service
class VerificationService {

    // gesture: what the next selfie must show (null while a check is waiting or done)
    record VerificationView(
            @Schema(allowableValues = {"unverified", "pending", "verified", "rejected"}) String status,
            @Schema(nullable = true) String docType,
            @Schema(nullable = true) String rejectReason,
            @Schema(nullable = true) OffsetDateTime submittedAt,
            @Schema(nullable = true) String gesture) {}

    record Row(String status, String docType, String rejectReason, OffsetDateTime createdAt) {}
    record Stale(UUID id, String docKey, String selfieKey) {}

    static final Set<String> DOC_TYPES = Set.of("passport", "driving_licence", "voter_id", "masked_aadhaar");
    // Easy to do with one hand and easy to see; an old photo of someone can't match a gesture picked a minute ago
    static final List<String> GESTURES = List.of(
            "Hold up 3 fingers next to your face",
            "Hold up 2 fingers next to your face",
            "Give a thumbs up next to your face",
            "Touch your left ear",
            "Put your hand flat on top of your head");
    static final Duration GESTURE_TTL = Duration.ofMinutes(30);
    static final Duration KEEP_PHOTOS = Duration.ofDays(7);

    private static final Logger log = LoggerFactory.getLogger(VerificationService.class);
    private static final SecureRandom random = new SecureRandom();

    private final JdbcClient db;
    private final PhotoStore photos;
    private final StringRedisTemplate redis;
    private final RateLimiter limits;
    private final UserService users;
    private final PushService push;

    VerificationService(JdbcClient db, PhotoStore photos, StringRedisTemplate redis, RateLimiter limits, UserService users,
                        PushService push) {
        this.db = db;
        this.photos = photos;
        this.redis = redis;
        this.limits = limits;
        this.users = users;
        this.push = push;
    }

    VerificationView current(UUID me) {
        Optional<Row> latest = db.sql("""
                        select status::text, doc_type, reject_reason, created_at from verifications
                        where user_id = ? order by created_at desc limit 1""")
                .param(me).query(Row.class).optional();
        if (latest.isPresent() && (latest.get().status().equals("pending") || latest.get().status().equals("verified"))) {
            Row r = latest.get();
            return new VerificationView(r.status(), r.docType(), null, r.createdAt(), null);
        }
        return new VerificationView(latest.map(Row::status).orElse("unverified"), latest.map(Row::docType).orElse(null),
                latest.map(Row::rejectReason).orElse(null), latest.map(Row::createdAt).orElse(null), gesture(me));
    }

    // The same gesture for 30 minutes (time to take the photos), then a new one
    private String gesture(UUID me) {
        String key = gestureKey(me);
        redis.opsForValue().setIfAbsent(key, GESTURES.get(random.nextInt(GESTURES.size())), GESTURE_TTL);
        return redis.opsForValue().get(key);
    }

    VerificationView submit(UUID me, String docType, boolean consent, MultipartFile idPhoto, MultipartFile selfie) {
        if (!consent) {
            throw bad("CONSENT_REQUIRED", "Agree to the notice before sending your photos.");
        }
        if (docType == null || !DOC_TYPES.contains(docType)) {
            throw bad("DOC_TYPE_INVALID", "Choose passport, driving licence, voter ID or masked Aadhaar.");
        }
        if (pending(me)) {
            throw alreadyPending();
        }
        String gesture = redis.opsForValue().get(gestureKey(me));
        if (gesture == null) {
            throw new ApiException(HttpStatus.CONFLICT, "GESTURE_EXPIRED",
                    "Your selfie instructions expired. Take the selfie again with the new gesture.");
        }
        limits.check("verify", me, 5, Duration.ofDays(1));
        byte[] id = Photos.clean(idPhoto, "ID photo");
        byte[] face = Photos.clean(selfie, "selfie");

        // Random, unguessable names; nothing in a key says whose photo it is
        String idKey = "verification/" + UUID.randomUUID() + ".jpg", selfieKey = "verification/" + UUID.randomUUID() + ".jpg";
        // shortcut: a crash between the uploads and the insert leaves photos with no row; in production the bucket's 7-day
        // lifecycle rule deletes them, in development nothing does. Upgrade: a sweep comparing the bucket with the table.
        photos.put(idKey, id);
        try {
            photos.put(selfieKey, face);
            db.sql("""
                            insert into verifications (user_id, doc_type, doc_key, selfie_key, gesture, consented_at)
                            values (?, ?, ?, ?, ?, now())""")
                    .params(me, docType, idKey, selfieKey, gesture).update();
        } catch (RuntimeException e) {
            // Nothing saved means nothing kept: the photos go too
            photos.delete(idKey);
            photos.delete(selfieKey);
            throw e instanceof DuplicateKeyException ? alreadyPending() : e;   // two submits at once: the unique index wins
        }
        redis.delete(gestureKey(me));   // used: the next attempt gets a new gesture
        // A check nobody looks at helps nobody (Learning Notes 80). No name in the alert, like report alerts.
        push.send(users.adminIds(), new Push("New ID check waiting", "Review it in Namastute: Profile → Review ID checks",
                Duration.ofDays(1)));
        return current(me);
    }

    private boolean pending(UUID me) {
        return db.sql("select exists (select 1 from verifications where user_id = ? and status = 'pending')")
                .param(me).query(Boolean.class).single();
    }

    // The safety net: photos older than 7 days are deleted whatever happened, and a check nobody reviewed in time is
    // closed so the person can try again. Also finishes any decided check whose photos weren't deleted at decision time
    // (S3 was briefly unreachable, Learning Notes 80). Objects first, then the row: a run that stops halfway is finished
    // by the next one.
    int purgeOld() {
        List<Stale> stale = db.sql("""
                        select id, doc_key, selfie_key from verifications
                        where (doc_key is not null or selfie_key is not null)
                          and (status <> 'pending' or created_at < now() - make_interval(days => ?))""")
                .param((int) KEEP_PHOTOS.toDays()).query(Stale.class).list();
        stale.forEach(this::deletePhotos);
        if (!stale.isEmpty()) {
            log.info("Deleted the photos of {} verification(s)", stale.size());
        }
        return stale.size();
    }

    // Deletes a check's two photos, then forgets their keys. Still pending (only the 7-day net does that) → closed.
    void deletePhotos(Stale s) {
        if (s.docKey() != null) photos.delete(s.docKey());
        if (s.selfieKey() != null) photos.delete(s.selfieKey());
        db.sql("""
                        update verifications set doc_key = null, selfie_key = null,
                          reject_reason = case when status = 'pending'
                            then 'Not reviewed within 7 days, so your photos were deleted. Please try again.' else reject_reason end,
                          reviewed_at = case when status = 'pending' then now() else reviewed_at end,
                          status = case when status = 'pending' then 'rejected'::verification_status else status end
                        where id = ?""")
                .param(s.id()).update();
    }

    Stale photosOf(UUID verificationId) {
        return db.sql("select id, doc_key, selfie_key from verifications where id = ?").param(verificationId).query(Stale.class).single();
    }

    private static String gestureKey(UUID me) {
        return "verify:gesture:" + me;
    }

    private static ApiException alreadyPending() {
        return new ApiException(HttpStatus.CONFLICT, "ALREADY_PENDING", "Your ID is already being checked. We'll let you know.");
    }

    private static ApiException bad(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }
}

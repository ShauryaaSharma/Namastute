package com.namastute.verification;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// The photo safety net (Learning Notes 78), every 6 hours: a photo is gone at most 7 days and 6 hours after upload.
// Production also has an S3 lifecycle rule doing the same, in case this job ever stops.
@Component
class VerificationJobs {

    private final VerificationService verification;

    VerificationJobs(VerificationService verification) {
        this.verification = verification;
    }

    @Scheduled(fixedDelay = 6 * 60 * 60_000, initialDelay = 5 * 60_000)
    void purge() {
        verification.purgeOld();
    }
}

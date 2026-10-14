package com.namastute.groups;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Runs the group sweep every minute (Learning Notes 48). A separate class on purpose: calling GroupService's
// @Transactional methods from inside GroupService would skip Spring's proxy and get no transaction (40.3).
@Component
class GroupJobs {

    private final GroupService groups;

    GroupJobs(GroupService groups) {
        this.groups = groups;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    void sweep() {
        groups.expireUnformed();   // first: a group whose meeting passed long ago expires instead of being locked late
        groups.autoLock();
        groups.finish();
    }
}

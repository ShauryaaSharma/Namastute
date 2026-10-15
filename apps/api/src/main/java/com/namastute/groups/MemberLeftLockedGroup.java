package com.namastute.groups;

import java.util.UUID;

// Published when a member (not the booker) leaves a LOCKED group with a reason. The feedback module files it as a
// report on the booker (Learning Notes 56.5). An event, so the groups module never depends on feedback.
public record MemberLeftLockedGroup(UUID groupId, UUID memberId, UUID bookerId, String reason) {}

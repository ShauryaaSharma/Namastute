package com.namastute.groups;

import java.util.UUID;

// Published by the groups module after a group changes (someone asked, joined, left, or it was locked).
// Other modules listen; groups never needs to know who they are. systemMessage: text for the group chat, or null.
public record GroupChanged(UUID groupId, String systemMessage) {}

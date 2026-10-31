package com.autodroid;

/** Pure cursor policy: ignore records before monitoring and already processed IDs. */
final class InboxCheckpoint {
    final long since;
    private long lastId;

    InboxCheckpoint(long since, long lastId) {
        this.since = since;
        this.lastId = lastId;
    }

    boolean accept(long id, long date) {
        if (id <= lastId || date < since) return false;
        lastId = id;
        return true;
    }

    long lastId() {
        return lastId;
    }
}

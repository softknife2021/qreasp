package com.softknife.http.helper;

public enum ExecutionState {
    STARTED,
    /** A response came back, whatever its status code. */
    COMPLETED,
    /** The request was not attempted. */
    SKIPPED,
    /** The request was attempted and no response came back: refused, timed out, unknown host. */
    FAILED,
    UNKNOWN;

    public static ExecutionState fromString(String status) {
        if (status == null) {
            return UNKNOWN;
        }
        try {
            return ExecutionState.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            return UNKNOWN;
        }
    }
}

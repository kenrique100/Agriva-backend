package com.agriva.shared.security.blacklist;

public interface IpBlacklistService {

    /** @return true if the IP is currently blocked. */
    boolean isBlocked(String ip);

    /** @return seconds until unblocked; 0 if not blocked. */
    long blockRemainingSeconds(String ip);

    /** Increments the failure counter; auto-blocks at the configured threshold. */
    void recordFailure(String ip);

    /** Clears failures after a successful login. */
    void clearFailures(String ip);
}
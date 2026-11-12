package com.splitbuys.backend.common.notify;

/**
 * Whether a person can be reached by a notification right now (the feature is on and they have registered a device). Lives in
 * {@code common} so a feature that wants to know does not depend on the one that sends.
 */
public interface DeviceReach {
    boolean canNotify(long userId);
}

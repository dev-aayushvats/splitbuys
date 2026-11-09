package com.splitbuys.backend.push;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.splitbuys.backend.common.error.InvalidRequestException;
import org.junit.jupiter.api.Test;

class PushEndpointsTest {
    @Test
    void realPushServicesAreAccepted() {
        for (String ok : new String[] {
                "https://fcm.googleapis.com/fcm/send/abc:def",
                "https://web.push.apple.com/QGxyz",
                "https://updates.push.services.mozilla.com/wpush/v2/gAAAA",
                "https://par02p.notify.windows.com/w/?token=abc",
                "https://fcm.googleapis.com:443/fcm/send/abc",
                "https://FCM.GOOGLEAPIS.COM/fcm/send/abc"}) {
            assertEquals(ok, PushEndpoints.require(ok));
        }
    }

    @Test
    void anythingElseIsRejectedSoTheServerCannotBePointedAtInternalAddresses() {
        for (String bad : new String[] {
                null, "", "not a url",
                "http://fcm.googleapis.com/fcm/send/abc",                   // not https
                "https://localhost/x", "https://127.0.0.1/x", "https://10.0.0.5/x", "https://169.254.169.254/latest/meta-data",
                "https://[::1]/x", "https://internal.example.com/x",
                "https://fcm.googleapis.com.evil.com/x",                    // suffix trick
                "https://evilfcm.googleapis.com.attacker.io/x",
                "https://notfcm.googleapis.com/x",                          // other googleapis hosts are not push services
                "https://googleapis.com/x",
                "https://evil.com@fcm.googleapis.com/x",                    // credentials in the URL
                "https://fcm.googleapis.com@evil.com/x",
                "https://fcm.googleapis.com:8443/x",                        // non-default port
                "https://fcm.googleapis.com/x#frag",
                "ftp://fcm.googleapis.com/x", "file:///etc/passwd", "javascript:alert(1)",
                "https://fcm.googleapis.com/" + "a".repeat(PushEndpoints.MAX_LENGTH)}) {
            assertThrows(InvalidRequestException.class, () -> PushEndpoints.require(bad), String.valueOf(bad));
        }
    }
}

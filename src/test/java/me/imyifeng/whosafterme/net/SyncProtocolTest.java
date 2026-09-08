package me.imyifeng.whosafterme.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Sync protocol constants (spec v1 §4): the hello handshake carries a u8 protocol
 * version, the server marks a player modded only on a version match, and v1 sends
 * only the reserved TARGETING threat kind.
 */
class SyncProtocolTest {

    @Test
    void theWireVersionIsOne() {
        assertEquals(1, SyncProtocol.PROTOCOL_VERSION);
    }

    @Test
    void theTargetingKindIsZero() {
        assertEquals(0, SyncProtocol.TARGETING);
    }

    @Test
    void theCurrentVersionIsAccepted() {
        assertTrue(SyncProtocol.accepts(SyncProtocol.PROTOCOL_VERSION));
    }

    @Test
    void otherVersionsAreRejected() {
        // A future protocol revision bumps the byte and renegotiates (spec v1 §4);
        // any other value is logged and treated as unmarked.
        assertFalse(SyncProtocol.accepts(0));
        assertFalse(SyncProtocol.accepts(SyncProtocol.PROTOCOL_VERSION + 1));
        assertFalse(SyncProtocol.accepts(255));
    }
}

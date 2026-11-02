package com.drhiidden.agentic.core;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmallUnitsTest {

    @Test
    void statusPolicyLegacyAndStrict() {
        assertTrue(StatusPolicy.passes(200, 200, false, false));
        assertTrue(StatusPolicy.passes(200, 204, false, false));
        assertTrue(StatusPolicy.passes(200, 404, false, false));
        assertFalse(StatusPolicy.passes(200, 404, false, true), "strict: 404 falla");
        assertTrue(StatusPolicy.passes(200, 401, false, false));
        assertFalse(StatusPolicy.passes(200, 401, true, false), "fail-on-auth: 401 falla");
        assertFalse(StatusPolicy.passes(200, 403, false, false), "403 siempre falla");
        assertFalse(StatusPolicy.passes(200, 500, false, false));
        assertTrue(StatusPolicy.passes(List.of(200, 404), 404));
    }

    @Test
    void jwtExpiryIsReadWithoutVerifyingSignature() {
        long exp = Instant.now().plusSeconds(3600).getEpochSecond();
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(("{\"exp\":" + exp + ",\"sub\":\"x\"}").getBytes());
        var parsed = JwtInfo.expiry("aaa." + payload + ".sig");
        assertTrue(parsed.isPresent());
        assertEquals(exp, parsed.get().getEpochSecond());
        assertTrue(JwtInfo.expiry("no-es-un-jwt").isEmpty());
    }

    @Test
    void diffEngineReportsPathsAndHonoursIgnore() {
        String a = "{\"locked\":false,\"v\":[1,2],\"ts\":1}";
        String b = "{\"locked\":true,\"v\":[1,2,3],\"ts\":2}";
        List<String> diffs = DiffEngine.diff(a, b, List.of("ts"));
        assertTrue(diffs.stream().anyMatch(d -> d.startsWith("$.locked")));
        assertTrue(diffs.stream().anyMatch(d -> d.startsWith("$.v:")));
        assertTrue(diffs.stream().noneMatch(d -> d.contains("$.ts")));
        assertTrue(DiffEngine.diff(a, a, null).isEmpty());
    }
}

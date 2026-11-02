package com.drhiidden.agentic.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

/** Lee SOLO la caducidad ('exp') de un JWT, sin verificar firma y sin exponer otros claims. */
public final class JwtInfo {

    private JwtInfo() {}

    public static Optional<Instant> expiry(String jwt) {
        try {
            String[] parts = jwt.split("\\.");
            if (parts.length < 2) return Optional.empty();
            byte[] payload = Base64.getUrlDecoder().decode(parts[1]);
            JsonNode node = new ObjectMapper().readTree(payload);
            return node.hasNonNull("exp") ? Optional.of(Instant.ofEpochSecond(node.get("exp").asLong())) : Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}

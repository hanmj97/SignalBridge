package dev.signalbridge.api.auth;

import java.time.Instant;
public record Account(long id, String email, String nickname, String passwordHash,
                      Instant createdAt, boolean emailVerified) {}

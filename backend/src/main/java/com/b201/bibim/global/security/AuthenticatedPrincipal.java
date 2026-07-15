package com.b201.bibim.global.security;

import java.util.UUID;

public record AuthenticatedPrincipal(UUID id, AuthRole role) {
}

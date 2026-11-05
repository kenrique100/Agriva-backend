package com.agriva.shared.security;

import java.util.UUID;

/**
 * Principal stored in the SecurityContext by JwtAuthFilter.
 * Controllers receive this via @AuthenticationPrincipal so they never
 * need to parse or validate the JWT again (fixes S7).
 */
public record AuthenticatedUser(
        UUID userId,
        UUID farmId,
        String email,
        String username,
        String role,
        String jti
) {
    public boolean isMaster() { return RoleConstants.MASTER.equals(role); }
    public boolean isAdmin()  { return RoleConstants.ADMIN.equals(role); }
    public boolean isMasterOrAdmin() { return isMaster() || isAdmin(); }
    public boolean isSubUser() { return RoleConstants.SUB_USER.equals(role); }
}
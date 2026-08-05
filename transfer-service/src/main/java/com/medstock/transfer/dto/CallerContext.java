package com.medstock.transfer.dto;

import org.springframework.util.StringUtils;

/**
 * Who is making the call, as established by the API gateway and forwarded in the X-Auth-*
 * headers. The gateway is the only component that validates the JWT, so these values are
 * trusted here - but they still have to be checked against the request being acted on, since
 * a valid token for one branch says nothing about another branch's requests.
 */
public record CallerContext(String username, String role, Long branchId) {

    private static final String ADMIN_ROLE = "ADMIN";

    public static CallerContext fromHeaders(String username, String role, String branchId) {
        return new CallerContext(username, role, parseBranchId(branchId));
    }

    /** The gateway sends an empty header for roles that are not tied to a branch. */
    private static Long parseBranchId(String rawBranchId) {
        if (!StringUtils.hasText(rawBranchId)) {
            return null;
        }
        try {
            return Long.parseLong(rawBranchId.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public boolean isAdmin() {
        return ADMIN_ROLE.equals(role);
    }

    public boolean belongsToBranch(Long candidateBranchId) {
        return branchId != null && branchId.equals(candidateBranchId);
    }
}

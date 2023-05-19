package pt.unl.fct.di.apdc.chatfct.fctconnect.util;

public final class BackOfficeRolePermissions {

    public static final String ADMIN_ROLE = "ADMIN";
    public static final String MOD_ROLE = "MOD";

    private BackOfficeRolePermissions() {
    }

    public static boolean canApproveAccount(String role) {
        return role.equals(ADMIN_ROLE);
    }

    public static boolean canUpdateRole(String usernameRole, String otherRole) {
        return usernameRole.equals(ADMIN_ROLE) && otherRole.equals(MOD_ROLE);
    }
}
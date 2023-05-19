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

    public static boolean canRemoveRegularUser(String usernameRole) {
        return usernameRole.equals(ADMIN_ROLE);
    }

    public static boolean canRemoveBackOfficeUser(RemoveData data, String username, String usernameRole, String otherRole) {
        final boolean canRemoveSelf = data.isSameUser(username);
        final boolean canRemoveOther = usernameRole.equals(ADMIN_ROLE) && otherRole.equals(MOD_ROLE);
        return canRemoveSelf || canRemoveOther;
    }

    public static boolean canListAdmins(String role) {
        return role.equals(ADMIN_ROLE);
    }

    public static boolean isSelf(String u1, String u2) {
        return u1.equals(u2);
    }

    public static boolean canGetAdminsInfo(String role) {
        return role.equals(ADMIN_ROLE);
    }
}
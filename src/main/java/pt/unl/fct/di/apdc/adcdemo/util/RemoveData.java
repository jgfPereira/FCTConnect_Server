package pt.unl.fct.di.apdc.adcdemo.util;

public class RemoveData {

    public String removerUsername;
    public String removedUsername;

    public RemoveData() {
    }

    public boolean validateData() {
        return !(this.removerUsername == null || this.removedUsername == null);
    }

    public boolean isSameUser() {
        return this.removerUsername.equals(this.removedUsername);
    }

    public boolean validateRemovalPermissions(String removerRole, String removedRole) {
        return this.isSameUser() || RolePermissions.canRemove(removerRole, removedRole);
    }
}

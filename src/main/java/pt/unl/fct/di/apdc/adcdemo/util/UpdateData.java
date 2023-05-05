package pt.unl.fct.di.apdc.adcdemo.util;

public class UpdateData {

    public String updaterUsername;
    public String updatedUsername;
    public String[] attributesNames;
    public String[] attributesValues;

    public UpdateData() {
    }

    public boolean validateData() {
        return !(this.updaterUsername == null || this.updatedUsername == null || this.attributesNames == null || this.attributesValues == null);
    }

    public boolean isSameUser() {
        return this.updaterUsername.equals(this.updatedUsername);
    }

    public boolean validateUpdatePermissions(String updaterRole, String updatedRole, String attributeName, String attributeValue) {
        return RolePermissions.canUpdate(this, updaterRole, updatedRole, attributeName, attributeValue);
    }
}

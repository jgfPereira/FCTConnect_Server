package pt.unl.fct.di.apdc.chatfct.fctconnect.util;

public class AddCheckpointData {

    public String username;
    public String quest;
    public String score;
    public String[] items;

    public AddCheckpointData() {
    }

    public boolean validateData() {
        return !(username == null || quest == null || score == null || items == null);
    }

    public boolean validateQuestAndScore() {
        return quest.matches(RegexExp.WHOLE_NUMBER_REGEX) && score.matches(RegexExp.WHOLE_NUMBER_REGEX);
    }

    public boolean isTokenSameUser(String usernameToken) {
        return username.equals(usernameToken);
    }
}
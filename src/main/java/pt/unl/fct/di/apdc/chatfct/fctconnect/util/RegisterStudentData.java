package pt.unl.fct.di.apdc.chatfct.fctconnect.util;

public class RegisterStudentData {

    public RegisterBasicData basicData;
    public String studentNumber;

    public RegisterStudentData() {
    }

    public boolean validateStudentNumber() {
        return this.studentNumber != null && this.studentNumber.matches(RegexExp.STUDENT_NUMBER_REGEX);
    }
}

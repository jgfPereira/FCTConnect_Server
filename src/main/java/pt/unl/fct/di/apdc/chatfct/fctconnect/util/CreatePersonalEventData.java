package pt.unl.fct.di.apdc.chatfct.fctconnect.util;

public class CreatePersonalEventData {

    public String name;
    public String location;
    public String description;
    public String startDate;
    public String endDate;
    public String color;
    public String recurrenceRule;

    public CreatePersonalEventData() {
    }

    public boolean validateData() {
        return !(name == null || startDate == null || endDate == null || color == null
                || !DateUtils.isTimestampValid(startDate) || !DateUtils.isTimestampValid(endDate));
    }

    public boolean checkDatesValidity() {
        return DateUtils.isTimestampBefore(startDate, endDate);
    }

    public boolean areDatesOnFuture() {
        return DateUtils.areTimestampsOnFuture(startDate);
    }
}
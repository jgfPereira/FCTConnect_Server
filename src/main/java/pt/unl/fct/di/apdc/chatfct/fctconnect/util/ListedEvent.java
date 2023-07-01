package pt.unl.fct.di.apdc.chatfct.fctconnect.util;

import com.google.cloud.datastore.Entity;

import java.time.ZoneId;
import java.util.Date;

public class ListedEvent {

    private static final String DEFAULT_TIME_ZONE = "UTC";
    private final String name;
    private final String location;
    private final String description;
    private final String startDate;
    private final String endDate;
    private final String color;
    private final String recurrenceRule;

    protected ListedEvent(Entity event) {
        name = event.getString(DatastoreTypes.EVENT_NAME_ATTR);
        location = event.getString(DatastoreTypes.EVENT_LOCATION_ATTR);
        description = event.getString(DatastoreTypes.EVENT_DESCRIPTION_ATTR);
        startDate = dateToString(event.getTimestamp(DatastoreTypes.EVENT_START_DATE_ATTR).toDate());
        endDate = dateToString(event.getTimestamp(DatastoreTypes.EVENT_END_DATE_ATTR).toDate());
        color = event.getString(DatastoreTypes.EVENT_COLOR_ATTR);
        recurrenceRule = event.getString(DatastoreTypes.EVENT_RECURRENCE_RULE_ATTR);
    }

    public static ListedEvent createListedEvent(Entity event) {
        return new ListedEvent(event);
    }

    private static String dateToString(Date date) {
        return date.toInstant().atZone(ZoneId.of(DEFAULT_TIME_ZONE))
                .toLocalDateTime().format(LocalDateTimeAdapter.FORMATTER);
    }

    @Override
    public String toString() {
        return "ListedEvent{" +
                "name='" + name + '\'' +
                ", location='" + location + '\'' +
                ", description='" + description + '\'' +
                ", startDate='" + startDate + '\'' +
                ", endDate='" + endDate + '\'' +
                ", color='" + color + '\'' +
                ", recurrenceRule='" + recurrenceRule + '\'' +
                '}';
    }
}
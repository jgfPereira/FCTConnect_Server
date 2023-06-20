package pt.unl.fct.di.apdc.chatfct.fctconnect.util;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public final class DateUtils {

    private static final String TIMESTAMP_RFC_3339_FMT = "yyyy-MM-dd'T'HH:mm:ss.SSSX";
    private static final String TIME_SEPARATOR = "T";

    private DateUtils() {
    }

    private static boolean isTimestampRFC3339(String str) {
        final DateTimeFormatter formatter = DateTimeFormatter.ofPattern(TIMESTAMP_RFC_3339_FMT);
        try {
            OffsetDateTime.parse(str, formatter);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    public static boolean isTimestampValid(String str) {
        return isTimestampRFC3339(str) && str.split(TIME_SEPARATOR)[0].matches(RegexExp.DATE_REGEX);
    }
}
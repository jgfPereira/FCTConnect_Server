package pt.unl.fct.di.apdc.chatfct.fctconnect.util;

import com.google.cloud.Timestamp;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Date;

public final class DateUtils {

    private static final String TIMESTAMP_RFC_3339_FMT = "yyyy-MM-dd'T'HH:mm:ssX";
    private static final String TIME_SEPARATOR = "T";
    private static final String UTC_SYMBOL = "Z";
    private static final String DATASTORE_DATES_TIMEZONE = "UTC+1";
    private static final String DEFAULT_TIMEZONE = "UTC";

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

    public static boolean isTimestampBefore(String startDate, String endDate) {
        final Timestamp startDateTimestamp = Timestamp.parseTimestamp(startDate);
        final Timestamp endDateTimestamp = Timestamp.parseTimestamp(endDate);
        return startDateTimestamp.compareTo(endDateTimestamp) < 0;
    }

    public static LocalDateTime timestampToLocalDateTimeUTC(Timestamp t) {
        final Instant instant = Instant.ofEpochSecond(t.getSeconds(), t.getNanos());
        final ZonedDateTime utcPlusOneDateTime = ZonedDateTime.ofInstant(instant, ZoneId.of(DATASTORE_DATES_TIMEZONE));
        final ZonedDateTime utcDateTime = utcPlusOneDateTime.withZoneSameInstant(ZoneId.of(DEFAULT_TIMEZONE));
        return utcDateTime.toLocalDateTime();
    }

    public static boolean areTimestampsOnFuture(String startDate) {
        final LocalDateTime startLocalDate = timestampToLocalDateTimeUTC(Timestamp.parseTimestamp(startDate));
        final LocalDateTime nowLocalDate = timestampToLocalDateTimeUTC(Timestamp.now());
        return startLocalDate.isAfter(nowLocalDate);
    }

    public static String dateToStringUTC(Date date) {
        return date.toInstant().atZone(ZoneId.of(DEFAULT_TIMEZONE))
                .toLocalDateTime().format(LocalDateTimeAdapter.FORMATTER) + UTC_SYMBOL;
    }
}
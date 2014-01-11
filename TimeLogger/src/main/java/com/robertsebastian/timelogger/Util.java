package com.robertsebastian.timelogger;

import android.view.Menu;
import android.view.MenuItem;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

// Miscellaneous utility functions
public class Util {
    // Format a duration in milliseconds into a meaningful time range description
    public static String formatDuration(long ms) {

        double h = (double)(ms / 1000) / 3600.0;
        return String.format(Locale.US, "%.03f", h);
    }

    // Format a time in milliseconds to the short DateFormat
    public static String formatTime(long ms) {
        if(ms == -1) return "";

        DateFormat f = DateFormat.getTimeInstance(DateFormat.SHORT, Locale.US);
        return f.format(new Date(ms));
    }

    public static String formatTime(Calendar c) {
        return DateFormat.getTimeInstance(DateFormat.SHORT, Locale.US).format(c.getTime());
    }

    public static long durationToMs(String duration) {
        return (long)(Float.parseFloat(duration) * 3600.0 * 1000.0);
    }

    // Format a time depending on its range from the current date
    private static final SimpleDateFormat DAY_OF_WEEK_FORMAT = new SimpleDateFormat("EEEE");
    private static final SimpleDateFormat MONTH_DAY_FORMAT = new SimpleDateFormat("EEE, LLL d");
    private static final SimpleDateFormat MONTH_DAY_YEAR_FORMAT = new SimpleDateFormat("EEE, LLL d, yyyy");

    public static String formatDate(Calendar d) {
        Calendar now = Calendar.getInstance();

        if(isSameDay(d, now)) {
            return "Today";
        } else if(isSameWeek(d, now)) {
            return DAY_OF_WEEK_FORMAT.format(d.getTime());
        } else if(isSameYear(d, now)) {
            return MONTH_DAY_FORMAT.format(d.getTime());
        }
        return MONTH_DAY_YEAR_FORMAT.format(d.getTime());
    }

    public static String formatDate(long ms) {
        return formatDate(newCalendar(ms));
    }

    public static String formatDateRange(Calendar d1, Calendar d2) {
        return formatDate(d1) + " - " + formatDate(d2);
    }

    public static String formatDateRange(long ms1, long ms2) {
        return formatDateRange(newCalendar(ms1), newCalendar(ms2));
    }

    public static Calendar newCalendar(long ms) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(ms);
        return c;
    }

    public static Calendar newCalendar(long initTime, int hourOfDay, int minute) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(initTime);
        c.set(Calendar.HOUR_OF_DAY, hourOfDay);
        c.set(Calendar.MINUTE, minute);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c;
    }

    public static Calendar getToday() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);

        return c;
    }

    public static Calendar getStartOfWeek() {
        Calendar c = getToday();
        c.set(Calendar.DAY_OF_WEEK, c.getFirstDayOfWeek());

        return c;
    }

    public static long getTimeMs() {
        return Calendar.getInstance().getTimeInMillis();
    }

    public static boolean isSameDay(Calendar d1, Calendar d2) {
        return d1.get(Calendar.DAY_OF_YEAR) == d2.get(Calendar.DAY_OF_YEAR) &&
               d1.get(Calendar.YEAR) == d2.get(Calendar.YEAR);
    }

    public static boolean isSameWeek(Calendar d1, Calendar d2) {
        // FIXME: Broken for first week of year
        return d1.get(Calendar.WEEK_OF_YEAR) == d2.get(Calendar.WEEK_OF_YEAR) &&
               d1.get(Calendar.YEAR) == d2.get(Calendar.YEAR);
    }

    public static boolean isSameYear(Calendar d1, Calendar d2) {
        return d1.get(Calendar.YEAR) == d2.get(Calendar.YEAR);
    }

    // Disable and hide a menu item
    public static void setMenuItemEnabled(Menu menu, int id, boolean enabled) {
        MenuItem item = menu.findItem(id);
        if(item == null) return;

        item.setEnabled(enabled);
        item.setVisible(enabled);
    }
}

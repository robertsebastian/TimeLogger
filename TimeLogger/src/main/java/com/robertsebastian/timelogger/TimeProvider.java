package com.robertsebastian.timelogger;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Locale;

public class TimeProvider extends ContentProvider {
    public static final String TAG = TimeProvider.class.getSimpleName();

    public static final String AUTHORITY    = "com.robertsebastian.timelogger";
    public static final String TIME_TYPE    = "vnd.robertsebastian.timelogger.time";
    public static final String TASK_TYPE = "vnd.robertsebastian.timelogger.task";

    public static final Uri TASKS_URI =
            new Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT).authority(AUTHORITY).path("tasks").build();
    public static final Uri TIMES_URI =
            new Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT).authority(AUTHORITY).path("times").build();

    private static final UriMatcher URI_MATCHER;

    public static final int TIME_LIST    = 1;
    public static final int TIME_ID      = 2;
    public static final int TASK_LIST = 3;
    public static final int TASK_ID = 4;

    private static final SparseArray<String> URI_TYPE_TABLE;
    private static final String TASKS_TABLE = "tasks";
    private static final String TIMES_TABLE = "times";
    private static final String TIMES_TABLE_MORE =
            "times INNER JOIN tasks ON (times.task_id = tasks._id)";

    private static final SparseArray<HashMap<String, String>> URI_TYPE_PROJECTION;
    private static final HashMap<String, String> TASK_PROJECTION;
    private static final HashMap<String, String> TIME_PROJECTION;

    static {
        URI_MATCHER = new UriMatcher(UriMatcher.NO_MATCH);
        URI_MATCHER.addURI(AUTHORITY, "times", TIME_LIST);
        URI_MATCHER.addURI(AUTHORITY, "times/#", TIME_ID);
        URI_MATCHER.addURI(AUTHORITY, "tasks", TASK_LIST);
        URI_MATCHER.addURI(AUTHORITY, "tasks/#", TASK_ID);

        URI_TYPE_TABLE = new SparseArray<String>();
        URI_TYPE_TABLE.put(TIME_LIST, TIMES_TABLE_MORE);
        URI_TYPE_TABLE.put(TIME_ID, TIMES_TABLE_MORE);
        URI_TYPE_TABLE.put(TASK_LIST, TASKS_TABLE);
        URI_TYPE_TABLE.put(TASK_ID, TASKS_TABLE);

        TASK_PROJECTION = new HashMap<String, String>();
        TASK_PROJECTION.put("_id", "_id");
        TASK_PROJECTION.put("name", "name");
        TASK_PROJECTION.put("description", "description");
        TASK_PROJECTION.put("time_added", "time_added");
        TASK_PROJECTION.put("last_used", "last_used");
        TASK_PROJECTION.put("selected", "selected");
        TASK_PROJECTION.put("hidden", "hidden");
        TASK_PROJECTION.put("duration", buildTaskDurationCol(Long.MIN_VALUE, Long.MAX_VALUE));

        TIME_PROJECTION = new HashMap<String, String>();
        TIME_PROJECTION.put("_id",         "times._id as _id");
        TIME_PROJECTION.put("task_id",     "task_id");
        TIME_PROJECTION.put("start",       "start");
        TIME_PROJECTION.put("stop",        "stop");
        TIME_PROJECTION.put("duration",    "(CASE stop WHEN -1 THEN strftime('%s', 'now') * 1e3 ELSE stop END) - start AS duration ");
        TIME_PROJECTION.put("name",        "tasks.name AS name");
        TIME_PROJECTION.put("description", "tasks.description AS description");

        URI_TYPE_PROJECTION = new SparseArray<HashMap<String, String>>();
        URI_TYPE_PROJECTION.put(TIME_LIST, TIME_PROJECTION);
        URI_TYPE_PROJECTION.put(TIME_ID, TIME_PROJECTION);
        URI_TYPE_PROJECTION.put(TASK_LIST, TASK_PROJECTION);
        URI_TYPE_PROJECTION.put(TASK_ID, TASK_PROJECTION);
    }

    private DbHelper mDbHelper = null;

    static public String buildTaskDurationCol(long start, long stop) {
        String format = "(SELECT sum("
                + "(CASE stop WHEN -1 THEN strftime('%%s', 'now') * 1e3 ELSE stop END) - start) FROM times "
                + "WHERE tasks._id = task_id AND start >= %d AND start <= %d) AS duration";
        return String.format(Locale.US, format, start, stop);
    }

    ////////////////////////////////////////////////////////////////////////////////
    public class DbHelper extends SQLiteOpenHelper {
        DbHelper(Context context) {
            //super(context, null, null, 1);
            super(context, "time_db", null, 1);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE tasks ("
                    + "_id         INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "name        TEXT NOT NULL,"
                    + "description TEXT NOT NULL,"
                    + "time_added  INTEGER DEFAULT (strftime('%s', 'now') * 1e3),"
                    + "last_used   INTEGER DEFAULT (strftime('%s', 'now') * 1e3),"
                    + "selected    INTEGER DEFAULT 0,"
                    + "hidden      INTEGER DEFAULT 0)");
            db.execSQL("CREATE TABLE times ("
                    + "_id        INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "task_id    INTEGER NOT NULL,"
                    + "start      INTEGER DEFAULT (strftime('%s', 'now') * 1e3),"
                    + "stop       INTEGER DEFAULT -1)");
            db.execSQL("PRAGMA recursive_triggers = OFF");

            // Finish time range for previous selection and clean out any too-short entries in the database table
            db.execSQL("CREATE TRIGGER stop_previous_time_range "
                    + "AFTER UPDATE OF selected ON tasks "
                    + "FOR EACH ROW WHEN old.selected = 1 AND new.selected = 0 "
                    + "BEGIN "
                    + "    UPDATE times SET stop = (strftime('%s', 'now') * 1e3) WHERE task_id = new._id AND stop = -1;"
                    + "    DELETE FROM times WHERE stop != -1 AND stop - start < 3600;"
                    + "END");

            // Start a new time range when a new task is selected
            db.execSQL("CREATE TRIGGER start_new_time_range "
                    + "AFTER UPDATE OF selected ON tasks "
                    + "FOR EACH ROW WHEN old.selected = 0 AND new.selected = 1 "
                    + "BEGIN "
                    + "    UPDATE tasks SET last_used = (strftime('%s', 'now') * 1e3) WHERE _id = new._id;"
                    + "    UPDATE times SET stop = (strftime('%s', 'now') * 1e3) WHERE task_id = new._id AND stop = -1;"
                    + "    INSERT INTO times (task_id) values (new._id);"
                    + "END");

            long start = Calendar.getInstance().getTimeInMillis();

            ContentValues proj = new ContentValues();
            proj.put("name",        "Project 1");
            proj.put("description", "Description");
            proj.put("time_added",  start);
            db.insert("tasks", null, proj);

            proj.put("name", "Project 2");
            proj.put("description", "Description 2");
            proj.put("time_added", start + 1e3);
            db.insert("tasks", null, proj);

            proj.put("name", "Project 3");
            proj.put("description", "Description 3");
            proj.put("time_added", start + 3e3);
            long id = db.insert("tasks", null, proj);

            ContentValues time = new ContentValues();
            time.put("task_id", id);
            time.put("start", new GregorianCalendar(2013, 11, 31, 10, 20, 30).getTimeInMillis());
            time.put("stop", new GregorianCalendar(2013, 11, 31, 10, 40, 30).getTimeInMillis());
            db.insert("times", null, time);

            time.put("start", new GregorianCalendar(2013, 11, 31, 8, 20, 30).getTimeInMillis());
            time.put("stop", new GregorianCalendar(2013, 11, 31, 8, 40, 30).getTimeInMillis());
            db.insert("times", null, time);

            time.put("start", new GregorianCalendar(2013, 11, 30, 8, 20, 30).getTimeInMillis());
            time.put("stop", new GregorianCalendar(2013, 11, 30, 8, 40, 30).getTimeInMillis());
            db.insert("times", null, time);

            time.put("start", new GregorianCalendar(2013, 11, 27, 10, 20, 30).getTimeInMillis());
            time.put("stop", new GregorianCalendar(2013, 11, 27, 10, 40, 30).getTimeInMillis());
            db.insert("times", null, time);

            time.put("start", new GregorianCalendar(2013, 11, 27, 11, 20, 30).getTimeInMillis());
            time.put("stop", new GregorianCalendar(2013, 11, 27, 11, 40, 30).getTimeInMillis());
            db.insert("times", null, time);

            time.put("start", new GregorianCalendar(2013, 11, 27, 12, 0, 0).getTimeInMillis());
            time.put("stop", new GregorianCalendar(2013, 11, 27, 23, 0, 0).getTimeInMillis());
            db.insert("times", null, time);

            time.put("start", new GregorianCalendar(2013, 11, 23, 12, 0, 0).getTimeInMillis());
            time.put("stop", new GregorianCalendar(2013, 11, 26, 12, 0, 0).getTimeInMillis());
            db.insert("times", null, time);
        }

        @Override
        public void onUpgrade(SQLiteDatabase sqLiteDatabase, int i, int i2) {
            Log.e(TAG, "Attempt to upgrade db from " + i + " to " + i2);
        }
    }

    ////////////////////////////////////////////////////////////////////////////////
    @Override
    public boolean onCreate() {
        mDbHelper = new DbHelper(getContext());
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        int uriType = URI_MATCHER.match(uri);
        SQLiteDatabase db = mDbHelper.getReadableDatabase();
        SQLiteQueryBuilder builder = new SQLiteQueryBuilder();

        if(db == null) return null;

        if(uriType == UriMatcher.NO_MATCH) {
            throw new IllegalArgumentException("Unsupported URI: " + uri);
        }

        // Pick correct table
        builder.setTables(URI_TYPE_TABLE.get(uriType));

        // Set projection
        builder.setProjectionMap(URI_TYPE_PROJECTION.get(uriType));

        String start = uri.getQueryParameter("start");
        String stop  = uri.getQueryParameter("stop");
        if((uriType == TASK_LIST || uriType == TASK_ID) && start != null && stop != null) {
            HashMap<String, String> newMap = new HashMap<String, String>(TASK_PROJECTION);
            newMap.put("duration", buildTaskDurationCol(Long.parseLong(start), Long.parseLong(stop)));
            builder.setProjectionMap(newMap);
        }

        // Append ID search clause if applicable
        if(uriType == TIME_ID) builder.appendWhere("times._id=" + uri.getLastPathSegment());
        if(uriType == TASK_ID) builder.appendWhere("tasks._id=" + uri.getLastPathSegment());

        Cursor c = builder.query(db, projection, selection, selectionArgs, null, null, sortOrder);
        if(c != null && getContext() != null) {
            c.setNotificationUri(getContext().getContentResolver(), uri);
        }

        return c;
    }

    @Override
    public String getType(Uri uri) {
        switch(URI_MATCHER.match(uri)) {
            case TIME_LIST:    return ContentResolver.CURSOR_DIR_BASE_TYPE + "/" + TIME_TYPE;
            case TIME_ID:      return ContentResolver.CURSOR_ITEM_BASE_TYPE + "/" + TIME_TYPE;
            case TASK_LIST: return ContentResolver.CURSOR_DIR_BASE_TYPE + "/" + TASK_TYPE;
            case TASK_ID:   return ContentResolver.CURSOR_ITEM_BASE_TYPE + "/" + TASK_TYPE;
            default:
                throw new IllegalArgumentException("Unsupported URI: " + uri);
        }
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        if(db == null) return null;

        int uriType = URI_MATCHER.match(uri);

        String table = null;
        if(uriType == TIME_LIST) {
            table = TIMES_TABLE;
        } else if(uriType == TASK_LIST) {
            table = TASKS_TABLE;
        } else {
            throw new IllegalArgumentException("Unsupported URI: " + uri);
        }

        long id = db.insertWithOnConflict(table, null, values, SQLiteDatabase.CONFLICT_REPLACE);

        Uri newUri = ContentUris.withAppendedId(uri, id);
        if(getContext() != null) {
            getContext().getContentResolver().notifyChange(newUri, null);
        }
        return newUri;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        if(db == null) return 0;

        int uriType = URI_MATCHER.match(uri);
        if(uriType == UriMatcher.NO_MATCH) {
            throw new IllegalArgumentException("Unsupported URI: " + uri);
        }

        String table = null;
        if(uriType == TIME_ID || uriType == TIME_LIST) {
            table = TIMES_TABLE;
        } else if(uriType == TASK_ID || uriType == TASK_LIST) {
            table = TASKS_TABLE;
        }
        if(table == null) return 0;

        String where = (selection == null ? "" : selection);
        if(uriType == TIME_ID || uriType == TASK_ID) {
            if(!TextUtils.isEmpty(where)) where += " and ";
            where += "_id = " + uri.getLastPathSegment();
        }

        int delCount = db.delete(table, where, selectionArgs);
        if(delCount > 0 && getContext() != null) {
            getContext().getContentResolver().notifyChange(uri, null);
        }
        return delCount;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        if(db == null) return 0;

        int uriType = URI_MATCHER.match(uri);
        if(uriType == UriMatcher.NO_MATCH) {
            throw new IllegalArgumentException("Unsupported URI: " + uri);
        }

        String table = null;
        if(uriType == TIME_ID || uriType == TIME_LIST) {
            table = TIMES_TABLE;
        } else if(uriType == TASK_ID || uriType == TASK_LIST) {
            table = TASKS_TABLE;
        }
        if(table == null) return 0;

        String where = selection == null ? "" : selection;
        if(uriType == TIME_ID || uriType == TASK_ID) {
            if(!TextUtils.isEmpty(where)) where += " and ";
            where += "_id = " + uri.getLastPathSegment();
        }

        int updateCount = db.update(table, values, where, selectionArgs);
        Log.d(TAG, "Update count: " + updateCount);
        if(updateCount > 0 && getContext() != null) {
            getContext().getContentResolver().notifyChange(uri, null);
        }
        return updateCount;
    }
}

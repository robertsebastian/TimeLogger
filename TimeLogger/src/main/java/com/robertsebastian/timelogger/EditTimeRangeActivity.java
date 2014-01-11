package com.robertsebastian.timelogger;

import android.app.Activity;
import android.app.LoaderManager;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.CursorLoader;
import android.content.Loader;
import android.database.Cursor;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.CursorAdapter;
import android.widget.EditText;
import android.widget.SimpleCursorAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.Calendar;

public class EditTimeRangeActivity extends Activity implements
    LoaderManager.LoaderCallbacks<Cursor>,
    DateTimePickerDialogFragment.OnDateTimePickedListener,
    AdapterView.OnItemSelectedListener,
    TextWatcher
{
    public static final String TAG = EditTimeRangeActivity.class.getSimpleName();

    private static final int TASKS_QUERY_ID = 1;
    private static final int TIME_QUERY_ID = 2;

    private Calendar mStart, mStop;
    private long mTaskId = -1;
    private long mTimeRangeId = -1;

    private Spinner mTaskSpinner;
    private EditText mDuration;
    private TextView mStartDate, mStopDate;
    private TextView mStartTime, mStopTime;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);

        assert(getIntent() != null);
        assert(getIntent().getExtras() != null);

        setContentView(R.layout.activity_edit_time_range);

        mStartDate = (TextView)findViewById(R.id.start_date);
        mStartTime = (TextView)findViewById(R.id.start_time);
        mStopDate  = (TextView)findViewById(R.id.stop_date);
        mStopTime  = (TextView)findViewById(R.id.stop_time);
        mDuration  = (EditText)findViewById(R.id.duration);

        // Update date ranges when duration is changed
        mDuration.addTextChangedListener(this);

        // Fill the task spinner from the database
        SimpleCursorAdapter adapter = new SimpleCursorAdapter(this,
                android.R.layout.simple_spinner_item, null,
                new String[] {"name"}, new int[] {android.R.id.text1}, 0);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        mTaskSpinner = (Spinner)findViewById(R.id.task_spinner);
        mTaskSpinner.setAdapter(adapter);
        mTaskSpinner.setOnItemSelectedListener(this);

        getLoaderManager().initLoader(TASKS_QUERY_ID, null, this);

        mTimeRangeId = getIntent().getExtras().getLong("id");

        // Restore state if available, otherwise query initial time state from database
        if(saved != null) {
            mStart = Util.newCalendar(saved.getLong("start"));
            mStop = saved.containsKey("stop") ? Util.newCalendar(saved.getLong("stop")) : null;
            mTaskId = saved.getLong("task_id");
            taskUpdated();
            timeUpdated(true);
        } else {
            getLoaderManager().initLoader(TIME_QUERY_ID, null, this);
        }
    }

    // Save off date and task fields -- everything else can be recalculated
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putLong("start", mStart.getTimeInMillis());
        if(mStop != null) outState.putLong("stop", mStop.getTimeInMillis());
        outState.putLong("task_id", mTaskId);
    }

    // Add save/cancel options
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.edit_time_range, menu);
        return true;
    }

    // Handle save/cancel options
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if(id == R.id.action_save) {
            // Update database with new values
            ContentValues updates = new ContentValues();
            updates.put("task_id", mTaskId);
            updates.put("start", mStart.getTimeInMillis());
            updates.put("stop", mStop.getTimeInMillis());

            getContentResolver().update(
                ContentUris.withAppendedId(TimeProvider.TIMES_URI, mTimeRangeId),
                updates, null, null);
            finish();
        } else if(id == R.id.action_cancel) {
            // Nothing to do on cancel
            finish();
        }

        return super.onOptionsItemSelected(item);
    }

    // Handle stop time edit button click
    public void onEditStopTime(View v) {
        Bundle args = new Bundle();
        args.putString("field", "stop");

        // Launch dialog to pick new stop time
        DateTimePickerDialogFragment dialog = DateTimePickerDialogFragment.newInstance(
                "Select ending time",
                mStop.getTimeInMillis(),
                mStart.getTimeInMillis() + 60000, Long.MAX_VALUE, args);
        dialog.show(getFragmentManager(), "edit_dialog");
    }

    // Handle start time edit button click
    public void onEditStartTime(View v) {
        Bundle args = new Bundle();
        args.putString("field", "start");

        // Launch dialog to pick new start time
        long max = (mStop == null ? Util.getTimeMs() : mStop.getTimeInMillis());
        DateTimePickerDialogFragment dialog = DateTimePickerDialogFragment.newInstance(
                "Select starting time",
                mStart.getTimeInMillis(),
                Long.MIN_VALUE, max, args);
        dialog.show(getFragmentManager(), "edit_dialog");
    }

    // Update selected item in the task spinner -- intended to be called twice: once when we get the
    // list items and once when we get the selected task ID. When the second query finishes, the
    // spinner can be updated.
    public void taskUpdated() {
        if(mTaskId == -1) return;

        Cursor c = ((CursorAdapter)mTaskSpinner.getAdapter()).getCursor();
        if(c == null) return;

        c.moveToFirst();
        for(int i = 0; i < c.getCount(); i++) {
            c.moveToPosition(i);
            if(c.getLong(c.getColumnIndex("_id")) == mTaskId) {
                mTaskSpinner.setSelection(i, false);
                break;
            }
        }
    }

    // Update time and duration fields
    public void timeUpdated(boolean recalculateDuration) {
        mStartTime.setText(Util.formatTime(mStart));
        mStartDate.setText(Util.formatDate(mStart));

        // mStop is null when the task is currently selected and counting. Hide stop and duration related fields.
        if(mStop != null) {
            findViewById(R.id.stop_time_dependent_fields).setVisibility(View.VISIBLE);
            mStopTime.setText(Util.formatTime(mStop));
            mStopDate.setText(Util.formatDate(mStop));

            // Avoid infinite loop with TextWatcher by only updating when not being edited
            if(recalculateDuration) {
                mDuration.removeTextChangedListener(this);
                mDuration.setText(Util.formatDuration(mStop.getTimeInMillis() - mStart.getTimeInMillis()));
                mDuration.addTextChangedListener(this);
            }
        } else {
            findViewById(R.id.stop_time_dependent_fields).setVisibility(View.GONE);
        }
    }

    // Handle database cursor loader
    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle bundle) {
        switch(id) {
        case TASKS_QUERY_ID:
            return new CursorLoader(this,
                    TimeProvider.TASKS_URI,
                    new String[] {"name", "_id"},
                    "hidden=0", null, "name asc");
        case TIME_QUERY_ID:
            return new CursorLoader(this,
                    ContentUris.withAppendedId(TimeProvider.TIMES_URI, mTimeRangeId),
                    new String[] {"start", "stop", "task_id"},
                    null, null, null);
        }
        return null;
    }

    // Update state with new data from database queries
    @Override
    public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor c) {
        c.moveToFirst();

        switch(cursorLoader.getId()) {
        case TASKS_QUERY_ID:
            ((SimpleCursorAdapter)mTaskSpinner.getAdapter()).swapCursor(c);
            taskUpdated();
            break;

        case TIME_QUERY_ID:
            mStart = Util.newCalendar(c.getLong(c.getColumnIndex("start")));
            long stopTime = c.getLong(c.getColumnIndex("stop"));
            mStop = stopTime == -1 ? null : Util.newCalendar(stopTime);
            mTaskId = c.getLong(c.getColumnIndex("task_id"));

            timeUpdated(true);
            taskUpdated();
            break;
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> cursorLoader) {
        if(cursorLoader.getId() == TASKS_QUERY_ID) {
            ((SimpleCursorAdapter)mTaskSpinner.getAdapter()).swapCursor(null);
        }
    }

    // Update time fields when dialog returns
    @Override
    public void onDateTimePicked(long time, Bundle resultArgs) {
        String field = resultArgs.getString("field");
        if(field == null) return;

        if(field.equals("start")) {
            mStart = Util.newCalendar(time);
        } else if(field.equals("stop")) {
            mStop = Util.newCalendar(time);
        }
        timeUpdated(true);
    }

    // Update saved task ID when new item selected in spinner
    @Override
    public void onItemSelected(AdapterView<?> adapterView, View view, int pos, long id) {
        Cursor c = ((SimpleCursorAdapter)mTaskSpinner.getAdapter()).getCursor();
        if(c == null) return;

        c.moveToPosition(pos);
        mTaskId = c.getLong(c.getColumnIndex("_id"));
    }

    @Override
    public void onNothingSelected(AdapterView<?> adapterView) {
        mTaskId = -1;
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

    @Override
    public void onTextChanged(CharSequence s, int start, int count, int after) {}

    // Update time when duration field is edited
    @Override
    public void afterTextChanged(Editable editable) {
        try {
            mStop.setTimeInMillis(mStart.getTimeInMillis() + Util.durationToMs(editable.toString()));
            timeUpdated(false);
        } catch(NumberFormatException ignored) {}
    }
}

package com.robertsebastian.timelogger;

import android.app.ListFragment;
import android.app.LoaderManager;
import android.content.AsyncQueryHandler;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.Loader;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Checkable;
import android.widget.CursorAdapter;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.util.*;
import android.content.*;

public class TaskListFragment extends ListFragment implements
        LoaderManager.LoaderCallbacks<Cursor>,
        SimpleCursorAdapter.ViewBinder,
        DateTimePickerDialogFragment.OnDateTimePickedListener
{
    public static final String TAG = TaskListFragment.class.getSimpleName();

    private static final int TASKS_QUERY_ID = 1;

    private static final int DIALOG_ACTION_PICK_SINGLE_DAY = 0;
    private static final int DIALOG_ACTION_PICK_FIRST_DAY = 1;
    private static final int DIALOG_ACTION_PICK_LAST_DAY = 2;

    private static final long ONE_DAY = 24 * 3600 * 1000; // 24 hours in milliseconds
    private static final long ONE_WEEK = ONE_DAY * 7;

    // Restore data
    private long mStartRange   = Long.MIN_VALUE;
    private long mStopRange    = Long.MAX_VALUE;
    private String mDateText   = "";
    private boolean mDateShowHidden = false;
    private String mDateSort   = "last_used desc";

    // Frequently accessed views
    private TextView mTotalDurationTextView;
    private TextView mDateTextView;

    // Handler to periodically update duration counts
    private Handler mHandler = new Handler();
    private Runnable mUpdateRunnable = new Runnable() {
        @Override
        public void run() {
            if(getActivity() != null) {
                getActivity().getContentResolver().notifyChange(TimeProvider.TASKS_URI, null);
            }
            mHandler.postDelayed(this, 3600);
        }
    };

    @Override
    public void onResume() {
        super.onResume();
        mHandler.postDelayed(mUpdateRunnable, 0);
    }

    @Override
    public void onPause() {
        super.onPause();
        mHandler.removeCallbacks(mUpdateRunnable);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
		
        setHasOptionsMenu(true);
		setRetainInstance(true);
		
		// Default to "today" view
        long today = Util.getToday().getTimeInMillis();
        mStartRange = today;
        mStopRange  = today + ONE_DAY;
        mDateText   = getString(R.string.date_range_today);	
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);

        View root = inflater.inflate(R.layout.fragment_task_list, container, false);
        mDateTextView = (TextView)root.findViewById(R.id.date_range);
        mTotalDurationTextView = (TextView)root.findViewById(R.id.total_duration);

        return root;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        assert(getActivity() != null);
        assert(getLoaderManager() != null);
        assert(getListView() != null);

        SimpleCursorAdapter adapter = new SimpleCursorAdapter(getActivity(),
                R.layout.row_tasks,
                null,
                new String[] {"name", "description", "selected", "duration"},
                new int[] {R.id.name, R.id.description, R.id.selected, R.id.duration}, 0);
        adapter.setViewBinder(this);
        setListAdapter(adapter);

        registerForContextMenu(getListView());
        
        // Use the last filter/sort criteria
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(getActivity());
        mDateShowHidden = pref.getBoolean("date_show_hidden", false);
        mDateSort = pref.getString("date_sort", "name collate nocase asc");
        
        // Initialize date range
        updateDateRange(mStartRange, mStopRange, mDateText);
    }

    // Create context menu for modifying a task
    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo info) {
        super.onCreateContextMenu(menu, v, info);

        // Inflate context menu
        if(getActivity() == null) return;

        MenuInflater inflater = getActivity().getMenuInflater();
        inflater.inflate(R.menu.task_list_context, menu);

        // Enable/Disable relevant items
        Cursor c = getCursorAtPos((AdapterView.AdapterContextMenuInfo)info);
        if(c == null) return;

        boolean isHidden = c.getLong(c.getColumnIndex("hidden")) != 0;
        Util.setMenuItemEnabled(menu, R.id.action_hide, !isHidden);
        Util.setMenuItemEnabled(menu, R.id.action_unhide, isHidden);
    }

    // Handle option selection from context menu
    @Override
    public boolean onContextItemSelected(MenuItem item) {
        assert(getActivity() != null);
        assert(getFragmentManager() != null);
        assert(item.getMenuInfo() != null);

        int itemId = item.getItemId();

        Cursor c = getCursorAtPos((AdapterView.AdapterContextMenuInfo)item.getMenuInfo());
        if(c == null) return true;

        long taskId = c.getLong(c.getColumnIndex("_id"));

        if(itemId == R.id.action_edit) {
            TaskEditDialogFragment.newInstance(taskId).show(getFragmentManager(), "edit");

        } else if(itemId == R.id.action_hide || itemId == R.id.action_unhide) {
            ContentValues values = new ContentValues();
            values.put("hidden", itemId == R.id.action_hide ? 1 : 0);

            AsyncQueryHandler handler = new AsyncQueryHandler(getActivity().getContentResolver()) {};
            handler.startUpdate(0, null, ContentUris.withAppendedId(TimeProvider.TASKS_URI, taskId), values, null, null);

        } else {
            return super.onContextItemSelected(item);
        }

        return true;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.task_list_action_bar, menu);

        menu.findItem(R.id.show_hidden).setChecked(mDateShowHidden);
    }

    // Get the current cursor and move it to the position at which a context menu is open
    public Cursor getCursorAtPos(AdapterView.AdapterContextMenuInfo info) {
        assert(info != null);
        return getCursorAtPos(info.position);
    }

    public Cursor getCursorAtPos(int pos) {
        assert(getListAdapter() != null);

        Cursor c = ((CursorAdapter)getListAdapter()).getCursor();
        if(c == null) return null;

        c.moveToPosition(pos);
        return c;
    }

    @Override
    public void onListItemClick(ListView list, View v, int i, long itemId) {
        if(getActivity() == null) return;

        ContentValues values = new ContentValues();

        // Deselect everything
        values.put("selected", 0);
        getActivity().getContentResolver().update(TimeProvider.TASKS_URI, values, "selected = 1", null);

        // Select new row
        Cursor c = (Cursor)list.getAdapter().getItem(i);
        long wasSelected = c.getLong(c.getColumnIndex("selected"));
        if(wasSelected == 0) {
            long taskId = c.getLong(c.getColumnIndex("_id"));
            Uri thisTask = ContentUris.withAppendedId(TimeProvider.TASKS_URI, taskId);

            values.put("selected", 1);
            getActivity().getContentResolver().update(thisTask, values, null, null);
        }

    }

    // Update the selected date range and start new query for it
    private void updateDateRange(long start, long stop, int textId) {
        updateDateRange(start, stop, getString(textId));
    }

    private void updateDateRange(long start, long stop) {
        if(stop - start == ONE_DAY) {
            updateDateRange(start, stop, Util.formatDate(start));
        } else {
            updateDateRange(start, stop, Util.formatDateRange(start, stop - ONE_DAY));
        }
    }

    private void updateDateRange(long start, long stop, String text) {
        assert(getLoaderManager() != null);

        mStartRange = start;
        mStopRange = stop;
        mDateText = text;

        getLoaderManager().restartLoader(TASKS_QUERY_ID, null, this);
    }

    // Update sort criteria and start new query with it
    private void updateSortCriteria(String criteria) {
        assert(getActivity() != null);
        assert(getLoaderManager() != null);

        mDateSort = criteria;
        getLoaderManager().restartLoader(TASKS_QUERY_ID, null, this);

        // Save this as a preference so it persists across app restarts
        SharedPreferences.Editor pref = PreferenceManager.getDefaultSharedPreferences(getActivity()).edit();
        pref.putString("date_sort", mDateSort);
        pref.commit();
    }

    // Update filter criteria and start new query with it
    private void updateShowHidden(boolean showHidden) {
        assert(getActivity() != null);
        assert(getLoaderManager() != null);

        mDateShowHidden = showHidden;
        getLoaderManager().restartLoader(TASKS_QUERY_ID, null, this);

        // Save this as a preference so it persists across app restarts
        SharedPreferences.Editor pref = PreferenceManager.getDefaultSharedPreferences(getActivity()).edit();
        pref.putBoolean("date_show_hidden", mDateShowHidden);
        pref.commit();
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        if(id == TASKS_QUERY_ID) {
            Uri uri = TimeProvider.TASKS_URI.buildUpon()
                    .appendQueryParameter("start", Long.toString(mStartRange))
                    .appendQueryParameter("stop", Long.toString(mStopRange))
                    .build();

            return new CursorLoader(this.getActivity(),
                uri,
                new String[] {"_id", "name", "description", "selected", "duration", "hidden"},
                mDateShowHidden ? null : "hidden = 0 or duration > 0", null,
                mDateSort);
        }
        return null;
    }

    @Override
    public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor cursor) {
        assert(getListAdapter() != null);

        // Sum up duration column
        cursor.moveToFirst();
        int durationCol = cursor.getColumnIndex("duration");
        long total = 0;
        for(; !cursor.isAfterLast(); cursor.moveToNext()) {
            total += cursor.getLong(durationCol); }
        mTotalDurationTextView.setText(Util.formatDuration(total));

        // Set time range
        mDateTextView.setText(mDateText);

        ((SimpleCursorAdapter)getListAdapter()).swapCursor(cursor);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> cursorLoader) {
        assert(getListAdapter() != null);
        ((SimpleCursorAdapter)getListAdapter()).swapCursor(null);
    }

    @Override
    public boolean setViewValue(View view, Cursor c, int i) {
        if(view.getId() == R.id.selected) {
            ((Checkable)view).setChecked(c.getInt(i) != 0);
            return true;
        } else if(view.getId() == R.id.name || view.getId() == R.id.description) {
            TextView text = (TextView)view;

            text.setText(c.getString(i));
            if(c.getInt(c.getColumnIndex("hidden")) != 0) {
                text.setPaintFlags(text.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            } else {
                text.setPaintFlags(text.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
            }
            return true;
        } else if(view.getId() == R.id.duration) {
            ((TextView)view).setText(Util.formatDuration(c.getLong(i)));
            return true;
        }
        return false;
    }

    private void showDatePickerDialog(int action, int titleResource, long initial, long min, long max) {
        assert(getFragmentManager() != null);

        Bundle args = new Bundle();
        args.putInt("action", action);
        args.putLong("initial", initial);
        DateTimePickerDialogFragment dialog = DateTimePickerDialogFragment.newInstance(
                getString(titleResource), initial, min, max, true, args);
        dialog.setTargetFragment(this, 0);
        dialog.show(getFragmentManager(), "date_dialog");
    }

    @Override
    public void onDateTimePicked(long time, Bundle resultArgs) {
        int action = resultArgs.getInt("action", -1);

        if(action == DIALOG_ACTION_PICK_SINGLE_DAY) {
            updateDateRange(time, time + ONE_DAY);
        } else if(action == DIALOG_ACTION_PICK_FIRST_DAY) {
            // Open dialog to pick final date
            showDatePickerDialog(DIALOG_ACTION_PICK_LAST_DAY,
                    R.string.prompt_select_last_day, time, time, Long.MAX_VALUE);
        } else if(action == DIALOG_ACTION_PICK_LAST_DAY) {
            updateDateRange(resultArgs.getLong("initial"), time + ONE_DAY);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        assert(getFragmentManager() != null);

        long today    = Util.getToday().getTimeInMillis();
        long thisWeek = Util.getStartOfWeek().getTimeInMillis();

        switch(item.getItemId()) {
        // Handle updates to date range
        case R.id.action_date_range_today:
            updateDateRange(today, today + ONE_DAY, R.string.date_range_today);
            return true;
        case R.id.action_date_range_yesterday:
            updateDateRange(today - ONE_DAY, today, R.string.date_range_yesterday);
            return true;
        case R.id.action_date_range_this_week:
            updateDateRange(thisWeek, thisWeek + ONE_WEEK, R.string.date_range_this_week);
            return true;
        case R.id.action_date_range_last_week:
            updateDateRange(thisWeek - ONE_WEEK, thisWeek, R.string.date_range_last_week);
            return true;
        case R.id.action_date_range_all_time:
            updateDateRange(Long.MIN_VALUE, Long.MAX_VALUE, R.string.date_range_all_time);
            return true;
        case R.id.action_date_range_pick_day:
            showDatePickerDialog(DIALOG_ACTION_PICK_SINGLE_DAY,
                    R.string.prompt_select_day, mStartRange, Long.MIN_VALUE, Long.MAX_VALUE);
            return true;
        case R.id.action_date_range_pick_range:
            showDatePickerDialog(DIALOG_ACTION_PICK_FIRST_DAY,
                    R.string.prompt_select_first_day, mStartRange, Long.MIN_VALUE, Long.MAX_VALUE);
            return true;

        // Handle updates to filter criteria
        case R.id.show_hidden:
            item.setChecked(!item.isChecked());
            updateShowHidden(item.isChecked());
            return true;

        // Handle updates to sort criteria
        case R.id.sort_by_name:
            updateSortCriteria("hidden asc, name collate nocase asc");
            return true;
        case R.id.sort_by_usage:
            updateSortCriteria("hidden asc, last_used desc");
            return true;
        case R.id.sort_by_creation_time:
            updateSortCriteria("hidden asc, time_added desc");
            return true;

        case R.id.action_new_task:
            new TaskEditDialogFragment().show(getFragmentManager(), "create");
            return true;

        case R.id.action_report:
            startActivity(new Intent(getActivity(), ReportActivity.class));
            return true;
        }
        return false;
    }
}

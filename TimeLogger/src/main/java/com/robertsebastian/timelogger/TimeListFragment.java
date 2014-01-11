package com.robertsebastian.timelogger;

import android.app.ListFragment;
import android.app.LoaderManager;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.ContextMenu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.CursorAdapter;
import android.widget.ListView;
import android.widget.ResourceCursorAdapter;
import android.widget.TextView;

import java.util.Calendar;

public class TimeListFragment extends ListFragment implements
        LoaderManager.LoaderCallbacks<Cursor>,
        DateTimePickerDialogFragment.OnDateTimePickedListener
{
    public static final String TAG = TimeListFragment.class.getSimpleName();

    // Handle periodic updates (0.001 hours) of the list
    private Handler mHandler = new Handler();
    private Runnable mUpdateRunnable = new Runnable() {
        @Override
        public void run() {
            if(getActivity() != null) {
                getActivity().getContentResolver().notifyChange(TimeProvider.TIMES_URI, null);
            }
            mHandler.postDelayed(this, 3600);
        } };

    // Resume periodic updates on resumed
    @Override
    public void onResume() {
        super.onResume();
        mHandler.postDelayed(mUpdateRunnable, 0);
    }

    // Cancel periodic updates on pause
    @Override
    public void onPause() {
        super.onPause();
        mHandler.removeCallbacks(mUpdateRunnable);
    }

    static private class TimeRangeCursorAdapter extends ResourceCursorAdapter {
        TimeRangeCursorAdapter(Context context, int layout, Cursor c, int flags) {
            super(context, layout, c, flags);
        }

        @Override
        public void bindView(View v, Context context, Cursor c) {
            String name   = c.getString(c.getColumnIndex("name"));
            long start    = c.getLong(c.getColumnIndex("start"));
            long stop     = c.getLong(c.getColumnIndex("stop"));
            long duration = c.getLong(c.getColumnIndex("duration"));

            Calendar date = Util.newCalendar(start);
            Calendar prevDate = null;
            if(!c.isFirst()) {
                c.moveToPrevious();
                prevDate = Util.newCalendar(c.getLong(c.getColumnIndex("start")));
                c.moveToNext();
            }

            TextView dateHeaderView = (TextView)v.findViewById(R.id.date_header);
            if(prevDate == null || !Util.isSameDay(date, prevDate)) {
                dateHeaderView.setVisibility(View.VISIBLE);
                dateHeaderView.setText(Util.formatDate(date));
            } else {
                dateHeaderView.setVisibility(View.GONE);
            }

            ((TextView)v.findViewById(R.id.name)).setText(name);
            ((TextView)v.findViewById(R.id.start)).setText(Util.formatTime(start));
            ((TextView)v.findViewById(R.id.stop)).setText(Util.formatTime(stop));
            ((TextView)v.findViewById(R.id.duration)).setText(Util.formatDuration(duration));
        }
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        assert(getLoaderManager()) != null;
        assert(getListView()) != null;

        super.onActivityCreated(savedInstanceState);

        TimeRangeCursorAdapter adapter = new TimeRangeCursorAdapter(
                getActivity(),
                R.layout.row_times,
                null, 0);

        setListAdapter(adapter);
        setListShown(false);
        setEmptyText(getString(R.string.empty_time_list_text));

        registerForContextMenu(getListView());

        getLoaderManager().initLoader(0, null, this);
    }

    // Create context menu for modifying a time range
    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo info) {
        super.onCreateContextMenu(menu, v, info);

        // Inflate context menu
        if(getActivity() == null) return;

        MenuInflater inflater = getActivity().getMenuInflater();
        inflater.inflate(R.menu.time_list_context, menu);

        // Enable/Disable relevant items
        Cursor c = getCursorAtPos((AdapterView.AdapterContextMenuInfo)info);
        if(c == null) return;

        Util.setMenuItemEnabled(menu, R.id.join_up, !c.isFirst());
        Util.setMenuItemEnabled(menu, R.id.join_down, !c.isLast());
    }

    private void startEditTimeRangeActivity(long id) {
        Intent i = new Intent(getActivity(), EditTimeRangeActivity.class);
        i.putExtra("id", id);
        startActivity(i);
    }

    // Listener for date time selection dialog
    @Override
    public void onDateTimePicked(long time, Bundle args) {
        if(args == null) return;

        String action = args.getString("action");
        assert(action != null);

        if(action.equals("split")) {
            // Split a time range
            updateTime(args.getLong("_id"), args.getLong("start"), time);
            insertTime(args.getLong("task_id"), time, args.getLong("stop"));

        } else if(action.equals("join")) {
            // If tasks have the same ID, merge them into one, otherwise join them
            if(args.getLong("top_task_id") == args.getLong("bottom_task_id")) {
                updateTime(args.getLong("top_id"), args.getLong("bottom_start"), args.getLong("top_stop"));
                deleteTime(args.getLong("bottom_id"));
            } else {
                updateTime(args.getLong("top_id"), time, args.getLong("top_stop"));
                updateTime(args.getLong("bottom_id"), args.getLong("bottom_start"), time);
            }
        }
    }

    // Show dialog for joining two time ranges
    private void showTimeJoinDialog(int top, int bottom, int init) {
        assert(getListAdapter() != null);
        assert(getFragmentManager() != null);

        Cursor c = ((CursorAdapter)getListAdapter()).getCursor();
        if(c == null) return;

        // Data needed to process the join when the dialog returns
        Bundle args = new Bundle();
        args.putString("action", "join");

        // Extract data from newer time range
        c.moveToPosition(top);
        args.putLong("top_id",      c.getLong(c.getColumnIndex("_id")));
        args.putLong("top_task_id", c.getLong(c.getColumnIndex("task_id")));
        args.putLong("top_stop",    c.getLong(c.getColumnIndex("stop")));

        // Extract data from older time range
        c.moveToPosition(bottom);
        args.putLong("bottom_id",      c.getLong(c.getColumnIndex("_id")));
        args.putLong("bottom_task_id", c.getLong(c.getColumnIndex("task_id")));
        args.putLong("bottom_start",   c.getLong(c.getColumnIndex("start")));

        // Determine which time range to initialize the dialog setting to
        c.moveToPosition(init);
        final long initTime = c.getLong(c.getColumnIndex(init == bottom ? "stop" : "start"));

        // Nothing to do if the time range is too short
        long maxTime = (args.getLong("top_stop") == -1 ? Util.getTimeMs() : args.getLong("top_stop")) - 60000;
        long minTime = args.getLong("bottom_start") + 60000;
        if(maxTime <= minTime) return;

        // Kick off dialog
        DateTimePickerDialogFragment dialog = DateTimePickerDialogFragment.newInstance(
                "Select join time", initTime, minTime, maxTime, args);
        dialog.setTargetFragment(this, 0);
        dialog.show(getFragmentManager(), "join_dialog");
    }

    // Show dialog to request time at which to split a time range
    private void showTimeSplitDialog(int pos) {
        assert(getListAdapter() != null);
        assert(getFragmentManager() != null);

        Cursor c = ((CursorAdapter)getListAdapter()).getCursor();
        if(c == null) return;

        // Data needed to process the join when the dialog returns
        Bundle args = new Bundle();
        args.putString("action", "split");

        // Copy data from cursor
        c.moveToPosition(pos);
        args.putLong("_id",     c.getLong(c.getColumnIndex("_id")));
        args.putLong("task_id", c.getLong(c.getColumnIndex("task_id")));
        args.putLong("start",   c.getLong(c.getColumnIndex("start")));
        args.putLong("stop",    c.getLong(c.getColumnIndex("stop")));

        // Nothing to do if the range is too short (less than two minutes)
        long maxTime = (args.getLong("stop") == -1 ? Util.getTimeMs() : args.getLong("stop")) - 60000;
        long minTime = args.getLong("start") + 60000;
        if(maxTime <= minTime) return;

        // Initialize to splitting the time in half
        final long initTime = (maxTime - minTime) / 2 + minTime;

        // Kick off dialog
        DateTimePickerDialogFragment dialog = DateTimePickerDialogFragment.newInstance(
                "Select split time", initTime, minTime, maxTime, args);
        dialog.setTargetFragment(this, 0);
        dialog.show(getFragmentManager(), "split_dialog");
    }

    // Modify the start/stop times of a time range with the given ID
    private void updateTime(long id, long start, long stop) {
        assert(getActivity() != null);

        ContentValues vals = new ContentValues();
        vals.put("start", start);
        vals.put("stop", stop);

        Uri uri = ContentUris.withAppendedId(TimeProvider.TIMES_URI, id);
        //assert(uri != null);

        getActivity().getContentResolver().update(uri, vals, null, null);
    }

    // Insert a new time range
    private void insertTime(long taskId, long start, long stop) {
        assert(getActivity() != null);

        ContentValues vals = new ContentValues();
        vals.put("task_id", taskId);
        vals.put("start", start);
        vals.put("stop", stop);

        Log.d(TAG, "Inserting " + taskId);
        getActivity().getContentResolver().insert(TimeProvider.TIMES_URI, vals);
    }

    // Delete the time range with a given ID
    private void deleteTime(long id) {
        assert(getActivity() != null);

        Uri uri = TimeProvider.TIMES_URI.buildUpon().appendPath(Long.toString(id)).build();
        if(uri == null) return;

        getActivity().getContentResolver().delete(uri, null, null);
    }

    // Delete the time range at position in the list adapter's cursor
    private void deleteTimeAtPos(int pos) {
        assert(getListAdapter() != null);

        Cursor c = ((CursorAdapter)getListAdapter()).getCursor();
        if(c == null) return;

        c.moveToPosition(pos);
        long id = c.getLong(c.getColumnIndex("_id"));

        deleteTime(id);
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

    // Handle item selected in listview
    @Override
    public void onListItemClick(ListView l, View v, int pos, long id) {
        Cursor c = getCursorAtPos(pos);
        startEditTimeRangeActivity(c.getLong(c.getColumnIndex("_id")));
    }

    // Handle option selection from context menu
    @Override
    public boolean onContextItemSelected(MenuItem item) {
        assert(getActivity() != null);
        assert(item.getMenuInfo() != null);

        int itemId = item.getItemId();
        int itemPos = ((AdapterView.AdapterContextMenuInfo)item.getMenuInfo()).position;

        Cursor c = getCursorAtPos((AdapterView.AdapterContextMenuInfo)item.getMenuInfo());
        if(c == null) return true;

        if(itemId == R.id.edit) {
            startEditTimeRangeActivity(c.getLong(c.getColumnIndex("_id")));
        } else if(itemId == R.id.join_up) {
            showTimeJoinDialog(itemPos - 1, itemPos, itemPos - 1);
        } else if(itemId == R.id.join_down) {
            showTimeJoinDialog(itemPos, itemPos + 1, itemPos + 1);
        } else if(itemId == R.id.split) {
            showTimeSplitDialog(itemPos);
        } else if(itemId == R.id.delete) {
            deleteTimeAtPos(itemPos);
        } else {
            return super.onContextItemSelected(item);
        }

        return true;
    }

    // Handle database cursor loader
    @Override
    public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
        return new CursorLoader(
            this.getActivity(),
            TimeProvider.TIMES_URI,
            null, null, null,
            "start desc");
    }

    @Override
    public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor cursor) {
        assert(getListAdapter()) != null;
        ((CursorAdapter)getListAdapter()).swapCursor(cursor);

        if (isResumed()) {
            setListShown(true);
        } else {
            setListShownNoAnimation(true);
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> cursorLoader) {
        assert(getListAdapter()) != null;
        ((CursorAdapter)getListAdapter()).swapCursor(null);
    }
}
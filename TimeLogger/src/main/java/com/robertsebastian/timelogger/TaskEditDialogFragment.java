package com.robertsebastian.timelogger;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.LoaderManager;
import android.content.AsyncQueryHandler;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.CursorLoader;
import android.content.DialogInterface;
import android.content.Loader;
import android.database.Cursor;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

public class TaskEditDialogFragment extends DialogFragment implements
        DialogInterface.OnClickListener,
        LoaderManager.LoaderCallbacks<Cursor>
{
    EditText mName;
    EditText mDescription;

    public static TaskEditDialogFragment newInstance(long id) {
        TaskEditDialogFragment f = new TaskEditDialogFragment();

        Bundle args = new Bundle();
        args.putLong("id", id);
        f.setArguments(args);

        return f;
    }

    private class TaskQueryHandler extends AsyncQueryHandler {
        public TaskQueryHandler(ContentResolver cr, long id) {
            super(cr);

            startQuery(0, null,  ContentUris.withAppendedId(TimeProvider.TASKS_URI, id),
                    new String[] {"name", "description", "hidden"}, null, null, null);
        }

        @Override
        public void onQueryComplete(int token, Object cookie, Cursor c) {
            if(!c.moveToFirst()) return;

            mName.setText(c.getString(c.getColumnIndex("name")));
            mName.setEnabled(true);

            mDescription.setText(c.getString(c.getColumnIndex("description")));
            mDescription.setEnabled(true);
        }
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        assert(getActivity() != null);
        assert(getLoaderManager() != null);

        LayoutInflater inflater = getActivity().getLayoutInflater();
        View layout = inflater.inflate(R.layout.dialog_edit_task, null);
        assert(layout != null);

        mName = (EditText)layout.findViewById(R.id.name);
        mDescription = (EditText)layout.findViewById(R.id.description);

        if(getArguments() != null && getArguments().containsKey("id")) {
            long id = getArguments().getLong("id");

            //getLoaderManager().initLoader(0, null, this);
            mName.setEnabled(false);
            mDescription.setEnabled(false);
            new TaskQueryHandler(getActivity().getContentResolver(), id);
        }

        // Initialize and return alert dialog
        return new AlertDialog.Builder(getActivity())
                .setTitle(getArguments() == null ? "New task" : "Edit task")
                .setView(layout)
                .setPositiveButton(android.R.string.ok, this)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        assert(getActivity() != null);
        assert(mName.getText() != null);
        assert(mDescription.getText() != null);

        ContentValues values = new ContentValues();
        values.put("name", mName.getText().toString());
        values.put("description", mDescription.getText().toString());

        if(getArguments() != null && getArguments().containsKey("id")) {
            long id = getArguments().getLong("id");

            getActivity().getContentResolver().update(
                    ContentUris.withAppendedId(TimeProvider.TASKS_URI, id),
                    values, null, null);
        } else if(!TextUtils.isEmpty(mName.getText())) {
            getActivity().getContentResolver().insert(TimeProvider.TASKS_URI, values);
        }
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        assert(getArguments() != null);

        return new CursorLoader(this.getActivity(),
                ContentUris.withAppendedId(TimeProvider.TASKS_URI, getArguments().getLong("id", -1)),
                new String[] {"name", "description", "hidden"}, null, null, null);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor cursor) {
        if(!cursor.moveToFirst()) return;

        mName.setText(cursor.getString(cursor.getColumnIndex("name")));
        mName.setEnabled(true);

        mDescription.setText(cursor.getString(cursor.getColumnIndex("description")));
        mDescription.setEnabled(true);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> cursorLoader) {}
}

package com.robertsebastian.timelogger;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.DatePicker;
import android.widget.TimePicker;

import java.util.Calendar;
import java.util.GregorianCalendar;

public class DateTimePickerDialogFragment extends DialogFragment implements
    TimePicker.OnTimeChangedListener, DialogInterface.OnClickListener
{
    private Calendar mInit, mMax, mMin;
    private DatePicker mDate;
    private TimePicker mTime;
    private OnDateTimePickedListener mListener;

    public interface OnDateTimePickedListener {
        public void onDateTimePicked(long time, Bundle resultArgs);
    }

    public static DateTimePickerDialogFragment newInstance(String title, long init, long min, long max, Bundle resultArgs) {
        return newInstance(title, init, min, max, false, resultArgs);
    }

    public static DateTimePickerDialogFragment newInstance(String title, long init, long min, long max, boolean dateOnly, Bundle resultArgs) {
        DateTimePickerDialogFragment f = new DateTimePickerDialogFragment();

        Bundle args = new Bundle();
        args.putString("title", title);
        args.putLong("init", init);
        args.putLong("min", min);
        args.putLong("max", max);
        args.putBoolean("dateOnly", dateOnly);
        args.putBundle("resultArgs", resultArgs);
        f.setArguments(args);

        return f;
    }

    public Dialog onCreateDialog(Bundle savedInstanceState) {
        assert(getActivity() != null);
        assert(getArguments() != null);

        LayoutInflater inflater = getActivity().getLayoutInflater();
        View layout = inflater.inflate(R.layout.dialog_date_time_picker, null);
        assert(layout != null);

        mInit = Util.newCalendar(getArguments().getLong("init"));
        mMin = Util.newCalendar(getArguments().getLong("min"));
        mMax = Util.newCalendar(getArguments().getLong("max"));

        // Initialize date picker
        boolean dateShown =
                mMin.get(Calendar.YEAR) != mMax.get(Calendar.YEAR) ||
                mMin.get(Calendar.DAY_OF_YEAR) != mMax.get(Calendar.DAY_OF_YEAR);

        mDate = (DatePicker)layout.findViewById(R.id.date);

        if(mMin.getTimeInMillis() != Long.MIN_VALUE) mDate.setMinDate(mMin.getTimeInMillis());
        if(mMax.getTimeInMillis() != Long.MAX_VALUE) mDate.setMaxDate(mMax.getTimeInMillis());
        mDate.updateDate(
                mInit.get(Calendar.YEAR),
                mInit.get(Calendar.MONTH),
                mInit.get(Calendar.DAY_OF_MONTH));
        mDate.setVisibility(dateShown ? View.VISIBLE : View.GONE);

        // Initialize time picker
        mTime = (TimePicker)layout.findViewById(R.id.time);
        mTime.setCurrentHour(mInit.get(Calendar.HOUR_OF_DAY));
        mTime.setCurrentMinute(mInit.get(Calendar.MINUTE));
        mTime.setOnTimeChangedListener(this);
        mTime.setVisibility(getArguments().getBoolean("dateOnly") ? View.GONE : View.VISIBLE);

        // Initialize and return alert dialog
        return new AlertDialog.Builder(getActivity())
                .setTitle(getArguments().getString("title"))
                .setView(layout)
                .setPositiveButton(android.R.string.ok, this)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
    }

    // Restrict time selector to specific time range
    @Override
    public void onTimeChanged(TimePicker view, int hourOfDay, int minute) {
        Calendar c = new GregorianCalendar(mDate.getYear(), mDate.getMonth(), mDate.getDayOfMonth(), hourOfDay, minute);

        if(c.getTimeInMillis() > mMax.getTimeInMillis()) {
            view.setCurrentHour(mMax.get(Calendar.HOUR_OF_DAY));
            view.setCurrentMinute(mMax.get(Calendar.MINUTE));
        } else if(c.getTimeInMillis() < mMin.getTimeInMillis()) {
            view.setCurrentHour(mMin.get(Calendar.HOUR_OF_DAY));
            view.setCurrentMinute(mMin.get(Calendar.MINUTE));
        }
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        assert(getArguments() != null);

        OnDateTimePickedListener listener = getTargetFragment() != null ?
                (OnDateTimePickedListener)getTargetFragment() :
                (OnDateTimePickedListener)getActivity();
        assert(listener != null);

        GregorianCalendar result = new GregorianCalendar(
                mDate.getYear(), mDate.getMonth(), mDate.getDayOfMonth(),
                mTime.getCurrentHour(), mTime.getCurrentMinute());

        listener.onDateTimePicked(result.getTimeInMillis(), getArguments().getBundle("resultArgs"));
    }
}

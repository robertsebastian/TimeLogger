package com.robertsebastian.timelogger;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.Html;
import android.util.Log;
import android.widget.TextView;

import java.util.Locale;

public class ReportActivity extends Activity
{
    public static final String TAG = ReportActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        setContentView(R.layout.activity_report);

        TextView content = (TextView)findViewById(R.id.content);

        String [][] vals = new String[][] {
                {"Task",     "Sun", "Mon",  "Tue",  "Wed",  "Thu",   "Fri",  "Sat"},
                {"Task ONE", "",    "2.00", "3.00", "",     "",      "2.20", "5.00"},
                {"Task TWO", "",    "5.00", "6.25", "7.00", "10.10", "2.20", "5.00"}
        };

        StringBuilder str = new StringBuilder();

        str.append("<tt>TABLE 1</tt><br />");
        str.append(buildTable(vals));
        str.append("<br /><br /><tt>TABLE 2</tt><br />");
        str.append(buildTable(vals));
        str.append("<br /><br /><tt>TABLE 3</tt><br />");
        str.append(buildTable(vals));

        content.setText(Html.fromHtml(str.toString()));

        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/html");
        i.putExtra(Intent.EXTRA_EMAIL, new String[]{"test@test.org"});
        i.putExtra(Intent.EXTRA_SUBJECT, "Report");
        i.putExtra(Intent.EXTRA_TEXT, Html.fromHtml(str.toString()));
        //i.putExtra(Intent.EXTRA_TEXT, "Text");
        //i.putExtra(Intent.EXTRA_TEXT, Html.fromHtml(str.toString()));

        startActivity(Intent.createChooser(i, "Email"));
    }

    private String buildTable(String [][] vals) {
        int [] maxLen = new int[vals[0].length];
        for(int r = 0; r < vals.length; r++) {
            for(int c = 0; c < vals[r].length; c++) {
                if(vals[r][c].length() > maxLen[c]) maxLen[c] = vals[r][c].length();
            }
        }

        String [] colFormat = new String[maxLen.length];
        for(int i = 0; i < maxLen.length; i++) colFormat[i] = String.format(Locale.US, "%%-%ds", maxLen[i]);

        StringBuilder str = new StringBuilder();
        for(int r = 0; r < vals.length; r++) {
            str.append("<tt>");
            for(int c = 0; c < vals[r].length; c++) {
                for(int i = 0; i < maxLen[c] - vals[r][c].length(); i++) str.append("&nbsp;");
                str.append(vals[r][c]);
                str.append("&nbsp;");
            }
            str.append("</tt><br />");
            str.append(System.getProperty("line.separator"));
        }

        Log.d(TAG, str.toString());

        return str.toString();
    }
}

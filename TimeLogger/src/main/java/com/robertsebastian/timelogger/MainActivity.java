package com.robertsebastian.timelogger;

import android.app.ActionBar;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.os.Bundle;
import android.util.Log;
import android.widget.ArrayAdapter;
import java.util.*;
import android.app.*;

public class MainActivity extends Activity implements ActionBar.OnNavigationListener {
    public static final String TAG = MainActivity.class.getSimpleName();

	private HashMap<String, Fragment> mFragments = new HashMap<String, Fragment>();
	private String[] mTags;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        // setup action bar for navigation dropdown
        ActionBar actionBar = getActionBar();
        assert(actionBar != null);

        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);
        actionBar.setDisplayShowTitleEnabled(false);
        actionBar.setDisplayHomeAsUpEnabled(false);
        actionBar.setHomeButtonEnabled(false);

        // Create dropdown navigation list
		mTags = getResources().getStringArray(R.array.action_bar_nav_labels);
        ArrayAdapter<String> arr = new ArrayAdapter<String>(actionBar.getThemedContext(),
                android.R.layout.simple_list_item_1, android.R.id.text1,
                mTags);
        actionBar.setListNavigationCallbacks(arr, this);
		
		// Find any existing fragment instances
		FragmentManager fm = getFragmentManager();
		for(String tag : mTags) {
			mFragments.put(tag, fm.findFragmentByTag(tag));
		}
		
		// Add fragmemts
		if(mFragments.get(mTags[0]) == null) mFragments.put(mTags[0], new TaskListFragment());
		if(mFragments.get(mTags[1]) == null) mFragments.put(mTags[1], new TimeListFragment());

        // Go to default nav item or restore
        if(savedInstanceState == null) {
            actionBar.setSelectedNavigationItem(0);
        } else {
            getActionBar().setSelectedNavigationItem(savedInstanceState.getInt("nav_position"));
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        assert(getActionBar() != null);
		
		super.onSaveInstanceState(outState);

        outState.putInt("nav_position", getActionBar().getSelectedNavigationIndex());
        Log.d(TAG, "Saved instance state");
    }

    @Override
    public boolean onNavigationItemSelected(int itemPosition, long itemId) {
        FragmentTransaction ft = getFragmentManager().beginTransaction();
        ft.replace(android.R.id.content, mFragments.get(mTags[itemPosition]), mTags[itemPosition]);
        ft.commit();
        return true;
    }
}

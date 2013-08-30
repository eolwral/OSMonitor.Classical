/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
 
package com.eolwral.osmonitor.classical.processes;


import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.app.TabActivity;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.widget.ListView;
import android.view.ContextMenu;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.view.GestureDetector;
import android.view.GestureDetector.OnGestureListener;

import com.eolwral.osmonitor.classical.*;
import com.eolwral.osmonitor.classical.messages.DebugBox;
import com.eolwral.osmonitor.classical.preferences.Preferences;

public class ProcessList extends ListActivity implements OnGestureListener, OnTouchListener,  ListView.OnScrollListener
{
	private boolean mBusy = false;
	private static ProcessListAdapter UpdateInterface = null;
	private static ProcessList Self = null;
	private static JNIInterface JNILibrary = JNIInterface.getInstance();
	private static int OrderBy = JNILibrary.doSortPID;
	
	private ProcessInfoQuery ProcessInfo = null;
	 
	// TextView
	private static TextView CPUUsage = null;
	private static TextView RunProcess = null;
	private static TextView MemTotal = null;
	private static TextView MemFree = null;

	private static DecimalFormat MemoryFormat = new DecimalFormat(",000");

	// Short & Click
	private static int longClick = 2;
	private static int shortClick = 3;
	private static boolean shortTOlong = false;
	private static boolean longTOshort = false;
	
	// Selected item
	private static int selectedPosition = 0;
	private static String selectedPackageName = null;
	private static int selectedPackagePID = 0;
	
	// MultiSelect
	private static CheckBox MultiSelect = null;
	private static Button MultiKill = null;

	// Freeze
	private static CheckBox Freeze = null;
	private static boolean FreezeIt =  false;
	private static boolean FreezeTask = false;
	
	// Root
	private static boolean Rooted = false;
	
	// Slow Adapter
	private static boolean SlowAdapter = false;
	
	// Gesture
	private GestureDetector gestureScanner = new GestureDetector(this);;
	
	private static boolean GestureLong = false;
	private static boolean GestureSingleTap = false;
	
	@Override
	public boolean onTouchEvent(MotionEvent me)
	{
		return gestureScanner.onTouchEvent(me);
	}
	
	@Override
	public boolean onDown(MotionEvent e)
	{
		return false;
	}

	@Override
	public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY)
	{
		try {
			if (Math.abs(e1.getY() - e2.getY()) > CompareFunc.SWIPE_MAX_OFF_PATH)
				return false;
			else if (e1.getX() - e2.getX() > CompareFunc.SWIPE_MIN_DISTANCE && 
						Math.abs(velocityX) > CompareFunc.SWIPE_THRESHOLD_VELOCITY) 
				((TabActivity) this.getParent()).getTabHost().setCurrentTab(1);
			else if (e2.getX() - e1.getX() > CompareFunc.SWIPE_MIN_DISTANCE &&
						Math.abs(velocityX) > CompareFunc.SWIPE_THRESHOLD_VELOCITY) 
				((TabActivity) this.getParent()).getTabHost().setCurrentTab(4);
			else
				return false;
		} catch (Exception e) {
			// nothing
		}

		GestureLong = false;

		return true;
	}
	
	@Override
	public void onLongPress(MotionEvent e)
	{
		//performLongClick();
		GestureLong = true;
		return;
	}
	
	@Override
	public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY)
	{
		return false;
	}
	
	@Override
	public void onShowPress(MotionEvent e)
	{
		return;
	} 
	
	@Override
	public boolean onSingleTapUp(MotionEvent e) {
		GestureSingleTap = true;
		return true;
	}


	@Override
	public boolean onTouch(View v, MotionEvent event) {

		GestureSingleTap = false;
		
		if(gestureScanner.onTouchEvent(event))
		{
			GestureLong = false;
			
			if(GestureSingleTap == true)
				v.onTouchEvent(event);
			
			return true;
		}
		else
		{
			
			if(v.onTouchEvent(event))
				return true;
			return false;
		}
	}
	
	private Runnable uiRunnable = new Runnable() {
		public void run() {

     		if(JNILibrary.doDataLoad() == 1) {
     			CPUUsage.setText(JNILibrary.GetCPUUsage());
    	     	RunProcess.setText(JNILibrary.GetProcessCounts()+"");
    	     	MemTotal.setText(MemoryFormat.format(JNILibrary.GetMemTotal())+ "K");
    	     	MemFree.setText(MemoryFormat.format(JNILibrary.GetMemBuffer()
    	     					+JNILibrary.GetMemCached()+JNILibrary.GetMemFree())+ "K");
    	     	
    	     	
    	     	Self.onRefresh();
   	     	
     		}
     		else
     		{
     			if(FreezeIt)
    			{
    				if(!FreezeTask)
    				{
    					JNILibrary.doTaskStop();
    					FreezeTask = true;
    				}
    				else
    	     			CPUUsage.setText(JNILibrary.GetCPUUsage());
    			}
    			else
    			{
    				if(FreezeTask)
    				{
    					JNILibrary.doTaskStart(JNILibrary.doTaskProcess);
    					FreezeTask = false;
    				}
    			}

     		}

	        uiHandler.postDelayed(this, 50);
		}
	};   
	
	private Handler uiHandler = new Handler();
	private ActivityManager ActivityMan = null;

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        
        gestureScanner = new GestureDetector(this);
        
        // Use a custom layout file
        setContentView(R.layout.processlayout);

        CPUUsage = (TextView) findViewById(R.id.CPUUsage);
        RunProcess = (TextView) findViewById(R.id.RunProcessText);
        MemTotal = (TextView) findViewById(R.id.MemTotalText);
        MemFree = (TextView) findViewById(R.id.MemFreeText);
        
        // Tell the list view which view to display when the list is empty
        getListView().setEmptyView(findViewById(R.id.empty));

        // Use our own list adapter
        Self = this;
        Self.getListView().setOnTouchListener(this);
        setListAdapter(new ProcessListAdapter(this));
        UpdateInterface = (ProcessListAdapter) getListAdapter();
        ProcessInfo = ProcessInfoQuery.getInstance(this);
        ActivityMan = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        getListView().setOnScrollListener(this);
        
        // MultiKill
        MultiKill = (Button) findViewById(R.id.MultiKill);
        MultiKill.setOnClickListener(
          	new OnClickListener(){
           		public void onClick(View v) {
           			
           			String KillCmd = ""; 
           			ArrayList<String> KillList = ProcessInfo.getSelected();
           			for(String pid:KillList)
           			{
           				int tPID = Integer.parseInt(pid);
           				
           	   	        if(Rooted)
           	   	        {
           	   	        	if(KillCmd.length() == 0)
           	   	        		KillCmd += "kill -9 "+tPID;
           	   	        	else
           	   	        		KillCmd += ";kill -9 "+tPID;
           	   	        }
           	   	        else
           	   	        {
           	   	        	for(int i =0; i < JNILibrary.GetProcessCounts(); i++)
           	   	        	{
           	   	        		if(JNILibrary.GetProcessPID(i) == tPID)
           	   	        		{
           	   	        			android.os.Process.killProcess(tPID);
           	   	        			ActivityMan.restartPackage(JNILibrary.GetProcessName(i));
           	   	        			break;
           	   	        		}
           	   	        	}
           	   	        }
           			}
           			
           			if(Rooted)
           				JNILibrary.execCommand(KillCmd+"\n");

         	        ProcessInfo.clearSelected();

         	        JNILibrary.doDataRefresh();
         	        
         	        UpdateInterface.notifyDataSetChanged();
         	        
           			Toast.makeText(Self, "Kill "+KillList.size()+" Process..",
           													Toast.LENGTH_SHORT).show();
    			}
           	}
        );
        
        // Freeze
        Freeze = (CheckBox) findViewById(R.id.Freeze);
        Freeze.setOnClickListener(
        	new OnClickListener(){
        		public void onClick(View v) {
        			if(FreezeIt)
        			{
        				FreezeIt = false;
        			}
        			else
        			{
        				FreezeIt = true;
        			}
				}
        	}
        );
        
        // Multi-Select
        MultiSelect = (CheckBox) findViewById(R.id.MultiSelect);
        MultiSelect.setOnCheckedChangeListener(
        	new CompoundButton.OnCheckedChangeListener() {
        		@Override
        		public void onCheckedChanged( CompoundButton buttonView, boolean isChecked) 
        		{
        			if(isChecked)
        			{
        				MultiKill.setEnabled(true);
        			}
        			else
        			{
        				MultiKill.setEnabled(false);
        				ProcessInfo.clearSelected();
        				UpdateInterface.notifyDataSetChanged();
        			}
        		}
        	}
        );
    
        // restore
        registerForContextMenu(getListView());
    }
    

	public void onRefresh()
	{
		JNILibrary.doDataSwap(); 
		UpdateInterface.notifyDataSetChanged();
	}
	
	private void restorePrefs()
    {
		boolean ExcludeSystem = false;
		boolean SortIn = false;
		int Algorithm = 1;

		// load settings
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);

		try {

			longClick = Integer.parseInt(settings.getString(Preferences.PREF_LONGBEHAVIOR, "2"));
			shortClick = Integer.parseInt(settings.getString(Preferences.PREF_SHORTBEHAVIOR, "3"));

			JNILibrary.doDataTime(Integer.parseInt(settings.getString(Preferences.PREF_UPDATE, "2")));

			OrderBy =  Integer.parseInt(settings.getString(Preferences.PREF_ORDER, "1"));
			
			Algorithm = Integer.parseInt(settings.getString(Preferences.PREF_ALGORITHM, "1"));
		
		} catch(Exception e) {}

	    SortIn = settings.getBoolean(Preferences.PREF_SORT, false);
	    ExcludeSystem = settings.getBoolean(Preferences.PREF_EXCLUDE, false);
	    
	    // change options
   		JNILibrary.SetProcessSort(OrderBy);
   		
   		JNILibrary.SetProcessAlgorithm(Algorithm);
   		
        if(ExcludeSystem)
    		JNILibrary.SetProcessFilter(1);
        else
        	JNILibrary.SetProcessFilter(0);
        
        if(SortIn)
        	JNILibrary.SetProcessOrder(0);
        else 
        	JNILibrary.SetProcessOrder(1);
        
        // change display
        TextView OrderType = (TextView) findViewById(R.id.OrderType);
        
        switch(OrderBy)
        {
        case 1:
        case 2:
        case 5:
        	OrderType.setText(getResources().getString(R.string.load_text));
        	break;
        case 3:
        	OrderType.setText(getResources().getString(R.string.mem_text));
        	break;
        case 4:
        	OrderType.setText(getResources().getString(R.string.thread_text));
        	break;
        }
        
        UpdateInterface.OrderBy = OrderBy;
        
    	TableLayout Msv = (TableLayout) findViewById(R.id.MultiSelectView);
        if(settings.getBoolean(Preferences.PREF_HIDEMULTISELECT, false))
        	Msv.setVisibility(View.GONE);
        else
        	Msv.setVisibility(View.VISIBLE);
                
        // Status Bar
        if(settings.getBoolean(Preferences.PREF_STATUSBAR, false))
        {
        	if(OSMonitorService.getInstance() == null)
        		startService(new Intent(this, OSMonitorService.class));
        	else
        		OSMonitorService.getInstance().Notify();
        }
        else
        	if(OSMonitorService.getInstance() != null)
        		OSMonitorService.getInstance().stopSelf();

        // Root
		Rooted = settings.getBoolean(Preferences.PREF_ROOTED, false);
		
		// Slow Adapter
		SlowAdapter = settings.getBoolean(Preferences.PREF_SLOWADAPTER, false);
		

    }

    public boolean onCreateOptionsMenu(Menu optionMenu) 
    {
     	super.onCreateOptionsMenu(optionMenu);
     	optionMenu.add(0, 1, 0, getResources().getString(R.string.options_text));
       	optionMenu.add(0, 4, 0, getResources().getString(R.string.aboutoption_text));
       	optionMenu.add(0, 5, 0, getResources().getString(R.string.forceexit_text));
       	
        
    	return true;
    }
    @Override
    protected Dialog onCreateDialog(int id) 
    {
    	switch (id)
    	{
    	case 0:
        	return new AlertDialog.Builder(this)
			   .setIcon(R.drawable.monitor)
			   .setTitle(R.string.app_name)
			   .setMessage(R.string.about_text)
			   .setPositiveButton(R.string.aboutbtn_text,
			   new DialogInterface.OnClickListener() {
				   public void onClick(DialogInterface dialog, int whichButton) { } })
			   .create();
        	
    	case 1:
    		return null;
    	}
    	
    	return null;
    }
    
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
    	restorePrefs();
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
    	      
        super.onOptionsItemSelected(item);
        switch(item.getItemId())
        {
        case 1:
            Intent launchPreferencesIntent = new Intent().setClass( this, Preferences.class);
            startActivityForResult(launchPreferencesIntent, 0);
        	break;
        	
        case 4:
        	this.showDialog(0);
        	break;
        	
        case 5:
        	if(OSMonitorService.getInstance() != null)
        		OSMonitorService.getInstance().stopSelf();

        	JNILibrary.killSelf(this);

        	break;
        }
        
        return true;
    }

    @Override
    public void onPause() 
    {
    	uiHandler.removeCallbacks(uiRunnable);
    	JNILibrary.doTaskStop();
    	
    	if(Freeze.isChecked())
    	{
    		Freeze.setChecked(false);
    		FreezeIt = false;
    	}
    	
    	if(MultiSelect.isChecked())
    	{
			MultiSelect.setChecked(false);
			MultiKill.setEnabled(false);
			ProcessInfo.clearSelected();
    	}
    	
     	super.onPause();
    }

    @Override
    protected void onResume() 
    {    
        restorePrefs();

        JNILibrary.doTaskStart(JNILibrary.doTaskProcess);
    	uiHandler.post(uiRunnable);
    	super.onResume();
    }
    
    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {

    	
    	boolean useMenu = false; 
    	ProcessDetailView selectedItemView =  (ProcessDetailView) 
    							((AdapterContextMenuInfo)menuInfo).targetView;

    	if(!GestureLong)
    	{
    		selectedItemView.setSelected(true);
    		return;
    	}

    	selectedPosition = (int) ((AdapterContextMenuInfo)menuInfo).position;
    	selectedPackageName = ProcessInfo.getPacakge(selectedPosition);
    	selectedPackagePID = JNILibrary.GetProcessPID(selectedPosition);
 
    	if(shortTOlong)
    	{
    		useMenu = true;
    		shortTOlong = false;
    	}
    	else
    	{
    		if(longClick == 1)
    			((ProcessListAdapter)getListAdapter()).toggle(selectedItemView,
    														  selectedPosition,
    														  false,
    														  false);
    		else if(longClick == 2)
        		useMenu = true;
    		else if(longClick == 3)
        		if(!((ProcessListAdapter)getListAdapter()).toggle(selectedItemView,
        														 selectedPosition,
        														 true,
        														 false))
        			useMenu = true;

    	}


    	if(useMenu)
      	{
       		menu.setHeaderTitle(ProcessInfo.getPackageName(selectedPosition));
       		menu.add(0, 1, 0, getResources().getString(R.string.killdialog_text));
       		menu.add(0, 2, 0, getResources().getString(R.string.switchdialog_text));
       		menu.add(0, 3, 0, getResources().getString(R.string.watchlog_text));
       		menu.add(0, 4, 0, getResources().getString(R.string.btncancel_title));
    	}
    	else
    	{
    		menu.clear();
    		longTOshort = true;
    	}
    	

    }
    
    @Override
    public boolean onContextItemSelected(MenuItem item) 
    {
        switch(item.getItemId()) 
   	    {
   	    case 1:

   	    	if(Rooted)
   	        {
   	    		JNILibrary.execCommand("kill -9 "+JNILibrary.GetProcessPID(selectedPosition)+"\n");
   	        }
   	        else
   	        {
   	        	android.os.Process.killProcess(JNILibrary.GetProcessPID(selectedPosition));
   	        	ActivityMan.restartPackage(selectedPackageName);
   	        }
   	        
   	        if(FreezeIt && FreezeTask)
   	        {
   	        	JNILibrary.doTaskStart(JNILibrary.doTaskProcess);
   	        	JNILibrary.doDataRefresh();
   	        	JNILibrary.doTaskStop();
   	        }
   	        else 
   	        {
   	        	JNILibrary.doDataRefresh();
   	        }
   	        
   	        UpdateInterface.notifyDataSetChanged();
   	        
   	        return true;
   	        
   	    case 2:

   	    	String ClassName = null;
   	        
   	    	// find ClassName
   	    	PackageManager QueryPackage = this.getPackageManager();
   	        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
   	        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER); 
   	        List<ResolveInfo> appList = QueryPackage.queryIntentActivities(mainIntent, 0);
   	        for(int i=0; i<appList.size(); i++)
   	        {
   	        	if(appList.get(i).activityInfo.applicationInfo.packageName.equals(selectedPackageName))
   	        		ClassName = appList.get(i).activityInfo.name;
   	        }
   	        
   	        if(ClassName != null)
   	        {
   	   	        Intent switchIntent = new Intent();
   	   	        switchIntent.setAction(Intent.ACTION_MAIN);
   	   	        switchIntent.addCategory(Intent.CATEGORY_LAUNCHER);
   	   	        switchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
   	   	        		   			  Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED |
   	   	        		   			  Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT);
   	   	        switchIntent.setComponent(new ComponentName(selectedPackageName, ClassName));
   	   	        startActivity(switchIntent);
   	   	        finish();
   	        }
   	        return true;
   	        
   	    case 3:
   	    	Intent WatchLog =  new Intent(this, DebugBox.class);
   	    	WatchLog.putExtra("targetPID", selectedPackagePID);
   	    	startActivity(WatchLog);
   	    	return true;
   	    }
   	    return super.onContextItemSelected(item);
 	}    
    
    @Override
    protected void onListItemClick(ListView l, View v, int position, long id)
    {
    	if(!GestureSingleTap && !GestureLong)
    	{
    		((ProcessDetailView) v).setSelected(false);
    		return;
    	}
    	
    	if(GestureLong)
    	{
    		v.performLongClick();
    		return;
    	}
    	
    	if(longTOshort)
    	{
    		longTOshort = false;
    		return;
    	}
    	
    	if(MultiSelect.isChecked())
    	{
    		((ProcessListAdapter)getListAdapter()).toggle((ProcessDetailView) v,
					position, false, MultiSelect.isChecked());
    		return;
    	}
    	
    		
    	if(shortClick == 1)
    		((ProcessListAdapter)getListAdapter()).toggle((ProcessDetailView) v,
											position, false, false);
    	else if(shortClick == 2)
		{
			shortTOlong = true;
			GestureLong = true;
			v.performLongClick();
		}
    	else if(shortClick == 3)
    		if(!((ProcessListAdapter)getListAdapter()).toggle((ProcessDetailView) v,
    		   								position, true, false))
    		{
    			shortTOlong = true;
    			GestureLong = true;
    			v.performLongClick();
    		}
    }
    
    private class ProcessListAdapter extends BaseAdapter {
    	
    	private ProcessInfoQuery ProcessInfo = null;
    	public int OrderBy = JNILibrary.doSortPID;
    	
        public ProcessListAdapter(Context context)
        {
            ProcessInfo = ProcessInfoQuery.getInstance(context);
            mContext = context;
        }

        public int getCount() {
            return JNILibrary.GetProcessCounts();
        }

        public Object getItem(int position) {
            return position;
        }
     
        public long getItemId(int position) {
            return position;
        }
 
        public View getView(int position, View convertView, ViewGroup parent) {
        	
            ProcessDetailView sv = null;

            ProcessInfo.doCacheInfo(position);

        	String OrderValue = "";
        	 
        	switch(OrderBy)
        	{
        	case 1:
        	case 2:
        	case 5:
        		OrderValue = ProcessInfo.getProcessLoad(position);
        		break;
        	case 3:
        		OrderValue = ProcessInfo.getProcessMem(position);
        		break;
        	case 4:
        		OrderValue = ProcessInfo.getProcessThreads(position);
        		break;
        	}
        	
    		Drawable DetailIcon = null;
    		if(!ProcessInfo.getExpaned(position))
        		DetailIcon = mContext.getResources().getDrawable(R.drawable.dshow);
    		else
    			DetailIcon = mContext.getResources().getDrawable(R.drawable.dclose);

    		
    		if (convertView == null && mBusy == true)
    		{
    			sv = new ProcessDetailView(mContext, ProcessInfo.getProcessPID(position),
    										ProcessInfo.getExpaned(position), position);
    		}
    		else if (convertView == null && mBusy == false) {
                sv = new ProcessDetailView(mContext, ProcessInfo.getAppIcon(position),
                							ProcessInfo.getProcessPID(position),
                							ProcessInfo.getPackageName(position),
                							OrderValue,
        	        						ProcessInfo.getAppInfo(position), 
        	        						ProcessInfo.getExpaned(position),
	               							position,
	               							DetailIcon);
            } 
            else if (mBusy == true)
            {
                sv = (ProcessDetailView)convertView;
            	sv.setView(ProcessInfo.getProcessPID(position), position);
                
                sv.setContext("");
                sv.setExpanded(ProcessInfo.getExpaned(position));
                sv.setMultiSelected(ProcessInfo.getSelected(position));
            }
            else
            {
                sv = (ProcessDetailView)convertView;
               	sv.setView( ProcessInfo.getAppIcon(position), 
               				 ProcessInfo.getProcessPID(position),
               				 ProcessInfo.getPackageName(position),
               				 OrderValue,
               				 position,
               				 DetailIcon);
                
                sv.setContext(ProcessInfo.getAppInfo(position));
                sv.setExpanded(ProcessInfo.getExpaned(position));
                sv.setMultiSelected(ProcessInfo.getSelected(position));
        	}
            
           	return sv;
        }

        public boolean toggle(ProcessDetailView v, int position, boolean split, boolean multi) {

    		if(multi)
    		{
    			if(ProcessInfo.getSelected(position))
    				ProcessInfo.setSelected(position, false);
    			else
    				ProcessInfo.setSelected(position, true);
    			
            	notifyDataSetChanged();
            	
            	return false;
    		}

        	if(v.checkClick() != 1 && split == true) 
        	{
        		return false;
        	}
        	else
        	{
            	if(ProcessInfo.getExpaned(position))
            		ProcessInfo.setExpaned(position, false);
            	else
            		ProcessInfo.setExpaned(position, true);
        	}

        	notifyDataSetChanged();
        	
        	return true;
        }
        
        private Context mContext;
    }
    
    private class ProcessDetailView extends TableLayout {
    	
    	private TableRow TitleRow;
    	private TextView PIDField;
    	private ImageView IconField;
    	private TextView NameField;
    	private ImageView DetailField;
    	private TextView ValueField;
    	private TextView AppInfoField;
    	
    	private boolean Expanded = false;

        public ProcessDetailView(Context context, Drawable Icon, int PID, String Name,
        						 String Value, String AppInfo, boolean expanded, int position,
        						 Drawable DetailIcon) {
            super(context);
            this.setColumnStretchable(2, true);
            
            //this.setOrientation(VERTICAL);
            
            PIDField = new TextView(context);
            IconField = new ImageView(context);  
            NameField = new TextView(context);

            ValueField = new TextView(context);
            AppInfoField = new TextView(context);
            DetailField = new ImageView(context);

            DetailField.setImageDrawable(DetailIcon);
            DetailField.setPadding(3, 3, 3, 3);
            

            PIDField.setText(""+PID);

           	IconField.setImageDrawable(Icon);
           	IconField.setPadding(8, 3, 3, 3);
            
            NameField.setText(Name);
	     	ValueField.setText(Value);

            PIDField.setGravity(Gravity.LEFT);
            PIDField.setPadding(3, 3, 3, 3);
            if(CompareFunc.getScreenSize() == 2)
            	PIDField.setWidth(90);
            else if(CompareFunc.getScreenSize() == 0)
            	PIDField.setWidth(35);
            else
            	PIDField.setWidth(55);

            NameField.setPadding(3, 3, 3, 3);
            NameField.setGravity(Gravity.LEFT);
            NameField.setWidth(getWidth()- IconField.getWidth() - DetailField.getWidth() - 115);

            ValueField.setPadding(3, 3, 8, 3);

            if(CompareFunc.getScreenSize() == 2)
            	ValueField.setWidth(80);
            else if (CompareFunc.getScreenSize() == 0)
            	ValueField.setWidth(35);
            else
            	ValueField.setWidth(50);
            
            TitleRow = new TableRow(context);
            TitleRow.addView(PIDField);
            TitleRow.addView(IconField);
            TitleRow.addView(NameField);
            TitleRow.addView(ValueField);
            TitleRow.addView(DetailField);
            addView(TitleRow);

	     	AppInfoField.setText(AppInfo);
            addView(AppInfoField);
	     	AppInfoField.setVisibility(expanded ? VISIBLE : GONE);
	     	
	     	if(position % 2 == 0)
	     		setBackgroundColor(0x80444444);
	     	else
	     		setBackgroundColor(0x80000000);

        }
        
        public ProcessDetailView(Context context, int PID, boolean expanded,int position) {
        	
        	super(context);
        	this.setColumnStretchable(2, true);

        	PIDField = new TextView(context);
        	IconField = new ImageView(context);  
        	NameField = new TextView(context);

        	ValueField = new TextView(context);
        	AppInfoField = new TextView(context);
        	DetailField = new ImageView(context);

        	DetailField.setImageDrawable(null);
            DetailField.setPadding(3, 3, 3, 3);
            
           	IconField.setImageDrawable(null);
        	IconField.setPadding(8, 3, 3, 3);

			PIDField.setText(""+PID);
        	PIDField.setGravity(Gravity.LEFT);
        	PIDField.setPadding(3, 3, 3, 3);

        	if(CompareFunc.getScreenSize() == 2)
        		PIDField.setWidth(90);
        	else if(CompareFunc.getScreenSize() == 0)
        		PIDField.setWidth(35);
        	else
        		PIDField.setWidth(55);

        	NameField.setPadding(3, 3, 3, 3);
        	NameField.setGravity(Gravity.LEFT);
        	NameField.setWidth(getWidth()- IconField.getWidth() - DetailField.getWidth() - 115);

        	ValueField.setPadding(3, 3, 8, 3);

        	if(CompareFunc.getScreenSize() == 2)
        		ValueField.setWidth(80);
        	else if (CompareFunc.getScreenSize() == 0)
        		ValueField.setWidth(35);
        	else
        		ValueField.setWidth(50);

        	NameField.setText("Loading");
        	
        	TitleRow = new TableRow(context);
        	TitleRow.addView(PIDField);
        	TitleRow.addView(IconField);
        	TitleRow.addView(NameField);
        	TitleRow.addView(ValueField);
        	TitleRow.addView(DetailField);
        	addView(TitleRow);

        	addView(AppInfoField);
        	AppInfoField.setVisibility(expanded ? VISIBLE : GONE);

        	if(position % 2 == 0)
        		setBackgroundColor(0x80444444);
        	else
        		setBackgroundColor(0x80000000);

        }


        public void setContext(String AppInfo) {
       		AppInfoField.setText(AppInfo);
		}

		public void setView( Drawable Icon, int PID, String Name, String Value, int position,
							  Drawable DetailIcon) {

			IconField.setImageDrawable(Icon);
			DetailField.setImageDrawable(DetailIcon);
			PIDField.setText(""+PID);
			NameField.setText(Name);
			ValueField.setText(Value);

			if(position % 2 == 0)
				setBackgroundColor(0x80444444);
			else
				setBackgroundColor(0x80000000);
    	}
		
		public void setView(int PID, int position) {

			IconField.setImageDrawable(null);
			DetailField.setImageDrawable(null);
//			IconField.setVisibility(View.GONE);
//			DetailField.setVisibility(View.INVISIBLE);
			PIDField.setText(""+PID);
			NameField.setText("Loading");
			ValueField.setText("");
			
			if(position % 2 == 0)
				setBackgroundColor(0x80444444);
			else
				setBackgroundColor(0x80000000);
		}

        /**
         * Convenience method to expand or hide the dialogue
         */
        public void setExpanded(boolean expanded) {
        	AppInfoField.setVisibility(expanded ? VISIBLE : GONE);
        }
        
        public void setMultiSelected(boolean selected) {
        	if(selected)
        		setBackgroundColor(0x803CC8FF);
        }
        
		public boolean onTouchEvent(MotionEvent event)
		{
			if(event.getX() > getWidth()/3*2 )
				Expanded = true;
			else if (event.getX() <= getWidth()/3*2 )
				Expanded = false;

			return super.onTouchEvent(event);
		}
        
        public int checkClick()
        {
        	if(Expanded == true)
        	{ 
        		Expanded = false;
        		return 1;
        	} 
        	return 0;
        }
        
    }

	@Override
	public void onScroll(AbsListView view, int firstVisibleItem,
			int visibleItemCount, int totalItemCount) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onScrollStateChanged(AbsListView view, int scrollState) {
		// TODO Auto-generated method stub
		switch (scrollState) {
			case OnScrollListener.SCROLL_STATE_IDLE:
	            mBusy = false;

	            if(SlowAdapter)
				{
		            int count = view.getChildCount();
		            for (int i=0; i<count; i++) {
		            	view.getChildAt(i).refreshDrawableState();
		            }
				}
	            break;
	        case OnScrollListener.SCROLL_STATE_TOUCH_SCROLL:
	        case OnScrollListener.SCROLL_STATE_FLING:
	        	if(SlowAdapter)
	        		mBusy = true;
	        	else
	        		mBusy = false;
	            break;
        }
	}

}

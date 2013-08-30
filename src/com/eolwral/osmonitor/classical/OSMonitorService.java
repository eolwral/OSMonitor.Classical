package com.eolwral.osmonitor.classical;

import java.text.DecimalFormat;

import com.eolwral.osmonitor.classical.preferences.Preferences;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;

public class OSMonitorService extends Service
{
	private static final int NOTIFYID = 20091231;
	private static int battLevel = 0;  // percentage, or -1 for unknown
	private static int temperature = 0;
	private static int useColor = 0;
	private static boolean useCelsius = true;
    private NotificationManager serviceNM = null;
	private Notification serviceNotify = null;
	private Context serviceContext = null;

	private boolean TimeUpdate = false;
	private int UpdateInterval = 2;
	
	private static JNIInterface JNILibrary = JNIInterface.getInstance();;

	private static OSMonitorService single = null;

	public static OSMonitorService getInstance()
	{
		if(single != null)
			return single;
		return null;
	}
	
	public class OSMonitorBinder extends Binder 
	{
		OSMonitorService getService()
		{
			return OSMonitorService.this;
		}
	}
	
	private final IBinder mBinder = new OSMonitorBinder();

	private static DecimalFormat MemoryFormat = new DecimalFormat(",000");
	
	private static int cpuLoad = 0;
	
	private Handler mHandler = new Handler();
	private Runnable mRefresh = new Runnable() 
	{
		@Override  
            public void run() {

            	cpuLoad = JNILibrary.GetCPUUsageValue();
				if(cpuLoad < 20)
					serviceNotify.iconLevel = 1+useColor*100;
				else if(cpuLoad < 40)
					serviceNotify.iconLevel = 2+useColor*100;
				else if(cpuLoad < 60)
					serviceNotify.iconLevel = 3+useColor*100;
				else if(cpuLoad < 80)
					serviceNotify.iconLevel = 4+useColor*100;
				else if(cpuLoad < 100)
					serviceNotify.iconLevel = 5+useColor*100;
				else 
					serviceNotify.iconLevel = 6+useColor*100;
				
				String maininfo = "CPU: "+cpuLoad+"% , "
									 +"MEM:"+MemoryFormat.format(JNILibrary.GetMemBuffer()+JNILibrary.GetMemCached()+JNILibrary.GetMemFree())+ "K"; 

				String extendinfo = "";
				if(useCelsius)
					extendinfo = " Battery: "+battLevel+"%"+" ("+temperature/10+"°C)";
				else
					extendinfo = " Battery: "+battLevel+"%"+" ("+((int)temperature/10*9/5+32)+"°F)";


				
				serviceNotify.setLatestEventInfo(serviceContext, maininfo,
												 extendinfo, serviceNotify.contentIntent);
				
/*				serviceNotify.contentView.setTextViewText(R.id.StatusBarCPU, "CPU: "+cpuLoad+"%");
				serviceNotify.contentView.setTextViewText(R.id.StatusBarMEM, "MEM: "+MemoryFormat.format(JNILibrary.GetMemBuffer()+JNILibrary.GetMemCached()+JNILibrary.GetMemFree())+ "K");

				if(useCelsius)
					serviceNotify.contentView.setTextViewText(R.id.StatusBarBAT, "BAT: "+battLevel+"%"+" ("+temperature/10+"°C)");
				else
					serviceNotify.contentView.setTextViewText(R.id.StatusBarBAT, "BAT: "+battLevel+"%"+" ("+((int)temperature/10*9/5+32)+"°F)");
	*/			

				try
				{
					serviceNM.notify(NOTIFYID, serviceNotify);
				} catch(Exception e) {}

				mHandler.postDelayed(mRefresh, UpdateInterval * 1000);
            }
    };

    @Override
    public void onCreate() {
    	serviceNM = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
    	InitNotification();
    	Notify();

    	single = this;
    }
    
    @Override
    public void onDestroy() {
    	Disable();
    }

    public void Notify()
    {
    	Enable();
    }
    
	
    private void Enable()
    {
    	if(!mRegistered)
    	{
    		IntentFilter filterScreenON = new IntentFilter(Intent.ACTION_SCREEN_ON);
    		registerReceiver(mReceiver, filterScreenON);

    		IntentFilter filterScreenOFF = new IntentFilter(Intent.ACTION_SCREEN_OFF);
    		registerReceiver(mReceiver, filterScreenOFF);
    		
    		mRegistered = true;
    	}
    	
		// load settings
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);

		try {
			UpdateInterval = Integer.parseInt(settings.getString(Preferences.PREF_UPDATE, "2"));
		} catch(Exception e) {}		
		
		if(settings.getBoolean(Preferences.PREF_CPUUSAGE, false))
		{
			if(TimeUpdate == false)
			{
				JNILibrary.doCPUUpdate(1);
				mHandler.postDelayed(mRefresh, UpdateInterval * 1000);
				TimeUpdate = true;
			}
		}
		else
		{
			if(TimeUpdate == true)
			{
	    		JNILibrary.doCPUUpdate(0);
	    		mHandler.removeCallbacks(mRefresh);
	    		TimeUpdate = false;
			}
			serviceNotify.iconLevel = 0;
			serviceNM.notify(NOTIFYID, serviceNotify);
		}
		
		useCelsius = settings.getBoolean(Preferences.PREF_TEMPERATURE, true);
		useColor =  Integer.parseInt(settings.getString(Preferences.PREF_STATUSBARCOLOR, "0"));
		
		startBatteryMonitor();
    }
    
    private void Disable()
    {
    	serviceNM.cancel(NOTIFYID);
    	
    	if(TimeUpdate)
    	{
    		JNILibrary.doCPUUpdate(0);
    		mHandler.removeCallbacks(mRefresh);
    		TimeUpdate = false;
    	}

    	if(mRegistered)
    	{
    		unregisterReceiver(mReceiver);
    		mRegistered = false;
    	}
    	
    	stopBatteryMonitor();
    }
    
    private void startBatteryMonitor()
    {
    	IntentFilter battFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
    	registerReceiver(battReceiver, battFilter);		        		
    }
    
    private void stopBatteryMonitor()
    {
    	unregisterReceiver(battReceiver);
    }

	private static BroadcastReceiver battReceiver = new BroadcastReceiver() 
	{
		public void onReceive(Context context, Intent intent) {
			
			int rawlevel = intent.getIntExtra("level", -1);
			int scale = intent.getIntExtra("scale", -1);
			
			temperature = intent.getIntExtra("temperature", -1);

			if (rawlevel >= 0 && scale > 0) {
				battLevel = (rawlevel * 100) / scale;
			}
		}
	};

    private boolean mRegistered = false;
    private BroadcastReceiver mReceiver = new BroadcastReceiver() 
    {
    	public void onReceive(Context context, Intent intent) {
    		if(intent.getAction().equals(Intent.ACTION_SCREEN_OFF))
    		{
    	    	if(TimeUpdate)
    	    	{
    	    		JNILibrary.doCPUUpdate(0);
    	    		mHandler.removeCallbacks(mRefresh);
    	    		TimeUpdate = false;
    	    	}
    		}
    		else if (intent.getAction().equals(Intent.ACTION_SCREEN_ON))
    		{
    			// load settings
    			SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);

    			if(settings.getBoolean(Preferences.PREF_CPUUSAGE, false))
    			{
    				if(TimeUpdate == false)
    				{
    					JNILibrary.doCPUUpdate(1);
    					mHandler.postDelayed(mRefresh, UpdateInterval * 1000);
    					TimeUpdate = true;
    				}
    			}
    		}
    	}
    }; 
     
    @Override
    public IBinder onBind(Intent intent) {
            return mBinder;
    }
	
    private void InitNotification() 
    {
	    int thisIcon = R.anim.statusicon;        		// icon from resources
	    long thisTime = System.currentTimeMillis();     // notification time
	    
	    serviceContext = this; 
	    CharSequence tickerText = getResources().getString(R.string.bar_title);
	    CharSequence contentText =  getResources().getString(R.string.notify_text);
	    CharSequence contentTitle = getResources().getString(R.string.app_title);
	    
	    serviceNotify = new Notification(thisIcon, tickerText, thisTime);
	    serviceNotify.flags |= Notification.FLAG_NO_CLEAR|Notification.FLAG_ONGOING_EVENT;

	    //RemoteViews contentView = new RemoteViews(getPackageName(), R.layout.notificationlayout);
    	//contentView.setImageViewResource(R.id.image, R.drawable.appicon);
    	//contentView.setTextViewText(R.id.StatusBarCPU, "Wait..");
    	//serviceNotify.contentView = contentView;

    	Intent notificationIntent = new Intent(this, OSMonitor.class);
    	notificationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
    	PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
    	serviceNotify.contentIntent = contentIntent;

	    serviceNotify.setLatestEventInfo(this, contentTitle, contentText, contentIntent);

    	serviceNM.notify(NOTIFYID, serviceNotify);
    }

}

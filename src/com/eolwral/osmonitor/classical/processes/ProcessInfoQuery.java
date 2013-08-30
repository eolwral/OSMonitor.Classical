package com.eolwral.osmonitor.classical.processes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.concurrent.Semaphore;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import com.eolwral.osmonitor.classical.*;

public class ProcessInfoQuery extends Thread
{
	private static JNIInterface JNILibrary = JNIInterface.getInstance();
	
	private static ProcessInfoQuery singletone = null;
	private static PackageManager AppInfo = null;
	private static Resources  ResInfo = null;
	
	public static ProcessInfoQuery getInstance(Context context)
	{
		if(singletone == null)
		{
			singletone = new ProcessInfoQuery();
            AppInfo = context.getPackageManager();
            ResInfo = context.getResources();
            singletone.start();
		}
		
		return singletone;
	}
	
	class ProcessInstance
	{
		public String Name;
		public Drawable Icon;
		public String Package;
	}
	
    private final HashMap<String, Boolean> CacheExpaned = new HashMap<String, Boolean>();
    private final HashMap<String, Boolean> CacheSelected = new HashMap<String, Boolean>();
	private final HashMap<String, ProcessInstance> ProcessCache = new HashMap<String, ProcessInstance>();
    
	public void doCacheInfo(int position)
	{
		ProcessInstance CacheInstance = ProcessCache.get(JNILibrary.GetProcessName(position));
		if(CacheInstance != null)
			return;
		
		try {
			QueryQueueLock.acquire();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		QueryQueue.add(new WaitCache(JNILibrary.GetProcessName(position),
				JNILibrary.GetProcessOwner(position), JNILibrary.GetProcessUID(position)));
		QueryQueueLock.release();
		
		CacheInstance = new ProcessInstance();
		CacheInstance.Name = JNILibrary.GetProcessName(position);
		ProcessCache.put(JNILibrary.GetProcessName(position),
					      CacheInstance);
		
		return;
	}

	private class WaitCache
	{
		private final String ItemName;
		private final String ItemOwner;
		private final int ItemUID;
		public WaitCache(String Name, String Owner, int UID)
		{
			ItemName = Name;
			ItemOwner = Owner;
			ItemUID = UID;
		}
		
		public String getName()
		{
			return ItemName;
		}

		public String getOwner()
		{
			return ItemOwner;
		}
		
		public int getUID()
		{
			return ItemUID;
		}
	}
    private static LinkedList<WaitCache> QueryQueue = new LinkedList<WaitCache>();
	private final Semaphore QueryQueueLock = new Semaphore(1, true);
    
	
	@Override 
	public void run()
	{
 
		while(true)
		{
			if(!getCacheInfo())
			{
				try {
					sleep(500);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
	}
	
	public boolean getCacheInfo()
	{
		if(QueryQueue.isEmpty())
			return false;
		
		try {
			QueryQueueLock.acquire();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		WaitCache SearchObj = QueryQueue.remove();

		QueryQueueLock.release();
		
		PackageInfo appPackageInfo = null;
		String PackageName = null;
		if(SearchObj.getName().contains(":"))
			PackageName = SearchObj.getName().substring(0,
								SearchObj.getName().indexOf(":"));
		else
			PackageName = SearchObj.getName();
		
		// for system user
		if(SearchObj.getOwner().contains("system") &&
						SearchObj.getName().contains("system"))
			PackageName = "android";
		
		try {  
			appPackageInfo = AppInfo.getPackageInfo(PackageName, 0);
		} catch (NameNotFoundException e) {}
		
		if(appPackageInfo == null && SearchObj.getUID() >0)
		{
			String[] subPackageName = AppInfo.getPackagesForUid(SearchObj.getUID());
				
			if(subPackageName != null)
			{
				for(int PackagePtr = 0; PackagePtr < subPackageName.length; PackagePtr++)
				{
					if (subPackageName[PackagePtr] == null)
						continue;
					
					try {  
						appPackageInfo = AppInfo.getPackageInfo(subPackageName[PackagePtr], 0);
						PackagePtr = subPackageName.length;
					} catch (NameNotFoundException e) {}						
				}
			}
		}
		
		ProcessInstance CacheInstance = new ProcessInstance();
		
		CacheInstance.Package = PackageName;
	
		if(appPackageInfo != null)
		{  
			CacheInstance.Name = appPackageInfo.applicationInfo.loadLabel(AppInfo).toString();
			CacheInstance.Icon = resizeImage(appPackageInfo.applicationInfo.loadIcon(AppInfo));
		}
		else if(PackageName.equals("System"))
		{ 
			CacheInstance.Name = PackageName;
			CacheInstance.Icon = resizeImage(ResInfo.getDrawable(R.drawable.system));
		}
		else
			CacheInstance.Name = PackageName;
		
		ProcessCache.put(SearchObj.getName(), CacheInstance);
		
		return true;
	}
	
	public Boolean getExpaned(int position)
	{
		Boolean Flag = CacheExpaned.get(JNILibrary.GetProcessPID(position)+"");
		if(Flag == null)
			Flag = false;
		
		return Flag;
	}
	
	public void setExpaned(int position, Boolean Flag)
	{
//		ProcessInstance CacheInstance = ProcessCache.get(JNILibrary.GetProcessName(position));
	//	CacheInstance.Expaned = Flag;
		//ProcessCache.put(JNILibrary.GetProcessName(position), CacheInstance);
		CacheExpaned.put(JNILibrary.GetProcessPID(position)+"", Flag);
		return;
	}
	
	public Boolean getSelected(int position)
	{
		Boolean Flag = CacheSelected.get(JNILibrary.GetProcessPID(position)+"");
		if(Flag == null)
			Flag = false;
		
		return Flag;
	}
	
	public void setSelected(int position, Boolean Flag)
	{
		CacheSelected.put(JNILibrary.GetProcessPID(position)+"", Flag);
		return;
	}
	
	public ArrayList<String> getSelected()
	{
		ArrayList<String> selectPID = new ArrayList<String>();
        Iterator<String> It = CacheSelected.keySet().iterator();
        while (It.hasNext())
        {
        	String cacheKey = (String) It.next();
        	if(CacheSelected.get(cacheKey) == true)
        		selectPID.add(cacheKey);
        }
        
        return selectPID;
	}
	
	public void clearSelected()
	{
		CacheSelected.clear();
		return;
	}

	
	public String getPackageName(int position) 
	{
		return ProcessCache.get(JNILibrary.GetProcessName(position)).Name;
	}

	public String getPacakge(int position)
	{
		return ProcessCache.get(JNILibrary.GetProcessName(position)).Package;
	}
	
	public int getProcessPID(int position)
	{
		return JNILibrary.GetProcessPID(position);
	}
	
	public String getProcessThreads(int position)
	{
		return JNILibrary.GetProcessThreads(position)+"";
	}

	public String getProcessLoad(int position)
	{
		return JNILibrary.GetProcessLoad(position)+"%";
	}

	public String getProcessMem(int position)
	{
		if(JNILibrary.GetProcessRSS(position) > 1024) 
			return (JNILibrary.GetProcessRSS(position)/1024)+"M";
		return JNILibrary.GetProcessRSS(position)+"K";
	}
	
	
	private StringBuilder appbuf = new StringBuilder();
	public String getAppInfo(int position) {
		appbuf.setLength(0);
		
		if(JNILibrary.GetProcessRSS(position) > 1024) {
			appbuf.append("\tProcess: ")
				  .append(JNILibrary.GetProcessName(position))
			      .append("\n\tMemory: ")
				  .append(JNILibrary.GetProcessRSS(position)/1024)
				  .append("M\t  Thread: ")
				  .append(JNILibrary.GetProcessThreads(position))
				  .append("\t  Load: ")
				  .append(JNILibrary.GetProcessLoad(position))
				  .append("%\n\tSTime: ")
				  .append(JNILibrary.GetProcessSTime(position))
				  .append("\t  UTime: ")
				  .append(JNILibrary.GetProcessUTime(position))
				  .append("\n\tUser: ")
				  .append(JNILibrary.GetProcessOwner(position))
				  .append("\t  Status: ");
		}
		else {
			appbuf.append("\tProcess: ")
				  .append(JNILibrary.GetProcessName(position))
				  .append("\n\tMemory: ")
				  .append(JNILibrary.GetProcessRSS(position))
				  .append("K\t  Threads: ")
				  .append(JNILibrary.GetProcessThreads(position))
				  .append("\t  Load: ")
				  .append(JNILibrary.GetProcessLoad(position))
				  .append("%\n\tSTime: ")
				  .append(JNILibrary.GetProcessSTime(position))
				  .append("\t  UTime: ")
				  .append(JNILibrary.GetProcessUTime(position))
				  .append("\n\tUser: ")
				  .append(JNILibrary.GetProcessOwner(position))
				  .append("\t  Status: ");				  
		}
		
		String Status = JNILibrary.GetProcessStatus(position).trim();
		if(Status.compareTo("Z") == 0)
			appbuf.append("Zombie");
		else if(Status.compareTo("S") == 0)
			appbuf.append("Sleep");
		else if(Status.compareTo("R") == 0)
			appbuf.append("Running");
		else if(Status.compareTo("D") == 0)
			appbuf.append("Wait IO");
		else if(Status.compareTo("T") == 0)
			appbuf.append("Stop");
		else 
			appbuf.append("Unknown");

		return appbuf.toString();
	}
	
	public Drawable getAppIcon(int position) 
	{
		return ProcessCache.get(JNILibrary.GetProcessName(position)).Icon;
	}
	
	private Drawable resizeImage(Drawable Icon) {

		if(CompareFunc.getScreenSize() == 2)
		{
			Bitmap BitmapOrg = Bitmap.createBitmap(60, 60, Bitmap.Config.ARGB_8888); 
			Canvas BitmapCanvas = new Canvas(BitmapOrg);
			Icon.setBounds(0, 0, 60, 60);
			Icon.draw(BitmapCanvas); 
	        return new BitmapDrawable(BitmapOrg);
		}
		else if (CompareFunc.getScreenSize() == 0)
		{
			Bitmap BitmapOrg = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888); 
			Canvas BitmapCanvas = new Canvas(BitmapOrg);
			Icon.setBounds(0, 0, 10, 10);
			Icon.draw(BitmapCanvas); 
	        return new BitmapDrawable(BitmapOrg);
		}
		else
		{
			Bitmap BitmapOrg = Bitmap.createBitmap(22, 22, Bitmap.Config.ARGB_8888); 
			Canvas BitmapCanvas = new Canvas(BitmapOrg);
			Icon.setBounds(0, 0, 22, 22);
			Icon.draw(BitmapCanvas); 
	        return new BitmapDrawable(BitmapOrg);
		}
    }
	
}

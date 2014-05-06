package com.red_folder.phonegap.plugin.backgroundservice;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.Date;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.util.Log;
import android.support.v4.app.NotificationCompat;
import com.red_folder.phonegap.plugin.backgroundservice.BackgroundService;

public class MyService extends BackgroundService {
	
	private final static String TAG = MyService.class.getSimpleName();
	private String tag = "itinerary";
	private JSONObject itinerary;
	private String mode;
	private String busNum;
	private long departureTime;
	private long arrivingTime;
	private int nextLegIndex=0;
	private LocationManager locationManager;
	private String provider;
	private Double destLat=0.0;
	private Double destLon=0.0;
	private String srcStop;
	private String dstStop;
	private boolean dep_ntf_triggered=false;
	private boolean arr_ntf_triggered=false;
	

	@Override
	protected JSONObject doWork() {
		JSONObject result = new JSONObject();
		long ctime = new Date().getTime();
		Log.d(TAG, "currentTime:"+ctime+";departureTime:"+departureTime+";arrivingTime:"+arrivingTime);
		
		if(!dep_ntf_triggered && departureTime!=0 && departureTime>ctime){
			notifyDeparture(departureTime, ctime);
		}else if(!arr_ntf_triggered && arrivingTime!=0 && arrivingTime>ctime){
			notifyArriving(arrivingTime, ctime);
		}else if(arrivingTime<ctime){
			reset();
		}
		return result;	
	}
    //fire arrival alert 20 min before bus arrival
	private void notifyArriving(long arrivingTime2, long ctime) {
		if(arrivingTime2-ctime<=1200000 && arrivingTime2-ctime>0){
			if(isInRange(destLat, destLon)){
				Log.d(TAG, "triggering arriving notification");
				double minleft=(arrivingTime2-ctime)/60000.0;
				triggerNotification(false,minleft);
				arr_ntf_triggered=true;
				updateNextBusTimes(this.itinerary);
			}
			
		}
		
	}
    //fire departure alert 20 min before bus departure
	private void notifyDeparture(long departureTime2, long ctime) {
		if(departureTime2-ctime<=1200000 && departureTime2-ctime>0){
			Log.d(TAG, "triggering departure notification.");
			double minleft=(departureTime2-ctime)/60000.0;
			triggerNotification(true,minleft);
			dep_ntf_triggered=true;
		}
	}

	
	private void triggerNotification(boolean isDeparture, double minleft) {
		String title, contentText;
		int mId;
		if(isDeparture){
			title = mode + " " + busNum + " departing";
			contentText = "in "+String.format("%.1f", minleft)+" min from "+srcStop;
			mId=0;
		}else{
			title = mode + " " + busNum + " arriving";
			contentText = "in "+String.format("%.1f", minleft)+" min to "+dstStop;
			mId=1;
		}
		Intent intent = new Intent();
		PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(), 0, intent, Intent.FLAG_ACTIVITY_NEW_TASK);
		
		Notification noti = new NotificationCompat.Builder(this)
        .setContentTitle(title)
        .setContentText(contentText)
        .setContentIntent(pendingIntent)
        .setTicker("HSL alert")
        .setAutoCancel(false)
        .setSmallIcon(getResources().getIdentifier("icon","drawable", getPackageName())).build();
		NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		noti.defaults |= Notification.DEFAULT_ALL;
		notificationManager.notify(mId, noti);
	}
	
	// distance < 10km means in-range
	private boolean isInRange(Double lat, Double lon){
		if(lat==0.0 && lon==0.0){
			return false;
		}
		// Get the location manager
	    locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
	    // Define the criteria how to select the locatioin provider -> use
	    // default
	    Criteria criteria = new Criteria();
	    provider = locationManager.getBestProvider(criteria, true);
	    Location location = locationManager.getLastKnownLocation(provider);
	    if(location==null){
	    	return true;
	    }
	    float[] res = new float[1];
	    Location.distanceBetween(location.getLatitude(), location.getLongitude(), lat, lon, res);
	    return res[0]<10000;
	}

	@Override
	protected JSONObject getConfig() {
		JSONObject result = new JSONObject();
		
		try {
			if(itinerary!=null){
				result.put(tag, this.itinerary);
				Log.d(TAG, "itinerary.duration: " + itinerary.getLong("duration"));
			}	
		} catch (JSONException e) {
		}
		
		return result;
	}

	@Override
	protected void setConfig(JSONObject config) {
		try {
			if (config.has(tag)){
				this.itinerary = config.getJSONObject(tag);
				Log.d(TAG, "setConfig is triggerred. itinerary.duration: " + itinerary.get("duration").toString());
				this.nextLegIndex=0;
				this.updateNextBusTimes(itinerary);
			}else{
				Log.d(TAG, "invalid config data");
			}
		} catch (JSONException e) {
			Log.d(TAG,e.getMessage());
			disableServiceTimer();
		}
		
	}     

	@Override
	protected JSONObject initialiseLatestResult() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	protected void onTimerEnabled() {
		// TODO Auto-generated method stub
		
	}

	@Override
	protected void onTimerDisabled() {
		// TODO Auto-generated method stub
		
	}

	/**
	 * update the departure time and arriving time
	 * @param itinerary
	 */
	private void updateNextBusTimes(JSONObject itinerary){
		JSONArray legs;
		long ctime = new Date().getTime();
		long it_endTime;
		try {
			it_endTime = itinerary.getLong("endTime");
			if(it_endTime<ctime){
				Log.d(TAG, "it_endTime:"+it_endTime+"<ctime"+ctime);
				reset();
				return;
			}
			
			legs = itinerary.getJSONArray("legs");
			for(; nextLegIndex< legs.length(); nextLegIndex++){
				JSONObject leg = legs.getJSONObject(nextLegIndex);
				mode = leg.getString("mode");
				Log.d(TAG, "LegIndex:"+nextLegIndex+",mode:"+mode);
				long startTime=leg.getLong("startTime");
				long endTime=leg.getLong("endTime");
				if(endTime<ctime || mode.equals("WALK") || mode.equals("WAIT")){
					continue;
				}
				busNum=leg.getString("route");
				JSONObject src = leg.getJSONObject("from");
				JSONObject dst = leg.getJSONObject("to");
				srcStop=src.getString("name");
				dstStop=dst.getString("name");
				destLon=dst.getDouble("lon");
				destLat=dst.getDouble("lat");
				Log.d(TAG, "destLat:"+destLat+";destLon:"+destLon);
				if(startTime>ctime){
					departureTime=startTime;
					arrivingTime=endTime;
					dep_ntf_triggered=false;
					arr_ntf_triggered=false;
					nextLegIndex++;
					return;
				}else{
					/*when startTime<=ctime and endTime >= ctime,
					 *  it is when user already on bus*/
					departureTime=0;
					arrivingTime=endTime;
					arr_ntf_triggered=false;
					nextLegIndex++;
					return;
				}
			}
			/*the itinerary is parsed up, reset to initial state*/
			reset();
			
		} catch (JSONException e) {
			Log.d(TAG, "updateNextBusTimes:"+e.getMessage());
		}
		
	}
	
	private void reset(){
		this.departureTime=0;
		this.arrivingTime=0;
		this.busNum="NULL";
		this.destLat=0.0;
		this.destLon=0.0;
		dep_ntf_triggered=false;
		arr_ntf_triggered=false;
		nextLegIndex=0;
		disableServiceTimer();
	}

	private void disableServiceTimer() {
		super._disableTimer();	
	}

}



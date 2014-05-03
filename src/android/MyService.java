package com.red_folder.phonegap.plugin.backgroundservice;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.Date;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.util.Log;
import com.red_folder.phonegap.plugin.backgroundservice.BackgroundService;

public class MyService extends BackgroundService {
	
	private final static String TAG = MyService.class.getSimpleName();
	private String tag = "itinerary";
	private JSONObject itinerary;
	private String busNum;
	private long departureTime;
	private long arrivingTime;
	private int nextLegIndex=0;
	private LocationManager locationManager;
	private String provider;
	private Double destLat=0.0;
	private Double destLon=0.0;
	private boolean dep_ntf_triggered=false;
	private boolean arr_ntf_triggered=false;
	

	@Override
	protected JSONObject doWork() {
		JSONObject result = new JSONObject();
		long ctime = new Date().getTime();
		Log.d(TAG, "currentTime:"+ctime+";departureTime:"+departureTime+";arrivingTime:"+arrivingTime);
		
		if(!dep_ntf_triggered && departureTime!=0 && departureTime>ctime){
			notifyDeparture(departureTime, ctime);
			return null;
		}
		if(!arr_ntf_triggered && arrivingTime!=0 && arrivingTime>ctime){
			notifyArriving(arrivingTime, ctime);
			return null;
		}
		return result;	
	}

	private void notifyArriving(long arrivingTime2, long ctime) {
		if(arrivingTime2-ctime<=300000 && arrivingTime2-ctime>0){
			if(isInRange(destLat, destLon)){
				Log.d(TAG, "triggering arriving notification.");
				triggerDepartureNotification(busNum);
				arr_ntf_triggered=true;
			}
			
		}
		
	}

	private void notifyDeparture(long departureTime2, long ctime) {
		if(departureTime2-ctime<=600000 && departureTime2-ctime>0){
			Log.d(TAG, "triggering departure notification.");
			triggerDepartureNotification(busNum);
			dep_ntf_triggered=true;
		}
	}

	@SuppressLint("NewApi")
	private void triggerDepartureNotification(String busNum2) {
		Notification noti = new Notification.Builder(this)
        .setContentTitle("Bus"+busNum2 + "will arrived in a minute")
        .setContentText("Subject").setSmallIcon(getResources().getIdentifier("icon","drawable", getPackageName())).build();
		NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		noti.flags |= Notification.DEFAULT_ALL;
		notificationManager.notify(0, noti);
	}
	
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
	    
	    float[] res = new float[1];
	    Location.distanceBetween(location.getLatitude(), location.getLongitude(), lat, lon, res);
	    return res[0]<1000;
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
			if (config.has(tag))
				this.itinerary = config.getJSONObject(tag);
				Log.d(TAG, "setConfig is triggerred. itinerary.duration: " + itinerary.get("duration").toString());
				this.updateNextBusTimes(itinerary);
		} catch (JSONException e) {
			Log.d(TAG,e.getMessage());
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
				resetBus();
				return;
			}
			
			legs = itinerary.getJSONArray("legs");
			for(; nextLegIndex< legs.length(); nextLegIndex++){
				JSONObject leg = legs.getJSONObject(nextLegIndex);
				String mode = leg.getString("mode");
				Log.d(TAG, "LegIndex:"+nextLegIndex+",mode:"+mode);
				long startTime=leg.getLong("startTime");
				long endTime=leg.getLong("endTime");
				if(endTime<ctime || !mode.equals("BUS")){
					resetBus();
					continue;
				}
				busNum=leg.getString("route");
				JSONObject dest = leg.getJSONObject("to");
				destLon=dest.getDouble("lon");
				destLat=dest.getDouble("lat");
				Log.d(TAG, "destLat:"+destLat+";destLon:"+destLon);
				if(startTime>ctime){
					departureTime=startTime;
					arrivingTime=endTime;
					dep_ntf_triggered=false;
					arr_ntf_triggered=false;
					return;
				}else{
					/*when startTime<=ctime and endTime >= ctime,
					 *  it is when user already on bus*/
					departureTime=0;
					arrivingTime=endTime;
					arr_ntf_triggered=false;
					return;
				}
			}
			/*the itinerary is parsed up, reset to initial state*/
			resetBus();
			nextLegIndex=0;
		} catch (JSONException e) {
			Log.d(TAG, "updateNextBusTimes:"+e.getMessage());
		}
		
	}
	
	private void resetBus(){
		this.departureTime=0;
		this.arrivingTime=0;
		this.busNum="NULL";
		this.destLat=0.0;
		this.destLon=0.0;
		dep_ntf_triggered=false;
		arr_ntf_triggered=false;
		disableServiceTimer();
	}

	private void disableServiceTimer() {
		
		
	}

}
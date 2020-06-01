package com.example.pointchecker;

import android.location.Location;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;

public class CheckPoints {
    static final String BASE_URL = "http://localhost:10080";
    static final int DEFAULT_POST_TIMEOUT = 5000;
    static final String[] DIRECTION_TEXT = new String[]{"北", "北東", "東", "南東", "南", "南西", "西", "北西" };
    static final String[] DIRECTION_SIGN = new String[]{"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
    public static Location location;
    public static int origin_index = 0;
    public static boolean destination_completed = true;
    public static Checkpoint[] checkpoints;
    public static float[] distances;

    public interface ICompletedCb{
        void completed();
        void aborted(Exception ex);
    }

    public static class Checkpoint{
        String name;
        double lat;
        double lng;
        boolean waypoint;

        public Checkpoint(String name, double lat, double lng, boolean waypoint){
            this.name = name;
            this.lat = lat;
            this.lng = lng;
            this.waypoint = waypoint;
        }

        public String toString(){
            return this.name;
        }
    }

    public static String getDirectionText(){
        if( distances != null ) {
            double target = distances[1] + (360 / 16);
            return DIRECTION_TEXT[(int)(int)target / (360 / 8)];
        }else{
            return null;
        }
    }

    public static String getDirectionSign(){
        if( distances != null ) {
            double target = distances[1] + (360 / 16);
            return DIRECTION_SIGN[(int)(int)target / (360 / 8)];
        }else{
            return null;
        }
    }

    public static void update(ICompletedCb cb){
        update(cb, false);
    }

     public static void update(ICompletedCb cb, final boolean increment){
        final ICompletedCb callback = cb;

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    distances = null;

                    JSONObject body_orientation = new JSONObject();
                    body_orientation.put("type", "orientation");
                    JSONObject json_orientation = HttpPostJson.doPost(BASE_URL + "/get-data", body_orientation, DEFAULT_POST_TIMEOUT);
                    Log.d(MainActivity.TAG, json_orientation.toString());
                    JSONObject result = json_orientation.getJSONObject("result");
                    JSONObject data = result.getJSONObject("data");
                    origin_index = 0;
                    destination_completed = true;
                    try {
                        origin_index = data.getInt("origin_index");
                        destination_completed = data.getBoolean("destination_completed");
                    } catch (Exception ex) {}
                    Log.d(MainActivity.TAG, "origin_index:" + origin_index + " destination_completed:" + destination_completed);

                    JSONObject body_checkpoints = new JSONObject();
                    body_checkpoints.put("type", "checkpoints");
                    JSONObject json_checkpoints = HttpPostJson.doPost(BASE_URL + "/get-data", body_checkpoints, DEFAULT_POST_TIMEOUT);
                    Log.d(MainActivity.TAG, json_checkpoints.toString());
                    result = json_checkpoints.getJSONObject("result");
                    JSONArray array = result.getJSONArray("data");
                    Log.d(MainActivity.TAG, array.toString());

                    checkpoints = new Checkpoint[array.length()];
                    for (int i = 0; i < checkpoints.length; i++) {
                        JSONObject obj = array.getJSONObject(i);
                        String name = obj.getString("name");
                        double lat = obj.getDouble("lat");
                        double lng = obj.getDouble("lng");
                        boolean waypoint = false;
                        try{
                            waypoint = obj.getBoolean("waypoint");
                        }catch(Exception ex){}
                        checkpoints[i] = new Checkpoint(name, lat, lng, waypoint);
                    }

                    int next_point = getNextPoint();
                    if( increment ){
                        if( origin_index < next_point ){
                            JSONObject body_update = new JSONObject();
                            body_update.put("type", "orientation");
                            JSONObject data_update = new JSONObject();
                            data_update.put("origin_index", next_point );
                            data_update.put("destination_completed", false);
                            body_update.put("data", data_update);
                            JSONObject json_update = HttpPostJson.doPost(BASE_URL + "/update-data", body_update, DEFAULT_POST_TIMEOUT);
                            Log.d(MainActivity.TAG, json_update.toString());
                            origin_index = next_point;
                            destination_completed = false;
                        }
                    }

                    if( location != null && next_point > origin_index ) {
                        distances = new float[3];
                        Location.distanceBetween(location.getLatitude(), location.getLongitude(), checkpoints[next_point].lat, checkpoints[next_point].lng, distances);
                    }

                    callback.completed();
                } catch (Exception ex) {
                    callback.aborted(ex);
                }
            }
        }).start();
    }

    public static int getNextPoint(){
        int destination_index = origin_index + 1;
        for( ; destination_index < checkpoints.length ; destination_index++ )
            if( !checkpoints[destination_index].waypoint )
                break;
        if( destination_index >= checkpoints.length )
            destination_index = checkpoints.length - 1;
        return destination_index;
    }
}

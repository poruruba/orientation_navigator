package com.example.pointchecker;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    public static final String TAG = "testtest";
    private static final int REQUEST_PERMISSION_LOCATION = 101;
    boolean isLocationReady = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        final boolean gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        if (!gpsEnabled) {
            Intent settingsIntent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
            startActivity(settingsIntent);
        }

        checkLocationPermissions();

        Button btn;
        btn = (Button)findViewById(R.id.btn_start);
        btn.setOnClickListener(this);
        btn = (Button)findViewById(R.id.btn_stop);
        btn.setOnClickListener(this);
        btn = (Button)findViewById(R.id.btn_result);
        btn.setOnClickListener(this);
    }

    private  void checkLocationPermissions(){
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_PERMISSION_LOCATION);
        }else {
            isLocationReady = true;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_PERMISSION_LOCATION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                isLocationReady = true;
            }else{
                Toast.makeText(this, "位置情報の許可がないので計測できません", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public void onClick(View view) {
        switch(view.getId()){
            case R.id.btn_start:{
                if( !isLocationReady )
                    return;

                Intent intent = new Intent(getApplication(), LocationService.class);
                startForegroundService(intent);
                finish();
                break;
            }
            case R.id.btn_stop:{
                Intent intent = new Intent(getApplicationContext(), LocationService.class);
                stopService(intent);
                break;
            }
            case R.id.btn_result:{
                Intent intent = new Intent(this, ResultActivity.class);
                startActivity(intent);
                break;
            }
        }
    }
}

package com.example.pointchecker;

import androidx.core.app.ActivityCompat;
import android.Manifest;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.icu.text.SimpleDateFormat;
import android.icu.util.TimeZone;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.IBinder;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.bluetooth.le.ScanResult;
import android.bluetooth.BluetoothDevice;
import java.util.UUID;

public class LocationService extends Service{
    static final String CHANNEL_ID = "default";
    static final String NOTIFICATION_TITLE = "オリエンテーション";
    static final int NOTIFICATION_ID = 1;
    NotificationManager notificationManager;

    static final String WIRELESS_BLE_DEVICE_NAME = "M5Stick-C";
    static final UUID serviceUuid = UUID.fromString("08030900-7d3b-4ebf-94e9-18abc4cebede");
    static final UUID sendUuid = UUID.fromString("08030901-7d3b-4ebf-94e9-18abc4cebede");
//    static final String UUID_READ = "08030902-7d3b-4ebf-94e9-18abc4cebede";
    static final UUID noteUuid = UUID.fromString("08030903-7d3b-4ebf-94e9-18abc4cebede");
    static final UUID UUID_CLIENT_CHARACTERISTIC_CONFIG = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    static final int UUID_VALUE_SIZE = 20;
    static final int SEND_WAIT = 200;
    BluetoothManager btManager;
    BluetoothAdapter btAdapter;
//    ScanCallback mScanCallback;
    BluetoothLeScanner mScanner;
    BluetoothGatt mConnGatt = null;
    BluetoothGattService mGattService = null;
    BluetoothGattCharacteristic charNote = null;
    BluetoothGattCharacteristic charSend = null;
    boolean isProcessing = false;

    byte[] recv_buffer = new byte[512];
    int received_len = 0;
    byte expected_slot;
    int expected_len = 0;

    static final int MinTime = 5000; // millis
    static final float MinDistance = 25; // meters
    LocationManager locationManager;
    LocationListener locationListener;

    TextToSpeech tts;
    boolean isTtsReady = false;

    Context context;

    @Override
    public void onCreate() {
        super.onCreate();

        context = getApplicationContext();

        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        if( locationManager == null ) {
            Log.d(MainActivity.TAG, "LocationManager not available");
            return;
        }

        tts = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if( status == TextToSpeech.SUCCESS ) {
                    isTtsReady = true;
                    doSpeech("位置情報の更新を開始します。");
                }else{
                    Log.d(MainActivity.TAG, "TextToSpeech init error");
                }
            }
        });

        notificationManager = (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
        if( notificationManager == null ) {
            Log.d(MainActivity.TAG, "NotificationManager not available");
            return;
        }

        btManager= (BluetoothManager)context.getSystemService(Activity.BLUETOOTH_SERVICE);
        if( btManager == null ) {
            Log.d(MainActivity.TAG, "BluetoothManager not available");
            return;
        }

        btAdapter = btManager.getAdapter();
        if (btAdapter == null) {
            Log.d(MainActivity.TAG, "BluetoothAdapter not available");
            return;
        }
    }

    void doSpeech(String message){
        if(tts != null && isTtsReady)
            tts.speak(message, TextToSpeech.QUEUE_ADD, null, TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID );
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(MainActivity.TAG, "onStartCommand");

        if( notificationManager != null ){
            Intent notifyIntent = new Intent(this, ResultActivity.class);
            notifyIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notifyIntent, PendingIntent.FLAG_UPDATE_CURRENT );

            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, NOTIFICATION_TITLE , NotificationManager.IMPORTANCE_DEFAULT);
            // 通知音を消さないと毎回通知音が出てしまう
            // この辺りの設定はcleanにしてから変更
            channel.setSound(null,null);
            channel.enableLights(false);
            channel.setLightColor(Color.BLUE);
            channel.enableVibration(false);

            notificationManager.createNotificationChannel(channel);
            Notification notification = new Notification.Builder(context, CHANNEL_ID)
                    .setContentTitle(NOTIFICATION_TITLE)
                    .setSmallIcon(android.R.drawable.btn_star)
                    .setContentText("GPSとBLEでナビゲーション中")
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent)
                    .setWhen(System.currentTimeMillis())
                    .build();
            startForeground(NOTIFICATION_ID, notification);
        }

        startGPS();
        startBleScan();

        return START_NOT_STICKY;
    }

    private final ScanCallback mScanCallback = new ScanCallback(){
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            Log.d(MainActivity.TAG, "onScanResult");

            BluetoothDevice device = result.getDevice();
            if( device == null )
                return;
            String name = device.getName();
            if( name != null && name.equals(WIRELESS_BLE_DEVICE_NAME) ){
                if( mScanner != null ) {
                    mScanner.stopScan(this);
                    Log.d(MainActivity.TAG, WIRELESS_BLE_DEVICE_NAME + "を発見しました");
                    mConnGatt = device.connectGatt(context, false, mGattcallback);
                }
            }
        }
    };

    private final BluetoothGattCallback mGattcallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d(MainActivity.TAG, WIRELESS_BLE_DEVICE_NAME + "と接続しました");
                if (mConnGatt != null)
                    mConnGatt.discoverServices();
            }else if( newState == BluetoothProfile.STATE_DISCONNECTED ){
                Log.d(MainActivity.TAG, WIRELESS_BLE_DEVICE_NAME + "と切断しました");
                mConnGatt = null;
                if(mScanner != null)
                    mScanner.startScan(mScanCallback);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            Log.d(MainActivity.TAG, "onServicesDiscovered");

            BluetoothGattDescriptor descriptor;
            boolean result;

            mGattService = mConnGatt.getService( serviceUuid );

            charNote = mGattService.getCharacteristic(noteUuid);
            result = mConnGatt.setCharacteristicNotification(charNote, true);
            if( !result ) {
                Log.d(MainActivity.TAG, "setCharacteristicNotification error");
                return;
            }
            descriptor = charNote.getDescriptor(UUID_CLIENT_CHARACTERISTIC_CONFIG);
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            result = mConnGatt.writeDescriptor(descriptor);
            if( !result ) {
                Log.d(MainActivity.TAG, "writeDescriptor error");
                return;
            }

            charSend = mGattService.getCharacteristic(sendUuid);
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            Log.d(MainActivity.TAG, "onCharacteristicChange");
            if( characteristic.getUuid().compareTo(noteUuid) == 0)
                processIndication(characteristic.getValue());
        }

/*
        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            Log.d(MainActivity.TAG, "onCharacteristicRead status=" + status);
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            Log.d(MainActivity.TAG, "onCharacteristicWrite status=" + status);
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            Log.d(MainActivity.TAG, "onDescriptorWrite status=" + status);
        }

        @Override
        public void  onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            Log.d(MainActivity.TAG, "onMtuChanged status=" + status);
        }
*/
    };

    private int parsePacket(byte[] value){
        if( expected_len > 0 && value[0] != expected_slot )
            expected_len = 0;
        if( expected_len == 0 ) {
            if (value[0] != (byte)0x83) {
                Log.d(MainActivity.TAG, "invalid packet");
                return -1;
            }
            received_len = 0;
            expected_len = (((value[1] << 8) & 0x00ff) | (value[2] & 0x00ff));
            System.arraycopy(value, 3, recv_buffer, received_len, value.length - 3);
            received_len += value.length - 3;
            expected_slot = 0x00;
        }else{
            System.arraycopy(value, 1, recv_buffer, received_len, value.length - 1);
            received_len += value.length - 1;
            expected_slot++;
        }

        if( received_len >= expected_len ) {
            expected_len = 0;
            return received_len;
        }else{
            return 0;
        }
    }

    private void processIndication(byte[] data){
        Log.d(MainActivity.TAG, "processIndication");

        int received_len = parsePacket(data);
        if( received_len > 0)
            processPacket(received_len);
    }

    private void processPacket(int received_len){
        Log.d(MainActivity.TAG, "processPacket:" + String.valueOf(recv_buffer[0]));

        if( isProcessing )
            return;

        if( recv_buffer[0] == (byte)0x18) { // RSP_BUTTON_EVENT
            isProcessing = true;
/*
            doSpeech("ボタンが押されました。");
*/
            if( recv_buffer[1] == 0x21 ){
                CheckPoints.update(new CheckPoints.ICompletedCb() {
                   @Override
                   public void completed() {
                       doSpeech("次の目的地に更新しました。");
                       isProcessing = false;
                   }

                    @Override
                    public void aborted(Exception ex) {
                        Log.d(MainActivity.TAG, ex.getMessage());
                        isProcessing = false;
                    }
                }, true);
            }else {
                CheckPoints.update(new CheckPoints.ICompletedCb() {
                    @Override
                    public void completed() {
                        if (CheckPoints.location != null && CheckPoints.distances != null) {
                            sendText(0, 0, 0, "");
                            sendText(2, 0, 0, String.valueOf((int) CheckPoints.distances[0]) + "m");
                            sendText(2, 0, 20, String.valueOf((int) CheckPoints.distances[1]) + "d");
                            sendText(3, 0, 40, CheckPoints.getDirectionSign());

                            doSpeech("目的地まで" + (int) CheckPoints.distances[0] + "メートル");
                            doSpeech(CheckPoints.getDirectionText() + "方向です。");
                        } else {
                            doSpeech("開始の準備ができていません。");
                        }

                        isProcessing = false;
                    }

                    @Override
                    public void aborted(Exception ex) {
                        Log.d(MainActivity.TAG, ex.getMessage());
                        isProcessing = false;
                    }
                });
            }
        }
    }

    private void sendBuffer(byte[] buffer){
        if( mConnGatt == null )
            return;

        byte[] value_write = new byte[UUID_VALUE_SIZE];
        int offset = 0;
        byte slot = 0;
        int packet_size;
        do {
            if( offset == 0 ) {
                value_write[0] = (byte) 0x83;
                value_write[1] = (byte) ((buffer.length >> 8) & 0xff);
                value_write[2] = (byte) (buffer.length & 0xff);
                packet_size = buffer.length - offset;
                if( packet_size > (UUID_VALUE_SIZE - 3))
                    packet_size = UUID_VALUE_SIZE - 3;
                System.arraycopy(buffer, offset, value_write, 3, packet_size);
                offset += packet_size;
                packet_size += 3;
            }else{
                value_write[0] = slot++;
                packet_size = buffer.length - offset;
                if( packet_size > (UUID_VALUE_SIZE - 1) )
                    packet_size = UUID_VALUE_SIZE - 1;
                System.arraycopy(buffer, offset, value_write, 1, packet_size);

                offset += packet_size;
                packet_size += 1;
            }

            if( !charSend.setValue( value_write ) ) {
                Log.d(MainActivity.TAG, "setValue error");
                break;
            }

            boolean ret = mConnGatt.writeCharacteristic( charSend );
            if( !ret ) {
                Log.d(MainActivity.TAG, "writeCharacteristic error");
                break;
            }

            sleep(SEND_WAIT);
        }while(packet_size >= UUID_VALUE_SIZE);
    }

    private void sendText(int font, int x, int y, String text){
        StringBuffer strbuf = new StringBuffer();
        strbuf.append(font);
        strbuf.append(",");
        strbuf.append(x);
        strbuf.append(",");
        strbuf.append(y);
        strbuf.append(",");
        strbuf.append(text);
        byte[] array = strbuf.toString().getBytes();
        byte[] array2 = new byte[array.length + 2];
        array2[0] = 0x30; // CMD_TEXT
        System.arraycopy(array, 0, array2, 1, array.length);
        array2[array.length + 1] = (byte)0;

        sendBuffer(array2);
    }

    public static void sleep(int millis){
        try {
            Thread.sleep(millis);
        }catch(Exception ex){}
    }

    private void startBleScan(){
        if( btAdapter != null && mScanner == null ) {
            mScanner = btAdapter.getBluetoothLeScanner();
            mScanner.startScan(mScanCallback);
        }
    }

     private void startGPS() {
        if (locationManager != null) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
                return;

            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, MinTime, MinDistance, locationListener = new LocationListener() {
                @Override
                public void onLocationChanged(Location location) {
                    Log.d(MainActivity.TAG, "onLocationChanged");
                    CheckPoints.location = location;
                }

                @Override
                public void onStatusChanged(String s, int i, Bundle bundle) {
                }

                @Override
                public void onProviderEnabled(String s) {
                }

                @Override
                public void onProviderDisabled(String s) {
                }
            });
        }
    }

    @Override
    public void onDestroy() {
        Log.d(MainActivity.TAG, "onDestroy");

        if( mConnGatt != null ){
            mScanner = null;
            try {
                mConnGatt.disconnect();
                mConnGatt.close();
            }catch (Exception ex){}
            mConnGatt = null;
        }
        if(tts != null) {
            try {
                tts.stop();
                tts.shutdown();
            }catch (Exception ex){}
            tts = null;
        }
        if (locationListener != null) {
            locationManager.removeUpdates(locationListener);
            locationListener = null;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}

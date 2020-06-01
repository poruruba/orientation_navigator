package com.example.pointchecker;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

public class ResultActivity extends AppCompatActivity implements View.OnClickListener, UIHandler.Callback {
    UIHandler handler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result);

        handler = new UIHandler(this);

        CheckPoints.update(new CheckPoints.ICompletedCb() {
            @Override
            public void completed() {
                handler.sendIntMessage(0);
            }
            @Override
            public void aborted(Exception ex) {
                Log.d(MainActivity.TAG, ex.getMessage());
            }
        });

        Button btn;
        btn = (Button)findViewById(R.id.btn_stop);
        btn.setOnClickListener(this);
        btn = (Button)findViewById(R.id.btn_reload);
        btn.setOnClickListener(this);
    }

    @Override
    public void onClick(View view) {
        switch(view.getId()){
            case R.id.btn_stop:{
                Intent intent = new Intent(getApplicationContext(), LocationService.class);
                stopService(intent);
                finish();
                break;
            }
            case R.id.btn_reload:{
                CheckPoints.update(new CheckPoints.ICompletedCb() {
                    @Override
                    public void completed() {
                        handler.sendIntMessage(0);
                    }
                    @Override
                    public void aborted(Exception ex) {
                        Log.d(MainActivity.TAG, ex.getMessage());
                    }
                });
                break;
            }
        }
    }

    @Override
    public boolean handleMessage(@NonNull Message message) {
        TextView text;

        if( CheckPoints.location != null ) {
            text = (TextView) findViewById(R.id.txt_latitude);
            text.setText("緯度：" + String.valueOf(CheckPoints.location.getLatitude()));
            text = (TextView) findViewById(R.id.txt_longitude);
            text.setText("経度：" + String.valueOf(CheckPoints.location.getLongitude()));
        }

        if( CheckPoints.distances != null ){
            text = (TextView)findViewById(R.id.txt_next_distance);
            text.setText("次のポイントまで " + (int)CheckPoints.distances[0] + " メートルです。");
            text = (TextView) findViewById(R.id.txt_start_point);
            text.setText("今のチェックポイント：" + CheckPoints.checkpoints[CheckPoints.origin_index].name);
            text = (TextView) findViewById(R.id.txt_next_point);
            text.setText("次のチェックポイント：" + CheckPoints.checkpoints[CheckPoints.getNextPoint()].name);
            text = (TextView)findViewById(R.id.txt_next_direction);
            text.setText("方位：" + (int)CheckPoints.distances[1] + "° " + CheckPoints.getDirectionText());
        }

        if( CheckPoints.checkpoints != null ){
            ListView list;
            list = (ListView)findViewById(R.id.list_points);
            ArrayAdapter<CheckPoints.Checkpoint> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, CheckPoints.checkpoints);
            list.setAdapter(adapter);
        }

        return true;
    }
}

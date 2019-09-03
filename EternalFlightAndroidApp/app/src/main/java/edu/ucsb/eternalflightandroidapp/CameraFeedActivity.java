package edu.ucsb.eternalflightandroidapp;

import android.content.Intent;
import android.support.constraint.ConstraintLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;

import com.github.niqdev.mjpeg.DisplayMode;
import com.github.niqdev.mjpeg.Mjpeg;
import com.github.niqdev.mjpeg.MjpegView;

import java.lang.invoke.ConstantCallSite;

public class CameraFeedActivity extends AppCompatActivity {

    int TIMEOUT = 5; //seconds

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera_feed);

        MjpegView mjpegView = findViewById(R.id.cameraFeedView);

        Mjpeg.newInstance()
                .open("http://192.168.11.115:8000/stream.mjpg", TIMEOUT)
                .subscribe(inputStream -> {
                    mjpegView.setSource(inputStream);
                    mjpegView.setDisplayMode(DisplayMode.BEST_FIT);
                    mjpegView.showFps(true);
                });

        FloatingActionButton returnButton = findViewById(R.id.feedReturnButton);
        returnButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent waypointActivity = new Intent(getApplicationContext(), WaypointActivity.class);
                startActivity(waypointActivity);
            }
        });
    }
}

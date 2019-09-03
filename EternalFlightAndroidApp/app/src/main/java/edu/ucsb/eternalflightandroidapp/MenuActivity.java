package edu.ucsb.eternalflightandroidapp;

import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;

public class MenuActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_menu);

        ImageButton statusButton = findViewById(R.id.statusbutton);
        statusButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent statusActivity = new Intent(getApplicationContext(), StatusActivity.class);
                startActivity(statusActivity);
            }
        });

        ImageButton joystickButton = findViewById(R.id.joystickbutton);
        joystickButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent joystickActivity = new Intent(getApplicationContext(), JoystickActivity.class);
                startActivity(joystickActivity);
            }
        });

        ImageButton accelerometerButton = findViewById(R.id.accelerometerbutton);
        accelerometerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent accelerActivity =
                        new Intent(getApplicationContext(), AccelerometerActivity.class);
                startActivity(accelerActivity);
            }
        });

        ImageButton waypointButton = findViewById(R.id.waypointbutton);
        waypointButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent waypointActivity =
                        new Intent(getApplicationContext(), WaypointActivity.class);
                startActivity(waypointActivity);
            }
        });
    }
}



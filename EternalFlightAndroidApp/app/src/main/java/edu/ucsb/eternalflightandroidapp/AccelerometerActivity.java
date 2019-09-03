package edu.ucsb.eternalflightandroidapp;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.hardware.SensorManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.content.Context;
import android.util.Log;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.UnknownHostException;

import android.widget.Button;
import android.view.View;
import android.view.MotionEvent;
import android.widget.CompoundButton;

import java.net.Socket;
import java.util.Timer;
import java.util.TimerTask;
import android.widget.SeekBar;
import android.widget.ToggleButton;

import com.github.niqdev.mjpeg.DisplayMode;
import com.github.niqdev.mjpeg.Mjpeg;
import com.github.niqdev.mjpeg.MjpegView;

import java.util.concurrent.locks.Lock;


public class AccelerometerActivity extends AppCompatActivity implements SensorEventListener {

    private SensorManager sensorManager;
    private final float[] accelerometerReading = new float[3];
    private final float[] magnetometerReading = new float[3];
    private final float[] gyroReading = new float[3];
    private final float[] rotationMatrix = new float[9];
    private final float[] orientationAngles = new float[3];

    Socket s;
    private Timer myTimer;

    float roll=0, pitch=0, yaw=0, thrust=0;

    float FLIGHT_SMOOTHNESS = 1.5f;
    float PITCH_THRESHOLD = 50f;
    float ROLL_THRESHOLD = 30f;
    float YAW_THRESHOLD = 40f;
    float THRUST_SCALAR = 8f;
    float YAW_SCALAR = 100f / ((float)Math.PI);
    float PI_RC_SCALAR = 900f / ((float)Math.PI * FLIGHT_SMOOTHNESS);

    Button yawButton;
    ToggleButton armButton;
    Boolean useYaw = false;
    String arm = "0";
    Boolean toggle = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_accelerometer);
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        s = SocketHandler.getSocket();

        yawButton = findViewById(R.id.yawButton);
        armButton = findViewById(R.id.armButton);

        // ----------------------
        // MPEG Video from PiCam
        // ----------------------
        MjpegView mjpegView;
        mjpegView = findViewById(R.id.mjpegViewDefault);
        int TIMEOUT = 5; //seconds
        Mjpeg.newInstance()
                .open("http://192.168.11.115:8000/stream.mjpg", TIMEOUT)
                .subscribe(inputStream -> {
                    mjpegView.setSource(inputStream);
                    mjpegView.setDisplayMode(DisplayMode.BEST_FIT);
                    mjpegView.showFps(true);
                });

        // ----------------------------
        // Vertical seekBar for Thrust
        // ----------------------------
        SeekBar seekBar = findViewById(R.id.seekBar);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                thrust = Float.parseFloat(String.valueOf(progress)) * THRUST_SCALAR;
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        // ------------------------------------
        // 10 Hz timer for sending RC commands
        // ------------------------------------
        myTimer = new Timer();

        // ------------------------------------
        // Add button for yaw control
        // ------------------------------------
        yawButton.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch(event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        // PRESSED
                        useYaw = true;
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        // RELEASED
                        useYaw = false;
                        break;
                }
                return false;
            }
        });

        // ------------------------------------
        // Add button for arming drone
        // ------------------------------------
        armButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                arm = "0";
                toggle = true;
                if (isChecked) {
                    // The toggle is enabled
                    arm = "1";
                }
            }
        });
    }

    public void sendAccelerometerControls() {

        if (toggle) {
            toggle = false;
            String str = "arm, %s";
            str = String.format(str, arm);
            Log.d("String: ", str);

            try {
                PrintWriter out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(s.getOutputStream())), true);
                out.println(str);
            } catch (UnknownHostException E) {
                E.printStackTrace();
            } catch (IOException E) {
                E.printStackTrace();
            } catch (Exception E) {
                E.printStackTrace();
            }
        }

        yaw = 0;
        if (useYaw) {
            yaw = gyroReading[2] * YAW_SCALAR;
//            yaw = (Math.abs(yaw) < YAW_THRESHOLD) ? 0 : yaw;
        }

        pitch = orientationAngles[1] * 2 * PI_RC_SCALAR; //Down: Positive
        pitch = (Math.abs(pitch) < PITCH_THRESHOLD) ? 0 : pitch;

        roll = orientationAngles[2] * PI_RC_SCALAR;
        roll = (Math.abs(roll) < ROLL_THRESHOLD) ? 0 : roll;

        String str = "move, %.2f, %.2f, %.2f, %.2f, 1, 0";
        str = String.format(str, roll, pitch, thrust, yaw);
        Log.d("String: ", str);
        try {
            PrintWriter out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(s.getOutputStream())), true);
            out.println(str);
        } catch (UnknownHostException E) {
            E.printStackTrace();
        } catch (IOException E) {
            E.printStackTrace();
        } catch (Exception E) {
            E.printStackTrace();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Leave blank
    }

    @Override
    protected void onResume() {
        super.onResume();

        myTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                sendAccelerometerControls();
            }
        }, 0, 100);

        // Get updates from the accelerometer and magnetometer at a constant rate.
        // To make batch operations more efficient and reduce power consumption,
        // provide support for delaying updates to the application.
        //
        // In this example, the sensor reporting delay is small enough such that
        // the application receives an update before the system checks the sensor
        // readings again.
        Sensor accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL, SensorManager.SENSOR_DELAY_UI);
        }
        Sensor magneticField = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        if (magneticField != null) {
            sensorManager.registerListener(this, magneticField, SensorManager.SENSOR_DELAY_NORMAL, SensorManager.SENSOR_DELAY_UI);
        }
        Sensor gyro = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        if (gyro != null) {
            sensorManager.registerListener(this, gyro, SensorManager.SENSOR_DELAY_NORMAL, SensorManager.SENSOR_DELAY_UI);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        myTimer.cancel();

        // Don't receive any more updates from either sensor.
        sensorManager.unregisterListener(this);

        finish();
    }

    // Get readings from accelerometer and magnetometer. To simplify calculations,
    // consider storing these readings as unit vectors.
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            System.arraycopy(event.values, 0, accelerometerReading,
                    0, accelerometerReading.length);
        } else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            System.arraycopy(event.values, 0, magnetometerReading,
                    0, magnetometerReading.length);
        } else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            System.arraycopy(event.values, 0, gyroReading,
                    0, gyroReading.length);
        }

        updateOrientationAngles();
    }

    // Compute the three orientation angles based on the most recent readings from
    // the device's accelerometer and magnetometer.
    public void updateOrientationAngles() {
        // Update rotation matrix, which is needed to update orientation angles.
        SensorManager.getRotationMatrix(rotationMatrix, null, accelerometerReading, magnetometerReading);
        // "rotationMatrix" now has up-to-date information.

        SensorManager.getOrientation(rotationMatrix, orientationAngles);
        // "orientationAngles" now has up-to-date information.
    }
}

package edu.ucsb.eternalflightandroidapp;

import android.content.Context;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import java.net.Socket;
import java.util.Timer;
import java.util.TimerTask;

import android.util.Log;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.BufferedWriter;
import java.io.PrintWriter;
import java.net.UnknownHostException;

import com.github.niqdev.mjpeg.DisplayMode;
import com.github.niqdev.mjpeg.Mjpeg;
import com.github.niqdev.mjpeg.MjpegView;

import android.hardware.SensorManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;

public class JoystickActivity extends AppCompatActivity implements JoystickView.JoystickListener, SensorEventListener {

    JoystickView joystick;
    private Timer myTimer;
    Socket s;

    private SensorManager sensorManager;
    private final float[] accelerometerReading = new float[3];
    private final float[] magnetometerReading = new float[3];
    private final float[] rotationMatrix = new float[9];
    private final float[] orientationAngles = new float[3];

    float roll=0, pitch=0, yaw=0, thrust=0;

    float FLIGHT_SMOOTHNESS = 1.5f;
    float YAW_THRESHOLD = 100f;
    float PI_RC_SCALAR = 2 * 900f / ((float)Math.PI * FLIGHT_SMOOTHNESS);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_joystick);

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        joystick = new JoystickView(this);
        joystick.bringToFront();

        s = SocketHandler.getSocket();

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

        // ------------------------------------
        // 10 Hz timer for sending RC commands
        // ------------------------------------
        myTimer = new Timer();
    }

    private void SendRCCommands() {

        float[] joystick_values = joystick.getJoystick_values();

        // Set yaw, roll, pitch values

        yaw = orientationAngles[1] * PI_RC_SCALAR;
        yaw = (Math.abs(yaw) < YAW_THRESHOLD) ? 0 : yaw;
        if (yaw > 500)
            yaw = 500;
        else if (yaw < -500)
            yaw = -500;

        roll = joystick_values[2] * 500;
        pitch = joystick_values[3] * -500;
        if (joystick.touched)
            thrust = 400 + (joystick_values[1] * -400);
        else
            thrust = 0;

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

    @Override
    protected void onPause() {
        super.onPause();
        myTimer.cancel();
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();

        myTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                SendRCCommands();
            }
        }, 0, 100);

        Sensor accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL, SensorManager.SENSOR_DELAY_UI);
        }
        Sensor magneticField = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        if (magneticField != null) {
            sensorManager.registerListener(this, magneticField, SensorManager.SENSOR_DELAY_NORMAL, SensorManager.SENSOR_DELAY_UI);
        }
    }

    @Override
    public void onJoystickMoved(float xPercent, float yPercent, int id) {
        // Leave blank
    }
}

package edu.ucsb.eternalflightandroidapp;

import android.content.Intent;
import android.graphics.Color;
import android.os.SystemClock;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.w3c.dom.Text;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;

public class StatusActivity extends AppCompatActivity {

    private Socket socket;
    String statusInformation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_status);
        LinearLayout background = findViewById(R.id.statusBackground);
        background.setBackgroundColor(Color.WHITE);

        socket = SocketHandler.getSocket();

        new Thread(new CommunicationThread()).start();

        SystemClock.sleep(3000);

        if (statusInformation == null) {
            Toast.makeText(getApplicationContext(), "Could not get status information from drone. Please exit this feature and click to try again.", Toast.LENGTH_LONG).show();
            return;
        }

        TextView batteryText = findViewById(R.id.batteryText);
        TextView modeText = findViewById(R.id.modeText);
        TextView satText = findViewById(R.id.satText);
        TextView locationText = findViewById(R.id.locationText);
        TextView armedText = findViewById(R.id.armedText);
        TextView isarmableText = findViewById(R.id.isArmableText);
        TextView firmwareText = findViewById(R.id.firmwareText);

        String[] categories = statusInformation.split(";");

        String voltage = categories[0].split("=|,")[1];
        batteryText.setText("Battery Voltage | " + voltage);

        modeText.setText("Flight Mode | " + categories[1]);

        String numSats = categories[2].split("=")[2];
        satText.setText("Number of Satellites | " + numSats);

        String[] locationInfo = categories[3].split("=|,");
        locationText.setText("Current Location | Latitude: "+ locationInfo[1] + " Longitude: " + locationInfo[3] + " Altitude: " + locationInfo[5]);

        if (categories[4].equals("True")) {
            armedText.setText("ARMED");
        }
        if (categories[5].equals("True")) {
            isarmableText.setText("ABLE TO ARM");
        }
        firmwareText.setText("Firmware Version | " + categories[6]);
    }

    class CommunicationThread implements Runnable {
        @Override
        public void run() {
            String command = "status";
            try {
                PrintWriter out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())), true);
                out.println(command);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                statusInformation = in.readLine();
            } catch (UnknownHostException E) {
                E.printStackTrace();
            } catch (IOException E) {
                E.printStackTrace();
            } catch (Exception E) {
                E.printStackTrace();
            }
        }

    }
}


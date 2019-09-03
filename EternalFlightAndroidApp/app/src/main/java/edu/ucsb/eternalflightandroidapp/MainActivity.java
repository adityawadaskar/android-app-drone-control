package edu.ucsb.eternalflightandroidapp;

import android.content.Intent;
import android.os.SystemClock;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;

import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;


public class MainActivity extends AppCompatActivity {

    Socket socket;

    public String IP_ADDRESS;
    public int PORT_NUMBER;

    Exception exception;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        EditText ip = findViewById(R.id.ConnectText01);
        ip.setText("192.168.11.115");
        EditText port = findViewById(R.id.ConnectText02);
        port.setText("5005");
    }

    public void onClick(View view) {
        try {
            EditText ipAddress = findViewById(R.id.ConnectText01);
            EditText portNumber = findViewById(R.id.ConnectText02);
            IP_ADDRESS = ipAddress.getText().toString();
            PORT_NUMBER = Integer.parseInt(portNumber.getText().toString());

            new Thread(new ClientThread()).start();

        } catch (Exception e) {
            e.printStackTrace();
        }

        SystemClock.sleep(3000);

        if (SocketHandler.getSocket() == null) {
            Toast.makeText(getApplicationContext(), "Could not connect to drone. Please ensure that the drone is ready to receive a connection and try again.", Toast.LENGTH_LONG).show();
        } else {
            Intent menu = new Intent(getApplicationContext(), MenuActivity.class);
            startActivity(menu);
        }
    }

    class ClientThread implements Runnable {
        @Override
        public void run() {
            try {

                Log.v("MainActivity", "Attempting to connect to " + IP_ADDRESS + " " + PORT_NUMBER);

                socket = new Socket(IP_ADDRESS, PORT_NUMBER);
                SocketHandler.setSocket(socket);

                Log.v("MainActivity", "CONNECTION SUCCESSFUL");

            } catch (UnknownHostException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }
}
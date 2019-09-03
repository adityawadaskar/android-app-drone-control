package edu.ucsb.eternalflightandroidapp;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.location.Location;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.FragmentActivity;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.github.niqdev.mjpeg.DisplayMode;
import com.github.niqdev.mjpeg.Mjpeg;
import com.github.niqdev.mjpeg.MjpegView;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;

import permissions.dispatcher.NeedsPermission;
import permissions.dispatcher.OnPermissionDenied;
import permissions.dispatcher.RuntimePermissions;

@RuntimePermissions
public class WaypointActivity extends FragmentActivity implements OnMapReadyCallback,
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        LocationListener {

    // WAYPOINT FUNCTIONALITY VARIABLES
    Boolean MISSION_CURRENTLY_RUNNING;
    ArrayList<Marker> markers;
    int altitude;

    // Location Services - VARIABLES FOR GETTING CURRENT LOCATION
    private GoogleApiClient mGoogleApiClient;
    private LocationRequest mLocationRequest;
    private GoogleMap mMap;
    private LatLng currentHomeLoc;
    private Marker currentDroneMarker;

    private long UPDATE_INTERVAL = 10 * 1000;  /* 10 secs */
    private long FASTEST_INTERVAL = 10000; /* 10 sec */

    private Socket socket;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_waypoint);

        // Create the location client to start receiving updates
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(LocationServices.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this).build();

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        MISSION_CURRENTLY_RUNNING = false;
        markers = new ArrayList<>();
        socket = SocketHandler.getSocket();
        altitude = 0;

        // TODO: set values based on shared preferences here

        SeekBar altitudeBar = findViewById(R.id.altitudeBar);
        final TextView barText = findViewById(R.id.altitudeText);
        altitudeBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                String update = "Altitude Control | Current Value: " + i + " meters";
                barText.setText(update);
                altitude = i;

                if (MISSION_CURRENTLY_RUNNING) {
                    sendCommandToPi("altitude," + altitude);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        final Button startMissionButton = findViewById(R.id.startButton);
        final Button landButton = findViewById(R.id.landButton);
        final Button returnButton = findViewById(R.id.returnButton);

        startMissionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (altitude <= 0) {
                    Toast.makeText(getApplicationContext(), "Desired altitude for " +
                            "waypoint navigation must be larger than 0 meters.", Toast.LENGTH_LONG).show();
                } else if (markers.size() > 0) {
                    if (!MISSION_CURRENTLY_RUNNING) {
                        returnButton.setText("Return Home");
                        startMissionButton.setText("Move to Next Point");
                        MISSION_CURRENTLY_RUNNING = true;
                    }
                    if (currentDroneMarker != null) currentDroneMarker.remove();
                    currentDroneMarker = markers.remove(0);
                    String waypointCommand = String.format("waypoint,%f,%f,%d", currentDroneMarker.getPosition().latitude, currentDroneMarker.getPosition().longitude, altitude);
                    sendCommandToPi(waypointCommand);
                    Bitmap original = BitmapFactory.decodeResource(getResources(),
                            R.drawable.drone);
                    Bitmap droneImage  = Bitmap.createScaledBitmap(original,
                            (int)(original.getWidth() * 0.25) ,
                            (int)(original.getHeight() * 0.25),
                            true);
                    currentDroneMarker.setIcon(BitmapDescriptorFactory.fromBitmap(droneImage));
                    if (markers.isEmpty()) startMissionButton.setText("Move Back Home");
                } else {
                    if (MISSION_CURRENTLY_RUNNING) {
                        sendDroneHome();
                        MISSION_CURRENTLY_RUNNING = false;
                        if (currentDroneMarker != null) currentDroneMarker.remove();
                        returnButton.setText("Reset");
                        startMissionButton.setText("Start Mission");
                    } else {
                        Toast.makeText(getApplicationContext(), "Please set at least one " +
                                "destination by tapping the map in order for waypoint navigation " +
                                "to be valid.", Toast.LENGTH_LONG).show();
                    }
                }
            }
        });

        landButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (MISSION_CURRENTLY_RUNNING) {
                    for (int i = 0; i < markers.size(); i++) {
                        Marker curr = markers.get(i);
                        curr.remove();
                    }
                    markers.clear();
                    sendCommandToPi("land");
                    MISSION_CURRENTLY_RUNNING = false;
                    if (currentDroneMarker != null) currentDroneMarker.remove();
                    returnButton.setText("Reset");
                    startMissionButton.setText("Start Mission");
                }
            }
        });

        returnButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                for (int i = 0; i < markers.size(); i++) {
                    Marker curr = markers.get(i);
                    curr.remove();
                }
                markers.clear();
                if (MISSION_CURRENTLY_RUNNING) {
                    sendDroneHome();
                    if (currentDroneMarker != null) currentDroneMarker.remove();
                    MISSION_CURRENTLY_RUNNING = false;
                    returnButton.setText("Reset");
                    startMissionButton.setText("Start Mission");
                }
            }
        });

        FloatingActionButton cameraFeed = findViewById(R.id.cameraFeedButton);
        cameraFeed.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent cameraFeed = new Intent(getApplicationContext(), CameraFeedActivity.class);
                startActivity(cameraFeed);
                // TODO: save state in shared preferences here
            }
        });

        FloatingActionButton returnToMenu = findViewById(R.id.returnToMenuButton);
        returnToMenu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent menu = new Intent(getApplicationContext(), MenuActivity.class);
                startActivity(menu);
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // NOTE: delegate the permission handling to generated method
        WaypointActivityPermissionsDispatcher.onRequestPermissionsResult(this, requestCode, grantResults);
    }

    protected void onStart() {
        super.onStart();
        // Connect the client.
        mGoogleApiClient.connect();
    }

    protected void onStop() {
        // Disconnecting the client invalidates it.
        LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);

        // only stop if it's connected, otherwise we crash
        if (mGoogleApiClient != null) {
            mGoogleApiClient.disconnect();
        }
        super.onStop();
    }

    @Override
    @SuppressWarnings("all")
    @NeedsPermission({Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION})
    public void onConnected(Bundle dataBundle) {
        // Get last known recent location.
        Location mCurrentLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
        // Note that this can be NULL if last location isn't already known.
        if (mCurrentLocation != null) {
            // Print current location if not null
            Log.d("DEBUG", "current location: " + mCurrentLocation.toString());
            currentHomeLoc = new LatLng(mCurrentLocation.getLatitude(), mCurrentLocation.getLongitude());
        }
        // Begin polling for new location updates.
        WaypointActivityPermissionsDispatcher.startLocationUpdatesWithCheck(WaypointActivity.this);
    }

    @Override
    public void onConnectionSuspended(int i) {
        if (i == CAUSE_SERVICE_DISCONNECTED) {
            Toast.makeText(this, "Disconnected. Please re-connect.", Toast.LENGTH_SHORT).show();
        } else if (i == CAUSE_NETWORK_LOST) {
            Toast.makeText(this, "Network lost. Please re-connect.", Toast.LENGTH_SHORT).show();
        }
    }

    @SuppressWarnings("all")
    @NeedsPermission({Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION})
    protected void startLocationUpdates() {
        mLocationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(UPDATE_INTERVAL)
                .setFastestInterval(FASTEST_INTERVAL);
        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient,
                mLocationRequest, this);
    }

    @Override
    public void onLocationChanged(Location location) {
        currentHomeLoc = new LatLng(location.getLatitude(), location.getLongitude());
        CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngZoom(currentHomeLoc, 17);
        mMap.animateCamera(cameraUpdate);
    }

    @OnPermissionDenied({Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION})
    void showDeniedForLocationUpdates() {
        Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            mMap.setMyLocationEnabled(true);
        } else {
            Toast.makeText(getApplicationContext(), "Access to current location needed in " +
                    "order for waypoint navigation to function correctly. " +
                    "Please check application permissions and try again.", Toast.LENGTH_LONG).show();
            Intent menu = new Intent(getApplicationContext(), MenuActivity.class);
            startActivity(menu);
        }

        mMap.setOnMapClickListener(new GoogleMap.OnMapClickListener() {
            @Override
            public void onMapClick(LatLng latLng) {
                if (markers.size()>= 3) {
                    Toast.makeText(getApplicationContext(), "Waypoint Navigation is limited to " +
                            "three locations. Press the reset button to clear previously " +
                            "set markers.", Toast.LENGTH_LONG).show();
                } else {
                    markers.add(mMap.addMarker(new MarkerOptions().position(latLng)));
                    mMap.moveCamera(CameraUpdateFactory.newLatLng(latLng));
                }
            }
        });
    }

    public void sendDroneHome() {
        String waypointCommand = String.format("waypoint,%f,%f,%d", currentHomeLoc.latitude, currentHomeLoc.longitude, altitude);
        sendCommandToPi(waypointCommand);
    }

    public void sendCommandToPi(String command) {
        try {
            PrintWriter out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())), true);
            out.println(command);
        } catch (UnknownHostException E) {
            E.printStackTrace();
        } catch (IOException E) {
            E.printStackTrace();
        } catch (Exception E) {
            E.printStackTrace();
        }
    }
}
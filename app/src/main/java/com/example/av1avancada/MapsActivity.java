package com.example.av1avancada;

import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.av1avancada.route.Route;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.widget.AutocompleteSupportFragment;
import com.google.android.libraries.places.widget.listener.PlaceSelectionListener;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

public class MapsActivity extends AppCompatActivity
        implements OnMapReadyCallback, LocationListener {

    private static final String TAG = MapsActivity.class.getSimpleName();
    private GoogleMap googleMapa;
    private FusedLocationProviderClient fusedLocationProviderClient;
    // Localização de Lavras-MG como padrão caso a permissão de localização for negada.
    private final LatLng defaultLocation = new LatLng(-21.24528, -44.99972);
    protected LatLng origemLatLng;
    protected LatLng destinoLatLng;
    protected List<Route> route;
    protected JSONObject jObject;
    ArrayList markerPoints = new ArrayList();
    private CameraPosition cameraPosition;
    private static final int DEFAULT_ZOOM = 15;
    private static final int PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 1;
    private boolean locationPermissionGranted;
    private Location lastKnownLocation;
    private static final String KEY_CAMERA_POSITION = "camera_position";
    private static final String KEY_LOCATION = "location";
    private TextView textVelocidade;
    private TextView textVelocidadeObjetivo;
    private TextView textViewTempo;
    private Button iniciar;
    private CardView cardview;
    private Handler handler;
    private Runnable runnable;
    private boolean isTimerRunning = false;
    private int seconds = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Retrieve location and camera position from saved instance state.
        if (savedInstanceState != null) {
            lastKnownLocation = savedInstanceState.getParcelable(KEY_LOCATION);
            cameraPosition = savedInstanceState.getParcelable(KEY_CAMERA_POSITION);
        }

        Places.initialize(getApplicationContext(), "AIzaSyBGgkO6xusRHK_ryDFUu_HvcixyOFhLsEg");

        // Retrieve the content view that renders the map.
        setContentView(R.layout.activity_maps);

        // Construct a FusedLocationProviderClient.
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);

        // Build the map.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        cardview = findViewById(R.id.cardview);

        AutocompleteSupportFragment starting = (AutocompleteSupportFragment)
                getSupportFragmentManager().findFragmentById(R.id.start);
        starting.setHint("Escolha local de partida");

        AutocompleteSupportFragment destination = (AutocompleteSupportFragment)
                getSupportFragmentManager().findFragmentById(R.id.destination);
        destination.setHint("Escolha local de destino");

        iniciar = findViewById(R.id.buttonIniciar);
        iniciar.setBackgroundResource(R.drawable.start_background);
        iniciar.setVisibility(View.INVISIBLE);

        Button encerrar = findViewById(R.id.buttonEncerrar);
        iniciar.setBackgroundResource(R.drawable.stop_background);
        iniciar.setVisibility(View.INVISIBLE);

        textVelocidade = findViewById(R.id.textVelocidade);
        textVelocidadeObjetivo = findViewById(R.id.textVelocidadeObjetivo);

        TextView textViewDistancia = findViewById(R.id.textViewDistancia);
        textViewTempo = findViewById(R.id.textViewTempo);
        TextView textViewCombustivel = findViewById(R.id.textViewCombustivel);

        textViewDistancia.setVisibility(View.INVISIBLE);
        textViewTempo.setVisibility(View.INVISIBLE);
        textViewCombustivel.setVisibility(View.INVISIBLE);

        iniciar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                iniciar.setVisibility(View.INVISIBLE);
                cardview.setVisibility(View.INVISIBLE);
                encerrar.setVisibility(View.VISIBLE);
                textViewDistancia.setVisibility(View.VISIBLE);
                textViewTempo.setVisibility(View.VISIBLE);
                textViewCombustivel.setVisibility(View.VISIBLE);
                startTimer();
            }
        });

        encerrar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                iniciar.setVisibility(View.VISIBLE);
                cardview.setVisibility(View.VISIBLE);
                encerrar.setVisibility(View.INVISIBLE);
                textViewDistancia.setVisibility(View.INVISIBLE);
                textViewTempo.setVisibility(View.INVISIBLE);
                textViewCombustivel.setVisibility(View.INVISIBLE);
                stopTimer();
            }
        });

        // Specify the types of place data to return.
        starting.setPlaceFields(Arrays.asList(Place.Field.ID, Place.Field.NAME, Place.Field.ADDRESS, Place.Field.LAT_LNG));
        destination.setPlaceFields(Arrays.asList(Place.Field.ID, Place.Field.NAME, Place.Field.ADDRESS, Place.Field.LAT_LNG));

        // Set up a PlaceSelectionListener to handle the response.
        starting.setOnPlaceSelectedListener(new PlaceSelectionListener() {
            @Override
            public void onPlaceSelected(@NonNull Place place) {
                if (markerPoints.size() > 1) {
                    markerPoints.clear();
                    googleMapa.clear();
                }

                origemLatLng = place.getLatLng();
                String optionSnippet = place.getAddress();

                markerPoints.add(origemLatLng);

                googleMapa.addMarker(new MarkerOptions()
                        .title(place.getName())
                        .position(origemLatLng)
                        .snippet(optionSnippet)
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)));

                getRouteNavigate();

                Log.i(TAG, "Place: " + place.getName() + ", " + place.getId());
            }

            @Override
            public void onError(@NonNull Status status) {
                // TODO: Handle the error.
                Log.i(TAG, "An error occurred: " + status);
            }
        });

        destination.setOnPlaceSelectedListener(new PlaceSelectionListener() {
            @Override
            public void onPlaceSelected(@NonNull Place place) {
                if (markerPoints.size() > 1) {
                    markerPoints.clear();
                    googleMapa.clear();
                }

                destinoLatLng = place.getLatLng();
                String optionSnippet = place.getAddress();

                markerPoints.add(destinoLatLng);

                googleMapa.addMarker(new MarkerOptions()
                        .title(place.getName())
                        .position(destinoLatLng)
                        .snippet(optionSnippet)
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));

                getRouteNavigate();

                Log.i(TAG, "Place: " + place.getName() + ", " + place.getId());
            }

            @Override
            public void onError(@NonNull Status status) {
                // TODO: Handle the error.
                Log.i(TAG, "An error occurred: " + status);
            }
        });
    }

    private void getRouteNavigate() {
        if (markerPoints.size() >= 2) {

            // Getting URL to the Google Directions API
            String url = getDirectionsUrl(origemLatLng, destinoLatLng);

            DownloadTask downloadTask = new DownloadTask();

            // Start downloading json data from Google Directions API
            downloadTask.execute(url);
        }
    }

    /**
     * Salva o estado do mapa quando a atividade é pausada.
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (googleMapa != null) {
            outState.putParcelable(KEY_CAMERA_POSITION, googleMapa.getCameraPosition());
            outState.putParcelable(KEY_LOCATION, lastKnownLocation);
        }
        super.onSaveInstanceState(outState);
    }


    /**
     * Manipulates the map when it's available.
     * This callback is triggered when the map is ready to be used.
     */
    @Override
    public void onMapReady(GoogleMap map) {
        this.googleMapa = map;

        // Prompt the user for permission.
        getLocationPermission();

        // Turn on the My Location layer and the related control on the map.
        updateLocationUI();

        // Get the current location of the device and set the position of the map.
        getDeviceLocation();

    }

    /**
     * Obtém a localização atual do dispositivo e posiciona a câmera do mapa.
     */
    private void getDeviceLocation() {
        /*
         * Get the best and most recent location of the device, which may be null in rare
         * cases when a location is not available.
         */
        try {
            if (locationPermissionGranted) {
                Task<Location> locationResult = fusedLocationProviderClient.getLastLocation();
                locationResult.addOnCompleteListener(this, new OnCompleteListener<Location>() {
                    @Override
                    public void onComplete(@NonNull Task<Location> task) {
                        if (task.isSuccessful()) {
                            // Set the map's camera position to the current location of the device.
                            lastKnownLocation = task.getResult();
                            if (lastKnownLocation != null) {
                                googleMapa.moveCamera(CameraUpdateFactory.newLatLngZoom(
                                        new LatLng(lastKnownLocation.getLatitude(),
                                                lastKnownLocation.getLongitude()), DEFAULT_ZOOM));
                            }
                        } else {
                            Log.d(TAG, "Current location is null. Using defaults.");
                            Log.e(TAG, "Exception: %s", task.getException());
                            googleMapa.moveCamera(CameraUpdateFactory
                                    .newLatLngZoom(defaultLocation, DEFAULT_ZOOM));
                            googleMapa.getUiSettings().setMyLocationButtonEnabled(false);
                        }
                    }
                });
            }
        } catch (SecurityException e) {
            Log.e("Exception: %s", e.getMessage(), e);
        }
    }

    /**
     * Solicita permissão ao usuário para usar a localização do dispositivo.
     */
    private void getLocationPermission() {
        /*
         * Request location permission, so that we can get the location of the
         * device. The result of the permission request is handled by a callback,
         * onRequestPermissionsResult.
         */
        if (ContextCompat.checkSelfPermission(this.getApplicationContext(),
                android.Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            locationPermissionGranted = true;
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                    PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);
        }
    }

    /**
     * Manipula o resultado da solicitação de permissões de localização.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        locationPermissionGranted = false;
        if (requestCode
                == PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION) {// If request is cancelled, the result arrays are empty.
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                locationPermissionGranted = true;
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
        updateLocationUI();
    }

    /**
     * Atualiza as configurações de interface do usuário do mapa com base no fato de o usuário ter
     * concedido permissão de localização.
     */
    private void updateLocationUI() {
        if (googleMapa == null) {
            return;
        }
        try {
            if (locationPermissionGranted) {
                googleMapa.setMyLocationEnabled(true);
                googleMapa.getUiSettings().setMyLocationButtonEnabled(true);
            } else {
                googleMapa.setMyLocationEnabled(false);
                googleMapa.getUiSettings().setMyLocationButtonEnabled(false);
                lastKnownLocation = null;
                getLocationPermission();
            }
        } catch (SecurityException e) {
            Log.e("Exception: %s", e.getMessage());
        }
    }

    @Override
    public void onLocationChanged(@NonNull Location location) {
        // Obtém a velocidade em metros por segundo
        float velocidadeMetrosPorSegundo = location.getSpeed();

        // Converte para km/h
        double velocidadeKmPorHora = velocidadeMetrosPorSegundo * 3.6;

        // Atualiza o TextView com a velocidade em tempo real
        textVelocidade.setText(String.format("%.2f km/h", velocidadeKmPorHora));
    }

    private class DownloadTask extends AsyncTask<String, Void, String> {

        @Override
        protected String doInBackground(String... url) {

            String data = "";

            try {
                data = downloadUrl(url[0]);
            } catch (Exception e) {
                Log.d("Background Task", e.toString());
            }
            return data;
        }

        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);

            ParserTask parserTask = new ParserTask();


            parserTask.execute(result);

        }
    }

    /**
     * Uma classe para analisar o Google Places no formato JSON
     */
    private class ParserTask extends AsyncTask<String, Integer, List<List<HashMap<String, String>>>> {

        // Parsing the data in non-ui thread
        @Override
        protected List<List<HashMap<String, String>>> doInBackground(String... jsonData) {

            List<List<HashMap<String, String>>> routes = null;

            try {
                jObject = new JSONObject(jsonData[0]);
                DirectionsJSONParser parser = new DirectionsJSONParser();

                route = parser.parseString(jObject);

                routes = parser.parse(jObject);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return routes;
        }

        @Override
        protected void onPostExecute(List<List<HashMap<String, String>>> result) {
            ArrayList points = null;
            PolylineOptions lineOptions = null;
            MarkerOptions markerOptions = new MarkerOptions();

            for (int i = 0; i < result.size(); i++) {
                points = new ArrayList();
                lineOptions = new PolylineOptions();

                List<HashMap<String, String>> path = result.get(i);

                for (int j = 0; j < path.size(); j++) {
                    HashMap<String, String> point = path.get(j);

                    double lat = Double.parseDouble(point.get("lat"));
                    double lng = Double.parseDouble(point.get("lng"));
                    LatLng position = new LatLng(lat, lng);

                    points.add(position);
                }

                lineOptions.addAll(points);
                lineOptions.width(12);
                lineOptions.color(Color.RED);
                lineOptions.geodesic(true);
            }

            googleMapa.clear();

            // Drawing polyline in the Google Map for the i-th route
            googleMapa.addPolyline(lineOptions);

            googleMapa.addMarker(new MarkerOptions()
                    .position(origemLatLng)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)));

            //End marker
            MarkerOptions optionsDestino = new MarkerOptions()
                    .position(destinoLatLng)
                    .title("Destino: " + route.get(0).getEndAddressText())
                    .snippet("Distance: " + route.get(0).getDistanceText() + ", Duration: " + route.get(0).getDurationText());

            googleMapa.addMarker(optionsDestino);

            iniciar.setVisibility(View.VISIBLE);
        }
    }

    private String getDirectionsUrl(LatLng origin, LatLng dest) {
        // Origin of route
        String str_origin = "origin=" + origin.latitude + "," + origin.longitude;

        // Destination of route
        String str_dest = "destination=" + dest.latitude + "," + dest.longitude;

        // Sensor enabled
        String sensor = "sensor=false";
        String mode = "mode=driving";
        String key = "key=AIzaSyBGgkO6xusRHK_ryDFUu_HvcixyOFhLsEg";
        // Building the parameters to the web service
        String parameters = str_origin + "&" + str_dest + "&" + sensor + "&" + mode + "&" + key;

        // Output format
        String output = "json";

        // Building the url to the web service
        String url = "https://maps.googleapis.com/maps/api/directions/" + output + "?" + parameters;

        return url;
    }

    /**
     * Um método para baixar dados json do url
     */
    private String downloadUrl(String strUrl) throws IOException {
        String data = "";
        InputStream iStream = null;
        HttpURLConnection urlConnection = null;
        try {
            URL url = new URL(strUrl);

            urlConnection = (HttpURLConnection) url.openConnection();

            urlConnection.connect();

            iStream = urlConnection.getInputStream();

            BufferedReader br = new BufferedReader(new InputStreamReader(iStream));

            StringBuffer sb = new StringBuffer();

            String line = "";
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }

            data = sb.toString();

            br.close();

        } catch (Exception e) {
            Log.d("Exception", e.toString());
        } finally {
            iStream.close();
            urlConnection.disconnect();
        }
        return data;
    }

    private void startTimer() {
        if (!isTimerRunning) {
            isTimerRunning = true;


            handler = new Handler();
            runnable = new Runnable() {
                @Override
                public void run() {
                    seconds++;
                    updateTimerText();
                    handler.postDelayed(this, 1000);
                }
            };

            handler.postDelayed(runnable, 1000);
        }
    }

    private void stopTimer() {
        if (isTimerRunning) {
            isTimerRunning = false;

            if (handler != null && runnable != null) {
                handler.removeCallbacks(runnable);
            }
        }
    }

    private void updateTimerText() {
        int hours = seconds / 3600;
        int minutes = (seconds % 3600) / 60;
        int secs = seconds % 60;

        String time = String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, secs);
        textViewTempo.setText(time);
    }
}
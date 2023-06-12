package com.example.av1avancada;

import android.util.Log;

import com.example.av1avancada.route.Route;
import com.example.av1avancada.route.RouteException;
import com.example.av1avancada.route.Segment;
import com.google.android.gms.maps.model.LatLng;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;


public class DirectionsJSONParser {

    private static final String VALUE = "value";
    private static final String DISTANCE = "distance";
    /**
     * Distance covered. *
     */
    private int distance;

    /* Status code returned when the request succeeded */
    private static final String OK = "OK";

    /** Receives a JSONObject and returns a list of lists containing latitude and longitude */
    public final List<Route> parseString(JSONObject jObject) throws RouteException {
        List<Route> routes = new ArrayList<>();

        try {

            if(!jObject.getString("status").equals(OK)){
                throw new RouteException(jObject);
            }

            JSONArray jsonRoutes = jObject.getJSONArray("routes");

            for (int i = 0; i < jsonRoutes.length(); i++) {
                Route route = new Route();
                //Create an empty segment
                Segment segment = new Segment();

                JSONObject jsonRoute = jsonRoutes.getJSONObject(i);
                //Get the bounds - northeast and southwest
                final JSONObject jsonBounds = jsonRoute.getJSONObject("bounds");
                final JSONObject jsonNortheast = jsonBounds.getJSONObject("northeast");
                final JSONObject jsonSouthwest = jsonBounds.getJSONObject("southwest");

                route.setLatLgnBounds(new LatLng(jsonNortheast.getDouble("lat"), jsonNortheast.getDouble("lng")), new LatLng(jsonSouthwest.getDouble("lat"), jsonSouthwest.getDouble("lng")));

                //Get the leg, only one leg as we don't support waypoints
                final JSONObject leg = jsonRoute.getJSONArray("legs").getJSONObject(0);
                //Get the steps for this leg
                final JSONArray steps = leg.getJSONArray("steps");
                //Number of steps for use in for loop
                final int numSteps = steps.length();
                //Set the name of this route using the start & end addresses
                route.setName(leg.getString("start_address") + " to " + leg.getString("end_address"));
                //Get google's copyright notice (tos requirement)
                route.setCopyright(jsonRoute.getString("copyrights"));
                //Get distance and time estimation
                route.setDurationText(leg.getJSONObject("duration").getString("text"));
                route.setDurationValue(leg.getJSONObject("duration").getInt(VALUE));
                route.setDistanceText(leg.getJSONObject(DISTANCE).getString("text"));
                route.setDistanceValue(leg.getJSONObject(DISTANCE).getInt(VALUE));
                route.setEndAddressText(leg.getString("end_address"));
                //Get the total length of the route.
                route.setLength(leg.getJSONObject(DISTANCE).getInt(VALUE));
                //Get any warnings provided (tos requirement)
                if (!jsonRoute.getJSONArray("warnings").isNull(0)) {
                    route.setWarning(jsonRoute.getJSONArray("warnings").getString(0));
                }

                routes.add(route);
            }

        } catch (JSONException e) {
            throw new RouteException("JSONException. Msg: "+e.getMessage());
        }
        return routes;
    }

    private static String convertStreamToString(final InputStream input) {
        if (input == null) return null;

        final BufferedReader reader = new BufferedReader(new InputStreamReader(input));
        final StringBuilder sBuf = new StringBuilder();

        String line;
        try {
            while ((line = reader.readLine()) != null) {
                sBuf.append(line);
            }
        } catch (IOException e) {
            Log.e("Routing Error", e.getMessage());
        } finally {
            try {
                input.close();
                reader.close();
            } catch (IOException e) {
                Log.e("Routing Error", e.getMessage());
            }
        }
        return sBuf.toString();
    }

    public List<List<HashMap<String,String>>> parse(JSONObject jObject){

        List<List<HashMap<String, String>>> routes = new ArrayList<List<HashMap<String,String>>>() ;
        JSONArray jRoutes = null;
        JSONArray jLegs = null;
        JSONArray jSteps = null;

        try {

            jRoutes = jObject.getJSONArray("routes");

            /** Traversing all routes */
            for(int i=0;i<jRoutes.length();i++){
                jLegs = ( (JSONObject)jRoutes.get(i)).getJSONArray("legs");
                List path = new ArrayList<HashMap<String, String>>();

                /** Traversing all legs */
                for(int j=0;j<jLegs.length();j++){
                    jSteps = ( (JSONObject)jLegs.get(j)).getJSONArray("steps");

                    /** Traversing all steps */
                    for(int k=0;k<jSteps.length();k++){
                        String polyline = "";
                        polyline = (String)((JSONObject)((JSONObject)jSteps.get(k)).get("polyline")).get("points");
                        List list = decodePoly(polyline);

                        /** Traversing all points */
                        for(int l=0;l <list.size();l++){
                            HashMap<String, String> hm = new HashMap<String, String>();
                            hm.put("lat", Double.toString(((LatLng)list.get(l)).latitude) );
                            hm.put("lng", Double.toString(((LatLng)list.get(l)).longitude) );
                            path.add(hm);
                        }
                    }
                    routes.add(path);
                }
            }

        } catch (JSONException e) {
            e.printStackTrace();
        }catch (Exception e){
        }

        return routes;
    }

    /**
     * Method to decode polyline points
     * Courtesy : http://jeffreysambells.com/2010/05/27/decoding-polylines-from-google-maps-direction-api-with-java
     * */
    private List decodePoly(String encoded) {

        List poly = new ArrayList();
        int index = 0, len = encoded.length();
        int lat = 0, lng = 0;

        while (index < len) {
            int b, shift = 0, result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlat = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lat += dlat;

            shift = 0;
            result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlng = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lng += dlng;

            LatLng p = new LatLng((((double) lat / 1E5)),
                    (((double) lng / 1E5)));
            poly.add(p);
        }

        return poly;
    }
}
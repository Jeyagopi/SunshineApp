package com.example.android.sunshine;

import android.media.AsyncPlayer;
import android.net.Uri;
import android.os.AsyncTask;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.format.Time;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    String[] arrayWheather;
    String format = "json";
    String units ="metric";
    String appId = "5efbae2f0a00795300e97d54f805d1c2";
    Integer numDays =7;
    List<String> weeklyWhether;
    ListView lvwForecast;
    ArrayAdapter<String> adapter;

    private final String CLASS_NAME = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
//        arrayWheather = getResources().getStringArray(R.array.dailyWheather);
//        weeklyWhether = new ArrayList<String>(Arrays.asList(arrayWheather));
//
//        lvwForecast = (ListView) findViewById(R.id.lvwForecast);
//        adapter = new ArrayAdapter<String>(this, R.layout.list_item_forecast, R.id.txtListItemForecast, weeklyWhether);
//        lvwForecast.setAdapter(adapter);

        refreshWheather();
    }

    private final String LOG_TAG = FetchWeatherTask.class.getSimpleName();

    /* The date/time conversion code is going to be moved outside the asynctask later,
     * so for convenience we're breaking it out into its own method now.
     */
    private String getReadableDateString(long time){
        // Because the API returns a unix timestamp (measured in seconds),
        // it must be converted to milliseconds in order to be converted to valid date.
        SimpleDateFormat shortenedDateFormat = new SimpleDateFormat("EEE MMM dd");
        return shortenedDateFormat.format(time);
    }

    /**
     * Prepare the weather high/lows for presentation.
     */
    private String formatHighLows(double high, double low) {
        // For presentation, assume the user doesn't care about tenths of a degree.
        long roundedHigh = Math.round(high);
        long roundedLow = Math.round(low);

        String highLowStr = roundedHigh + "/" + roundedLow;
        return highLowStr;
    }

    /**
     * Take the String representing the complete forecast in JSON Format and
     * pull out the data we need to construct the Strings needed for the wireframes.
     *
     * Fortunately parsing is easy:  constructor takes the JSON string and converts it
     * into an Object hierarchy for us.
     */
    private String[] getWeatherDataFromJson(String forecastJsonStr, int numDays)
            throws JSONException {



        // These are the names of the JSON objects that need to be extracted.
        final String OWM_LIST = "list";
        final String OWM_WEATHER = "weather";
        final String OWM_TEMPERATURE = "temp";
        final String OWM_MAX = "max";
        final String OWM_MIN = "min";
        final String OWM_DESCRIPTION = "main";

        JSONObject forecastJson = new JSONObject(forecastJsonStr);
        JSONArray weatherArray = forecastJson.getJSONArray(OWM_LIST);

        // OWM returns daily forecasts based upon the local time of the city that is being
        // asked for, which means that we need to know the GMT offset to translate this data
        // properly.

        // Since this data is also sent in-order and the first day is always the
        // current day, we're going to take advantage of that to get a nice
        // normalized UTC date for all of our weather.

        Time dayTime = new Time();
        dayTime.setToNow();

        // we start at the day returned by local time. Otherwise this is a mess.
        int julianStartDay = Time.getJulianDay(System.currentTimeMillis(), dayTime.gmtoff);

        // now we work exclusively in UTC
        dayTime = new Time();

        String[] resultStrs = new String[numDays];
        for(int i = 0; i < weatherArray.length(); i++) {
            // For now, using the format "Day, description, hi/low"
            String day;
            String description;
            String highAndLow;

            // Get the JSON object representing the day
            JSONObject dayForecast = weatherArray.getJSONObject(i);

            // The date/time is returned as a long.  We need to convert that
            // into something human-readable, since most people won't read "1400356800" as
            // "this saturday".
            long dateTime;
            // Cheating to convert this to UTC time, which is what we want anyhow
            dateTime = dayTime.setJulianDay(julianStartDay+i);
            day = getReadableDateString(dateTime);

            // description is in a child array called "weather", which is 1 element long.
            JSONObject weatherObject = dayForecast.getJSONArray(OWM_WEATHER).getJSONObject(0);
            description = weatherObject.getString(OWM_DESCRIPTION);

            // Temperatures are in a child object called "temp".  Try not to name variables
            // "temp" when working with temperature.  It confuses everybody.
            JSONObject temperatureObject = dayForecast.getJSONObject(OWM_TEMPERATURE);
            double high = temperatureObject.getDouble(OWM_MAX);
            double low = temperatureObject.getDouble(OWM_MIN);

            highAndLow = formatHighLows(high, low);
            resultStrs[i] = day + " - " + description + " - " + highAndLow;
        }

        for (String s : resultStrs) {
            Log.v(LOG_TAG, "Forecast entry: " + s);
        }
        return resultStrs;

    }

    public class FetchWeatherTask extends AsyncTask<String,Void,String[]> {


        @Override
        protected String[] doInBackground(String... params) {
            return connectToNetwork(params[0]);
        }

        protected  void onPostExecure(String[] array){
            arrayWheather = array;
            weeklyWhether = new ArrayList<String>(Arrays.asList(arrayWheather));

            lvwForecast = (ListView) findViewById(R.id.lvwForecast);
            adapter = new ArrayAdapter<String>(this, R.layout.list_item_forecast, R.id.txtListItemForecast, weeklyWhether);
            lvwForecast.setAdapter(adapter);
        }
    }

    private String[] connectToNetwork(String postalcode) {
        HttpURLConnection urlConnection = null;
        BufferedReader reader = null;
        String forecastJsonStr = null;
        try {
            //String baseUrl = "http://api.openweathermap.org/data/2.5/forecast/daily?q=94043&mode=json&units=metric&cnt=7&appid=5efbae2f0a00795300e97d54f805d1c2";

            final String FORECAST_BASE_URL = "http://api.openweathermap.org/data/2.5/forecast/daily?";
            final String QUERY_PARAM ="q";
            final String FORMAT_PARAM ="mode";
            final String UNIT_PARAM ="units";
            final String DAYS_PARAM = "cnt";
            final String APPID_PARAM ="appid";

            Uri builtUri = Uri.parse(FORECAST_BASE_URL).buildUpon()
                            .appendQueryParameter(QUERY_PARAM, postalcode)
                    .appendQueryParameter(FORMAT_PARAM, format)
                    .appendQueryParameter(UNIT_PARAM, units)
                    .appendQueryParameter(DAYS_PARAM, Integer.toString(numDays))
                    .appendQueryParameter(APPID_PARAM, appId)
                    .build();


            URL url = new URL(builtUri.toString());

            Log.v(CLASS_NAME,"BUILT URI " + builtUri);

            urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setRequestMethod("GET");
            urlConnection.connect();

            InputStream inputStream = urlConnection.getInputStream();
            StringBuffer buffer = new StringBuffer();
            if (inputStream == null){
                forecastJsonStr = null;
            }
            reader = new BufferedReader(new InputStreamReader(inputStream));
            String line;
            while ((line = reader.readLine()) != null){
                buffer.append((line +"\n"));

            }
            if(buffer.length() == 0) {
                forecastJsonStr =  null;
            }

            forecastJsonStr  = buffer.toString();



        } catch (IOException ex) {
            Log.e(CLASS_NAME, "ERROR_!!!!", ex);
            forecastJsonStr = null;
        }finally {
            if(urlConnection != null){
                urlConnection.disconnect();
            }
            if (reader != null) {
                try {
                    reader.close();
                } catch (final IOException e) {
                    Log.e(CLASS_NAME, "Error closing stream", e);
                }
            }

            try {
                return getWeatherDataFromJson(forecastJsonStr, numDays);
            } catch (JSONException e) {
                Log.e(LOG_TAG, e.getMessage(), e);
                e.printStackTrace();
            }
            return null;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater =getMenuInflater();
        inflater.inflate(R.menu.main_menu,menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()){
            case (R.id.mnuActionRefresh):
                return refreshWheather();
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private boolean refreshWheather(){
        new FetchWeatherTask().execute("94043");
        return true;
    }
}


package by.styx.coinstatus;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.RoundingMode;
import java.net.URL;
import java.net.URLConnection;
import java.text.DecimalFormat;

import static java.lang.Double.parseDouble;

public class MainActivity extends AppCompatActivity {
    SharedPreferences sharedPreferences;
    JsonAsync jsonAsync;
    FloatingActionButton refreshFab;
    TextView tvUnpaid;
    TextView tvReported;
    TextView tvCurrent;
    TextView tvAverage;
    TextView tvEstimate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        tvUnpaid = (TextView) findViewById(R.id.tvUnpaid);
        tvReported = (TextView) findViewById(R.id.tvReported);
        tvCurrent = (TextView) findViewById(R.id.tvCurrent);
        tvAverage = (TextView) findViewById(R.id.tvAverage);
        tvEstimate = (TextView) findViewById(R.id.tvEstimate);

//        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.addNew);
//        fab.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View view) {
//                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
//                        .setAction("Action", null).show();
//            }
//        });

        refreshFab = (FloatingActionButton) findViewById(R.id.refresh);
        refreshFab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // 0b50a36755058608015fa069a63fa63ab798675c
                String url = "https://ethermine.org/api/miner_new/" + sharedPreferences.getString(SettingsActivity.ETHERMINE_ETC, "");

                jsonAsync = new JsonAsync();
                jsonAsync.execute(url);
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_settings) {
            this.startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private class JsonAsync extends AsyncTask<Object, Integer, JSONObject> {

        static final String UNPAID = "unpaid";
        static final String ETH_PER_MIN = "ethPerMin";
        static final String AVG_HASHRATE = "avgHashrate";
        static final String REPORTED_HASH_RATE = "reportedHashRate";
        static final String HASH_RATE = "hashRate";
        final static String FETCHING = "Fetching...";
        final static String FAILED = "Failed";
        static final String READY = "Ready";

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            refreshFab.setEnabled(false);
            setFields(FETCHING);
        }

        @Override
        protected JSONObject doInBackground(Object... params) {
            URLConnection urlConn;
            BufferedReader bufferedReader = null;
            try {
                URL url = new URL((String) params[0]);
                urlConn = url.openConnection();
                bufferedReader = new BufferedReader(new InputStreamReader(urlConn.getInputStream()));

                StringBuilder stringBuilder = new StringBuilder();
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    stringBuilder.append(line);
                }

                return new JSONObject(stringBuilder.toString());
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            } finally {
                if (bufferedReader != null) {
                    try {
                        bufferedReader.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        private String getUnpaid(JSONObject json) {
            double c = 1000000000000000000.0;

            try {
                DecimalFormat df = new DecimalFormat("#.#####");
                df.setRoundingMode(RoundingMode.CEILING);

                return df.format(parseDouble(json.getString(UNPAID)) / c);
            } catch (JSONException ex) {
                return FAILED;
            }
        }

        private String getReportedHashRate(JSONObject json) {
            try {
                return json.getString(REPORTED_HASH_RATE);
            } catch (JSONException ex) {
                return FAILED;
            }
        }

        private String getCurrentHashRate(JSONObject json) {
            try {
                return json.getString(HASH_RATE);
            } catch (JSONException ex) {
                return FAILED;
            }
        }

        private String getAverageHashRate(JSONObject json) {
            double c = 1000000.0;

            try {
                DecimalFormat df = new DecimalFormat("#.#");
                df.setRoundingMode(RoundingMode.CEILING);

                return df.format(parseDouble(json.getString(AVG_HASHRATE)) / c) + " MH/s";
            } catch (JSONException ex) {
                return FAILED;
            }
        }

        private String getEstimate(JSONObject json) {
            double c = 1000000000000000000.0;

            try {
                DecimalFormat df = new DecimalFormat("#.##");
                df.setRoundingMode(RoundingMode.CEILING);

                double unpaid = parseDouble(json.getString(UNPAID)) / c;
                double ethPerMin = Double.parseDouble(json.getString(ETH_PER_MIN));

                if (unpaid >= 1) {
                    return READY;
                }

                double minutes = (1 - unpaid) / ethPerMin;
                if (minutes <= 60) {
                    return df.format(minutes) + " mins";
                }

                double hours = minutes / 60;
                if (hours <= 24) {
                    return df.format(hours) + " hours";
                }

                double days = Math.floor(hours / 24);
                return df.format(days) + "d " + String.valueOf((int) Math.ceil(hours - days * 24)) + " h";
            } catch (JSONException ex) {
                return FAILED;
            }
        }

        private void setFields(String s) {
            tvUnpaid.setText(s);
            tvReported.setText(s);
            tvCurrent.setText(s);
            tvAverage.setText(s);
            tvEstimate.setText(s);
        }

        @Override
        protected void onPostExecute(JSONObject response) {
            super.onPostExecute(response);

            refreshFab.setEnabled(true);

            if (response == null) {
                setFields(FAILED);
                return;
            }

            tvUnpaid.setText(getUnpaid(response));
            tvReported.setText(getReportedHashRate(response));
            tvCurrent.setText(getCurrentHashRate(response));
            tvAverage.setText(getAverageHashRate(response));
            tvEstimate.setText(getEstimate(response));
        }

        @Override
        protected void onCancelled() {
            super.onCancelled();
            refreshFab.setEnabled(true);
        }

        @Override
        protected void onCancelled(JSONObject jsonObject) {
            super.onCancelled(jsonObject);
            refreshFab.setEnabled(true);
        }
    }
}

package de.blankedv.sx4monitor;

import java.util.Timer;
import java.util.TimerTask;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.TableRow.LayoutParams;
import android.graphics.Color;
import android.graphics.Typeface;
import android.widget.Toast;


import static de.blankedv.sx4monitor.SX4MonitorApplication.*;

public class MainActivity extends AppCompatActivity {


    final int NCOL = 14;  // to display 7 SX-ADR/SX-Data columns
    final int NROW = 16;  // to display 16 SX Values
    TableLayout tl;
    TextView tvTitle;
    volatile TextView[] tvSxData = new TextView[112];


    AlertDialog.Builder builder;

    private boolean forceDisplay = true;
    private final String APPSTRING = "MainActivity";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        tvTitle = (TextView) findViewById(R.id.tvTitle);
        /* FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });  */

        tl = (TableLayout) findViewById(R.id.maintable);

        addMainDataTable();

        builder = new AlertDialog.Builder(this);
        builder.setMessage("Are you sure you want to exit?")
                .setCancelable(false)
                .setPositiveButton("Yes",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                shutdownSXClient();
                                try {
                                    Thread.sleep(100);
                                } catch (InterruptedException e) {
                                    // TODO Auto-generated catch block
                                    e.printStackTrace();
                                }
                                finish();
                            }
                        })
                .setNegativeButton("No", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }
                });

        pauseTimer = false;
        final Timer timer = new Timer();
        final TimerTask task = new TimerTask() {
            @Override
            public void run() {
                if (!pauseTimer) {
                    counter++;
                    updatetimer();
                }
            }
        };

        timer.scheduleAtFixedRate(task, 1000, 1000);


    }


    private void updatetimer() {
        if (DEBUG) Log.d(TAG, "update called");

        runOnUiThread(new Runnable() {
            public void run() {

                if ((System.currentTimeMillis() - mLastMessage) > TIMEOUT_MILLISECS) {
                    for (int i = 0; i <= MAX_DISPLAY_CHAN; i++) {
                        tvSxData[i].setText("--------");
                        tvSxData[i].setTextColor(Color.GRAY);
                    }
                } else {
                    for (int i = 0; i <= MAX_DISPLAY_CHAN; i++) {
                        if (forceDisplay || (oldData[i] != sxData[i])) {
                            oldData[i] = sxData[i];
                            tvSxData[i].setText(SXBinaryString(oldData[i]));
                            tvSxData[i].setTextColor(Color.RED);
                            colorMark[i] = 5;
                            if (DEBUG) {
                                Log.d(TAG, "changed ch=" + i + " d=" + oldData[i]);
                            }
                        } else {
                            tvSxData[i].setText(SXBinaryString(oldData[i]));
                            if (colorMark[i] >= 1) {
                                colorMark[i]--;
                                tvSxData[i].setTextColor(Color.RED);
                            } else { // reset color
                                if (oldData[i] == 0) {
                                    tvSxData[i].setTextColor(Color.GRAY);
                                } else {
                                    tvSxData[i].setTextColor(Color.BLACK);
                                }
                            }
                        }
                    }
                }
                if (DEBUG) {
                    tvSxData[111].setText("debug on");
                    tvSxData[111].setTextColor(Color.RED);
                }
                //Toast.makeText(getApplicationContext(), "Data: ", Toast.LENGTH_SHORT).show();
                if (globalPower) {
                    tvTitle.setText("SX0 Daten - bit 12345678 - Power is ON");
                } else {
                    tvTitle.setText("SX0 Daten - bit 12345678 - Power is OFF");
                }
                forceDisplay = false;
            }

        });
    }

    private String SXBinaryString(int data) {
        StringBuffer s = new StringBuffer("00000000");
        int pos = 0;

        if (data == INVALID_INT) return "--------";   // empty data

        // Selectrix Schreibweise LSB vorn !!
        if ((data & 0x01) == 0x01) {
            s.setCharAt(0 + pos, '1');
        }
        if ((data & 0x02) == 0x02) {
            s.setCharAt(1 + pos, '1');
        }
        if ((data & 0x04) == 0x04) {
            s.setCharAt(2 + pos, '1');
        }
        if ((data & 0x08) == 0x08) {
            s.setCharAt(3 + pos, '1');
        }
        if ((data & 0x10) == 0x10) {
            s.setCharAt(4 + pos, '1');
        }
        if ((data & 0x20) == 0x20) {
            s.setCharAt(5 + pos, '1');
        }
        if ((data & 0x40) == 0x40) {
            s.setCharAt(6 + pos, '1');
        }
        if ((data & 0x80) == 0x80) {
            s.setCharAt(7 + pos, '1');
        }
        return s.toString();
    }


    /**
     * This function adds the main table
     **/
    public void addMainDataTable() {

        int channel = 0;
        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(this);


        String ff = prefs.getString(KEY_FONT_FACTOR, "1.0");
        Log.d(TAG, APPSTRING + " - font factor =" + ff);
        float f1 = Float.parseFloat(ff);

        tvTitle.setTextSize(14f*f1);

        for (int j = 0; j < NROW; j++) {   // create a new row of data
            TableRow tr = new TableRow(this);
            for (int i = 0; i < NCOL; i = i + 2) {
                /** Creating a TextView to add to the row **/
                TextView col = new TextView(this);
                channel = j + (i * 8);
                col.setText("" + channel);
                col.setTextColor(Color.GRAY);
                col.setGravity(Gravity.CENTER_VERTICAL);
                col.setTypeface(Typeface.DEFAULT);
                col.setTextSize(10.0f*f1);
                //col.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
                col.setLayoutParams(new LayoutParams(0, LayoutParams.FILL_PARENT, 1f));
                col.setPadding(3, 3, 3, 3);
                tr.addView(col);  // Adding textView to tablerow.

                /** Creating a TextView to add to the row **/
                TextView tvData = new TextView(this);
                if (channel <= MAX_DISPLAY_CHAN) {
                    tvData.setText("--------");
                } else {
                    tvData.setText("    -   ");
                }
                tvData.setTextColor(Color.GRAY);
                tvData.setTextSize(14.0f*f1);
                tvData.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
                tvData.setLayoutParams(new LayoutParams(0, LayoutParams.FILL_PARENT, 3f));
                tvData.setPadding(3, 3, 3, 3);
                tr.addView(tvData);  // Adding textView to tablerow.
                tvSxData[channel] = tvData;  // store for later use
            }

            // add row to table
            tl.addView(tr, new TableLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    LayoutParams.WRAP_CONTENT));

        }


    }

    @Override
    protected void onPause() {
        super.onPause();
        if (DEBUG)
            Log.d(TAG, "onPause - MainActivity");
        // firstStart=false; // flag to avoid re-connection call during first
        // start
        //sendQ.add(DISCONNECT);
        // ((AndroPanelApplication) getApplication()).saveZoomEtc();
        // client.shutdown();
        pauseTimer = true;
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (fontFactorChanged) {
            tl.removeAllViews();
            addMainDataTable();
            fontFactorChanged = false;
        }

        if (DEBUG)
            Log.d(TAG, "onResume - MainActivity");

        startSXNetCommunication();

        forceDisplay = true; // refresh display
        pauseTimer = false;
    }


    public void shutdownSXClient() {
        Log.d(TAG, "MainActivity - shutting down SXnet Client.");
        if (client != null)
            client.shutdown();
        if (client != null)
            client.disconnectContext();
        client = null;

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            Intent intent = new Intent(this, Preferences.class);
            startActivity(intent);
            return true;
        } else if (id == R.id.action_about) {
            Intent intent = new Intent(this, AboutActivity.class);
            startActivity(intent);
            return (true);
        } else if (id == R.id.action_reconnect) {
            startSXNetCommunication();
            forceDisplay = true; // refresh display
            pauseTimer = false;
            Toast.makeText(this, "reconnect", Toast.LENGTH_SHORT).show();
        } else if (id == R.id.action_exit) {
            AlertDialog alert = builder.create();
            alert.show();
            return (true);
        }

        return super.onOptionsItemSelected(item);
    }

    public void startSXNetCommunication() {
        Log.d(TAG, APPSTRING + " - startSXNetCommunication.");
        if (client != null) {
            client.shutdown();
            try {
                Thread.sleep(100); // give client some time to shut down.
            } catch (InterruptedException e) {
                if (DEBUG)
                    Log.e(TAG, "could not sleep...");
            }
        }

        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(this);
        String ip = prefs.getString(KEY_IP, SXNET_START_IP);

        if (DEBUG) Log.d(TAG, "connecting to " + ip);
        client = new SXnetClientThread(this, ip, SXNET_PORT);
        client.start();

    }

}

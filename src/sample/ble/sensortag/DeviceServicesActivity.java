package sample.ble.sensortag;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.content.Intent;
import android.location.Criteria;
import android.location.Location;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ExpandableListView;
import android.widget.SimpleExpandableListAdapter;
import android.widget.TextView;
import android.widget.Toast;

import sample.ble.sensortag.adapters.BleDevicesAdapter;
import sample.ble.sensortag.adapters.TiServicesAdapter;
import sample.ble.sensortag.ble.BleDevicesScanner;
import sample.ble.sensortag.config.AppConfig;
import sample.ble.sensortag.fusion.SensorFusionActivity;
import sample.ble.sensortag.sensor.TiSensor;
import sample.ble.sensortag.sensor.TiSensors;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;

/**
 * For a given BLE device, this Activity provides the user interface to connect, display data,
 * and display GATT services and characteristics supported by the device.  The Activity
 * communicates with {@code BleService}, which in turn interacts with the
 * Bluetooth LE API.
 */
public class DeviceServicesActivity extends BleServiceBindingActivity
                                    implements ExpandableListView.OnChildClickListener,
                                               TiServicesAdapter.OnServiceItemClickListener {
    @SuppressWarnings("UnusedDeclaration")
    private final static String TAG = DeviceServicesActivity.class.getSimpleName();

    private TextView dataField;
    private ExpandableListView gattServicesList;
    private TiServicesAdapter gattServiceAdapter;
    private BluetoothDevice device;

    private BleDevicesScanner scanner;
    private BleDevicesAdapter leDeviceListAdapter;
    private BluetoothAdapter bluetoothAdapter;

    private int rssi;

    private TiSensor<?> activeSensor;

    TextView calDistView, ptsRecView;
    EditText actDistEdit;

    private final HashMap<BluetoothDevice, Integer> rssiMap = new HashMap<BluetoothDevice, Integer>();

    //***************************************************************** file
    String actDistString;
    float actDistance, calDistance;
    int ptIdx;
    String fullyCompiledString = "";
    File reportDirectoryName;
    File myFile;
    boolean recording;
    Calendar myCalendar;
    FileOutputStream myOutWriter;

    String BLELogFileName = "BLE_Distance_LOG.txt";
    //*****************************************************************

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.device_services_activity);

        calDistView = (TextView) findViewById(R.id.calcDistVal);
        ptsRecView = (TextView) findViewById(R.id.pointsRecVal);
        actDistEdit = (EditText) findViewById(R.id.actualDistVal);

        gattServicesList = (ExpandableListView) findViewById(R.id.gatt_services_list);
        gattServicesList.setOnChildClickListener(this);
        final View emptyView = findViewById(R.id.empty_view);
        gattServicesList.setEmptyView(emptyView);

        //dataField = (TextView) findViewById(R.id.data_value);

        //dataField = (TextView) findViewById(R.id.data_value);
        device = getDevice();
        getActionBar().setTitle(getDeviceName());
        getActionBar().setSubtitle(getDeviceAddress());
        getActionBar().setDisplayHomeAsUpEnabled(true);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        final String deviceName = getDeviceName();
        if (deviceName != null) {
            getMenuInflater().inflate(R.menu.gatt_services, menu);

            // enable demo for SensorTag device only
            menu.findItem(R.id.menu_demo).setEnabled(
                    deviceName.startsWith(AppConfig.BLE_DEVICE_NAME));

        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.menu_demo:
                final Intent demoIntent = new Intent();
                demoIntent.setClass(DeviceServicesActivity.this, SensorFusionActivity.class);
                demoIntent.putExtra(SensorFusionActivity.EXTRA_DEVICE_ADDRESS, getDeviceAddress());
                startActivity(demoIntent);
                break;
            case android.R.id.home:
                onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /*@Override
    public void onDisconnected() {
        finish();
    }*/

    @Override
    public void onServiceDiscovered() {
        // Show all the supported services and characteristics on the user interface.
        displayGattServices(getBleService().getSupportedGattServices());
    }

    @Override
    public void onDataAvailable(String serviceUuid, String characteristicUUid, String text, byte[] data) {
        dataField.setText(text);
    }

    @Override
    public boolean onChildClick(ExpandableListView parent, View v, int groupPosition,
                                int childPosition, long id) {
        if (gattServiceAdapter == null)
            return false;

        final BluetoothGattCharacteristic characteristic = gattServiceAdapter.getChild(groupPosition, childPosition);
        final TiSensor<?> sensor = TiSensors.getSensor(characteristic.getService().getUuid().toString());

        if (activeSensor != null)
            getBleService().enableSensor(activeSensor, false);

        if (sensor == null) {
            getBleService().getBleManager().readCharacteristic(characteristic);
            return true;
        }

        if (sensor == activeSensor)
            return true;

        activeSensor = sensor;
        getBleService().enableSensor(sensor, true);
        return true;
    }

    @Override
    public void onServiceUpdated(BluetoothGattService service) {
        final TiSensor<?> sensor = TiSensors.getSensor(service.getUuid().toString());
        if (sensor == null)
            return;

        getBleService().updateSensor(sensor);
    }

    private void displayGattServices(List<BluetoothGattService> gattServices) {
        if (gattServices == null)
            return;

        gattServiceAdapter = new TiServicesAdapter(this, gattServices);
        gattServiceAdapter.setServiceListener(this);
        gattServicesList.setAdapter(gattServiceAdapter);
    }

/*
    //***************************************************************** file
    String distance;    //wayPoint
    String fullyCompiledString = "";
    String currentString = "";
    File reportDirectoryName;
    File myFile;
    BufferedWriter myOutWriter;

    String BLELogFileName = "BLE_Distance_LOG.txt";
    //*****************************************************************
*/

    public void startRecording(View view)   //opens a file
    {
        Toast.makeText(getBaseContext(), "Begin Recording...", Toast.LENGTH_SHORT).show();
        recording = true;
    }

    public void update(View view)   //gets the most recent signal strength measurement and updates the displayed values
    {
        //rssiMap.put(device, rssi);        //pass the bluetooth device to get the db level
        // initialize scanner
        /*
        scanner = new BleDevicesScanner(bluetoothAdapter, new BluetoothAdapter.LeScanCallback() {
            @Override
            public void onLeScan(final BluetoothDevice mdevice, final int mrssi, byte[] scanRecord) {
                leDeviceListAdapter.addDevice(mdevice, mrssi);
                leDeviceListAdapter.notifyDataSetChanged();
                if(mdevice == device)
                {
                    Toast.makeText(getBaseContext(),
                            "Strength: " + mrssi + " db",
                            Toast.LENGTH_SHORT).show();
                }
            }
        });
        */
        //scanner.setScanPeriod(SCAN_PERIOD);
        if(recording)
        {
            Toast.makeText(getBaseContext(), "Updating...", Toast.LENGTH_SHORT).show();
        }
    }

    public void recordPoint(View view)  //records a new measurement by adding the most recent value to the fully compiled string
    {
        if(recording)
        {
            Log.d("Devon Test", "logging...");
            actDistString = actDistEdit.getText().toString();   //gets the distance set by the user as string
            actDistance = Float.parseFloat(actDistString);      //convert to float
            fullyCompiledString.concat(ptIdx + " Actual: " + actDistance + "Calculated: " + calDistance + "\n");   //concatenate the most recent measurement to the existing data
        }
    }

    public void endRecording(View view) //save file and close
    {
        Toast.makeText(getBaseContext(),
                "Saving BLE File...",
                Toast.LENGTH_SHORT).show();
        try
        {
            //BLELogFileName = "BLELog" + myCalendar.getTimeInMillis() + ".txt";  //
            BLELogFileName = "BLELog.txt";
            Log.d("Dev", BLELogFileName);
            /*
            reportDirectoryName = new File(Environment.getRootDirectory(), "/BLELog_Files/");
            Log.d("Dev", "2");
            if(!reportDirectoryName.exists()){
                Log.d("Dev", "3");
                reportDirectoryName.mkdirs();
                Log.d("Dev", "4");
            }
            Log.d("Dev", "5");
            myFile = new File(reportDirectoryName, BLELogFileName);
            Log.d("Dev", "6");
            myFile.createNewFile();
            */
            Log.d("Dev", "7");
            myOutWriter = openFileOutput(BLELogFileName, Context.MODE_PRIVATE);
            Log.d("Dev", "8");

            try     //write all the data to the file
            {
                Log.d("Dev", "9");
                myOutWriter.write(fullyCompiledString.getBytes());
                Log.d("Dev", "10");
            }
            catch (Exception e) {
                Toast.makeText(getBaseContext(), "Error writing to GPS File",
                        Toast.LENGTH_SHORT).show();
            }

            myOutWriter.close();   //then close the output stream

            Toast.makeText(getBaseContext(),
                    "BLE File Saved.",
                    Toast.LENGTH_SHORT).show();
        }
        catch (Exception e) {
            Toast.makeText(getBaseContext(), "Error opening or closing BLE records File",
                    Toast.LENGTH_SHORT).show();
        }
        recording = false;
/*
        try {//
            Toast.makeText(getBaseContext(),
                    "BLE File successfully saved",
                    Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(getBaseContext(), "Error saving BLE File",
                    Toast.LENGTH_SHORT).show();
        }
*/
    }

}

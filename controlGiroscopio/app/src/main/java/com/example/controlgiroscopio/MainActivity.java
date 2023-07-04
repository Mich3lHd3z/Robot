package com.example.controlgiroscopio;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_BLUETOOTH_PERMISSION = 2;
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private BluetoothAdapter bluetoothAdapter;
    private Set<BluetoothDevice> pairedDevices;
    private BluetoothDevice connectedDevice;
    private BluetoothSocket bluetoothSocket;
    private OutputStream outputStream;

    private SensorManager sensorManager;
    private Sensor gyroscopeSensor;

    private boolean sendingData = false;
    private Handler repeatHandler;
    private boolean repeatFlag = false;

    private TextView txtStatus;
    private Button btnConnectBluetooth;
    private Button btnDetener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        txtStatus = findViewById(R.id.txtStatus);
        btnConnectBluetooth = findViewById(R.id.btnConnectBluetooth);
        btnDetener = findViewById(R.id.btnDetener);

        // Inicializar Bluetooth
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Este dispositivo no soporta Bluetooth", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Verificar y solicitar permisos necesarios
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN,
                    Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_BLUETOOTH_PERMISSION);
        }

        // Establecer listener para el botón de conexión
        btnConnectBluetooth.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                connectBluetooth();
            }
        });

        // Establecer listener para el botón de detener
        btnDetener.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopSendingData();
            }
        });

        // Inicializar el sensor de giroscopio
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            gyroscopeSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Registrar el listener del sensor de giroscopio
        if (gyroscopeSensor != null) {
            sensorManager.registerListener(this, gyroscopeSensor, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Desregistrar el listener del sensor de giroscopio
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }

    private void connectBluetooth() {
        if (!bluetoothAdapter.isEnabled()) {
            // Si el Bluetooth no está habilitado, solicitar habilitación al usuario
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        } else {
            // Si el Bluetooth está habilitado, mostrar lista de dispositivos emparejados
            showPairedDevicesDialog();
        }
    }

    private void showPairedDevicesDialog() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        pairedDevices = bluetoothAdapter.getBondedDevices();
        if (pairedDevices.size() > 0) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Seleccione un dispositivo");
            List<String> deviceList = new ArrayList<>();
            for (BluetoothDevice device : pairedDevices) {
                deviceList.add(device.getName());
            }
            final CharSequence[] items = deviceList.toArray(new CharSequence[deviceList.size()]);
            builder.setItems(items, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    String selectedDeviceName = items[which].toString();
                    for (BluetoothDevice device : pairedDevices) {
                        if (device.getName().equals(selectedDeviceName)) {
                            connectToDevice(device);
                            break;
                        }
                    }
                }
            });
            builder.show();
        } else {
            Toast.makeText(this, "No se encontraron dispositivos emparejados", Toast.LENGTH_SHORT).show();
        }
    }

    private void connectToDevice(BluetoothDevice device) {
        try {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return;
            }
            bluetoothSocket = device.createRfcommSocketToServiceRecord(MY_UUID);
            bluetoothSocket.connect();
            outputStream = bluetoothSocket.getOutputStream();
            connectedDevice = device;
            txtStatus.setText("Conectado a: " + device.getName());
            txtStatus.setTextColor(Color.GREEN);
            // Iniciar el envío de datos
            startSendingData();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error al conectar al dispositivo", Toast.LENGTH_SHORT).show();
        }
    }

    private void startSendingData() {
        sendingData = true;
        repeatHandler = new Handler();
        repeatFlag = true;
        repeatHandler.post(new Runnable() {
            @Override
            public void run() {
                if (repeatFlag) {
                    // Obtener los valores del giroscopio
                    float x = gyroscopeX;
                    float y = gyroscopeY;
                    float z = gyroscopeZ;
                    sendData(x, y, z);
                    repeatHandler.postDelayed(this, 1000); // Envío de datos cada 1 segundo
                }
            }
        });
    }

    private void stopSendingData() {
        sendingData = false;
        repeatFlag = false;
        if (repeatHandler != null) {
            repeatHandler.removeCallbacksAndMessages(null);
            repeatHandler = null;
        }
    }

    private void sendData(String data) {
        if (outputStream != null) {
            try {
                outputStream.write(data.getBytes());
                outputStream.flush();
            } catch (IOException e) {
                e.printStackTrace();
                Toast.makeText(this, "Error al enviar datos", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "No se ha establecido una conexión Bluetooth", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];
            // Realizar acciones según los valores del giroscopio
            // Ejemplo: si x > 0, enviar señal de movimiento hacia la derecha
            //          si x < 0, enviar señal de movimiento hacia la izquierda
            //          si y > 0, enviar señal de movimiento hacia arriba
            //          si y < 0, enviar señal de movimiento hacia abajo
            //          si z > 0, enviar señal de movimiento hacia adelante
            //          si z < 0, enviar señal de movimiento hacia atrás
            // Aquí puedes definir tus propias acciones y lógica para los valores del giroscopio
            String data = "X: " + x + " Y: " + y + " Z: " + z;
            sendData(data);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {

    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // No se necesita implementar en este ejemplo
    }
}

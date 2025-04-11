package com.example.polarproject;

import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Build;
import android.Manifest;
import android.util.Log;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import io.reactivex.rxjava3.annotations.NonNull;
import io.reactivex.rxjava3.disposables.Disposable;

import com.polar.androidcommunications.api.ble.model.DisInfo;
import com.polar.sdk.api.PolarBleApi;
import com.polar.sdk.api.PolarBleApiCallback;
import com.polar.sdk.api.PolarBleApiDefaultImpl;
import com.polar.sdk.api.errors.PolarInvalidArgument;
import com.polar.sdk.api.model.PolarDeviceInfo;
import com.polar.sdk.api.model.PolarHealthThermometerData;
import com.polar.sdk.api.model.PolarHrData;
import com.polar.sdk.api.model.PolarSensorSetting;
import com.polar.sdk.api.model.PolarTemperatureData;


import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "Polar Project";
    private Disposable broadcastDisposable = null;
    private PolarBleApi api;
    // Defina o código da requisição de permissão
    private static final int PERMISSION_REQUEST_CODE = 100;  // Você pode usar qualquer valor único aqui
    String deviceId = "";

    @SuppressLint("CheckResult")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);



        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        requestPermissions(
                new String[]{
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_CONNECT
                },
                PERMISSION_REQUEST_CODE
        );

        Set<PolarBleApi.PolarBleSdkFeature> features = new HashSet<>();
        features.add(PolarBleApi.PolarBleSdkFeature.FEATURE_HR);
        features.add(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_SDK_MODE);
        features.add(PolarBleApi.PolarBleSdkFeature.FEATURE_BATTERY_INFO);
        features.add(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_H10_EXERCISE_RECORDING);
        features.add(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_RECORDING);
        features.add(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING);
        features.add(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_DEVICE_TIME_SETUP);
        features.add(PolarBleApi.PolarBleSdkFeature.FEATURE_DEVICE_INFO);
        features.add(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_TEMPERATURE_DATA);

        api = PolarBleApiDefaultImpl.defaultImplementation(
                getApplicationContext(),features
        );

        api.setApiCallback(new PolarBleApiCallback() {
            @Override
            public void htsNotificationReceived(@androidx.annotation.NonNull String s, @androidx.annotation.NonNull PolarHealthThermometerData polarHealthThermometerData) {

            }

            @Override
            public void disInformationReceived(@androidx.annotation.NonNull String s, @androidx.annotation.NonNull DisInfo disInfo) {

            }

            @Override
            public void blePowerStateChanged(boolean powered) {
                Log.d(TAG, "BLE power: " + powered);
            }

            @Override
            public void deviceConnected(PolarDeviceInfo polarDeviceInfo) {
                Log.d(TAG, "CONNECTED: " + polarDeviceInfo.getDeviceId());
                deviceId=polarDeviceInfo.getDeviceId();
            }

            @Override
            public void deviceConnecting(PolarDeviceInfo polarDeviceInfo) {
                Log.d(TAG, "CONNECTING: " + polarDeviceInfo.getDeviceId());
            }

            @Override
            public void deviceDisconnected(PolarDeviceInfo polarDeviceInfo) {
                Log.d(TAG, "DISCONNECTED: " + polarDeviceInfo.getDeviceId());
            }

            @Override
            public void bleSdkFeatureReady(String identifier, PolarBleApi.PolarBleSdkFeature feature) {
                Log.d(TAG, "Polar BLE SDK feature " + feature + " is ready");
            }

            public void disInformationReceived(String identifier, UUID uuid, String value) {
                Log.d(TAG, "DIS INFO uuid: " + uuid + " value: " + value);
            }

            @Override
            public void batteryLevelReceived(String identifier, int level) {
                Log.d(TAG, "BATTERY LEVEL: " + level);
            }
        });

        api.autoConnectToDevice(-50, null, null).subscribe();



        printTemperature();
        printHR();
    }


    public void printTemperature(){
        if (broadcastDisposable == null || broadcastDisposable.isDisposed()) {
            Disposable requestSettings = api.requestStreamSettings(deviceId, PolarBleApi.PolarDeviceDataType.SKIN_TEMPERATURE).subscribe(settings -> {
                        PolarSensorSetting sensorSetting = settings.maxSettings();
                        broadcastDisposable = api.startSkinTemperatureStreaming(deviceId, sensorSetting)
                                .subscribe(
                                        data -> Log.w("Polar", "Temperatura da pele: " + data),
                                        error -> Log.e("Polar", "Erro ao iniciar stream: " + error.getMessage())
                                );
                    },
                    error -> {
                        Log.e("Polar", "Dispositivo NÃO suporta SKIN_TEMPERATURE: " + error.getMessage());
                    });
        } else {
            broadcastDisposable.dispose();
        }
    }

    public void printHR() {
        if (broadcastDisposable == null || broadcastDisposable.isDisposed()) {
            broadcastDisposable = api.startListenForPolarHrBroadcasts(null)
                    .subscribe(
                            polarBroadcastData -> {
                                Log.d(TAG, "HR BROADCAST " + polarBroadcastData.getPolarDeviceInfo().getDeviceId()
                                        + " HR: " + polarBroadcastData.getHr()
                                        + " batt: " + polarBroadcastData.getBatteryStatus());
                            },
                            error -> {
                                Log.e(TAG, "Broadcast listener failed. Reason " + error);
                            },
                            () -> {
                                Log.d(TAG, "complete");
                            }
                    );
        } else {
            broadcastDisposable.dispose();
        }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();
        api.shutDown();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0) {
                for (int i = 0; i < grantResults.length; i++) {
                    if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                        Log.d("GRANTED","PERMISSÃO CONCEDIDA");
                    } else {
                        Log.d("DENIED","PERMISSÃO NEGADA");
                    }
                }
            }
        }
    }
}
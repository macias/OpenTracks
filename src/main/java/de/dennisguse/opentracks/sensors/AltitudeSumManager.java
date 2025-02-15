package de.dennisguse.opentracks.sensors;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.util.concurrent.TimeUnit;

import de.dennisguse.opentracks.data.models.TrackPoint;

/**
 * Estimates the altitude gain and altitude loss using the device's pressure sensor (i.e., barometer).
 */
public class AltitudeSumManager implements SensorConnector, SensorEventListener {

    private static final String TAG = AltitudeSumManager.class.getSimpleName();

    private boolean isConnected = false;

    private float lastAcceptedPressureValue_hPa;

    private float lastSeenSensorValue_hPa;

    private Float altitudeGain_m;
    private Float altitudeLoss_m;

    public void start(Context context, Handler handler) {
        SensorManager sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);

        Sensor pressureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE);
        if (pressureSensor == null) {
            Log.w(TAG, "No pressure sensor available.");
            isConnected = false;
        } else {
            isConnected = sensorManager.registerListener(this, pressureSensor, (int) TimeUnit.SECONDS.toMicros(5), handler);
        }

        lastAcceptedPressureValue_hPa = Float.NaN;
        reset();
    }

    public void stop(Context context) {
        Log.d(TAG, "Stop");

        SensorManager sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        sensorManager.unregisterListener(this);

        isConnected = false;
        reset();
    }

    @VisibleForTesting
    public void setConnected(boolean isConnected) {
        this.isConnected = isConnected;
    }

    public void fill(@NonNull TrackPoint trackPoint) {
        trackPoint.setAltitudeGain(altitudeGain_m);
        trackPoint.setAltitudeLoss(altitudeLoss_m);
    }

    @Nullable
    public Float getAltitudeGain_m() {
        return isConnected ? altitudeGain_m : null;
    }

    @VisibleForTesting
    public void setAltitudeGain_m(float altitudeGain_m) {
        this.altitudeGain_m = altitudeGain_m;
    }

    @VisibleForTesting
    public void addAltitudeGain_m(float altitudeGain_m) {
        this.altitudeGain_m = this.altitudeGain_m == null ? 0f : this.altitudeGain_m;
        this.altitudeGain_m += altitudeGain_m;
    }

    @VisibleForTesting
    public void addAltitudeLoss_m(Float altitudeLoss_m) {
        this.altitudeLoss_m = this.altitudeLoss_m == null ? 0f : this.altitudeLoss_m;
        this.altitudeLoss_m += altitudeLoss_m;
    }

    @Nullable
    public Float getAltitudeLoss_m() {
        return isConnected ? altitudeLoss_m : null;
    }

    @VisibleForTesting
    public void setAltitudeLoss_m(float altitudeLoss_m) {
        this.altitudeLoss_m = altitudeLoss_m;
    }

    public void reset() {
        Log.d(TAG, "Reset");
        altitudeGain_m = null;
        altitudeLoss_m = null;
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        Log.w(TAG, "Sensor accuracy changes are (currently) ignored.");
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (!isConnected) {
            Log.w(TAG, "Not connected to sensor, cannot process data.");
            return;
        }
        onSensorValueChanged(event.values[0]);
    }

    @VisibleForTesting
    void onSensorValueChanged(float value_hPa) {
        if (Float.isNaN(lastAcceptedPressureValue_hPa)) {
            lastAcceptedPressureValue_hPa = value_hPa;
            lastSeenSensorValue_hPa = value_hPa;
            return;
        }

        altitudeGain_m = altitudeGain_m != null ? altitudeGain_m : 0;
        altitudeLoss_m = altitudeLoss_m != null ? altitudeLoss_m : 0;

        PressureSensorUtils.AltitudeChange altitudeChange = PressureSensorUtils.computeChangesWithSmoothing_m(lastAcceptedPressureValue_hPa, lastSeenSensorValue_hPa, value_hPa);
        if (altitudeChange != null) {
            altitudeGain_m += altitudeChange.getAltitudeGain_m();

            altitudeLoss_m += altitudeChange.getAltitudeLoss_m();

            lastAcceptedPressureValue_hPa = altitudeChange.getCurrentSensorValue_hPa();
        }

        lastSeenSensorValue_hPa = value_hPa;

        Log.v(TAG, "altitude gain: " + altitudeGain_m + ", altitude loss: " + altitudeLoss_m);
    }
}

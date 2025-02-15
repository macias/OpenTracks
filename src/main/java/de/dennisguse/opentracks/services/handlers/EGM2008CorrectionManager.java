package de.dennisguse.opentracks.services.handlers;

import android.content.Context;
import android.util.Log;

import java.io.IOException;

import de.dennisguse.opentracks.data.models.TrackPoint;
import de.dennisguse.opentracks.util.EGM2008Utils;

public class EGM2008CorrectionManager {

    private static final String TAG = EGM2008CorrectionManager.class.getSimpleName();

    private EGM2008Utils.EGM2008Correction egm2008Correction;

    public void correctAltitude(Context context, TrackPoint trackPoint) {
        if (!trackPoint.hasLocation() || !trackPoint.hasAltitude()) {
            Log.d(TAG, "No altitude correction necessary.");
            return;
        }

        if (egm2008Correction == null || !egm2008Correction.canCorrect(trackPoint.getLocation())) {
            try {
                egm2008Correction = EGM2008Utils.createCorrection(context, trackPoint.getLocation());
            } catch (IOException e) {
                Log.e(TAG, "Could not load altitude correction for " + trackPoint, e);
                return;
            }
        }

        trackPoint.setAltitude(egm2008Correction.correctAltitude(trackPoint.getLocation()));
    }
}
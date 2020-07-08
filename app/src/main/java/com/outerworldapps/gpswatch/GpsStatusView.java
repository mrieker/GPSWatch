//    Copyright (C) 2015, Mike Rieker, Beverly, MA USA
//    www.outerworldapps.com
//
//    This program is free software; you can redistribute it and/or modify
//    it under the terms of the GNU General Public License as published by
//    the Free Software Foundation; version 2 of the License.
//
//    This program is distributed in the hope that it will be useful,
//    but WITHOUT ANY WARRANTY; without even the implied warranty of
//    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//    GNU General Public License for more details.
//
//    EXPECT it to FAIL when someone's HeALTh or PROpeRTy is at RISk.
//
//    You should have received a copy of the GNU General Public License
//    along with this program; if not, write to the Free Software
//    Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
//
//    http://www.gnu.org/licenses/gpl-2.0.html

package com.outerworldapps.gpswatch;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.hardware.GeomagneticField;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.GnssStatus;
import android.location.Location;
import android.util.AttributeSet;
import android.view.View;

import java.util.Locale;

/**
 * Display a GPS status panel.
 * Also acts as a compass.
 */
public class GpsStatusView
        extends View
        implements SensorEventListener {

    private final static boolean USECOMPASS = true;
    private final static String[] compDirs = new String[] { "N", "E", "S", "W" };

    private float compRotDeg;  // compass rotation
    private float magvariation;
    private float[] geomag;
    private float[] gravity;
    private float[] orient = new float[3];
    private float[] rotmat = new float[9];
    private GnssStatus satellites;
    private Location location;
    private Paint ignoredSpotsPaint = new Paint ();
    private Paint ringsPaint        = new Paint ();
    private Paint textPaint         = new Paint ();
    private Paint trianglePaint     = new Paint ();
    private Paint usedSpotsPaint    = new Paint ();
    private Path  trianglePath;

    public GpsStatusView (Context ctx, AttributeSet attrs)
    {
        super (ctx, attrs);
        construct ();
    }

    public GpsStatusView (Context ctx)
    {
        super (ctx);
        construct ();
    }

    public void construct ()
    {
        ringsPaint.setColor (Color.YELLOW);
        ringsPaint.setStyle (Paint.Style.STROKE);
        ringsPaint.setStrokeWidth (2);

        trianglePaint.setColor (Color.MAGENTA);
        trianglePaint.setStyle (Paint.Style.FILL_AND_STROKE);
        trianglePaint.setTextAlign (Paint.Align.CENTER);

        usedSpotsPaint.setColor (Color.GREEN);
        usedSpotsPaint.setStyle (Paint.Style.FILL);

        ignoredSpotsPaint.setColor (Color.CYAN);
        ignoredSpotsPaint.setStyle (Paint.Style.STROKE);

        textPaint.setColor (Color.WHITE);
        textPaint.setStrokeWidth (3);
        textPaint.setTextAlign (Paint.Align.CENTER);
    }

    public void Startup ()
    {
        geomag     = null;
        gravity    = null;
        compRotDeg = Float.NaN;
        trianglePaint.setTextSize (24.0F);
        textPaint.setTextSize (24.0F);
        SensorManager instrSM = getContext ().getSystemService (SensorManager.class);
        if (USECOMPASS) {
            Sensor smf = instrSM.getDefaultSensor (Sensor.TYPE_MAGNETIC_FIELD);
            Sensor sac = instrSM.getDefaultSensor (Sensor.TYPE_ACCELEROMETER);
            instrSM.registerListener (this, smf, SensorManager.SENSOR_DELAY_UI);
            instrSM.registerListener (this, sac, SensorManager.SENSOR_DELAY_UI);
        }
    }

    /**
     * Got a compass reading.
     */
    @Override  // SensorEventListener
    public void onSensorChanged (SensorEvent event)
    {
        switch (event.sensor.getType ()) {
            case Sensor.TYPE_MAGNETIC_FIELD: {
                geomag = event.values;
                break;
            }
            case Sensor.TYPE_ACCELEROMETER: {
                gravity = event.values;
                break;
            }
        }

        if ((geomag != null) && (gravity != null)) {
            SensorManager.getRotationMatrix (rotmat, null, gravity, geomag);
            SensorManager.getOrientation (rotmat, orient);
            compRotDeg = (float) - Math.toDegrees (orient[0]);
            geomag  = null;
            gravity = null;
            invalidate ();
        }
    }

    @Override  // SensorEventListener
    public void onAccuracyChanged (Sensor sensor, int accuracy)
    { }

    /**
     * Got a GPS satellite status reading.
     */
    public void onStatusReceived (GnssStatus gnssStatus)
    {
        satellites = gnssStatus;
        invalidate ();
    }

    /**
     * Got a GPS location reading.
     */
    public void onLocationReceived (Location loc)
    {
        location = loc;
        GeomagneticField gmf = new GeomagneticField (
                (float) location.getLatitude (), (float) location.getLongitude (),
                (float) location.getAltitude (), location.getTime ());
        magvariation = - gmf.getDeclination ();
        invalidate ();
    }

    /**
     * Callback to draw the instruments on the screen.
     */
    @Override
    protected void onDraw (Canvas canvas)
    {
        float textHeight    = textPaint.getTextSize ();
        float circleCenterX = getWidth ()  / 2.0F;
        float circleCenterY = getHeight () / 2.0F;
        float circleRadius  = Math.min (circleCenterX, circleCenterY) - textHeight * 2.0F;

        if (trianglePath == null) {
            trianglePath = new Path ();
            trianglePath.moveTo (circleCenterX, circleCenterY-circleRadius*9.0F/8.0F);
            trianglePath.lineTo (circleCenterX - circleRadius / 16.0F, circleCenterY-circleRadius * 7.0F / 8.0F);
            trianglePath.lineTo (circleCenterX + circleRadius / 16.0F, circleCenterY-circleRadius * 7.0F / 8.0F);
            trianglePath.lineTo (circleCenterX, circleCenterY-circleRadius*9.0F/8.0F);
        }

        canvas.save ();
        try {

            // display GPS time at bottom
            if (location != null) {
                long gpstimems = location.getTime ();
                String gpstimestr = String.format (Locale.US, "%02d:%02d:%02d",
                        gpstimems / 3600000 % 24, gpstimems / 60000 % 60, gpstimems / 1000 % 60);
                canvas.drawText (gpstimestr, circleCenterX, circleCenterY * 2.0F, trianglePaint);
            }

            // draw compass heading string at top then rotate remainder of drawing by compass heading
            if (! Float.isNaN (compRotDeg)) {
                String cmphdgstr = Integer.toString (1360 - (int) Math.round (compRotDeg + 360.0) % 360).substring (1) + '\u00B0';
                canvas.drawText (cmphdgstr, circleCenterX, textHeight, textPaint);
                canvas.drawPath (trianglePath, textPaint);
                canvas.rotate (compRotDeg, circleCenterX, circleCenterY);
            }

            // draw N S E W letters
            for (String compDir : compDirs) {
                canvas.drawText (compDir, circleCenterX, circleCenterY - circleRadius, textPaint);
                canvas.rotate (90.0F, circleCenterX, circleCenterY);
            }

            // draw circles for satellites
            if (satellites != null) {
                canvas.drawCircle (circleCenterX, circleCenterY, circleRadius * 30 / 90, ringsPaint);
                canvas.drawCircle (circleCenterX, circleCenterY, circleRadius * 60 / 90, ringsPaint);
            }
            canvas.drawCircle (circleCenterX, circleCenterY, circleRadius * 90 / 90, ringsPaint);

            // draw triangle showing travel direction
            if ((location != null) /*&& (location.getSpeed () > 1.0F)*/) {
                float truebearing = location.getBearing ();
                float magbearing = truebearing + magvariation;
                canvas.save ();
                canvas.rotate (magbearing, circleCenterX, circleCenterY);
                canvas.drawPath (trianglePath, trianglePaint);
                String gpsheading = String.format (Locale.US, "%03d\u00B0",
                        Math.round (magbearing + 359) % 360 + 1);
                canvas.drawText (gpsheading, circleCenterX, circleCenterY-circleRadius*9.0F/8.0F, trianglePaint);
                canvas.restore ();
            }

            // draw dots for satellites
            if (satellites != null) {
                int n = satellites.getSatelliteCount ();
                for (int i = 0; i < n; i ++) {
                    // hasAlmanac() and hasEphemeris() seem to always return false
                    // getSnr() in range 0..30 approx
                    double size = satellites.getCn0DbHz (i) / 3;
                    double radius = (90 - satellites.getElevationDegrees (i)) * circleRadius / 90;
                    double azirad = Math.toRadians (satellites.getAzimuthDegrees (i));
                    double deltax = radius * Math.sin (azirad);
                    double deltay = radius * Math.cos (azirad);
                    Paint paint = satellites.usedInFix (i) ? usedSpotsPaint : ignoredSpotsPaint;
                    canvas.drawCircle ((float) (circleCenterX + deltax), (float) (circleCenterY - deltay), (float) size, paint);
                }
            }
        } finally {
            canvas.restore ();
        }
    }
}

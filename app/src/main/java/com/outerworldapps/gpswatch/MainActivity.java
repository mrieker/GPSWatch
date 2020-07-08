//    Copyright (C) 2020, Mike Rieker, Beverly, MA USA
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

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.support.wearable.activity.WearableActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.util.LinkedList;

import androidx.annotation.NonNull;

public class MainActivity extends WearableActivity {
    public final static String TAG = "GPSWatch";

    private final static int agreeDays = 60;  // agreement good for this many days

    private boolean gpsEnabled;
    public  Handler myHandler;
    public  InternalGps internalGps;

    @Override
    protected void onCreate (Bundle savedInstanceState)
    {
        super.onCreate (savedInstanceState);

        // make sure they have agreed to little agreement
        final SharedPreferences prefs = getPreferences (MODE_PRIVATE);
        long hasAgreed = prefs.getLong ("hasAgreed", 0);
        if ((System.currentTimeMillis () - hasAgreed) / 86400000L < agreeDays) {
            hasAgreed ();
        } else {
            setContentView (R.layout.agree_page);
            Button acceptButton = findViewById (R.id.acceptButton);
            acceptButton.setOnClickListener (new View.OnClickListener () {
                @Override
                public void onClick (View v)
                {
                    SharedPreferences.Editor editr = prefs.edit ();
                    editr.putLong ("hasAgreed", System.currentTimeMillis ());
                    editr.apply ();
                    hasAgreed ();
                }
            });
            Button declineButton = findViewById (R.id.declineButton);
            declineButton.setOnClickListener (new View.OnClickListener () {
                @Override
                public void onClick (View v)
                {
                    finish ();
                }
            });
        }
    }

    // user has agreed to agreement, either earlier or just now
    @SuppressLint("InflateParams")
    private void hasAgreed ()
    {
        myHandler = new Handler ();

        internalGps = new InternalGps (this);

        // enables Always-on
        setAmbientEnabled ();

        // finish up initializing
        finishInitializing ();
    }

    @Override
    public void onDestroy ()
    {
        deactivateGPS ();
        super.onDestroy ();
    }

    /**
     * Finish initializing everything.
     */
    public void finishInitializing ()
    {
        LayoutInflater layoutInflater = getLayoutInflater ();
        @SuppressLint("InflateParams")
        View satsPageView = layoutInflater.inflate (R.layout.sats_page, null);
        GpsStatusView gpsStatusView = satsPageView.findViewById (R.id.gpsStatusView);

        gpsStatusView.Startup ();
        internalGps.setStatusListener (gpsStatusView);
        setContentView (satsPageView);

        activateGPS ();
    }

    /**
     * Turn the GPS on if not already.
     * First time requires user to give permission.
     */
    private void activateGPS ()
    {
        if (! gpsEnabled) {
            if (! internalGps.startSensor ()) return;
            showToast ("turned GPS on");
            gpsEnabled = true;
        }
    }

    /**
     * Turn the GPS off if not already.
     */
    private void deactivateGPS ()
    {
        if (gpsEnabled) {
            showToast ("turning GPS off");
            internalGps.stopSensor ();
            gpsEnabled = false;
        }
    }

    // Permission granting
    public final static int RC_INTGPS = 9876;
    @Override
    public void onRequestPermissionsResult (int requestCode,
                                            @NonNull String[] permissions,
                                            @NonNull int[] grantResults)
    {
        if (requestCode == RC_INTGPS) {
            activateGPS ();
        }
    }

    /**
     * Display toast message.
     */
    public void showToast (String msg)
    {
        MyToast myToast = new MyToast ();
        myToast.length = Toast.LENGTH_SHORT;
        myToast.msg = msg;
        queueMyToast (myToast);
    }

    public void showToastLong (String msg)
    {
        MyToast myToast = new MyToast ();
        myToast.length = Toast.LENGTH_LONG;
        myToast.msg = msg;
        queueMyToast (myToast);
    }

    private void queueMyToast (MyToast myToast)
    {
        if (myToastShowing != null) {
            if (myToast.msg.equals (myToastShowing.msg)) return;
            for (MyToast qd : myToasts) if (myToast.msg.equals (qd.msg)) return;
            myToasts.add (myToast);
        } else {
            myToast.show ();
        }
    }

    private LinkedList<MyToast> myToasts = new LinkedList<> ();
    private MyToast myToastShowing;

    private class MyToast implements Runnable {
        public int length;
        public String msg;

        public void show ()
        {
            myToastShowing = this;
            Toast.makeText (MainActivity.this, msg, length).show ();
            int delay = 0;
            switch (length) {
                case Toast.LENGTH_SHORT: delay = 3000; break;
                case Toast.LENGTH_LONG:  delay = 5000; break;
            }
            myHandler.postDelayed (this, delay);
        }

        @Override
        public void run ()
        {
            MyToast next = myToasts.poll ();
            if (next == null) {
                myToastShowing = null;
            } else {
                next.show ();
            }
        }
    }
}

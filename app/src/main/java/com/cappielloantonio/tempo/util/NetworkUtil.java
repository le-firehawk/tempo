package com.cappielloantonio.tempo.util;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;

import com.cappielloantonio.tempo.App;

public class NetworkUtil {
    public static boolean isOffline() {
        if (Preferences.isOfflineModeEnabled()) {
            return true;
        }

        ConnectivityManager connectivityManager = (ConnectivityManager) App.getContext().getSystemService(Context.CONNECTIVITY_SERVICE);

        if (connectivityManager == null) {
            return true;
        }

        Network network = connectivityManager.getActiveNetwork();

        if (network != null) {
            NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);

            if (capabilities != null) {
                return !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
            }
        }

        NetworkInfo legacyNetworkInfo = connectivityManager.getActiveNetworkInfo();
        if (legacyNetworkInfo != null) {
            return !legacyNetworkInfo.isConnectedOrConnecting();
        }

        return true;
    }
}

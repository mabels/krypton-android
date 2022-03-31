package co.krypt.krypton.transport;

import android.util.Log;

import com.google.android.gms.tasks.Task;
import com.google.firebase.messaging.FirebaseMessaging;

import co.krypt.krypton.exception.TransportException;
import co.krypt.krypton.utils.Services;

public class FirebaseInstanceIDService extends com.google.firebase.messaging.FirebaseMessagingService {
    private static final Services services = new Services();
    private static final String TAG = "FirebaseInstanceID";

    public FirebaseInstanceIDService() {
    }

    @Override
    public void onNewToken(String refreshedToken) {
        // Get updated InstanceID token.
//        Task<String> refreshedToken = FirebaseMessaging.getInstance().getToken();
        Log.d(TAG, "Refreshed token: " + refreshedToken);

        // If you want to send messages to this application instance or
        // manage this apps subscriptions on the server side, send the
        // Instance ID token to your app server.
        try {
            SNSTransport.getInstance(getApplicationContext()).setDeviceToken(refreshedToken);
        } catch (TransportException e) {
            e.printStackTrace();
        }
    }
}

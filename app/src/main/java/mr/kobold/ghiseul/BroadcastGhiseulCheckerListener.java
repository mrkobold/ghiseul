package mr.kobold.ghiseul;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.RequiresApi;

public class BroadcastGhiseulCheckerListener extends BroadcastReceiver {

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    public void onReceive(Context context, Intent intent) {
        MainShitHttpUrlConnection.startWorkerThread(context, false);
    }
}

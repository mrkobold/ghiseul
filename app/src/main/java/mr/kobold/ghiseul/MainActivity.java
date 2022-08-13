package mr.kobold.ghiseul;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.View;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.Calendar;
import java.util.TimeZone;

public class MainActivity extends AppCompatActivity {

    public static final String CHANNEL_ID = "id_ghiseul_notification_channel";
    public static final String CHANNEL_NAME = "name_ghiseul_notification_channel";

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        MainShitHttpUrlConnection.text = findViewById(R.id.edit_text);
        MainShitHttpUrlConnection.text.setRawInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);

        MainShitHttpUrlConnection.context = this;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            createNotificationChannel();

        registerPeriodicChecksIfNeeded();
    }

    private void registerPeriodicChecksIfNeeded() {
        File yourFile = new File(Environment.getExternalStorageDirectory() + File.separator + "periodicEventsFile.txt");
        if (yourFile.exists()) {
            return;
        }
        try {
            yourFile.createNewFile();
        } catch (IOException e) {
            MainShitHttpUrlConnection.pushNotification("Ghiseul", "Couldn't assure that no additional periodic jobs will be registered");
        }

        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(System.currentTimeMillis());
        calendar.setTimeZone(TimeZone.getTimeZone("Europe/Bucharest"));
        calendar.set(Calendar.HOUR, 15);
        calendar.set(Calendar.MINUTE, 40);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.AM_PM, 0);
        calendar.get(Calendar.HOUR_OF_DAY);
        calendar.get(Calendar.MINUTE);
        if (calendar.getTimeInMillis() < System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1);
        }

        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(MainActivity.this, BroadcastGhiseulCheckerListener.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(MainActivity.this, 0, intent, 0);
        alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(), AlarmManager.INTERVAL_DAY, pendingIntent);
        new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(this, "Registered job to run everyday at 12:00", Toast.LENGTH_SHORT).show());
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    public void meow(View v) {
        MainShitHttpUrlConnection.startWorkerThread(getApplicationContext(), true);
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT);
        channel.setLightColor(Color.GREEN);
        channel.enableLights(true);
        ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).createNotificationChannel(channel);
    }
}
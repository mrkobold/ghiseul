package mr.kobold.ghiseul;

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
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.concurrent.CompletableFuture;

public class MainActivity extends AppCompatActivity {

    public static final String CHANNEL_ID = "id_ghiseul_notification_channel";
    public static final String CHANNEL_NAME = "name_ghiseul_notification_channel";

    private MainShitHttpUrlConnection mainShitHttpUrlConnection;

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            createNotificationChannel();

        new Thread(this::getUsernameAndPasswordIfNotSupplied).start();
        getUsernameAndPasswordIfNotSupplied();

        findViewById(R.id.forget).setOnClickListener(v -> {
            boolean b1 = new File(Environment.getExternalStorageDirectory() + File.separator + "credentials.txt").delete();
            new File(Environment.getExternalStorageDirectory() + File.separator + "periodicEventsFile.txt").delete();

            if (!b1)
                return;
            MainActivity.this.runOnUiThread(() -> {
                findViewById(R.id.username).setVisibility(View.VISIBLE);
                findViewById(R.id.password).setVisibility(View.VISIBLE);
                findViewById(R.id.check).setVisibility(View.VISIBLE);
            });
        });

        mainShitHttpUrlConnection = new MainShitHttpUrlConnection(this, findViewById(R.id.edit_text));
        registerPeriodicChecksIfNeeded();
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private void getUsernameAndPasswordIfNotSupplied() {
        File yourFile = new File(Environment.getExternalStorageDirectory() + File.separator + "credentials.txt");
        if (!yourFile.exists()) {
            String username = ((EditText) findViewById(R.id.username)).getText().toString();
            String plainPassword = ((EditText) findViewById(R.id.password)).getText().toString();
            findViewById(R.id.check).setOnClickListener(v -> {
                CompletableFuture<String> res = mainShitHttpUrlConnection.testCredentials(username, plainPassword);
                res.whenComplete((r, ex) -> {
                    if (ex != null)
                        return;
                    if (r != null) {
                        try {
                            saveToFile(username, r);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        MainActivity.this.runOnUiThread(() -> {
                            findViewById(R.id.username).setVisibility(View.INVISIBLE);
                            findViewById(R.id.password).setVisibility(View.INVISIBLE);
                            findViewById(R.id.check).setVisibility(View.INVISIBLE);
                        });
                    }
                });
            });
        } else {
            findViewById(R.id.username).setVisibility(View.INVISIBLE);
            findViewById(R.id.password).setVisibility(View.INVISIBLE);
            findViewById(R.id.check).setVisibility(View.INVISIBLE);
        }
    }

    private void saveToFile(String username, String passwordMd5) throws Exception {
        File yourFile = new File(Environment.getExternalStorageDirectory() + File.separator + "credentials.txt");
        FileOutputStream oFile = new FileOutputStream(yourFile, false);

        PrintWriter printWriter = new PrintWriter(oFile);
        printWriter.append(username).append("\n").append(passwordMd5);

        printWriter.close();
        oFile.close();
    }

    private void registerPeriodicChecksIfNeeded() {
        File yourFile = new File(Environment.getExternalStorageDirectory() + File.separator + "periodicEventsFile.txt");
        if (yourFile.exists()) {
            return;
        }
        try {
            yourFile.createNewFile();
        } catch (IOException e) {
            mainShitHttpUrlConnection.pushNotification("Couldn't assure that no additional periodic jobs will be registered");
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
        mainShitHttpUrlConnection.start();
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT);
        channel.setLightColor(Color.GREEN);
        channel.enableLights(true);
        ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).createNotificationChannel(channel);
    }
}
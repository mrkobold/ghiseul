package mr.kobold.ghiseul;

import static mr.kobold.ghiseul.MainActivity.CHANNEL_ID;

import android.app.Notification;
import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import org.apache.commons.codec.digest.HmacUtils;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

public class MainShitHttpUrlConnection {

    private static final String HOME_URL = "https://ghiseul.ro";
    private static final String LOGIN_URL = "https://www.ghiseul.ro/ghiseul/public/login/process";
    private static final String PHPSESSID = "PHPSESSID";
    private static final String HOME_PAROLA_HMAC_LINE = "parolaHmac = '";
    private static final String PASSWORD_MD5 = "dc772e7d6d2e1ea0610205d6f2c253d2";
    private static final String USERNAME = "rF587Au";
    private static final String FILE_PATH = Environment.getExternalStorageDirectory() + File.separator + "ghiseulChecker.txt";

    private static final Map<String, Supplier<String>> TABS_TO_URL = new HashMap<>();
    private static Map<String, String> SAVED_CONTENT = new HashMap<>();

    public static EditText text;
    public static Context context;
    private static boolean haveUi = false;

    static {
        TABS_TO_URL.put("--obligatii", () -> "https://www.ghiseul.ro/ghiseul/public/debite/get-institution-details/id_inst/302?_=" + System.currentTimeMillis());
        TABS_TO_URL.put("--anaf", () -> "https://www.ghiseul.ro/ghiseul/public/debite/incarca-debite-anaf?_=" + System.currentTimeMillis());
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    public static void startWorkerThread(Context applicationContext, boolean haveUi) {
        MainShitHttpUrlConnection.context = applicationContext;
        MainShitHttpUrlConnection.haveUi = haveUi;

        Thread worker = new Thread(() -> doShite(applicationContext));
        worker.start();
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private static void doShite(Context applicationContext) {
        try {
            SAVED_CONTENT = new HashMap<>();

            if (haveUi)
                Looper.prepare();

            loadSavedContentFromFile(applicationContext);

            show("Logging in to ghiseul.ro and acquiring login PHPSESSID...");
            String loggedInPHPSESSID = getLoggedInPHPSESSID();
            show("Done\n");
            Map<String, String> tabToResponse = new HashMap<>();
            TABS_TO_URL.forEach((k, v) -> {
                String currentValue = getResponseForURL(v.get(), loggedInPHPSESSID).replaceAll("(\\r|\\n)", "");
                tabToResponse.put(k, currentValue);
                show("Loaded section " + k + " from ghiseul: " + currentValue.substring(0, 90) + "\n");
            });

            show("Done loading sections from ghiseul\n");
            boolean isAlaaarm = false;
            for (String tab : TABS_TO_URL.keySet()) {
                String savedContent = SAVED_CONTENT.get(tab);
                if (savedContent == null || !Objects.equals(savedContent.trim(), tabToResponse.get(tab).trim())) {
                    String alaarm = "Change detected in " + tab;
                    show(alaarm + "\n");
                    SAVED_CONTENT.put(tab, tabToResponse.get(tab).trim());
                    isAlaaarm = true;
                }
            }
            if (!isAlaaarm) {
                if (haveUi) {
                    new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(context, "All good, no changes", Toast.LENGTH_SHORT).show());
                    show("All good, no changes\n");
                }
                pushNotification("Ghiseul", "All good, no changes");
            } else {
                pushNotification("Ghiseul", "There are changes, pls check");
            }
            show("Saving currently read data to file...");
            saveCurrentContent();
            show("Done\n\n\n");
        } catch (Exception e) {
            // TODO notify user of unexpected error
            show("Error:" + e);
        }
    }

    static void pushNotification(String title, String content) {
        Notification notification = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(content)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build();

        NotificationManagerCompat notificationManagerCompat = NotificationManagerCompat.from(context);
        notificationManagerCompat.notify(0, notification);
    }

    private static void show(String t) {
        if (!haveUi)
            return;
        MainActivity mainActivity = (MainActivity) context;
        mainActivity.runOnUiThread(() -> text.append(t));
    }

    private static void saveCurrentContent() throws Exception {
        File yourFile = new File(FILE_PATH);
        FileOutputStream oFile = new FileOutputStream(yourFile, false);

        PrintWriter printWriter = new PrintWriter(oFile);

        for (Map.Entry<String, String> entry : SAVED_CONTENT.entrySet()) {
            printWriter.append(entry.getKey()).append("\n");
            printWriter.flush();
            printWriter.append(entry.getValue()).append("\n");
            printWriter.flush();
        }
        printWriter.close();
        oFile.close();
    }

    private static String getResponseForURL(String URL, String loginPHPSESSID) {
        try {
            URL url = new URL(URL);
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("POST");
            con.setRequestProperty("Cookie", PHPSESSID + "=" + loginPHPSESSID);

            con.getResponseCode();
            BufferedReader reader = new BufferedReader(new InputStreamReader(con.getInputStream()));
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append("\n");
            }
            return builder.toString();
        } catch (Exception e) {
            show("Error " + e);
            return null;
        }
    }

    private static String getLoggedInPHPSESSID() throws Exception {
        // get login page
        HttpURLConnection welcomeResponse = getWelcomePage();

        // get necessary data from loginPage response
        String homePHPSESSID = getPHPSESSID(welcomeResponse);
        String homeHMAC = getHomeHMAC(welcomeResponse);

        // compute hashed password based on loginPage's homePHPSESSID, homeHMAC and PASSWORD_MD5
        String hashedPassword = HmacUtils.hmacSha1Hex(homeHMAC, PASSWORD_MD5);

        // perform login
        HttpURLConnection loginResponse = getLoginResponse(homePHPSESSID, hashedPassword);

        // return logged-in's loginPHPSESSID
        return getPHPSESSID(loginResponse);
    }

    private static HttpURLConnection getLoginResponse(String homePHPSESSID, String hashedPassword) throws IOException {
        URL url = new URL(LOGIN_URL);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("POST");
        con.setRequestProperty("Cookie", PHPSESSID + "=" + homePHPSESSID);
        String body = "username=" + USERNAME + "&password=" + hashedPassword;
        byte[] postData = body.getBytes(StandardCharsets.UTF_8);
        try (DataOutputStream wr = new DataOutputStream(con.getOutputStream())) {
            wr.write(postData);
        }

        con.getResponseCode();
        return con;
    }

    private static String getPHPSESSID(HttpURLConnection homeResponse) throws Exception {
        return homeResponse.getHeaderField("Set-Cookie").split(";")[0].split("=")[1];
    }

    private static String getHomeHMAC(HttpURLConnection homeResponse) throws Exception {
        StringBuilder builder = new StringBuilder();
        BufferedReader reader = new BufferedReader(new InputStreamReader(homeResponse.getInputStream()));
        String line;
        while ((line = reader.readLine()) != null) {
            builder.append(line).append("\n");
        }
        String bodyAll = builder.toString();
        String[] body = bodyAll.split("\n");

        for (String l : body)
            if (l.contains(HOME_PAROLA_HMAC_LINE))
                return l.split("'")[1];

        throw new Exception("Couldn't find 'parolaHmac'");
    }

    private static HttpURLConnection getWelcomePage() throws IOException {
        URL url = new URL(HOME_URL);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("GET");

        con.connect();
        return con;
    }

    private static void loadSavedContentFromFile(Context applicationContext) throws Exception {
        show("Loading previously saved data from file...\n");
        File yourFile = new File(FILE_PATH);
        if (!yourFile.exists()) {
            show("File didn't exist, creating one now...");
            yourFile.createNewFile();
            show("Done\n");
            return;
        }
        FileInputStream inputStream = new FileInputStream(yourFile);
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        String line = reader.readLine();
        show("Open previously created file, starting to read previously saved data\n");

        if (line == null) { // file is empty (first run, file was deleted, etc.)
            show("Previously created file is empty\n");
            return;
        }

        String currentSection = null;
        while (line != null) {
            if (line.startsWith("--"))
                currentSection = line;
            StringBuilder sectionBuilder = new StringBuilder();
            while ((line = reader.readLine()) != null && !line.startsWith("--")) {
                sectionBuilder.append(line);
            }
            SAVED_CONTENT.put(currentSection, sectionBuilder.toString());
            show("Loaded " + currentSection + ": " + sectionBuilder.substring(0, 50) + "\n");
        }
        show("Done loading file\n");
    }
}

package mr.kobold.ghiseul;

import static mr.kobold.ghiseul.MainActivity.CHANNEL_ID;

import android.app.Notification;
import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
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
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import static mr.kobold.ghiseul.ConstGhiseulRo.*;
import static mr.kobold.ghiseul.ConstOS.*;

public class MainShitHttpUrlConnection {
    private final Map<String, String> savedContent = new HashMap<>();
    private final EditText editText;
    private final Context context;
    private final boolean haveUi;

    private String username;
    private String passwordMd5;

    MainShitHttpUrlConnection(Context context, EditText editText) {
        this.context = context;
        this.editText = editText;
        this.haveUi = editText != null;
        if (editText != null)
            editText.setRawInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    void start() {
        new Thread(this::doShite).start();
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    CompletableFuture<String> testCredentials(String username, String password) {
        try {
            CompletableFuture<String> future = new CompletableFuture<>();
            new Thread(() -> {
                MessageDigest md5;
                try {
                    md5 = MessageDigest.getInstance("MD5");
                    md5.update(StandardCharsets.UTF_8.encode(password));
                    String passwordMd5 = String.format("%032x", new BigInteger(1, md5.digest()));
                    String loggedInPHPSESSID = getLoggedInPHPSESSID(username, passwordMd5);
                    future.complete(loggedInPHPSESSID != null ? passwordMd5 : null);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();
            return future;
        } catch (Exception e) {
            return CompletableFuture.completedFuture(null);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    void doShite() {
        try {

            readCredentialsFromFile();

            if (haveUi)
                Looper.prepare();

            loadSavedContentFromFile();

            show("Logging in to ghiseul.ro and acquiring login PHPSESSID...");
            String loggedInPHPSESSID = getLoggedInPHPSESSID(username, passwordMd5);
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
                String savedContent = this.savedContent.get(tab);
                if (savedContent == null || !Objects.equals(savedContent.trim(), tabToResponse.get(tab).trim())) {
                    String alaarm = "Change detected in " + tab;
                    show(alaarm + "\n");
                    this.savedContent.put(tab, tabToResponse.get(tab).trim());
                    isAlaaarm = true;
                }
            }
            if (!isAlaaarm) {
                if (haveUi) {
                    new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(context, "All good, no changes", Toast.LENGTH_SHORT).show());
                    show("All good, no changes\n");
                }
                pushNotification("All good, no changes");
            } else {
                pushNotification("There are changes, pls check ghiseul.ro");
            }
            show("Saving currently read data to file...");
            saveCurrentContent();
            show("Done\n\n\n");
        } catch (Exception e) {
            // TODO notify user of unexpected error
            show("Error:" + e);
        }
    }

    void readCredentialsFromFile() {
        File yourFile = new File(Environment.getExternalStorageDirectory() + File.separator + "credentials.txt");
        try {
            BufferedReader reader = new BufferedReader(new FileReader(yourFile));
            this.username = reader.readLine();
            this.passwordMd5 = reader.readLine();

        } catch (Exception e) {
            throw new RuntimeException("Couldn't read credentials from file", e);
        }
    }

    void pushNotification(String content) {
        Notification notification = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle("Ghiseul")
                .setContentText(content)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build();

        NotificationManagerCompat notificationManagerCompat = NotificationManagerCompat.from(context);
        notificationManagerCompat.notify(0, notification);
    }

    private void show(String t) {
        if (!haveUi)
            return;
        MainActivity mainActivity = (MainActivity) context;
        mainActivity.runOnUiThread(() -> editText.append(t));
    }

    private void saveCurrentContent() throws Exception {
        File yourFile = new File(SAVED_SITE_CONTENT_FILE);
        FileOutputStream oFile = new FileOutputStream(yourFile, false);

        PrintWriter printWriter = new PrintWriter(oFile);

        for (Map.Entry<String, String> entry : savedContent.entrySet()) {
            printWriter.append(entry.getKey()).append("\n");
            printWriter.flush();
            printWriter.append(entry.getValue()).append("\n");
            printWriter.flush();
        }
        printWriter.close();
        oFile.close();
    }

    private String getResponseForURL(String URL, String loginPHPSESSID) {
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

    private String getLoggedInPHPSESSID(String username, String passwordMd5) throws Exception {
        // get login page
        HttpURLConnection welcomeResponse = getWelcomePage();

        // get necessary data from loginPage response
        String homePHPSESSID = getPHPSESSID(welcomeResponse);
        String homeHMAC = getHomeHMAC(welcomeResponse);

        // compute hashed password based on loginPage's homePHPSESSID, homeHMAC and PASSWORD_MD5
        String hashedPassword = HmacUtils.hmacSha1Hex(homeHMAC, PASSWORD_MD5);

        // perform login
        HttpURLConnection loginResponse = getLoginResponse(homePHPSESSID, username, hashedPassword);

        // return logged-in's loginPHPSESSID
        return getPHPSESSID(loginResponse);
    }

    private HttpURLConnection getLoginResponse(String homePHPSESSID, String username, String hashedPassword) throws IOException {
        URL url = new URL(LOGIN_URL);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("POST");
        con.setRequestProperty("Cookie", PHPSESSID + "=" + homePHPSESSID);
        String body = "username=" + username + "&password=" + hashedPassword;
        byte[] postData = body.getBytes(StandardCharsets.UTF_8);
        try (DataOutputStream wr = new DataOutputStream(con.getOutputStream())) {
            wr.write(postData);
        }

        con.getResponseCode();
        return con;
    }

    private String getPHPSESSID(HttpURLConnection homeResponse) throws Exception {
        return homeResponse.getHeaderField("Set-Cookie").split(";")[0].split("=")[1];
    }

    private String getHomeHMAC(HttpURLConnection homeResponse) throws Exception {
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

    private HttpURLConnection getWelcomePage() throws IOException {
        URL url = new URL(HOME_URL);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("GET");

        con.connect();
        return con;
    }

    private void loadSavedContentFromFile() throws Exception {
        show("Loading previously saved data from file...\n");
        File yourFile = new File(SAVED_SITE_CONTENT_FILE);
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
            savedContent.put(currentSection, sectionBuilder.toString());
            show("Loaded " + currentSection + ": " + sectionBuilder.substring(0, 50) + "\n");
        }
        show("Done loading file\n");
    }
}

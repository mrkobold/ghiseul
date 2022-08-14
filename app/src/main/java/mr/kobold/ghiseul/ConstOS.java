package mr.kobold.ghiseul;

import android.os.Environment;

import java.io.File;

public class ConstOS {
    static final String SAVED_SITE_CONTENT_FILE = Environment.getExternalStorageDirectory() + File.separator + "ghiseulChecker.txt";
    static final String SAVED_PASSWORD_MD5_FILE = Environment.getExternalStorageDirectory() + File.separator + "md5.txt";
}
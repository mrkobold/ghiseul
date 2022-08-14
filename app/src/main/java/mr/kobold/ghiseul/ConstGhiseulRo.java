package mr.kobold.ghiseul;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public final class ConstGhiseulRo {
    static final String HOME_URL = "https://ghiseul.ro";
    static final String LOGIN_URL = "https://www.ghiseul.ro/ghiseul/public/login/process";
    static final String PHPSESSID = "PHPSESSID";
    static final String HOME_PAROLA_HMAC_LINE = "parolaHmac = '";

    static final Map<String, Supplier<String>> TABS_TO_URL = new HashMap<>();
    static {
        TABS_TO_URL.put("--obligatii", () -> "https://www.ghiseul.ro/ghiseul/public/debite/get-institution-details/id_inst/302?_=" + System.currentTimeMillis());
        TABS_TO_URL.put("--anaf", () -> "https://www.ghiseul.ro/ghiseul/public/debite/incarca-debite-anaf?_=" + System.currentTimeMillis());
    }

    static final String PASSWORD_MD5 = "dc772e7d6d2e1ea0610205d6f2c253d2";
    static final String USERNAME = "rF587Au";
}

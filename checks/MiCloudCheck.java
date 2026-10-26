import com.fitlog.app.data.mijia.*;
import com.fitlog.app.domain.WeightSync;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import org.json.*;

/** Runs production cloud parsing and crypto on the JVM; no account or network required. */
public final class MiCloudCheck {
    private static int checks;
    private static final MiCrypto crypto = MiCrypto.INSTANCE;
    private static void check(String name, boolean value) {
        if (!value) throw new AssertionError(name);
        checks++;
    }
    private static String sha1(String text) throws Exception {
        return Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1").digest(text.getBytes(StandardCharsets.UTF_8)));
    }
    private static JSONObject record(MiProfile p, double kg, long seconds) {
        return new JSONObject().put("uid", p.getUid()).put("accountId", p.getAccountId()).put("did", p.getDid())
            .put("model", p.getModel()).put("data", new JSONObject().put("weight", kg).put("time", "" + seconds).toString());
    }
    public static void main(String[] args) throws Exception {
        check("reference signed nonce", crypto.encode(crypto.signedNonce("c3NlY3VyaXR5", crypto.decode("bm9uY2U=")))
            .equals("/oX2A3COQbfnXTsssP9J8BTo+5jiwum99lk9VJaElVI="));
        byte[] key = new byte[32]; Arrays.fill(key, (byte)7);
        byte[] input = "米家 weight 67.25".getBytes(StandardCharsets.UTF_8);
        Cipher reference = Cipher.getInstance("RC4");
        reference.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "RC4"));
        reference.update(new byte[1024]);
        check("RC4-drop1024 matches independent JCE provider", Arrays.equals(reference.doFinal(input), crypto.crypt(key, input)));
        byte[] nonce = crypto.decode("MTIzNDU2Nzg5MDEy");
        String json = "{\"value\":1}";
        Map<String,String> form = crypto.params("/eco/scale/account/list", "c3NlY3VyaXR5", json, nonce);
        byte[] signed = crypto.signedNonce("c3NlY3VyaXR5", nonce);
        check("encrypted data decodes", new String(crypto.crypt(signed, crypto.decode(form.get("data"))), StandardCharsets.UTF_8).equals(json));
        String rawHash = sha1("POST&/eco/scale/account/list&data=" + json + "&" + crypto.encode(signed));
        check("plain signature order", new String(crypto.crypt(signed, crypto.decode(form.get("rc4_hash__"))), StandardCharsets.UTF_8).equals(rawHash));
        check("encrypted signature order", form.get("signature").equals(sha1("POST&/eco/scale/account/list&data=" + form.get("data")
            + "&rc4_hash__=" + form.get("rc4_hash__") + "&" + crypto.encode(signed))));
        check("nonce and security sent", form.get("_nonce").equals("MTIzNDU2Nzg5MDEy") && form.get("ssecurity").equals("c3NlY3VyaXR5"));
        check("nonce width", crypto.nonce().length == 12);

        MiCloudClient client = new MiCloudClient();
        MiProfile profile = new MiProfile("ble.123", "yunmai.scales.ms106", "S200", "123", "456", "Test");
        long seconds = System.currentTimeMillis() / 1000 - 3600;
        JSONArray records = new JSONArray().put(record(profile, 67.25, seconds)).put(record(profile, 68, seconds - 60));
        List<MiMeasurement> parsed = client.parseMeasurements(records, profile);
        check("weight already kg", parsed.get(0).getKg() == 67.25);
        check("seconds converted to milliseconds", parsed.get(0).getTimeMs() == seconds * 1000);
        check("empty history", client.parseMeasurements(new JSONArray(), profile).isEmpty());
        for (String field : Arrays.asList("uid", "accountId", "did", "model")) {
            check("reject wrong " + field, client.parseMeasurements(new JSONArray().put(record(profile, 67, seconds).put(field, "other")), profile).isEmpty());
        }
        check("object-shaped data accepted", client.parseMeasurements(new JSONArray().put(record(profile, 67, seconds)
            .put("data", new JSONObject().put("weight", 67).put("time", "" + seconds))), profile).size() == 1);
        check("zero rejected", client.parseMeasurements(new JSONArray().put(record(profile, 0, seconds)), profile).isEmpty());
        check("future timestamp rejected", client.parseMeasurements(new JSONArray().put(record(profile, 67, seconds + 7200)), profile).isEmpty());
        check("malformed data fails without a fabricated weight", fails(() -> client.parseMeasurements(new JSONArray().put(record(profile, 67, seconds).put("data", "broken")), profile)));
        check("profile round trip", MiProfile.Companion.from(profile.json()).equals(profile));
        check("valid login host", MiCloudClient.Companion.trustedLoginUrl("https://account.xiaomi.com/pass/serviceLogin"));
        check("valid callback host", MiCloudClient.Companion.trustedLoginUrl("https://sts.api.io.mi.com/sts"));
        check("reject http login", !MiCloudClient.Companion.trustedLoginUrl("http://account.xiaomi.com"));
        check("reject lookalike host", !MiCloudClient.Companion.trustedLoginUrl("https://account.xiaomi.com.evil.test"));
        check("cookie equals preserved", MiCloudClient.Companion.cookies("userId=123; passToken=abc==").get("passToken").equals("abc=="));

        WeightSync rule = WeightSync.INSTANCE;
        long now = System.currentTimeMillis(), measured = now - 10000;
        check("new cloud weight imported", rule.shouldImport(67, measured, measured - 1, now));
        check("duplicate ignored", !rule.shouldImport(67, measured, measured, now));
        check("newer manual weight preserved", !rule.shouldImport(67, measured, now, now));
        check("invalid weight ignored", !rule.shouldImport(Double.NaN, measured, 0, now));
        check("future weight ignored", !rule.shouldImport(67, now + 3600000, 0, now));
        check("missing time ignored", !rule.shouldImport(67, 0, 0, now));
        System.out.println("MiCloud: " + checks + " checks passed (crypto, member isolation, parsing, login hosts, freshness).");
    }
    private static boolean fails(Runnable action) {
        try { action.run(); return false; } catch (Exception expected) { return true; }
    }
}

package app.exteraless.utils;

import android.util.Base64;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.json.JSONException;
import org.json.JSONObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;

import tw.nekomimi.nekogram.NekoConfig;

public final class FaveStickersBackup {

    public static final String KEY = "exteralessFaveStickers";
    private static final int MAX_STICKERS = 10000;

    private FaveStickersBackup() {
    }

    public static JSONObject export() throws JSONException {
        if (!NekoConfig.unlimitedFavedStickers.Bool()) {
            return null;
        }
        JSONObject result = new JSONObject();
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            UserConfig config = UserConfig.getInstance(a);
            if (!config.isClientActivated()) {
                continue;
            }
            String encoded = encode(new ArrayList<>(MediaDataController.getInstance(a).getRecentStickersNoCopy(MediaDataController.TYPE_FAVE)));
            if (encoded != null) {
                result.put(String.valueOf(config.getClientUserId()), encoded);
            }
        }
        return result.length() == 0 ? null : result;
    }

    public static void restore(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return;
        }
        JsonObject json = element.getAsJsonObject();
        boolean enabled = false;
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            UserConfig config = UserConfig.getInstance(a);
            if (!config.isClientActivated()) {
                continue;
            }
            JsonElement value = json.get(String.valueOf(config.getClientUserId()));
            if (value == null || !value.isJsonPrimitive()) {
                continue;
            }
            ArrayList<TLRPC.Document> documents = decode(value.getAsString());
            if (documents.isEmpty()) {
                continue;
            }
            if (!enabled) {
                NekoConfig.unlimitedFavedStickers.setConfigBool(true);
                enabled = true;
            }
            MediaDataController.getInstance(a).importFaveStickers(documents);
        }
    }

    private static String encode(ArrayList<TLRPC.Document> documents) {
        ArrayList<TLRPC.Document> valid = new ArrayList<>();
        int size = 4;
        for (TLRPC.Document document : documents) {
            if (document instanceof TLRPC.TL_document) {
                valid.add(document);
                size += document.getObjectSize();
            }
        }
        if (valid.isEmpty()) {
            return null;
        }
        SerializedData data = new SerializedData(size);
        data.writeInt32(valid.size());
        for (TLRPC.Document document : valid) {
            document.serializeToStream(data);
        }
        String result = Base64.encodeToString(data.toByteArray(), Base64.NO_WRAP);
        data.cleanup();
        return result;
    }

    private static ArrayList<TLRPC.Document> decode(String value) {
        ArrayList<TLRPC.Document> documents = new ArrayList<>();
        SerializedData data = null;
        try {
            data = new SerializedData(Base64.decode(value, Base64.NO_WRAP));
            int count = data.readInt32(false);
            for (int i = 0; i < count && i < MAX_STICKERS; i++) {
                TLRPC.Document document = TLRPC.Document.TLdeserialize(data, data.readInt32(false), false);
                if (!(document instanceof TLRPC.TL_document)) {
                    break;
                }
                documents.add(document);
            }
        } catch (Exception e) {
            FileLog.e("FaveStickersBackup: cannot decode", e);
        } finally {
            if (data != null) {
                data.cleanup();
            }
        }
        return documents;
    }
}

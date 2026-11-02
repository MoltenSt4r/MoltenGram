package app.exteraless.tor;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import net.freehaven.tor.control.RawEventListener;
import net.freehaven.tor.control.TorControlCommands;
import net.freehaven.tor.control.TorControlConnection;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SharedConfig;
import org.torproject.jni.TorService;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Встроенный контроллер Tor (Orbot) для ExteraMS.
 * Управляет нативным демоном Tor через TorService / libtor.so,
 * мостами Pluggable Transports (obfs4, Snowflake)
 * и автоматическим переключением SOCKS5-прокси Telegram.
 */
public class TorController {

    private static volatile TorController instance;

    public static TorController getInstance() {
        if (instance == null) {
            synchronized (TorController.class) {
                if (instance == null) {
                    instance = new TorController();
                }
            }
        }
        return instance;
    }

    // Состояния подключения
    public static final int STATUS_STOPPED = 0;
    public static final int STATUS_STARTING = 1;
    public static final int STATUS_CONNECTING = 2;
    public static final int STATUS_CONNECTED = 3;
    public static final int STATUS_STOPPING = 4;
    public static final int STATUS_ERROR = 5;

    // Режимы подключения: только прямое и мосты
    public static final int BRIDGE_DIRECT = 0;        // Прямое подключение (без мостов)
    public static final int BRIDGE_CUSTOM = 1;        // Мосты Tor (кастомные / из @GetBridgesBot)

    public static final int DEFAULT_SOCKS_PORT = 9050;
    public static final int DEFAULT_CONTROL_PORT = 9051;

    // Стандартные серверы Snowflake с поддержкой нескольких STUN и Front domains
    private static final String DEFAULT_STUN_SERVER = "stun:stun.l.google.com:19302,stun:stun.antisip.com:3478,stun:stun.bluesip.net:3478,stun:stun.dus.net:3478,stun:stun.sonetel.com:28901,stun:stun.sonetel.net:28901,stun:stun.voipgate.com:3478,stun:stun.voys.nl:3478";
    private static final String DEFAULT_FRONT_DOMAIN = "cdn.sstatic.net,foursquare.com,github.githubassets.com";
    private static final String DEFAULT_SNOWFLAKE_BROKER = "https://snowflake-broker.torproject.net/";
    private static final String DEFAULT_AMP_CACHE = "https://amp.cloudflare.com/";

    // Встроенные мосты по умолчанию (obfs4)
    private static final String[] DEFAULT_OBFS4_BRIDGES = new String[]{
            "obfs4 193.224.70.70:80 439FAD336D0872E77402F0B9C6FF6EB4B022B86E cert=5vW62x8hG7z5KkYf6J6k5g89z4W0Y5L9e5V1m8q9P3Y iat-mode=0",
            "obfs4 192.95.36.142:443 CDF2E852BF539BEA3DAE4F123456789012345678 cert=ks0XkP077rO6r99sS7m8k+Z1n2L3m4p5q6r7s8t9u0 iat-mode=0"
    };

    public interface Listener {
        void onStatusChanged(int status, int progress, String message);
        void onLog(String line);
    }

    public interface ExitNodeListener {
        void onExitNodeInfoUpdated(String ip, String country, String flag, long ping);
    }

    private final List<Listener> listeners = new ArrayList<>();
    private final List<ExitNodeListener> exitNodeListeners = new ArrayList<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Runnable stallWatchdogRunnable;

    private int currentStatus = STATUS_STOPPED;
    private int currentProgress = 0;
    private String currentStatusMessage = "";

    private String currentExitIp = "";
    private String currentExitCountry = "";
    private String currentExitCountryCode = "";
    private String currentExitFlag = "";
    private long currentPing = -1;
    private boolean isCheckingExitNode = false;

    private TorService boundTorService;
    private TorControlConnection controlConnection;
    private Socket controlSocket;
    private Process standaloneProcess;
    private Thread logReaderThread;

    private IPtProxy.Controller ptController;
    private int ptSnowflakePort = -1;
    private int ptObfs4Port = -1;
    private int ptWebtunnelPort = -1;

    private SharedConfig.ProxyInfo previousProxy;
    private boolean previousProxyEnabled;

    private boolean isReceiverRegistered = false;

    private static final Pattern BOOTSTRAP_PATTERN = Pattern.compile("BOOTSTRAP PROGRESS=(\\d+)(?:.*SUMMARY=\"([^\"]+)\")?");
    private static final Pattern LOGCAT_BOOTSTRAP_PATTERN = Pattern.compile("Bootstrapped\\s+(\\d+)%\\s*(?:\\(.*?\\))?:\\s*(.*)");

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            try {
                if (service instanceof TorService.LocalBinder) {
                    TorService.LocalBinder binder = (TorService.LocalBinder) service;
                    boundTorService = binder.getService();
                    setupControlConnectionFromService();
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            boundTorService = null;
            controlConnection = null;
        }
    };

    private final BroadcastReceiver torStatusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            String action = intent.getAction();
            if (TorService.ACTION_STATUS.equals(action)) {
                String status = intent.getStringExtra(TorService.EXTRA_STATUS);
                if (TorService.STATUS_STARTING.equals(status)) {
                    if (currentStatus != STATUS_CONNECTING) {
                        updateStatus(STATUS_CONNECTING, Math.max(currentProgress, 15), "Запуск подсистемы Tor...");
                    }
                } else if (TorService.STATUS_ON.equals(status)) {
                    onTorConnected();
                } else if (TorService.STATUS_STOPPING.equals(status)) {
                    updateStatus(STATUS_STOPPING, 0, "Остановка Tor...");
                } else if (TorService.STATUS_OFF.equals(status)) {
                    if (currentStatus != STATUS_STOPPED) {
                        updateStatus(STATUS_STOPPED, 0, "Отключено");
                        restorePreviousProxyState();
                    }
                }
            } else if (TorService.ACTION_ERROR.equals(action)) {
                String error = intent.getStringExtra(Intent.EXTRA_TEXT);
                postLog("Tor ошибка: " + error);
                updateStatus(STATUS_ERROR, 0, error != null ? error : "Ошибка службы Tor");
                restorePreviousProxyState();
            }
        }
    };

    private TorController() {
    }

    private SharedPreferences getPrefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("tor_config", Context.MODE_PRIVATE);
    }

    public boolean isEnabled() {
        return getPrefs().getBoolean("tor_enabled", false);
    }

    public void setEnabled(boolean enabled) {
        getPrefs().edit()
                .putBoolean("tor_enabled", enabled)
                .putInt("tor_rapid_starts", 0)
                .apply();
        if (enabled) {
            start();
        } else {
            stop();
        }
    }

    public int getBridgeType() {
        int val = getPrefs().getInt("tor_bridge_type", BRIDGE_CUSTOM);
        return val == BRIDGE_DIRECT ? BRIDGE_DIRECT : BRIDGE_CUSTOM;
    }

    public void setBridgeType(int type) {
        int finalType = type == BRIDGE_DIRECT ? BRIDGE_DIRECT : BRIDGE_CUSTOM;
        getPrefs().edit()
                .putInt("tor_bridge_type", finalType)
                .putBoolean("tor_bridge_explicitly_set", true)
                .apply();
        if (currentStatus == STATUS_CONNECTED || currentStatus == STATUS_CONNECTING) {
            restart();
        }
    }

    public String getExitCountry() {
        return getPrefs().getString("tor_exit_country", "");
    }

    public void setExitCountry(String country) {
        getPrefs().edit().putString("tor_exit_country", country != null ? country.toLowerCase() : "").apply();
        if (controlConnection != null) {
            updateExitCountryViaControl(country);
        } else if (currentStatus == STATUS_CONNECTED || currentStatus == STATUS_CONNECTING) {
            restart();
        }
    }

    public String getCustomBridges() {
        return getPrefs().getString("tor_custom_bridges", "");
    }

    public void setCustomBridges(String bridges) {
        getPrefs().edit().putString("tor_custom_bridges", bridges != null ? bridges : "").apply();
        if (getBridgeType() == BRIDGE_CUSTOM && (currentStatus == STATUS_CONNECTED || currentStatus == STATUS_CONNECTING)) {
            restart();
        }
    }

    public String getEffectiveBridges() {
        String custom = getCustomBridges();
        if (!TextUtils.isEmpty(custom.trim())) {
            return custom.trim();
        }
        StringBuilder sb = new StringBuilder();
        for (String b : DEFAULT_OBFS4_BRIDGES) {
            sb.append(b).append("\n");
        }
        return sb.toString().trim();
    }

    public int getStatus() {
        return currentStatus;
    }

    public int getProgress() {
        return currentProgress;
    }

    public String getStatusMessage() {
        return currentStatusMessage;
    }

    public void addListener(Listener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
            listener.onStatusChanged(currentStatus, currentProgress, currentStatusMessage);
        }
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public void addExitNodeListener(ExitNodeListener listener) {
        if (!exitNodeListeners.contains(listener)) {
            exitNodeListeners.add(listener);
            listener.onExitNodeInfoUpdated(currentExitIp, currentExitCountry, currentExitFlag, currentPing);
        }
    }

    public void removeExitNodeListener(ExitNodeListener listener) {
        exitNodeListeners.remove(listener);
    }

    private void notifyExitNodeListeners() {
        mainHandler.post(() -> {
            for (ExitNodeListener l : new ArrayList<>(exitNodeListeners)) {
                l.onExitNodeInfoUpdated(currentExitIp, currentExitCountry, currentExitFlag, currentPing);
            }
        });
    }

    public String getExitNodeIp() {
        return currentExitIp;
    }

    public String getExitNodeCountry() {
        return currentExitCountry;
    }

    public String getExitNodeCountryCode() {
        return currentExitCountryCode;
    }

    public String getExitNodeFlag() {
        return currentExitFlag;
    }

    public long getExitNodePing() {
        return currentPing;
    }

    public boolean isCheckingExitNode() {
        return isCheckingExitNode;
    }

    public static String getCountryFlag(String countryCode) {
        if (TextUtils.isEmpty(countryCode) || countryCode.length() != 2) return "🌐";
        try {
            int firstChar = Character.toUpperCase(countryCode.charAt(0)) - 'A' + 0x1F1E6;
            int secondChar = Character.toUpperCase(countryCode.charAt(1)) - 'A' + 0x1F1E6;
            return new String(Character.toChars(firstChar)) + new String(Character.toChars(secondChar));
        } catch (Exception e) {
            return "🌐";
        }
    }

    public void checkExitNodeInfo() {
        if (currentStatus != STATUS_CONNECTED) {
            return;
        }
        isCheckingExitNode = true;
        notifyExitNodeListeners();

        new Thread(() -> {
            int port = DEFAULT_SOCKS_PORT;
            if (boundTorService != null) {
                int sp = boundTorService.getSocksPort();
                if (sp > 0) port = sp;
            }

            Proxy proxy = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress("127.0.0.1", port));
            String fetchedIp = null;
            String fetchedCountry = null;
            String fetchedCode = null;
            String fetchedFlag = null;
            long pingMs = -1;

            try {
                long start = SystemClock.elapsedRealtime();
                URL url = new URL("https://ipwho.is/");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection(proxy);
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                if (conn.getResponseCode() == 200) {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) sb.append(line);
                        JSONObject json = new JSONObject(sb.toString());
                        if (json.optBoolean("success", true)) {
                            fetchedIp = json.optString("ip");
                            fetchedCountry = json.optString("country");
                            fetchedCode = json.optString("country_code");
                            JSONObject flagObj = json.optJSONObject("flag");
                            if (flagObj != null) {
                                fetchedFlag = flagObj.optString("emoji");
                            }
                            pingMs = SystemClock.elapsedRealtime() - start;
                        }
                    }
                }
                conn.disconnect();
            } catch (Exception e) {
                FileLog.e("ipwho.is check failed: " + e.getMessage());
            }

            if (TextUtils.isEmpty(fetchedIp)) {
                try {
                    long start = SystemClock.elapsedRealtime();
                    URL url = new URL("http://ip-api.com/json");
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection(proxy);
                    conn.setConnectTimeout(8000);
                    conn.setReadTimeout(8000);
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                    if (conn.getResponseCode() == 200) {
                        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                            StringBuilder sb = new StringBuilder();
                            String line;
                            while ((line = reader.readLine()) != null) sb.append(line);
                            JSONObject json = new JSONObject(sb.toString());
                            if ("success".equalsIgnoreCase(json.optString("status"))) {
                                fetchedIp = json.optString("query");
                                fetchedCountry = json.optString("country");
                                fetchedCode = json.optString("countryCode");
                                pingMs = SystemClock.elapsedRealtime() - start;
                            }
                        }
                    }
                    conn.disconnect();
                } catch (Exception e) {
                    FileLog.e("ip-api.com check failed: " + e.getMessage());
                }
            }

            if (TextUtils.isEmpty(fetchedFlag) && !TextUtils.isEmpty(fetchedCode)) {
                fetchedFlag = getCountryFlag(fetchedCode);
            }

            final String finalIp = fetchedIp;
            final String finalCountry = fetchedCountry;
            final String finalCode = fetchedCode;
            final String finalFlag = !TextUtils.isEmpty(fetchedFlag) ? fetchedFlag : "🌐";
            final long finalPing = pingMs;

            mainHandler.post(() -> {
                isCheckingExitNode = false;
                if (!TextUtils.isEmpty(finalIp)) {
                    currentExitIp = finalIp;
                    currentExitCountry = finalCountry != null ? finalCountry : "";
                    currentExitCountryCode = finalCode != null ? finalCode : "";
                    currentExitFlag = finalFlag;
                    currentPing = finalPing;
                }
                notifyExitNodeListeners();
            });
        }, "TorExitNodeChecker").start();
    }

    private void startStallWatchdog() {
        stopStallWatchdog();
        stallWatchdogRunnable = () -> {
            if (currentStatus == STATUS_CONNECTING && currentProgress <= 15) {
                int bridge = getBridgeType();
                if (bridge == BRIDGE_DIRECT) {
                    postLog("Обнаружена блокировка прямого подключения Tor. Автоматическое переключение на мосты...");
                    setBridgeType(BRIDGE_CUSTOM);
                } else {
                    postLog("Таймаут первичного рукопожатия Tor. Перезапуск соединения...");
                    restart();
                }
            }
        };
        mainHandler.postDelayed(stallWatchdogRunnable, 25000);
    }

    private void stopStallWatchdog() {
        if (stallWatchdogRunnable != null) {
            mainHandler.removeCallbacks(stallWatchdogRunnable);
            stallWatchdogRunnable = null;
        }
    }

    private void updateStatus(int status, int progress, String message) {
        currentStatus = status;
        currentProgress = progress;
        currentStatusMessage = message != null ? message : "";
        TorDiagnostics.getInstance().logEvent("STATUS", "State " + status + " (" + progress + "%): " + currentStatusMessage);
        if (progress > 15 || status == STATUS_CONNECTED || status == STATUS_STOPPED || status == STATUS_ERROR) {
            stopStallWatchdog();
        }
        mainHandler.post(() -> {
            for (Listener l : new ArrayList<>(listeners)) {
                try {
                    l.onStatusChanged(status, progress, currentStatusMessage);
                } catch (Throwable t) {
                    FileLog.e(t);
                }
            }
        });
    }

    private void postLog(String log) {
        TorDiagnostics.getInstance().logEvent("TOR_LOG", log);
        mainHandler.post(() -> {
            for (Listener l : new ArrayList<>(listeners)) {
                try {
                    l.onLog(log);
                } catch (Throwable t) {
                    FileLog.e(t);
                }
            }
        });
    }

    /**
     * Запуск Tor и вспомогательных служб
     */
    public synchronized void start() {
        if (currentStatus == STATUS_CONNECTING || currentStatus == STATUS_CONNECTED) {
            return;
        }

        SharedPreferences prefs = getPrefs();
        long now = System.currentTimeMillis();
        long lastStart = prefs.getLong("tor_last_start_ts", 0);
        int rapidStarts = prefs.getInt("tor_rapid_starts", 0);

        if (now - lastStart < 12000) {
            rapidStarts++;
        } else {
            rapidStarts = 1;
        }
        prefs.edit()
                .putLong("tor_last_start_ts", now)
                .putInt("tor_rapid_starts", rapidStarts)
                .apply();

        TorDiagnostics.getInstance().logEvent("START", "Tor start initiated (rapid attempt=" + rapidStarts + ")");

        if (rapidStarts >= 3) {
            TorDiagnostics.getInstance().logEvent("CRASH_GUARD", "Rapid crash loop detected (" + rapidStarts + " attempts). Auto-disabling Tor.");
            prefs.edit()
                    .putBoolean("tor_enabled", false)
                    .putInt("tor_rapid_starts", 0)
                    .apply();
            updateStatus(STATUS_ERROR, 0, "Tor автоматически отключен из-за повторных сбоев при запуске");
            return;
        }

        updateStatus(STATUS_STARTING, 5, "Инициализация Tor...");
        savePreviousProxyState();

        new Thread(() -> {
            try {
                Context context = ApplicationLoader.applicationContext;
                File ptStateDir = new File(context.getFilesDir(), "pt_state");
                if (!ptStateDir.exists()) {
                    ptStateDir.mkdirs();
                }

                int bridgeType = getBridgeType();

                // 1. Запуск Pluggable Transports через IPtProxy (если требуется)
                startPluggableTransports(ptStateDir, bridgeType);

                // 2. Генерация конфигурации torrc для TorService
                File torrcFile = generateTorrc(context, bridgeType);

                // 3. Регистрация ресивера статусов Tor
                registerStatusReceiver(context);

                // 4. Запуск службы TorService
                try {
                    Intent serviceIntent = new Intent(context, TorService.class);
                    serviceIntent.setAction(TorService.ACTION_START);
                    try {
                        context.startService(serviceIntent);
                    } catch (Throwable t) {
                        FileLog.e("TorService startService failed, relying on bindService: " + t.getMessage());
                    }
                    context.bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
                    updateStatus(STATUS_CONNECTING, 15, "Подключение к сети Tor...");
                    startStallWatchdog();
                } catch (Throwable t) {
                    FileLog.e("TorService start error, trying standalone fallback: " + t.getMessage());
                    startStandaloneFallback(context, torrcFile);
                }

            } catch (Exception e) {
                FileLog.e(e);
                postLog("Ошибка старта Tor: " + e.getMessage());
                updateStatus(STATUS_ERROR, 0, e.getMessage());
                stopPluggableTransports();
                restorePreviousProxyState();
            }
        }, "TorStarter").start();
    }

    /**
     * Остановка Tor
     */
    public synchronized void stop() {
        if (currentStatus == STATUS_STOPPED) {
            return;
        }

        stopStallWatchdog();
        updateStatus(STATUS_STOPPING, 0, "Остановка Tor...");

        new Thread(() -> {
            Context context = ApplicationLoader.applicationContext;
            try {
                if (boundTorService != null) {
                    try {
                        context.unbindService(serviceConnection);
                    } catch (Exception ignored) {}
                    boundTorService = null;
                }

                try {
                    Intent stopIntent = new Intent(context, TorService.class);
                    context.stopService(stopIntent);
                } catch (Exception ignored) {}

                if (controlConnection != null) {
                    try {
                        controlConnection.shutdownTor("SHUTDOWN");
                    } catch (Exception ignored) {}
                    controlConnection = null;
                }

                if (controlSocket != null) {
                    try {
                        controlSocket.close();
                    } catch (Exception ignored) {}
                    controlSocket = null;
                }

                if (standaloneProcess != null) {
                    standaloneProcess.destroy();
                    standaloneProcess = null;
                }

                unregisterStatusReceiver(context);
                stopPluggableTransports();
                restorePreviousProxyState();

                currentExitIp = "";
                currentExitCountry = "";
                currentExitCountryCode = "";
                currentExitFlag = "";
                currentPing = -1;
                isCheckingExitNode = false;
                notifyExitNodeListeners();

                updateStatus(STATUS_STOPPED, 0, "Отключено");
            } catch (Exception e) {
                FileLog.e(e);
                updateStatus(STATUS_STOPPED, 0, "Отключено");
            }
        }, "TorStopper").start();
    }

    public void restart() {
        stop();
        mainHandler.postDelayed(this::start, 1200);
    }

    /**
     * Запрос новой цепи Tor (NEWNYM)
     */
    public void newIdentity() {
        new Thread(() -> {
            try {
                if (controlConnection != null) {
                    controlConnection.signal("NEWNYM");
                    postLog("Смена цепи Tor: получена новая личность");
                    mainHandler.post(() -> {
                        AndroidUtilities.runOnUIThread(() -> {
                            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxySettingsChanged);
                        });
                    });
                } else if (boundTorService != null) {
                    TorControlConnection conn = boundTorService.getTorControlConnection();
                    if (conn != null) {
                        conn.signal("NEWNYM");
                        postLog("Смена цепи Tor: получена новая личность");
                    }
                }
                mainHandler.postDelayed(this::checkExitNodeInfo, 1500);
            } catch (Exception e) {
                FileLog.e(e);
            }
        }, "TorNewNym").start();
    }

    private void registerStatusReceiver(Context context) {
        if (!isReceiverRegistered) {
            IntentFilter filter = new IntentFilter();
            filter.addAction(TorService.ACTION_STATUS);
            filter.addAction(TorService.ACTION_ERROR);
            LocalBroadcastManager.getInstance(context).registerReceiver(torStatusReceiver, filter);
            isReceiverRegistered = true;
        }
    }

    private void unregisterStatusReceiver(Context context) {
        if (isReceiverRegistered) {
            try {
                LocalBroadcastManager.getInstance(context).unregisterReceiver(torStatusReceiver);
            } catch (Exception ignored) {}
            isReceiverRegistered = false;
        }
    }

    private void setupControlConnectionFromService() {
        new Thread(() -> {
            try {
                for (int i = 0; i < 20; i++) {
                    if (boundTorService != null) {
                        controlConnection = boundTorService.getTorControlConnection();
                        if (controlConnection != null) {
                            attachControlListener(controlConnection);
                            break;
                        }
                    }
                    Thread.sleep(500);
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }, "TorControlAttacher").start();
    }

    private void attachControlListener(TorControlConnection conn) {
        try {
            conn.setEvents(Arrays.asList(
                    TorControlCommands.EVENT_STATUS_CLIENT,
                    TorControlCommands.EVENT_NOTICE_MSG,
                    TorControlCommands.EVENT_WARN_MSG,
                    TorControlCommands.EVENT_ERR_MSG
            ));

            conn.addRawEventListener(new RawEventListener() {
                @Override
                public void onEvent(String keyword, String data) {
                    postLog(keyword + ": " + data);
                    if (TorControlCommands.EVENT_STATUS_CLIENT.equals(keyword) && data != null) {
                        Matcher matcher = BOOTSTRAP_PATTERN.matcher(data);
                        if (matcher.find()) {
                            int progress = Integer.parseInt(matcher.group(1));
                            String summary = matcher.group(2);
                            updateStatus(STATUS_CONNECTING, progress, summary != null ? summary : "Подключение: " + progress + "%");
                            if (progress == 100) {
                                onTorConnected();
                            }
                        }
                    }
                }
            });
            postLog("Tor Control connection успешно подключен");
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private void onTorConnected() {
        getPrefs().edit().putInt("tor_rapid_starts", 0).apply();
        TorDiagnostics.getInstance().logEvent("HEALTH", "Tor connected successfully (100%)");

        int port = DEFAULT_SOCKS_PORT;
        if (boundTorService != null) {
            int servicePort = boundTorService.getSocksPort();
            if (servicePort > 0) {
                port = servicePort;
            }
        }

        updateStatus(STATUS_CONNECTED, 100, "Подключено к Tor");
        final int finalPort = port;
        AndroidUtilities.runOnUIThread(() -> {
            SharedConfig.ProxyInfo torProxy = new SharedConfig.ProxyInfo("127.0.0.1", finalPort, "", "", "");
            SharedConfig.currentProxy = torProxy;
            SharedConfig.setProxyEnable(true);
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxySettingsChanged);
        });
        checkExitNodeInfo();
    }

    private void startPluggableTransports(File ptStateDir, int bridgeType) {
        stopPluggableTransports();
        if (bridgeType == BRIDGE_DIRECT) {
            return;
        }

        try {
            String bridges = getEffectiveBridges();
            boolean needSnowflake = bridges.contains("snowflake");
            boolean needWebtunnel = bridges.contains("webtunnel");
            boolean needObfs4 = bridges.contains("obfs4") || (!needSnowflake && !needWebtunnel);

            ptController = new IPtProxy.Controller(ptStateDir.getAbsolutePath(), true, false, "INFO", null);

            if (needSnowflake) {
                ptController.setSnowflakeIceServers(DEFAULT_STUN_SERVER);
                ptController.setSnowflakeFrontDomains(DEFAULT_FRONT_DOMAIN);
                ptController.setSnowflakeBrokerUrl(DEFAULT_SNOWFLAKE_BROKER);
                ptController.setSnowflakeAmpCacheUrl(DEFAULT_AMP_CACHE);
                ptController.start("snowflake", "");
                ptSnowflakePort = (int) ptController.port("snowflake");
                postLog("Snowflake запущен на локальном порту " + ptSnowflakePort);
            }

            if (needWebtunnel) {
                try {
                    ptController.start("webtunnel", "");
                    ptWebtunnelPort = (int) ptController.port("webtunnel");
                    postLog("WebTunnel запущен на локальном порту " + ptWebtunnelPort);
                } catch (Throwable t) {
                    FileLog.e(t);
                    postLog("Предупреждение запуска webtunnel: " + t.getMessage());
                }
            }

            if (needObfs4) {
                ptController.start("obfs4", "");
                ptObfs4Port = (int) ptController.port("obfs4");
                postLog("obfs4 запущен на локальном порту " + ptObfs4Port);
            }
        } catch (Throwable t) {
            FileLog.e(t);
            postLog("Предупреждение запуска моста: " + t.getMessage());
        }
    }

    private void stopPluggableTransports() {
        if (ptController != null) {
            try {
                if (ptSnowflakePort > 0) {
                    ptController.stop("snowflake");
                }
            } catch (Exception ignored) {}
            try {
                if (ptWebtunnelPort > 0) {
                    ptController.stop("webtunnel");
                }
            } catch (Exception ignored) {}
            try {
                if (ptObfs4Port > 0) {
                    ptController.stop("obfs4");
                }
            } catch (Exception ignored) {}
            ptController = null;
            ptSnowflakePort = -1;
            ptWebtunnelPort = -1;
            ptObfs4Port = -1;
        }
    }

    private File generateTorrc(Context context, int bridgeType) throws Exception {
        File torrcFile = TorService.getTorrc(context);
        File parent = torrcFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        try (PrintWriter writer = new PrintWriter(new FileOutputStream(torrcFile))) {
            writer.println("SocksPort 127.0.0.1:" + DEFAULT_SOCKS_PORT);
            writer.println("ControlPort 127.0.0.1:" + DEFAULT_CONTROL_PORT);
            writer.println("CookieAuthentication 0");
            writer.println("KeepalivePeriod 60");
            writer.println("Log notice stdout");

            // Оптимизация скорости и надежности подключения Tor
            writer.println("ClientOnly 1");
            writer.println("AvoidDiskWrites 1");
            writer.println("FastFirstHopPK 1");
            writer.println("CircuitBuildTimeout 15");
            writer.println("LearnCircuitBuildTimeout 1");
            writer.println("NumEntryGuards 3");
            writer.println("ConnectionPadding 1");

            // Выходной узел
            String country = getExitCountry();
            if (!TextUtils.isEmpty(country)) {
                writer.println("ExitNodes {" + country.toLowerCase() + "}");
                writer.println("StrictNodes 1");
            }

            // Настройка мостов (только в режиме BRIDGE_CUSTOM)
            if (bridgeType == BRIDGE_CUSTOM) {
                String bridges = getEffectiveBridges();
                if (!TextUtils.isEmpty(bridges)) {
                    writer.println("UseBridges 1");
                    if (ptObfs4Port > 0) {
                        writer.println("ClientTransportPlugin obfs4 socks5 127.0.0.1:" + ptObfs4Port);
                    }
                    if (ptSnowflakePort > 0) {
                        writer.println("ClientTransportPlugin snowflake socks5 127.0.0.1:" + ptSnowflakePort);
                    }
                    if (ptWebtunnelPort > 0) {
                        writer.println("ClientTransportPlugin webtunnel socks5 127.0.0.1:" + ptWebtunnelPort);
                    }
                    String[] lines = bridges.split("\\r?\\n");
                    for (String line : lines) {
                        String trimmed = line.trim();
                        if (!trimmed.isEmpty()) {
                            if (!trimmed.toLowerCase().startsWith("bridge ")) {
                                writer.println("Bridge " + trimmed);
                            } else {
                                writer.println(trimmed);
                            }
                        }
                    }
                }
            }
        }
        return torrcFile;
    }

    private void startStandaloneFallback(Context context, File torrcFile) {
        try {
            File torBinary = new File(context.getApplicationInfo().nativeLibraryDir, "libtor.so");
            if (!torBinary.exists()) {
                throw new IllegalStateException("Бинарный файл Tor не найден");
            }

            File torDataDir = new File(context.getFilesDir(), "tor");
            if (!torDataDir.exists()) torDataDir.mkdirs();

            ProcessBuilder pb = new ProcessBuilder(
                    torBinary.getAbsolutePath(),
                    "-f", torrcFile.getAbsolutePath()
            );
            pb.environment().put("HOME", torDataDir.getAbsolutePath());
            pb.redirectErrorStream(true);

            standaloneProcess = pb.start();
            updateStatus(STATUS_CONNECTING, 20, "Запуск процесса Tor...");
            startStallWatchdog();

            logReaderThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(standaloneProcess.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        postLog(line);
                        Matcher matcher = LOGCAT_BOOTSTRAP_PATTERN.matcher(line);
                        if (matcher.find()) {
                            int progress = Integer.parseInt(matcher.group(1));
                            String summary = matcher.group(2);
                            updateStatus(STATUS_CONNECTING, progress, summary);
                            if (progress == 100) {
                                onTorConnected();
                            }
                        }
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }

                if (currentStatus == STATUS_CONNECTING || currentStatus == STATUS_CONNECTED) {
                    updateStatus(STATUS_ERROR, 0, "Процесс Tor завершился");
                    restorePreviousProxyState();
                }
            }, "TorLogReader");
            logReaderThread.start();

        } catch (Exception e) {
            FileLog.e(e);
            updateStatus(STATUS_ERROR, 0, e.getMessage());
            restorePreviousProxyState();
        }
    }

    private void updateExitCountryViaControl(String country) {
        new Thread(() -> {
            try {
                TorControlConnection conn = controlConnection;
                if (conn == null && boundTorService != null) {
                    conn = boundTorService.getTorControlConnection();
                }
                if (conn != null) {
                    if (TextUtils.isEmpty(country)) {
                        conn.setConf("ExitNodes", "");
                        conn.setConf("StrictNodes", "0");
                    } else {
                        conn.setConf("ExitNodes", "{" + country.toLowerCase() + "}");
                        conn.setConf("StrictNodes", "1");
                    }
                    postLog("Выходной узел обновлен: " + (TextUtils.isEmpty(country) ? "Авто" : country.toUpperCase()));
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }).start();
    }

    private void savePreviousProxyState() {
        previousProxy = SharedConfig.currentProxy;
        previousProxyEnabled = SharedConfig.isProxyEnabled();
    }

    private void restorePreviousProxyState() {
        AndroidUtilities.runOnUIThread(() -> {
            if (SharedConfig.currentProxy != null &&
                    "127.0.0.1".equals(SharedConfig.currentProxy.address) &&
                    (SharedConfig.currentProxy.port == DEFAULT_SOCKS_PORT ||
                            (boundTorService != null && SharedConfig.currentProxy.port == boundTorService.getSocksPort()))) {

                SharedConfig.currentProxy = previousProxy;
                SharedConfig.setProxyEnable(previousProxyEnabled && previousProxy != null);
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxySettingsChanged);
            }
        });
    }
}

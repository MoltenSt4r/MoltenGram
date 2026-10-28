package com.exteragram.messenger.utils;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.Keep;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import com.exteragram.messenger.utils.text.LocaleUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;
import org.telegram.ui.ActionBar.Theme;

import java.lang.reflect.Field;
import java.security.MessageDigest;
import java.util.Calendar;
import java.util.Locale;

/** Шим {@code com.exteragram.messenger.utils.AppUtils}: то, что плагины зовут по имени exteraGram. */
public final class AppUtils {

    private static final String RELEASE_SIGNATURE = "n4MjWgos1KTzGpMSD4ztPg==";

    private static volatile Gson gson;

    private AppUtils() {
    }

    public static Gson getGson() {
        if (gson == null) {
            synchronized (AppUtils.class) {
                if (gson == null) {
                    gson = new GsonBuilder()
                            .setPrettyPrinting()
                            .serializeSpecialFloatingPointValues()
                            .addSerializationExclusionStrategy(new ExclusionStrategy() {
                                @Override
                                public boolean shouldSkipClass(Class<?> clazz) {
                                    return isPlatformPackage(clazz);
                                }

                                @Override
                                public boolean shouldSkipField(FieldAttributes attributes) {
                                    return isPlatformPackage(attributes.getDeclaringClass());
                                }
                            })
                            .create();
                }
            }
        }
        return gson;
    }

    private static boolean isPlatformPackage(Class<?> clazz) {
        if (clazz == null || clazz.getPackage() == null) {
            return false;
        }
        String name = clazz.getPackage().getName();
        return name.startsWith("android.") || name.startsWith("androidx.");
    }

    @Keep
    public static void log(String message) {
        logInternal(message, null, 5);
    }

    @Keep
    public static void log(String message, Throwable throwable) {
        logInternal(message, throwable, 5);
    }

    @Keep
    public static void log(Throwable throwable) {
        logInternal(throwable != null ? throwable.getMessage() : null, throwable, 5);
    }

    private static void logInternal(String message, Throwable throwable, int depth) {
        StackTraceElement[] trace = Thread.currentThread().getStackTrace();
        StackTraceElement frame = trace[Math.max(3, Math.min(depth, trace.length - 1))];
        String owner = frame.getClassName();
        if (owner.contains(".")) {
            owner = owner.substring(owner.lastIndexOf('.') + 1);
        }
        if (owner.contains("$")) {
            owner = owner.substring(owner.lastIndexOf('$') + 1);
        }
        String tag = "[" + owner + "]";
        String text = String.format(Locale.US, "[%s] %s", frame.getMethodName(), message);
        if (throwable != null) {
            Log.e(tag, text, throwable);
        } else {
            Log.d(tag, text);
        }
    }

    @Keep
    public static void printObjectDetails(Object object) {
        if (object == null) {
            return;
        }
        try {
            logInternal(object.getClass().getName() + ": " + getGson().toJson(object), null, 6);
        } catch (Exception e) {
            logInternal(object.getClass().getName(), e, 6);
        }
    }

    public static int compareVersionValues(String left, String right) {
        String[] a = left.split("\\.");
        String[] b = right.split("\\.");
        int max = Math.max(a.length, b.length);
        for (int i = 0; i < max; i++) {
            int x = i < a.length ? Integer.parseInt(a[i]) : 0;
            int y = i < b.length ? Integer.parseInt(b[i]) : 0;
            if (x != y) {
                return Integer.compare(x, y);
            }
        }
        return 0;
    }

    public static boolean compareVersions(String operator, int left, int right) {
        return applyOperator(operator, Integer.compare(left, right));
    }

    public static boolean compareVersions(String operator, String left, String right) {
        return applyOperator(operator, compareVersionValues(left, right));
    }

    private static boolean applyOperator(String operator, int comparison) {
        if (operator == null) {
            return false;
        }
        switch (operator) {
            case "<":
                return comparison < 0;
            case ">":
                return comparison > 0;
            case "<=":
                return comparison <= 0;
            case "==":
                return comparison == 0;
            case ">=":
                return comparison >= 0;
            default:
                FileLog.e("AppUtils: unsupported operator " + operator);
                return false;
        }
    }

    public static void ensureRunningOnUi(Runnable runnable) {
        if (Thread.currentThread() != Looper.getMainLooper().getThread()) {
            AndroidUtilities.runOnUIThread(runnable);
        } else {
            runnable.run();
        }
    }

    public static int getNotificationColor() {
        Theme.ThemeInfo theme = Theme.getActiveTheme();
        int color = theme != null && theme.hasAccentColors()
                ? theme.getAccentColor(theme.currentAccentId) : 0;
        if (color == 0) {
            color = Theme.getColor(Theme.key_actionBarDefault) | 0xFF000000;
        }
        float brightness = AndroidUtilities.computePerceivedBrightness(color);
        return brightness >= 0.721f || brightness <= 0.279f
                ? Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader) | 0xFF000000
                : color;
    }

    public static int getSwipeVelocity() {
        Point size = AndroidUtilities.displaySize;
        return size.x > size.y ? 1250 : 850;
    }

    public static boolean isWinter() {
        int month = Calendar.getInstance().get(Calendar.MONTH);
        return month == Calendar.DECEMBER || month == Calendar.JANUARY || month == Calendar.FEBRUARY;
    }

    public static String getVersionText() {
        StringBuilder builder = new StringBuilder();
        builder.append(LocaleUtils.getAppName());
        if (!android.text.TextUtils.isEmpty(BuildVars.BUILD_CODENAME)) {
            builder.append(" ").append(BuildVars.BUILD_CODENAME);
        }
        builder.append(" ");
        builder.append(BuildVars.BUILD_VERSION_STRING);
        try {
            PackageInfo info = ApplicationLoader.applicationContext.getPackageManager()
                    .getPackageInfo(ApplicationLoader.applicationContext.getPackageName(), 0);
            builder.append(" (");
            builder.append(info.versionCode);
            builder.append(")");
        } catch (Exception e) {
            FileLog.e(e);
        }
        if (isAppModified()) {
            builder.append("\nbased on @exteraless");
        }
        return builder.toString();
    }

    public static boolean isAppModified() {
        try {
            PackageInfo info = ApplicationLoader.applicationContext.getPackageManager()
                    .getPackageInfo(ApplicationLoader.applicationContext.getPackageName(),
                            PackageManager.GET_SIGNATURES);
            String signature = Base64.encodeToString(MessageDigest.getInstance("MD5")
                    .digest(info.signatures[0].toByteArray()), Base64.DEFAULT).trim();
            return !BuildConfig.APPLICATION_ID.equals(info.packageName)
                    || !RELEASE_SIGNATURE.equals(signature);
        } catch (Exception e) {
            FileLog.e(e);
            return true;
        }
    }

    @Keep
    public static Object getPrivateField(Object object, String name)
            throws NoSuchFieldException, SecurityException {
        if (object == null) {
            return null;
        }
        try {
            Field field = findField(object.getClass(), name);
            if (field != null) {
                field.setAccessible(true);
                return field.get(object);
            }
        } catch (Exception e) {
            FileLog.e(object.getClass().getName(), e);
        }
        return null;
    }

    @Keep
    public static Object getPrivateStaticField(Class<?> clazz, String name)
            throws NoSuchFieldException, SecurityException {
        if (clazz == null) {
            return null;
        }
        try {
            Field field = findField(clazz, name);
            if (field != null) {
                field.setAccessible(true);
                return field.get(null);
            }
        } catch (Exception e) {
            FileLog.e(clazz.getName(), e);
        }
        return null;
    }

    @Keep
    public static void setPrivateField(Object object, String name, Object value)
            throws IllegalAccessException, NoSuchFieldException, SecurityException,
            IllegalArgumentException {
        if (object == null) {
            return;
        }
        try {
            Field field = findField(object.getClass(), name);
            if (field != null) {
                field.setAccessible(true);
                field.set(object, value);
            }
        } catch (Exception e) {
            FileLog.e(object.getClass().getName(), e);
        }
    }

    @Keep
    public static void setPrivateStaticField(Class<?> clazz, String name, Object value)
            throws IllegalAccessException, NoSuchFieldException, SecurityException,
            IllegalArgumentException {
        if (clazz == null) {
            return;
        }
        try {
            Field field = findField(clazz, name);
            if (field != null) {
                field.setAccessible(true);
                field.set(null, value);
            }
        } catch (Exception e) {
            FileLog.e(clazz.getName(), e);
        }
    }

    @Keep
    public static String stackTraceToString(Throwable throwable) {
        if (throwable == null) {
            return "";
        }
        java.io.StringWriter writer = new java.io.StringWriter();
        throwable.printStackTrace(new java.io.PrintWriter(writer));
        return writer.toString();
    }

    private static Field findField(Class<?> clazz, String name) {
        for (Class<?> current = clazz; current != null; current = current.getSuperclass()) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
            }
        }
        return null;
    }
}

package app.exteraless.plugins.xposed;

import java.lang.reflect.Member;

/**
 * No-op compatibility stub: exteraHook (hook-core + LSPlant 6.4) does not have
 * Aliuhook 1.1.4's HookInfo/hookRecords race condition.
 */
public final class HookGate {

    private HookGate() {
    }

    public static void prewarm(Member member) {
    }

    public static void guardBridge() {
    }

    public static void prewarmAllMethods(Class<?> clazz, String methodName) {
    }

    public static void prewarmAllConstructors(Class<?> clazz) {
    }
}

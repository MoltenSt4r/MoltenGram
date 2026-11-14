package app.exteraless.plugins.xposed;

import java.lang.reflect.Constructor;
import java.lang.reflect.Member;
import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;

final class HookNumbers {

    private HookNumbers() {
    }

    static void coerce(XC_MethodHook.MethodHookParam param) {
        final Object[] args = param.args;
        final boolean argsNeed = args != null && anyWide(args);
        final Object result = param.hasThrowable() ? null : param.getResult();
        final boolean resultNeeds = isWide(result);
        if (!argsNeed && !resultNeeds) {
            return;
        }
        final Member member = param.method;
        final Class<?>[] types;
        Class<?> returnType = null;
        if (member instanceof Method) {
            types = ((Method) member).getParameterTypes();
            returnType = ((Method) member).getReturnType();
        } else if (member instanceof Constructor) {
            types = ((Constructor<?>) member).getParameterTypes();
        } else {
            return;
        }
        if (argsNeed) {
            final int count = Math.min(args.length, types.length);
            for (int i = 0; i < count; i++) {
                if (args[i] instanceof Number) {
                    args[i] = convert((Number) args[i], types[i]);
                }
            }
        }
        if (resultNeeds && returnType != null) {
            final Object converted = convert((Number) result, returnType);
            if (converted != result) {
                param.setResult(converted);
            }
        }
    }

    private static boolean anyWide(Object[] values) {
        for (Object value : values) {
            if (isWide(value)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isWide(Object value) {
        return value instanceof Long || value instanceof Double;
    }

    static Object convert(Number value, Class<?> type) {
        if (type == int.class || type == Integer.class) {
            return value instanceof Integer ? value : (Object) value.intValue();
        }
        if (type == long.class || type == Long.class) {
            return value instanceof Long ? value : (Object) value.longValue();
        }
        if (type == float.class || type == Float.class) {
            return value instanceof Float ? value : (Object) value.floatValue();
        }
        if (type == double.class || type == Double.class) {
            return value instanceof Double ? value : (Object) value.doubleValue();
        }
        if (type == short.class || type == Short.class) {
            return value instanceof Short ? value : (Object) value.shortValue();
        }
        if (type == byte.class || type == Byte.class) {
            return value instanceof Byte ? value : (Object) value.byteValue();
        }
        if (type == char.class || type == Character.class) {
            return (char) value.intValue();
        }
        return value;
    }
}

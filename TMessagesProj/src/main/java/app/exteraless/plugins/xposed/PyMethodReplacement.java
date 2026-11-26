package app.exteraless.plugins.xposed;

import com.chaquo.python.PyObject;

import org.telegram.messenger.FileLog;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;

/**
 * Замена метода: Python-метод {@code replace_hooked_method(param)} вызывается ВМЕСТО
 * оригинала, его возвращаемое значение становится результатом (None -> null для
 * ссылочных типов). Гейтится только "before"-фильтрами.
 *
 * Наследуемся от XC_MethodHook, а не от XC_MethodReplacement, намеренно: у
 * XC_MethodReplacement.beforeHookedMethod (final) любой вылет из replaceHookedMethod
 * превращается в param.setThrowable — исключение улетит в вызывающий код приложения.
 * Нам это нельзя: при ошибке Python param не трогаем, и выполняется оригинальный метод.
 */
public class PyMethodReplacement extends XC_MethodHook {

    private final String pluginId;
    private final PyObject handler;
    private final List<HookFilter> beforeFilters;

    public PyMethodReplacement(String pluginId, PyObject handler) {
        this(pluginId, handler, PRIORITY_DEFAULT);
    }

    public PyMethodReplacement(String pluginId, PyObject handler, int priority) {
        this(pluginId, handler, priority, Collections.emptyList());
    }

    public PyMethodReplacement(String pluginId, PyObject handler, int priority,
                        List<HookFilter> beforeFilters) {
        super(priority);
        this.pluginId = pluginId;
        this.handler = handler;
        this.beforeFilters = beforeFilters;
    }

    @Override
    public void beforeHookedMethod(MethodHookParam param) {
        if (!HookFilter.evaluateAll(beforeFilters, param, false)) {
            return;
        }
        XposedHooks.PyResult result =
                XposedHooks.callPython(pluginId, handler, "replace_hooked_method", param);
        if (!result.ok) {
            return; // ошибка уже ушла в watchdog; param не тронут — выполнится оригинал
        }
        try {
            applyResult(param, result.value);
        } catch (Throwable t) {
            // Результат не сконвертировался в returnType — param не трогаем.
            XposedHooks.reportError(pluginId, t);
        }
    }

    /**
     * Поставить результат замены в param. Конвертация — через Chaquopy toJava под
     * точный returnType метода (Python int -> правильный примитив и т.п.).
     */
    private void applyResult(MethodHookParam param, PyObject pyResult) {
        if (!(param.method instanceof Method)) {
            // Конструктор: объект уже аллоцирован, setResult бессмысленен — Xposed
            // не умеет подменять результат конструктора.
            return;
        }
        Class<?> returnType = ((Method) param.method).getReturnType();
        if (returnType == void.class || returnType == Void.class) {
            param.setResult(null); // просто подавляем вызов оригинала
            return;
        }
        if (pyResult == null) { // Chaquopy может вернуть Java null для Python None
            setNullResult(param, returnType);
            return;
        }
        if (returnType.isPrimitive()) {
            if (pyResult.toJava(Object.class) == null) { // Python вернул None
                setNullResult(param, returnType);
                return;
            }
            // toJava(primitive.class) возвращает точный boxed-тип для unbox в бридже.
            param.setResult(pyResult.toJava(returnType));
            return;
        }
        param.setResult(pyResult.toJava(returnType)); // None -> null для ссылочных типов
    }

    private void setNullResult(MethodHookParam param, Class<?> returnType) {
        if (returnType.isPrimitive()) {
            // null в примитив не распакуется (NPE в бридже) — оставляем оригинал.
            FileLog.e("XposedHooks: replacement for " + param.method
                    + " returned None for primitive " + returnType + ", running original");
            return;
        }
        param.setResult(null);
    }
}

package app.exteraless.plugins;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;

import org.telegram.messenger.FileLog;

public final class PythonThreadState {

    private static final int PIN_AFTER_CALLS = 16;
    private static final ThreadLocal<int[]> CALLS = new ThreadLocal<>();
    private static volatile boolean unavailable;

    private PythonThreadState() {
    }

    public static void pinCurrentThread() {
        if (unavailable || !Python.isStarted()) {
            return;
        }
        final int[] calls = calls();
        if (calls[0] < 0) {
            return;
        }
        calls[0] = -1;
        pin();
    }

    public static void onCall() {
        if (unavailable) {
            return;
        }
        final int[] calls = calls();
        if (calls[0] < 0 || ++calls[0] < PIN_AFTER_CALLS) {
            return;
        }
        calls[0] = -1;
        pin();
    }

    private static int[] calls() {
        int[] calls = CALLS.get();
        if (calls == null) {
            calls = new int[1];
            CALLS.set(calls);
        }
        return calls;
    }

    private static void pin() {
        try {
            final PyObject pinned = Python.getInstance().getModule("extera_utils.thread_state").callAttr("pin");
            if (pinned == null || !pinned.toBoolean()) {
                unavailable = true;
                FileLog.d("PythonThreadState: PyGILState_Ensure is not reachable, thread states stay per call");
            }
        } catch (Throwable t) {
            unavailable = true;
            FileLog.e("PythonThreadState: pin failed", t);
        }
    }
}

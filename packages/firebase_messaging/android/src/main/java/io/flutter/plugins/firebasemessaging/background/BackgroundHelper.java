package io.flutter.plugins.firebasemessaging.background;


import android.app.ActivityManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.util.Log;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugins.firebasemessaging.FirebaseMessagingPlugin;
import io.flutter.view.FlutterCallbackInformation;
import io.flutter.view.FlutterMain;
import io.flutter.view.FlutterNativeView;
import io.flutter.view.FlutterRunArguments;

public class BackgroundHelper implements MethodChannel.MethodCallHandler {

    private Context context;

    private MethodChannel isolateChannel;
    private Handler mainHandler;
    private ArrayDeque<MethodCall> queue = new ArrayDeque();

    private static String TAG = "BackgroundHelper";
    private static String SHARED_PREFERENCES_KEY = "firebase_messaging";
    public static String KEY_DISPATCHER = "callback_dispatch_handler";
    public static String KEY_MESSAGE_RECEIVED = "message_received_handler";

    private static FlutterNativeView sBackgroundFlutterView = null;
    private final AtomicBoolean sIsIsolateRunning = new AtomicBoolean(false);

    public BackgroundHelper(Context context) {
        this.context = context;
        mainHandler = new Handler(context.getMainLooper());

        FlutterMain.ensureInitializationComplete(context, null);
    }

    @Override
    public void onMethodCall(io.flutter.plugin.common.MethodCall methodCall, MethodChannel.Result result) {
        String method = methodCall.method;

        if (method.equals("headlessRunnerInitialized")) {
            synchronized (sIsIsolateRunning) {
                while (!queue.isEmpty()) {
                    MethodCall queuedCall = queue.remove();
                    invokeCall(queuedCall);
                }
                sIsIsolateRunning.set(true);
            }
        } else {
            result.notImplemented();
            return;
        }
        result.success(null);
    }

    private void startBackgroundIsolate(Context context) throws Exception {
        Log.d(TAG, "startBackgroundIsolate");
        Long callbackHandle = getCallbackHandle(context, KEY_DISPATCHER);

        FlutterMain.ensureInitializationComplete(context, null);
        String mAppBundlePath = FlutterMain.findAppBundlePath(context);
        FlutterCallbackInformation flutterCallback = FlutterCallbackInformation.lookupCallbackInformation(callbackHandle);
        if (flutterCallback == null) {
            Log.e(TAG, "Fatal: failed to find callback");
            return;
        }

        sBackgroundFlutterView = new FlutterNativeView(context, true);
        if (mAppBundlePath != null && !sIsIsolateRunning.get()) {
            if (FirebaseMessagingPlugin.sPluginRegistrantCallback == null) {
                throw new Exception("PluginRegistrant has not been set");
            }

            FlutterRunArguments args = new FlutterRunArguments();
            args.bundlePath = mAppBundlePath;
            args.entrypoint = flutterCallback.callbackName;
            args.libraryPath = flutterCallback.callbackLibraryPath;
            sBackgroundFlutterView.runFromBundle(args);
            FirebaseMessagingPlugin.sPluginRegistrantCallback.registerWith(sBackgroundFlutterView.getPluginRegistry());
        }

        isolateChannel = new MethodChannel(sBackgroundFlutterView, "plugins.flutter.io/firebase_messaging_isolate");
        isolateChannel.setMethodCallHandler(this);
    }

    private void queueMethodCall(String method, Long callback, Object args) {
        final MethodCall methodCall = new MethodCall(method, callback, args);

        synchronized (sIsIsolateRunning) {
            if (!sIsIsolateRunning.get()) {
                // Queue up callbacks while background isolate is starting
                queue.add(methodCall);

                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            startBackgroundIsolate(context);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                });
            } else {
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        invokeCall(methodCall);
                    }
                });
            }
        }
    }

    public void onMessageReceived(Map<String, Object> message) {
        invokeMethod("onMessageReceived", KEY_MESSAGE_RECEIVED, message);
    }

    void invokeMethod(String name, String callbackName, Object arguments) {
        queueMethodCall(name, getCallbackHandle(context, callbackName), arguments);
    }

    private void invokeCall(MethodCall methodCall) {
        isolateChannel.invokeMethod(methodCall.method, methodCall.asArgsList());
    }

    public static void saveCallbackHandle(Context context, String prefKey, List<Object> args) {
        Long callbackHandle = (Long) args.get(0);
        getSharedPreferences(context).edit().putLong(prefKey, callbackHandle).apply();
    }

    public static Long getCallbackHandle(Context context, String key) {
        return getSharedPreferences(context).getLong(key, 0);
    }

    private static SharedPreferences getSharedPreferences(Context context) {
        return context.getSharedPreferences(
                SHARED_PREFERENCES_KEY,
                Context.MODE_PRIVATE);
    }

    class MethodCall {

        public MethodCall(String method, Long callback, Object args) {
            this.method = method;
            this.callback = callback;
            this.args = args;
        }

        String method;
        Long callback;
        Object args;

        List<Object> asArgsList() {
            ArrayList<Object> list = new ArrayList<>();
            list.add(callback);
            if (args != null) {
                list.add(args);
            }
            return list;
        }
    }
}


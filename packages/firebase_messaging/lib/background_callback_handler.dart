import 'dart:ui';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

void backgroundCallbackHandler() async {
  print("setup callback dispatcher");
  const MethodChannel backgroundChannel = MethodChannel('plugins.flutter.io/firebase_messaging_isolate');

  // 2. Setup internal state needed for MethodChannels.
  WidgetsFlutterBinding.ensureInitialized();

  // 3. Listen for background events from the platform portion of the plugin.
  backgroundChannel.setMethodCallHandler((MethodCall call) async {
    print("Invoking background method call");

    final int handle = call.arguments[0] as int;

    print("handle: $handle");
    // 3.1. Retrieve callback instance for handle.
    final Function callback = PluginUtilities.getCallbackFromHandle(CallbackHandle.fromRawHandle(handle));

    print("callback: $callback");

    assert(callback != null);

    print("Invoking callback");

    switch (call.method) {
      case "onMessageReceived":
        print("onMessageReceived");
        final Map<dynamic, dynamic> message = call.arguments[1] as Map<dynamic, dynamic>;
        callback(message);
        break;
      default:
        assert(false, "No handler defined for method type: '${call.method}'");
        break;
    }
  });

  await backgroundChannel.invokeMethod<void>('headlessRunnerInitialized');
}

import 'dart:async';
import 'dart:ui';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

void backgroundCallbackHandler() async {
  const MethodChannel backgroundChannel = MethodChannel('plugins.flutter.io/firebase_messaging_isolate');

  WidgetsFlutterBinding.ensureInitialized();

  backgroundChannel.setMethodCallHandler((MethodCall call) async {
    print("Invoking background method call");

    final int handle = call.arguments[0] as int;

    // 3.1. Retrieve callback instance for handle.
    final Function callback = PluginUtilities.getCallbackFromHandle(CallbackHandle.fromRawHandle(handle));

    assert(callback != null);

    print("Invoking callback");

    switch (call.method) {
      case "onMessageReceived":
        final Map<dynamic, dynamic> message = call.arguments[1] as Map<dynamic, dynamic>;
        await callback(message);
        break;
      default:
        assert(false, "No handler defined for method type: '${call.method}'");
        break;
    }
  });

  await backgroundChannel.invokeMethod<void>('headlessRunnerInitialized');
}

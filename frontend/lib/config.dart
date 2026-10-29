import 'package:flutter/foundation.dart';

/// Backend base URL. Override with --dart-define=API_URL=... or --dart-define-from-file=env/dev.json
/// The Android emulator reaches the host machine at 10.0.2.2.
String get apiBaseUrl {
  const fromEnv = String.fromEnvironment('API_URL');
  if (fromEnv.isNotEmpty) return fromEnv;
  if (!kIsWeb && defaultTargetPlatform == TargetPlatform.android) {
    return 'http://10.0.2.2:8080';
  }
  return 'http://localhost:8080';
}

/// Public address of the web app, used to build invite links.
/// On web it is whatever the app is served from; override on Android with --dart-define=APP_URL=...
String get appBaseUrl {
  if (kIsWeb) return Uri.base.origin;
  const fromEnv = String.fromEnvironment('APP_URL');
  return fromEnv.isNotEmpty ? fromEnv : 'http://localhost:5555';
}

String inviteLink(String token) => '$appBaseUrl/join/$token';

import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'api.dart';
import 'models.dart';

class AuthState extends ChangeNotifier {
  static const _tokenKey = 'token';

  final ApiClient api;
  User? user;

  AuthState(this.api) {
    api.onUnauthorized = signOut;
  }

  bool get loggedIn => user != null;
  bool get hasName => user?.name != null && user!.name!.isNotEmpty;

  /// Restores a saved session, if the token is still valid.
  Future<void> restore() async {
    final prefs = await SharedPreferences.getInstance();
    final saved = prefs.getString(_tokenKey);
    if (saved == null) return;
    api.token = saved;
    try {
      user = await api.me();
    } on ApiException catch (e) {
      // an expired token is dropped; on other errors (e.g. offline) keep it saved for next launch
      if (e.status == 401) {
        await _forget();
      } else {
        api.token = null;
      }
    }
  }

  Future<void> verify(String email, String code) async {
    final result = await api.verifyOtp(email, code);
    api.token = result.token;
    (await SharedPreferences.getInstance()).setString(_tokenKey, result.token);
    user = result.user;
    notifyListeners();
  }

  Future<void> setName(String name) async {
    user = await api.setName(name);
    notifyListeners();
  }

  Future<void> signOut() async {
    await _forget();
    notifyListeners();
  }

  Future<void> _forget() async {
    api.token = null;
    user = null;
    (await SharedPreferences.getInstance()).remove(_tokenKey);
  }
}

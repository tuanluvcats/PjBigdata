import 'package:shared_preferences/shared_preferences.dart';
import 'package:flutter/foundation.dart';

class SessionService {
  static const String _keyUserId = 'current_user_id';
  static const String _keyToken = 'auth_token';
  static const String _keyRefreshToken = 'refresh_token';
  static final ValueNotifier<bool> needRefreshAI = ValueNotifier(false);
  static int? _cachedUserId;
  static String? _cachedToken;
  static String? _cachedRefreshToken;

  static int? get currentUserId => _cachedUserId;
  static String? get currentToken => _cachedToken;
  static String? get currentRefreshToken => _cachedRefreshToken;

  static Future<void> init() async {
    final prefs = await SharedPreferences.getInstance();
    _cachedUserId = prefs.getInt(_keyUserId);
    _cachedToken = prefs.getString(_keyToken);
    _cachedRefreshToken = prefs.getString(_keyRefreshToken);
  }

  static Future<void> setCurrentUserId(int userId) async {
    _cachedUserId = userId;
    final prefs = await SharedPreferences.getInstance();
    await prefs.setInt(_keyUserId, userId);
  }

  static Future<void> setTokens(String token, String refreshToken) async {
    _cachedToken = token;
    _cachedRefreshToken = refreshToken;
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_keyToken, token);
    await prefs.setString(_keyRefreshToken, refreshToken);
  }

  static Future<void> clearSession() async {
    _cachedUserId = null;
    _cachedToken = null;
    _cachedRefreshToken = null;
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove(_keyUserId);
    await prefs.remove(_keyToken);
    await prefs.remove(_keyRefreshToken);
  }

  static Future<bool> isLoggedIn() async {
    return _cachedUserId != null;
  }
}

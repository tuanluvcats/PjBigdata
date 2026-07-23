import 'package:shared_preferences/shared_preferences.dart';

class SessionService {
  static const String _keyUserId = 'current_user_id';
  static const String _keyToken = 'auth_token';

  static int? _cachedUserId;
  static String? _cachedToken;

  static int? get currentUserId => _cachedUserId;
  static String? get currentToken => _cachedToken;

  static Future<void> init() async {
    final prefs = await SharedPreferences.getInstance();
    _cachedUserId = prefs.getInt(_keyUserId);
    _cachedToken = prefs.getString(_keyToken);
  }

  static Future<void> setCurrentUserId(int userId) async {
    _cachedUserId = userId;
    final prefs = await SharedPreferences.getInstance();
    await prefs.setInt(_keyUserId, userId);
  }

  static Future<void> setToken(String token) async {
    _cachedToken = token;
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_keyToken, token);
  }

  static Future<void> clearSession() async {
    _cachedUserId = null;
    _cachedToken = null;
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove(_keyUserId);
    await prefs.remove(_keyToken);
  }

  static Future<bool> isLoggedIn() async {
    return _cachedUserId != null;
  }
}

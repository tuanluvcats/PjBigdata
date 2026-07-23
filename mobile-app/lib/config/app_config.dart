enum AppMode { demo, full }

class AppConfig {
  static AppMode mode = AppMode.full;

  static const String _apiBase = String.fromEnvironment(
    "API_BASE",
    defaultValue: "http://localhost:8080",
  );
  static const String _mlBase = String.fromEnvironment(
    "ML_BASE",
    defaultValue: "http://localhost:8000",
  );

  static String get apiBaseUrl => _apiBase;
  static String get mlApiUrl => _mlBase;
  static const String apiVersion = "/api";

  static const String googleServerClientId =
      "450918412846-k6u2bcm1a5ap8kctiq0ebrosmg4getn1.apps.googleusercontent.com";

  static bool get isDemo => mode == AppMode.demo;
  static bool get isFull => mode == AppMode.full;
}

import 'package:flutter/material.dart';
import 'package:intl/date_symbol_data_local.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:finance_ai_app/core/theme/app_theme.dart';
import 'package:finance_ai_app/config/app_config.dart';
import 'package:finance_ai_app/screens/auth/login_page.dart';
import 'package:finance_ai_app/screens/main_scaffold.dart';
import 'package:finance_ai_app/services/session_service.dart';
import 'package:finance_ai_app/services/remote_api_helper.dart';
import 'package:finance_ai_app/core/constants/transaction_categories.dart';

final GlobalKey<NavigatorState> navigatorKey = GlobalKey<NavigatorState>();

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await initializeDateFormatting('vi_VN', null);
  await SessionService.init();
  RemoteApiHelper.instance.init();
  final prefs = await SharedPreferences.getInstance();
  final saved = prefs.getString('app_theme') ?? 'light';
  AppTheme.themeNotifier.value = saved == 'dark'
      ? ThemeMode.dark
      : ThemeMode.light;
  runApp(const DonutPayApp());
}

class DonutPayApp extends StatefulWidget {
  const DonutPayApp({super.key});

  @override
  State<DonutPayApp> createState() => _DonutPayAppState();
}

class _DonutPayAppState extends State<DonutPayApp> {
  bool _loading = true;
  bool _loggedIn = false;

  @override
  void initState() {
    super.initState();
    _check();
  }

  Future<void> _check() async {
    final loggedIn = await SessionService.isLoggedIn();
    final ok = AppConfig.isFull
        ? (loggedIn && SessionService.currentToken != null)
        : loggedIn;
    if (ok) {
      try {
        await TransactionCategories.loadFromApi();
      } catch (e) {
        debugPrint('Lỗi tải danh mục lúc startup: $e');
      }
    }

    if (!mounted) return;
    setState(() {
      _loading = false;
      _loggedIn = ok;
    });
  }

  @override
  Widget build(BuildContext context) {
    return ValueListenableBuilder<ThemeMode>(
      valueListenable: AppTheme.themeNotifier,
      builder: (_, themeMode, _) => MaterialApp(
        navigatorKey: navigatorKey,
        title: 'Donut Pay',
        debugShowCheckedModeBanner: false,
        theme: AppTheme.lightTheme,
        darkTheme: AppTheme.darkTheme,
        themeMode: themeMode,
        home: _loading
            ? Scaffold(
                backgroundColor: AppTheme.navyDark,
                body: const Center(
                  child: CircularProgressIndicator(color: AppTheme.mintAccent),
                ),
              )
            : _loggedIn
            ? const MainScaffold()
            : const LoginScreen(),
      ),
    );
  }
}

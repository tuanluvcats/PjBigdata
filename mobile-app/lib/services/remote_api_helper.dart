import 'dart:async';
import 'dart:io';
import 'package:dio/dio.dart';
import 'package:finance_ai_app/config/app_config.dart';
import 'package:finance_ai_app/services/session_service.dart';
import 'package:finance_ai_app/models/budget_model.dart';
import 'package:finance_ai_app/models/transaction_model.dart';
import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:finance_ai_app/main.dart';
import 'package:finance_ai_app/screens/auth/login_page.dart';

class RemoteApiHelper {
  static final RemoteApiHelper instance = RemoteApiHelper._();
  RemoteApiHelper._();

  late final Dio _dio;
  late final Dio _mlDio;

  bool _initialized = false;
  final ValueNotifier<int> dataChangedNotifier = ValueNotifier(0);

  /// [httpClientAdapter] replaces the real network layer of the core API, of the AI service and of the token refresh; only
  /// tests pass it, the app calls init() with no arguments.
  void init({HttpClientAdapter? httpClientAdapter}) {
    if (_initialized) return;
    _initialized = true;

    _dio = Dio(
      BaseOptions(
        baseUrl: AppConfig.apiBaseUrl,
        connectTimeout: const Duration(seconds: 15),
        receiveTimeout: const Duration(seconds: 30),
        headers: {'Content-Type': 'application/json'},
      ),
    );
    if (httpClientAdapter != null) _dio.httpClientAdapter = httpClientAdapter;

    _dio.interceptors.add(
      InterceptorsWrapper(
        onRequest: (options, handler) {
          final token = SessionService.currentToken;
          if (token != null) {
            options.headers['Authorization'] = 'Bearer $token';
          }
          handler.next(options);
        },
        onError: (DioException e, handler) async {
          if (e.response?.statusCode == 401) {
            final refreshToken = SessionService.currentRefreshToken;

            if (refreshToken != null) {
              try {
                // Same base as _dio: the paths below already start with /api, so adding apiVersion here would call /api/api/...
                final refreshDio = Dio(
                  BaseOptions(
                    baseUrl: AppConfig.apiBaseUrl,
                    headers: {'Content-Type': 'application/json'},
                  ),
                );
                if (httpClientAdapter != null) {
                  refreshDio.httpClientAdapter = httpClientAdapter;
                }
                final res = await refreshDio.post(
                  '/api/auth/refresh',
                  data: {'refreshToken': refreshToken},
                );

                final newToken = res.data['data']['token'];
                await SessionService.setTokens(newToken, refreshToken);

                final options = e.requestOptions;
                options.headers['Authorization'] = 'Bearer $newToken';

                final cloneReq = await refreshDio.request(
                  options.path,
                  options: Options(
                    method: options.method,
                    headers: options.headers,
                  ),
                  data: options.data,
                  queryParameters: options.queryParameters,
                );
                return handler.resolve(cloneReq);
              } catch (refreshError) {
                await SessionService.clearSession();
                navigatorKey.currentState?.pushAndRemoveUntil(
                  MaterialPageRoute(builder: (_) => const LoginScreen()),
                  (route) => false,
                );
              }
            } else {
              await SessionService.clearSession();
              navigatorKey.currentState?.pushAndRemoveUntil(
                MaterialPageRoute(builder: (_) => const LoginScreen()),
                (route) => false,
              );
            }
          }
          handler.next(e);
        },
      ),
    );

    _mlDio = Dio(
      BaseOptions(
        baseUrl: AppConfig.mlApiUrl,
        connectTimeout: const Duration(seconds: 30),
        receiveTimeout: const Duration(seconds: 90),
      ),
    );
    if (httpClientAdapter != null) _mlDio.httpClientAdapter = httpClientAdapter;
  }

  Future<Map<String, dynamic>> login(String email, String password) async {
    try {
      final res = await _dio.post(
        '/api/auth/login',
        data: {'email': email, 'password': password},
      );
      return Map<String, dynamic>.from(res.data);
    } on DioException catch (e) {
      final errorMsg =
          e.response?.data?['message'] ?? 'Sai email hoặc mật khẩu!';
      throw errorMsg;
    }
  }

  Future<Map<String, dynamic>> socialLogin({
    required String provider,
    required String token,
    String? email,
    String? name,
    String? avatar,
  }) async {
    try {
      final res = await _dio.post(
        '/api/auth/social-login',
        data: {
          'provider': provider,
          'token': token,
          'email': ?email,
          'name': ?name,
          'avatar': ?avatar,
        },
      );

      var responseData = res.data;
      if (responseData is String) {
        responseData = jsonDecode(responseData);
      }
      return Map<String, dynamic>.from(responseData);
    } on DioException catch (e) {
      final statusCode = e.response?.statusCode;
      final realUrl = e.requestOptions.uri.toString();
      final realError = "LỖI THẬT: Code $statusCode khi gọi $realUrl";

      debugPrint(" $realError");
      throw realError;
    } catch (e) {
      throw 'Lỗi hệ thống Dart: $e';
    }
  }

  Future<Map<String, dynamic>> register({
    required String email,
    required String password,
    required String fullName,
    String? phone,
  }) async {
    try {
      final res = await _dio.post(
        '/api/auth/register',
        data: {
          'username': email,
          'email': email,
          'password': password,
          'fullName': fullName,
          'phone': ?phone,
        },
      );
      return Map<String, dynamic>.from(res.data);
    } on DioException catch (e) {
      final errorMsg =
          e.response?.data?['message'] ?? 'Đăng ký thất bại, vui lòng thử lại!';
      throw errorMsg;
    }
  }

  Future<Map<String, dynamic>> getWallet() async {
    final userId = SessionService.currentUserId;
    final res = await _dio.get('/api/wallets/user/$userId');
    return Map<String, dynamic>.from(res.data['data'] ?? {});
  }

  Future<void> transfer({
    required String recipientPhone,
    required double amount,
    String? note,
  }) async {
    await _dio.post(
      '/api/wallets/transfer',
      data: {'recipientPhone': recipientPhone, 'amount': amount, 'note': ?note},
    );
  }

  Future<Map<String, dynamic>> createPayment({
    required String type,
    required double amount,
    String provider = 'MOCK',
    String? description,
  }) async {
    final res = await _dio.post(
      '/api/payment/create',
      data: {
        'type': type,
        'amount': amount,
        'provider': provider,
        'description': ?description,
      },
    );
    return Map<String, dynamic>.from(res.data['data'] ?? {});
  }

  Future<Map<String, dynamic>> paymentStatus(String orderCode) async {
    final res = await _dio.get('/api/payment/status/$orderCode');
    return Map<String, dynamic>.from(res.data['data'] ?? {});
  }

  Future<List<Map<String, dynamic>>> paymentHistory() async {
    final res = await _dio.get('/api/payment/mine');
    return List<Map<String, dynamic>>.from(res.data['data'] ?? []);
  }

  Future<Map<String, dynamic>> cancelPayment(String orderCode) async {
    final res = await _dio.put('/api/payment/cancel/$orderCode');
    return Map<String, dynamic>.from(res.data['data'] ?? {});
  }

  Future<List<Map<String, dynamic>>> getTransactions() async {
    final userId = SessionService.currentUserId;
    final res = await _dio.get(
      '/api/transactions?userId=$userId&limit=100&sort=date,desc',
    );
    return List<Map<String, dynamic>>.from(res.data['data'] ?? []);
  }

  Future<List<Map<String, dynamic>>> fetchCategories() async {
    try {
      final response = await _dio.get('/api/categories');

      if (response.statusCode == 200) {
        final data = response.data;
        if (data is List) {
          return List<Map<String, dynamic>>.from(data);
        } else if (data is Map && data.containsKey('data')) {
          return List<Map<String, dynamic>>.from(data['data'] ?? []);
        }
      }
    } catch (e) {
      debugPrint('Lỗi tải danh mục từ Backend bằng Dio: $e');
    }
    return [];
  }

  Future<List<TransactionModel>> fetchTransactions() async {
    final res = await _dio.get('/api/transactions/mine');
    final list = (res.data['data'] as List?) ?? [];
    return list
        .map(
          (e) => TransactionModel.fromJson(Map<String, dynamic>.from(e as Map)),
        )
        .toList();
  }

  Future<Map<String, dynamic>> fetchMe() async {
    final res = await _dio.get('/api/users/me');
    return Map<String, dynamic>.from(res.data['data'] ?? {});
  }

  Future<void> addTransaction(
    Map<String, dynamic> data, {
    String? imagePath,
  }) async {
    if (imagePath != null && imagePath.isNotEmpty) {
      try {
        final imageUrl = await uploadImage(imagePath);
        if (imageUrl != null) {
          data['imageUrl'] = imageUrl;
        }
      } catch (e) {
        debugPrint("Lỗi upload ảnh lên Cloudinary: $e");
      }
    }
    await _dio.post('/api/transactions/checkout', data: data);
    dataChangedNotifier.value++;
  }

  /// PUT /api/transactions/{id}: only the fields in [data] change (amount, categoryName, note ...) and the server keeps
  /// the wallet in step. The owner comes from the login token. A refusal reaches the caller as a DioException.
  Future<void> updateTransaction(String id, Map<String, dynamic> data) async {
    await _dio.put('/api/transactions/$id', data: data);
    dataChangedNotifier.value++;
  }

  Future<String?> uploadImage(String imagePath) async {
    try {
      final formData = FormData.fromMap({
        'file': await MultipartFile.fromFile(
          imagePath,
          filename: imagePath.split(Platform.pathSeparator).last,
        ),
      });
      final res = await _dio.post('/api/upload', data: formData);

      if (res.data is Map) {
        if (res.data['url'] != null) {
          return res.data['url'].toString();
        } else if (res.data['data'] != null &&
            res.data['data']['url'] != null) {
          return res.data['data']['url'].toString();
        }
      }

      debugPrint(
        "FLUTTER: Backend không trả về link ảnh! Nội dung: ${res.data}",
      );
      return null;
    } catch (e) {
      debugPrint("LỖI UPLOAD ẢNH FLUTTER: $e");
      return null;
    }
  }

  Future<Map<String, dynamic>> scanReceipt(String imagePath) async {
    final formData = FormData.fromMap({
      'image': await MultipartFile.fromFile(
        imagePath,
        filename: imagePath.split(Platform.pathSeparator).last,
      ),
    });
    final res = await _mlDio.post('/donut/scan', data: formData);
    return Map<String, dynamic>.from(res.data);
  }

  Future<List<Map<String, dynamic>>> analyzePatterns(
    List<Map<String, dynamic>> transactions,
  ) async {
    final res = await _mlDio.post(
      '/spade/analyze',
      data: {'transactions': transactions},
    );
    return List<Map<String, dynamic>>.from(res.data['patterns'] ?? []);
  }

  Future<Map<String, dynamic>> recommendBudget({
    required List<Map<String, dynamic>> transactions,
    double target = 0,
    double current = 0,
    int monthsLeft = 0,
    double? monthlyIncome,
    double? neededMonthly,
  }) async {
    final res = await _mlDio.post(
      '/spade/recommend',
      data: {
        'transactions': transactions,
        'target': target,
        'current': current,
        'months_left': monthsLeft,
        'monthly_income': ?monthlyIncome,
        'needed_monthly': ?neededMonthly,
      },
    );
    return Map<String, dynamic>.from(res.data);
  }

  Future<Map<String, dynamic>> getUserProfile() async {
    final userId = SessionService.currentUserId;
    final res = await _dio.get('/api/users/$userId');
    return Map<String, dynamic>.from(res.data['data'] ?? {});
  }

  Future<void> updateUserProfile({
    required String fullName,
    String? phone,
    String? birthdate,
  }) async {
    final userId = SessionService.currentUserId;
    await _dio.put(
      '/api/users/$userId',
      data: {'fullName': fullName, 'phone': ?phone, 'birthdate': ?birthdate},
    );
  }

  Future<void> forgotPassword({required String email}) async {
    await _dio.post(
      '/api/auth/forgot-password',
      queryParameters: {'email': email},
    );
  }

  /// Completes the reset started by [forgotPassword]. The OTP and the new password travel in the JSON body, never in
  /// the query string, so they cannot end up in server or proxy access logs.
  Future<void> resetPassword({
    required String email,
    required String otp,
    required String newPassword,
  }) async {
    await _dio.post(
      '/api/auth/reset-password',
      data: {'email': email, 'otp': otp, 'newPassword': newPassword},
    );
  }

  /// Changes the password of the logged-in user (the owner comes from the login token). The server answers 400 with a
  /// readable message when [currentPassword] is wrong or [newPassword] breaks the policy, and revokes the access token
  /// that made the call: the next request gets a 401 and the interceptor renews the token with the stored refresh token.
  Future<void> changePassword({
    required String currentPassword,
    required String newPassword,
  }) async {
    await _dio.put(
      '/api/auth/change-password',
      data: {'oldPassword': currentPassword, 'newPassword': newPassword},
    );
  }

  /// Longest the app waits for the server to revoke the tokens: signing out must not hang on a bad network.
  static const Duration _logoutTimeout = Duration(seconds: 5);

  /// Asks the server to revoke the stored access token and refresh token (POST /api/auth/logout). Best effort: a network
  /// error, a timeout, a 401 or a 5xx is only logged and never thrown, so the caller can always clear the local session
  /// and go to the login screen. Call it BEFORE SessionService.clearSession(), the request needs the stored tokens.
  Future<void> logout() async {
    if (SessionService.currentToken == null) return;
    final refreshToken = SessionService.currentRefreshToken;
    try {
      await _dio
          .post('/api/auth/logout', data: {'refreshToken': ?refreshToken})
          .timeout(_logoutTimeout);
    } on DioException catch (e) {
      debugPrint(
        'Logout: the server did not revoke the tokens (${e.response?.statusCode ?? e.type.name}).',
      );
    } on TimeoutException {
      debugPrint(
        'Logout: no answer from the server within ${_logoutTimeout.inSeconds}s.',
      );
    }
  }

  Future<List<Map<String, dynamic>>> getNotifications() async {
    final userId = SessionService.currentUserId;
    final res = await _dio.get('/api/notifications?userId=$userId');
    return List<Map<String, dynamic>>.from(res.data['data'] ?? []);
  }

  Future<void> markAllNotificationsRead() async {
    final userId = SessionService.currentUserId;
    await _dio.put('/api/notifications/read-all', data: {'userId': userId});
  }

  /// All budgets of the logged-in user, newest first. The owner comes from the login token, so nothing identifying
  /// the user is sent.
  Future<List<BudgetModel>> getBudgets() async {
    final res = await _dio.get('/api/budgets');
    final items = (res.data['data'] as List?) ?? [];
    return items
        .map(
          (item) =>
              BudgetModel.fromJson(Map<String, dynamic>.from(item as Map)),
        )
        .toList();
  }

  Future<BudgetModel> createBudget(BudgetModel budget) async {
    final res = await _dio.post('/api/budgets', data: budget.toJson());
    return BudgetModel.fromJson(
      Map<String, dynamic>.from(res.data['data'] as Map),
    );
  }

  Future<BudgetModel> updateBudget(BudgetModel budget) async {
    final res = await _dio.put(
      '/api/budgets/${budget.id}',
      data: budget.toJson(),
    );
    return BudgetModel.fromJson(
      Map<String, dynamic>.from(res.data['data'] as Map),
    );
  }

  Future<void> deleteBudget(int budgetId) async {
    await _dio.delete('/api/budgets/$budgetId');
  }

  Future<bool> submitAppReview(int rating, String comment) async {
    try {
      final userId = SessionService.currentUserId;
      if (userId == null) return false;

      final res = await _dio.post(
        '/api/reviews',
        data: {'userId': userId, 'rating': rating, 'comment': comment},
      );
      return res.data['code'] == 200;
    } catch (e) {
      debugPrint(" LỖI GỬI ĐÁNH GIÁ: $e");
      return false;
    }
  }

  Future<bool> deleteTransaction(String id) async {
    try {
      final res = await _dio.delete('/api/transactions/$id');
      dataChangedNotifier.value++;

      return res.data['code'] == 200;
    } catch (e) {
      debugPrint("LỖI XÓA GIAO DỊCH: $e");
      return false;
    }
  }

  Future<List<Map<String, dynamic>>> fetchSavings() async {
    try {
      final res = await _dio.get('/api/savings');
      return List<Map<String, dynamic>>.from(res.data['data'] ?? []);
    } catch (e) {
      return [];
    }
  }

  Future<void> addSaving(Map<String, dynamic> data) async {
    await _dio.post('/api/savings', data: data);
    dataChangedNotifier.value++;
  }

  Future<void> updateSavingCurrent(String id, double current) async {
    await _dio.put('/api/savings/$id', data: {'current': current});
    dataChangedNotifier.value++;
  }

  Future<void> deleteSaving(String id) async {
    await _dio.delete('/api/savings/$id');
    dataChangedNotifier.value++;
  }
}

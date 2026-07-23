import 'dart:io';
import 'package:dio/dio.dart';
import 'package:finance_ai_app/config/app_config.dart';
import 'package:finance_ai_app/services/session_service.dart';
import 'package:finance_ai_app/models/transaction_model.dart';
import 'dart:convert'; 
import 'package:http/http.dart' as http; 
import 'package:flutter/foundation.dart'; 
class RemoteApiHelper {
  static final RemoteApiHelper instance = RemoteApiHelper._();
  RemoteApiHelper._();

  late final Dio _dio;
  late final Dio _mlDio;

  bool _initialized = false;

  void init() {
    if (_initialized) return;
    _initialized = true;

    _dio = Dio(BaseOptions(
      baseUrl: AppConfig.apiBaseUrl + AppConfig.apiVersion,
      connectTimeout: const Duration(seconds: 15),
      receiveTimeout: const Duration(seconds: 30),
      headers: {'Content-Type': 'application/json'},
    ));
    _dio.interceptors.add(InterceptorsWrapper(
      onRequest: (options, handler) {
        final token = SessionService.currentToken;
        if (token != null) {
          options.headers['Authorization'] = 'Bearer $token';
        }
        handler.next(options);
      },
    ));

    _mlDio = Dio(BaseOptions(
      baseUrl: AppConfig.mlApiUrl,
      connectTimeout: const Duration(seconds: 30),
      receiveTimeout: const Duration(seconds: 90),
    ));
  }

  Future<Map<String, dynamic>> login(String email, String password) async {
    final res = await _dio.post('/auth/login', data: {'email': email, 'password': password});
    return Map<String, dynamic>.from(res.data);
  }

  Future<Map<String, dynamic>> socialLogin({
    required String provider,
    required String token,
    String? email,
    String? name,
  }) async {
    final res = await _dio.post('/auth/social-login', data: {
      'provider': provider,
      'token': token,
      if (email != null) 'email': email,
      if (name != null) 'name': name,
    });
    return Map<String, dynamic>.from(res.data);
  }

  Future<Map<String, dynamic>> register({
    required String email,
    required String password,
    required String fullName,
    String? phone,
  }) async {
    final res = await _dio.post('/auth/register', data: {
      'username': email,
      'email': email,
      'password': password,
      'fullName': fullName,
      if (phone != null) 'phone': phone,
    });
    return Map<String, dynamic>.from(res.data);
  }

  Future<Map<String, dynamic>> getWallet() async {
    final userId = SessionService.currentUserId;
    final res = await _dio.get('/wallets/user/$userId');
    return Map<String, dynamic>.from(res.data['data'] ?? {});
  }

  Future<void> transfer({
    required String recipientPhone,
    required double amount,
    String? note,
  }) async {
    await _dio.post('/wallets/transfer', data: {
      'recipientPhone': recipientPhone,
      'amount': amount,
      if (note != null) 'note': note,
    });
  }

  Future<Map<String, dynamic>> createPayment({
    required String type,
    required double amount,
    String provider = 'MOCK',
    String? description,
  }) async {
    final res = await _dio.post('/payment/create', data: {
      'type': type,
      'amount': amount,
      'provider': provider,
      if (description != null) 'description': description,
    });
    return Map<String, dynamic>.from(res.data['data'] ?? {});
  }

  Future<Map<String, dynamic>> paymentStatus(String orderCode) async {
    final res = await _dio.get('/payment/status/$orderCode');
    return Map<String, dynamic>.from(res.data['data'] ?? {});
  }

  Future<List<Map<String, dynamic>>> paymentHistory() async {
    final res = await _dio.get('/payment/mine');
    return List<Map<String, dynamic>>.from(res.data['data'] ?? []);
  }

  Future<Map<String, dynamic>> cancelPayment(String orderCode) async {
    final res = await _dio.put('/payment/cancel/$orderCode');
    return Map<String, dynamic>.from(res.data['data'] ?? {});
  }

  Future<List<Map<String, dynamic>>> getTransactions() async {
    final userId = SessionService.currentUserId;
    final res = await _dio.get('/transactions?userId=$userId&limit=100&sort=date,desc');
    return List<Map<String, dynamic>>.from(res.data['data'] ?? []);
  }
Future<List<Map<String, dynamic>>> fetchCategories() async {
final url = Uri.parse('${AppConfig.apiBaseUrl}/api/categories');
    final headers = {
      'Content-Type': 'application/json',
      'Accept': 'application/json',
      if (SessionService.currentToken != null)
        'Authorization': 'Bearer ${SessionService.currentToken}',
    };

    try {
      final response = await http.get(url, headers: headers);
      
      if (response.statusCode == 200) {
        final List<dynamic> data = jsonDecode(utf8.decode(response.bodyBytes));
        return data.cast<Map<String, dynamic>>();
      } else {
        debugPrint('Lỗi Backend trả về code: ${response.statusCode}');
      }
    } catch (e) {
      debugPrint('Lỗi tải danh mục từ Backend: $e');
    }
    return [];
  }
  Future<List<TransactionModel>> fetchTransactions() async {
    final res = await _dio.get('/transactions/mine');
    final list = (res.data['data'] as List?) ?? [];
    return list.map((e) {
      final m = Map<String, dynamic>.from(e as Map);
      return TransactionModel(
        id: m['id']?.toString(),
        title: (m['title'] ?? m['category'] ?? '').toString(),
        amount: (m['amount'] as num?)?.toDouble() ?? 0.0,
        date: DateTime.tryParse(m['date']?.toString() ?? '') ?? DateTime.now(),
        category: (m['category'] ?? 'Khác').toString(),
        isExpense: m['isExpense'] == true,
        iconName: (m['category'] ?? 'default').toString(),
        imageUrl: m['imageUrl']?.toString(),
      );
    }).toList();
  }

  Future<Map<String, dynamic>> fetchMe() async {
    final res = await _dio.get('/users/me');
    return Map<String, dynamic>.from(res.data['data'] ?? {});
  }

Future<void> addTransaction(Map<String, dynamic> data, {String? imagePath}) async {
    if (imagePath != null && imagePath.isNotEmpty) {
      try {
        final imageUrl = await uploadImage(imagePath);
        if (imageUrl != null) {
          data['imageUrl'] = imageUrl; 
        }
      } catch (e) {
        print("Lỗi upload ảnh lên Cloudinary: \$e");
      }
    }
    await _dio.post('/transactions/checkout', data: data);
  }

Future<String?> uploadImage(String imagePath) async {
    try {
      final formData = FormData.fromMap({
        'file': await MultipartFile.fromFile(
          imagePath,
          filename: imagePath.split(Platform.pathSeparator).last,
        ),
      });
      final res = await _dio.post(
        '/upload',
        data: formData,
      );

      if (res.data is Map) {
        if (res.data['url'] != null) {
          return res.data['url'].toString();
        } else if (res.data['data'] != null && res.data['data']['url'] != null) {
          return res.data['data']['url'].toString();
        }
      }
      
      print("FLUTTER: Backend không trả về link ảnh! Nội dung: ${res.data}");
      return null;
      
    } catch (e) {
      print("LỖI UPLOAD ẢNH FLUTTER: $e");
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
    final res = await _mlDio.post('/spade/analyze', data: {'transactions': transactions});
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
    final res = await _mlDio.post('/spade/recommend', data: {
      'transactions': transactions,
      'target': target,
      'current': current,
      'months_left': monthsLeft,
      if (monthlyIncome != null) 'monthly_income': monthlyIncome,
      if (neededMonthly != null) 'needed_monthly': neededMonthly,
    });
    return Map<String, dynamic>.from(res.data);
  }

  Future<Map<String, dynamic>> getUserProfile() async {
    final userId = SessionService.currentUserId;
    final res = await _dio.get('/users/$userId');
    return Map<String, dynamic>.from(res.data['data'] ?? {});
  }

  Future<void> updateUserProfile({
    required String fullName,
    String? phone,
    String? birthdate,
  }) async {
    final userId = SessionService.currentUserId;
    await _dio.put('/users/$userId', data: {
      'fullName': fullName,
      if (phone != null) 'phone': phone,
      if (birthdate != null) 'birthdate': birthdate,
    });
  }

  Future<void> forgotPassword({required String email}) async {
    await _dio.post('/auth/forgot-password', queryParameters: {'email': email});
  }

  Future<void> changePassword({
    required String currentPassword,
    required String newPassword,
  }) async {
    await _dio.put('/auth/change-password', data: {
      'currentPassword': currentPassword,
      'newPassword': newPassword,
    });
  }

  Future<List<Map<String, dynamic>>> getNotifications() async {
    final userId = SessionService.currentUserId;
    final res = await _dio.get('/notifications?userId=$userId');
    return List<Map<String, dynamic>>.from(res.data['data'] ?? []);
  }

  Future<void> markAllNotificationsRead() async {
    final userId = SessionService.currentUserId;
    await _dio.put('/notifications/read-all', data: {'userId': userId});
  }

  Future<List<Map<String, dynamic>>> getBudgets({
    required int month,
    required int year,
  }) async {
    final userId = SessionService.currentUserId;
    final res = await _dio.get('/budgets?userId=$userId&month=$month&year=$year');
    return List<Map<String, dynamic>>.from(res.data['data'] ?? []);
  }

  Future<void> createBudget({
    required String categoryName,
    required double limitAmount,
    required int month,
    required int year,
  }) async {
    final userId = SessionService.currentUserId;
    await _dio.post('/budgets', data: {
      'userId': userId,
      'categoryName': categoryName,
      'limitAmount': limitAmount,
      'month': month,
      'year': year,
    });
  }
  Future<bool> submitAppReview(int rating, String comment) async {
    try {
      final userId = SessionService.currentUserId;
      if (userId == null) return false;

      final res = await _dio.post('/reviews', data: {
        'userId': userId,
        'rating': rating,
        'comment': comment,
      });
      return res.data['code'] == 200;
    } catch (e) {
      print(" LỖI GỬI ĐÁNH GIÁ: $e");
      return false;
    }
  }

  Future<bool> deleteTransaction(String id) async {
    try {
      final res = await _dio.delete('/transactions/$id');
      return res.data['code'] == 200;
    } catch (e) {
      print("LỖI XÓA GIAO DỊCH: $e");
      return false;
    }
  }
}

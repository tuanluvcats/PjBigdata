import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:finance_ai_app/services/remote_api_helper.dart';
import 'package:finance_ai_app/services/session_service.dart';

import '../helpers/fake_backend.dart';

void main() {
  // The 401 interceptor reads the navigator key when a refresh fails, and a GlobalKey needs the binding.
  TestWidgetsFlutterBinding.ensureInitialized();

  final backend = FakeBackend();

  setUpAll(() {
    RemoteApiHelper.instance.init(httpClientAdapter: backend);
  });

  setUp(() async {
    SharedPreferences.setMockInitialValues({});
    backend.reset();
    await SessionService.clearSession();
    await SessionService.setTokens('access-token', 'refresh-token');
  });

  tearDown(() async {
    await SessionService.clearSession();
  });

  group('logout', () {
    test('asks the server to revoke both tokens: the access token as bearer, the refresh token in the body', () async {
      backend.on('POST', '/api/auth/logout', (_) => okResponse(null, message: 'Đăng xuất thành công!'));

      await RemoteApiHelper.instance.logout();

      expect(backend.calls, ['POST /api/auth/logout']);
      expect(backend.requests.single.headers['Authorization'], 'Bearer access-token');
      expect(backend.requests.single.data, {'refreshToken': 'refresh-token'});
    });

    test('leaves the refresh token out of the body when none is stored', () async {
      SharedPreferences.setMockInitialValues({'auth_token': 'access-token'});
      await SessionService.init();
      backend.on('POST', '/api/auth/logout', (_) => okResponse(null));

      await RemoteApiHelper.instance.logout();

      expect(SessionService.currentRefreshToken, isNull);
      expect(backend.requests.single.data, isNot(contains('refreshToken')));
    });

    test('calls nothing when there is no stored access token', () async {
      await SessionService.clearSession();

      await RemoteApiHelper.instance.logout();

      expect(backend.calls, isEmpty);
    });

    test('an error of the server (500) is not thrown, so the caller can still sign out locally', () async {
      backend.on('POST', '/api/auth/logout', (_) => jsonResponse(500, {'code': 500, 'message': 'Lỗi hệ thống'}));

      await expectLater(RemoteApiHelper.instance.logout(), completes);

      expect(backend.calls, ['POST /api/auth/logout']);
    });

    test('a network failure is not thrown either', () async {
      backend.on(
        'POST',
        '/api/auth/logout',
        (options) => throw DioException.connectionError(requestOptions: options, reason: 'no network'),
      );

      await expectLater(RemoteApiHelper.instance.logout(), completes);

      expect(backend.calls, ['POST /api/auth/logout']);
    });
  });

  group('changePassword', () {
    test('sends the old and the new password and nothing else, as the logged-in caller', () async {
      backend.on('PUT', '/api/auth/change-password', (_) => okResponse(null, message: 'Đổi mật khẩu thành công!'));

      await RemoteApiHelper.instance.changePassword(currentPassword: 'OldPass123', newPassword: 'NewPass456');

      expect(backend.calls, ['PUT /api/auth/change-password']);
      expect(backend.requests.single.headers['Authorization'], 'Bearer access-token');
      expect(backend.requests.single.data, {'oldPassword': 'OldPass123', 'newPassword': 'NewPass456'});
    });

    test('a refusal (400, wrong old password) reaches the caller and does not touch the session', () async {
      backend.on(
        'PUT',
        '/api/auth/change-password',
        (_) => jsonResponse(400, {'code': 400, 'message': 'Mật khẩu cũ không chính xác'}),
      );

      await expectLater(
        RemoteApiHelper.instance.changePassword(currentPassword: 'wrong', newPassword: 'NewPass456'),
        throwsA(isA<DioException>().having((e) => e.response?.statusCode, 'status code', 400)),
      );

      expect(SessionService.currentToken, 'access-token');
      expect(SessionService.currentRefreshToken, 'refresh-token');
    });
  });

  group('the access token revoked by a password change', () {
    test('is renewed silently: the 401 makes the app refresh with the stored token and repeat the request', () async {
      backend.on(
        'GET',
        '/api/budgets',
        (options) => options.headers['Authorization'] == 'Bearer access-token'
            ? jsonResponse(401, {'status': 401, 'error': 'Unauthorized'})
            : okResponse([]),
      );
      backend.on(
        'POST',
        '/api/auth/refresh',
        (_) => okResponse({'token': 'new-access-token', 'refreshToken': 'new-refresh-token'}),
      );

      final budgets = await RemoteApiHelper.instance.getBudgets();

      expect(budgets, isEmpty);
      expect(backend.calls, ['GET /api/budgets', 'POST /api/auth/refresh', 'GET /api/budgets']);

      final refresh = backend.requests[1];
      expect(refresh.data, {'refreshToken': 'refresh-token'});
      // The paths already start with /api: a base URL that ends with /api as well would call /api/api/auth/refresh.
      expect(refresh.uri.path, endsWith('/api/auth/refresh'));
      expect(refresh.uri.path, isNot(contains('/api/api/')));

      expect(backend.requests[2].headers['Authorization'], 'Bearer new-access-token');
      expect(SessionService.currentToken, 'new-access-token');
    });
  });
}

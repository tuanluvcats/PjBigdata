import 'package:dio/dio.dart';
import 'package:finance_ai_app/repositories/app_repository.dart';
import 'package:flutter/material.dart';

import 'package:finance_ai_app/core/theme/app_theme.dart';
import 'package:finance_ai_app/screens/main_scaffold.dart';
import 'package:finance_ai_app/widgets/common/primary_button.dart';
import 'package:finance_ai_app/config/app_config.dart';
import 'package:finance_ai_app/services/remote_api_helper.dart';
import 'package:finance_ai_app/services/session_service.dart';
import 'package:finance_ai_app/repositories/local_auth_repository.dart';
import 'package:finance_ai_app/services/social_auth_service.dart';
import 'package:finance_ai_app/widgets/dialogs/app_dialog.dart';

import 'register_page.dart';

class LoginScreen extends StatefulWidget {
  const LoginScreen({super.key});

  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  final _emailController = TextEditingController();
  final _passwordController = TextEditingController();
  bool _rememberMe = false;
  bool _obscure = true;
  bool _isLoginLoading = false;
  bool _isSocialLoading = false;
  @override
  void dispose() {
    _emailController.dispose();
    _passwordController.dispose();
    super.dispose();
  }

  Future<void> _handleLogin(
    BuildContext context, {
    required String email,
    required String password,
  }) async {
    final normalizedEmail = email.trim();
    final normalizedPassword = password;

    if (normalizedEmail.isEmpty || normalizedPassword.isEmpty) {
      await AppDialog.error(
        context,
        title: 'Thiếu thông tin',
        message: 'Vui lòng nhập email và mật khẩu.',
      );
      return;
    }
    if (_isLoginLoading || _isSocialLoading) return;
    setState(() => _isLoginLoading = true);
    try {
      if (AppConfig.isFull) {
        final result = await RemoteApiHelper.instance.login(
          normalizedEmail,
          normalizedPassword,
        );
        final data = (result['data'] as Map?) ?? result;

        final token = (data['token'] ?? result['token'])?.toString() ?? '';
        final refreshToken =
            (data['refreshToken'] ?? result['refreshToken'])?.toString() ?? '';

        final user = data['user'] as Map?;
        final userId =
            (user?['id'] ?? data['userId'] ?? result['userId'] ?? result['id'])
                as int?;

        if (token.isNotEmpty && refreshToken.isNotEmpty) {
          await SessionService.setTokens(token, refreshToken);
        }

        if (userId != null) {
          await SessionService.setCurrentUserId(userId);

          AppRepository.instance
              .getHomeSummary(userId)
              .catchError((_) => <String, dynamic>{});

          if (!context.mounted) return;
          Navigator.of(context).pushAndRemoveUntil(
            MaterialPageRoute(builder: (_) => const MainScaffold()),
            (route) => false,
          );
          return;
        }
      }

      final user = await LocalAuthRepository.instance.login(
        email: normalizedEmail,
        password: normalizedPassword,
      );

      if (!context.mounted) return;

      if (user?.id == null) {
        await AppDialog.error(
          context,
          title: 'Đăng nhập thất bại',
          message: 'Sai email hoặc mật khẩu.',
        );
        return;
      }

      await SessionService.setCurrentUserId(user!.id!);

      AppRepository.instance
          .getHomeSummary(user.id!)
          .catchError((_) => <String, dynamic>{});

      if (!context.mounted) return;
      Navigator.of(context).pushAndRemoveUntil(
        MaterialPageRoute(builder: (_) => const MainScaffold()),
        (route) => false,
      );
    } on DioException catch (e) {
      if (!context.mounted) return;
      final data = e.response?.data;
      final serverMsg = (data is Map ? data['message'] : null)?.toString();
      await AppDialog.error(
        context,
        title: 'Đăng nhập thất bại',
        message: e.response?.statusCode == 401
            ? (serverMsg ?? 'Sai email hoặc mật khẩu!')
            : (serverMsg ?? 'Không thể kết nối máy chủ. Vui lòng thử lại.'),
      );
    } catch (e) {
      if (!context.mounted) return;
      await AppDialog.error(
        context,
        title: 'Đăng nhập thất bại',
        message: '$e',
      );
    } finally {
      if (mounted) setState(() => _isLoginLoading = false);
    }
  }

  void _showForgotPassword(BuildContext context) {
    final emailCtrl = TextEditingController();
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: Colors.white,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
        title: const Text(
          'Quên mật khẩu',
          style: TextStyle(
            color: AppTheme.textPrimary,
            fontWeight: FontWeight.bold,
          ),
        ),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Text(
              'Nhập email để nhận liên kết đặt lại mật khẩu.',
              style: TextStyle(color: AppTheme.textSecondary, fontSize: 13),
            ),
            const SizedBox(height: 16),
            TextField(
              controller: emailCtrl,
              keyboardType: TextInputType.emailAddress,
              style: const TextStyle(color: AppTheme.textPrimary),
              decoration: InputDecoration(
                hintText: 'email@example.com',
                hintStyle: const TextStyle(color: AppTheme.textSecondary),
                filled: true,
                fillColor: AppTheme.bgLight,
                border: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(12),
                  borderSide: BorderSide.none,
                ),
              ),
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text(
              'Hủy',
              style: TextStyle(color: AppTheme.textSecondary),
            ),
          ),
          ElevatedButton(
            style: ElevatedButton.styleFrom(
              backgroundColor: AppTheme.primary,
              foregroundColor: Colors.white,
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(10),
              ),
            ),
            onPressed: () async {
              final email = emailCtrl.text.trim();
              if (email.isEmpty) return;
              Navigator.pop(ctx);
              if (AppConfig.isFull) {
                try {
                  await RemoteApiHelper.instance.forgotPassword(email: email);
                  if (!context.mounted) return;
                  ScaffoldMessenger.of(context).showSnackBar(
                    const SnackBar(
                      content: Text('Đã gửi email đặt lại mật khẩu!'),
                    ),
                  );
                } catch (e) {
                  if (!context.mounted) return;
                  ScaffoldMessenger.of(
                    context,
                  ).showSnackBar(SnackBar(content: Text('Lỗi: $e')));
                }
              } else {
                ScaffoldMessenger.of(context).showSnackBar(
                  const SnackBar(
                    content: Text('Tính năng chỉ khả dụng khi kết nối server.'),
                  ),
                );
              }
            },
            child: const Text('Gửi'),
          ),
        ],
      ),
    );
  }

  Future<void> _handleSocial(
    Future<Map<String, dynamic>> Function() signIn,
  ) async {
    if (!AppConfig.isFull) {
      return;
    }
    if (_isSocialLoading || _isLoginLoading) return;
    setState(() => _isSocialLoading = true);
    try {
      final res = await signIn();
      final data = (res['data'] as Map?) ?? res;
      final token = (data['token'] ?? res['token'])?.toString() ?? '';
      final refreshToken =
          (data['refreshToken'] ?? res['refreshToken'])?.toString() ?? '';

      final user = data['user'] as Map?;
      final userId = (user?['id'] ?? data['userId'] ?? res['userId']) as int?;

      if (token.isNotEmpty && refreshToken.isNotEmpty) {
        await SessionService.setTokens(token, refreshToken);
      }

      if (userId != null) {
        await SessionService.setCurrentUserId(userId);

        AppRepository.instance
            .getHomeSummary(userId)
            .catchError((_) => <String, dynamic>{});
      }

      if (!mounted) return;
      Navigator.of(context).pushAndRemoveUntil(
        MaterialPageRoute(builder: (_) => const MainScaffold()),
        (route) => false,
      );
    } catch (e) {
      if (!mounted) return;
      if (!e.toString().contains('Đã hủy') &&
          !e.toString().contains('canceled')) {
        await AppDialog.error(
          context,
          title: 'Đăng nhập thất bại',
          message: '$e',
        );
      }
    } finally {
      if (mounted) setState(() => _isSocialLoading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppTheme.bgLight,
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.symmetric(horizontal: 24.0, vertical: 16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              const SizedBox(height: 16),
              Center(child: _logoPlaceholder()),
              const SizedBox(height: 16),
              const Text(
                'Ledgermind App',
                textAlign: TextAlign.center,
                style: TextStyle(
                  color: AppTheme.primary,
                  fontSize: 26,
                  fontWeight: FontWeight.bold,
                ),
              ),
              const SizedBox(height: 6),
              const Text(
                'Quản lý tài chính thông minh',
                textAlign: TextAlign.center,
                style: TextStyle(color: AppTheme.textSecondary, fontSize: 14),
              ),
              const SizedBox(height: 36),
              _label('Email'),
              _input(
                controller: _emailController,
                hint: 'Nhập địa chỉ email',
                keyboardType: TextInputType.emailAddress,
              ),
              const SizedBox(height: 18),
              _label('Mật khẩu'),
              _input(
                controller: _passwordController,
                hint: 'Nhập mật khẩu',
                obscure: _obscure,
                suffix: IconButton(
                  icon: Icon(
                    _obscure
                        ? Icons.visibility_off_outlined
                        : Icons.visibility_outlined,
                    color: AppTheme.textGray,
                    size: 20,
                  ),
                  onPressed: () => setState(() => _obscure = !_obscure),
                ),
              ),
              const SizedBox(height: 8),
              Row(
                children: [
                  SizedBox(
                    width: 24,
                    height: 24,
                    child: Checkbox(
                      value: _rememberMe,
                      onChanged: (v) =>
                          setState(() => _rememberMe = v ?? false),
                      activeColor: AppTheme.primary,
                      materialTapTargetSize: MaterialTapTargetSize.shrinkWrap,
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(6),
                      ),
                    ),
                  ),
                  const SizedBox(width: 8),
                  const Text(
                    'Ghi nhớ đăng nhập',
                    style: TextStyle(
                      color: AppTheme.textSecondary,
                      fontSize: 13,
                    ),
                  ),
                  const Spacer(),
                  GestureDetector(
                    onTap: () => _showForgotPassword(context),
                    child: const Text(
                      'Quên mật khẩu?',
                      style: TextStyle(
                        color: AppTheme.primary,
                        fontSize: 13,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 24),
              PrimaryButton(
                text: _isLoginLoading ? 'Đang xử lý...' : 'Đăng nhập',
                onPressed: (_isLoginLoading || _isSocialLoading)
                    ? null
                    : () {
                        _handleLogin(
                          context,
                          email: _emailController.text,
                          password: _passwordController.text,
                        );
                      },
              ),
              const SizedBox(height: 16),
              Center(
                child: GestureDetector(
                  onTap: () => Navigator.push(
                    context,
                    MaterialPageRoute(builder: (_) => const RegisterScreen()),
                  ),
                  child: RichText(
                    text: const TextSpan(
                      text: 'Chưa có tài khoản? ',
                      style: TextStyle(
                        color: AppTheme.textSecondary,
                        fontSize: 14,
                      ),
                      children: [
                        TextSpan(
                          text: 'Đăng ký ngay',
                          style: TextStyle(
                            color: AppTheme.primary,
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              ),
              const SizedBox(height: 24),
              _orDivider('Hoặc đăng nhập với'),
              const SizedBox(height: 20),
              Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  _socialBtn(
                    Icons.g_mobiledata_rounded,
                    const Color(0xFFDB4437),
                    () => _handleSocial(
                      SocialAuthService.instance.signInWithGoogle,
                    ),
                  ),
                  const SizedBox(width: 20),
                  _socialBtn(
                    Icons.facebook,
                    const Color(0xFF1877F2),
                    () => _handleSocial(
                      SocialAuthService.instance.signInWithFacebook,
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 16),
            ],
          ),
        ),
      ),
    );
  }

  Widget _logoPlaceholder() {
    return Container(
      width: 88,
      height: 88,
      decoration: BoxDecoration(
        color: AppTheme.mintLight,
        borderRadius: BorderRadius.circular(24),
      ),
      clipBehavior: Clip.antiAlias,
      child: Image.asset(
        'assets/images/logo.png',
        fit: BoxFit.cover,
        errorBuilder: (_, _, _) => const Icon(
          Icons.add_photo_alternate_outlined,
          color: AppTheme.primary,
          size: 36,
        ),
      ),
    );
  }

  Widget _label(String text) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 8, left: 2),
      child: Align(
        alignment: Alignment.centerLeft,
        child: Text(
          text,
          style: const TextStyle(
            color: AppTheme.textPrimary,
            fontSize: 13,
            fontWeight: FontWeight.w600,
          ),
        ),
      ),
    );
  }

  Widget _input({
    required TextEditingController controller,
    required String hint,
    bool obscure = false,
    Widget? suffix,
    TextInputType? keyboardType,
  }) {
    return TextField(
      controller: controller,
      obscureText: obscure,
      keyboardType: keyboardType,
      style: const TextStyle(color: AppTheme.textPrimary),
      decoration: InputDecoration(
        hintText: hint,
        hintStyle: const TextStyle(color: AppTheme.textGray),
        suffixIcon: suffix,
      ),
    );
  }

  Widget _orDivider(String text) {
    return Row(
      children: [
        Expanded(
          child: Divider(color: AppTheme.textGray.withValues(alpha: 0.3)),
        ),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 12),
          child: Text(
            text,
            style: const TextStyle(color: AppTheme.textGray, fontSize: 12),
          ),
        ),
        Expanded(
          child: Divider(color: AppTheme.textGray.withValues(alpha: 0.3)),
        ),
      ],
    );
  }

  Widget _socialBtn(IconData icon, Color color, VoidCallback onTap) {
    return InkWell(
      onTap: _isSocialLoading ? null : onTap,
      borderRadius: BorderRadius.circular(30),
      child: Container(
        width: 56,
        height: 56,
        decoration: BoxDecoration(
          color: Colors.white,
          shape: BoxShape.circle,
          boxShadow: AppTheme.softShadow,
        ),
        child: Icon(icon, color: color, size: 30),
      ),
    );
  }
}

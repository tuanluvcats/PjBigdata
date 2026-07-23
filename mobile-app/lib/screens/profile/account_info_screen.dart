import 'package:flutter/material.dart';
import 'package:finance_ai_app/core/theme/app_theme.dart';
import 'package:finance_ai_app/screens/profile/account_credentials_screen.dart';
import 'package:finance_ai_app/screens/profile/user_info_screen.dart';
import 'package:finance_ai_app/services/session_service.dart';

class AccountInfoScreen extends StatelessWidget {
  const AccountInfoScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Thông tin tài khoản')),
      body: ListView(
        padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 12),
        children: [
          ListTile(
            leading: const Icon(
              Icons.person_outline,
              color: AppTheme.mintAccent,
            ),
            title: const Text(
              'Tài khoản',
              style: TextStyle(color: AppTheme.textWhite),
            ),
            trailing: const Icon(Icons.chevron_right, color: AppTheme.textGray),
            onTap: () {
              final userId = SessionService.currentUserId;
              if (userId == null) {
                ScaffoldMessenger.of(context).showSnackBar(
                  const SnackBar(content: Text('Vui lòng đăng nhập lại.')),
                );
                return;
              }

              Navigator.push(
                context,
                MaterialPageRoute(
                  builder: (_) => AccountCredentialsScreen(userId: userId),
                ),
              );
            },
          ),
          Divider(color: AppTheme.textGray.withValues(alpha: 0.1)),
          ListTile(
            leading: const Icon(
              Icons.badge_outlined,
              color: AppTheme.mintAccent,
            ),
            title: const Text(
              'Thông tin cá nhân',
              style: TextStyle(color: AppTheme.textWhite),
            ),
            trailing: const Icon(Icons.chevron_right, color: AppTheme.textGray),
            onTap: () {
              final userId = SessionService.currentUserId;
              if (userId == null) {
                ScaffoldMessenger.of(context).showSnackBar(
                  const SnackBar(content: Text('Vui lòng đăng nhập lại.')),
                );
                return;
              }

              Navigator.push(
                context,
                MaterialPageRoute(builder: (_) => const UserInfoScreen()),
              );
            },
          ),
        ],
      ),
    );
  }
}

import 'package:flutter/material.dart';

import 'package:finance_ai_app/core/theme/app_theme.dart';
import 'package:finance_ai_app/models/user_model.dart';
import 'package:finance_ai_app/screens/profile/set_user_info.dart';
import 'package:finance_ai_app/services/session_service.dart';
import 'package:finance_ai_app/repositories/app_repository.dart';

class UserInfoScreen extends StatefulWidget {
  const UserInfoScreen({super.key});

  @override
  State<UserInfoScreen> createState() => _UserInfoScreenState();
}

class _UserInfoScreenState extends State<UserInfoScreen> {
  bool _isLoading = true;
  UserModel? _user;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    final userId = SessionService.currentUserId;
    if (userId == null) {
      if (!mounted) return;
      setState(() {
        _isLoading = false;
        _user = null;
      });
      return;
    }

    final user = await AppRepository.instance.getCurrentUser(userId);
    if (!mounted) return;
    setState(() {
      _isLoading = false;
      _user = user;
    });
  }

  Future<void> _edit() async {
    final user = _user;
    final userId = user?.id;
    if (userId == null) return;

    final updated = await Navigator.push<bool>(
      context,
      MaterialPageRoute(
        builder: (_) =>
            SetUserInfoScreen(userId: userId, returnToLoginOnSave: false),
      ),
    );

    if (updated == true) {
      await _load();
    }
  }

  String _maskPhone(String phone) {
    if (phone.length <= 4) return phone;
    return '${'*' * (phone.length - 4)}${phone.substring(phone.length - 4)}';
  }

  Widget _buildInfoRow(String label, String value) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 10),
      child: Row(
        children: [
          SizedBox(
            width: 120,
            child: Text(
              label,
              style: const TextStyle(color: AppTheme.textGray),
            ),
          ),
          Expanded(
            child: Text(
              value,
              style: const TextStyle(
                color: AppTheme.textWhite,
                fontWeight: FontWeight.w600,
              ),
            ),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Thông tin cá nhân'),
        actions: [
          IconButton(
            icon: const Icon(Icons.edit_rounded, color: AppTheme.mintAccent),
            onPressed: _edit,
            tooltip: 'Chỉnh sửa',
          ),
        ],
      ),
      body: _isLoading
          ? const Center(
              child: CircularProgressIndicator(color: AppTheme.mintAccent),
            )
          : (_user == null)
          ? const Center(
              child: Text(
                'Chưa có thông tin cá nhân.',
                style: TextStyle(color: AppTheme.textGray),
              ),
            )
          : ListView(
              padding: const EdgeInsets.only(top: 12),
              children: [
                Card(
                  margin: const EdgeInsets.symmetric(horizontal: 16),
                  child: Column(
                    children: [
                      _buildInfoRow('Họ và tên', _user!.fullName),
                      Divider(
                        height: 1,
                        color: Colors.white.withValues(alpha: 0.06),
                      ),
                      _buildInfoRow(
                        'Năm sinh',
                        (_user!.birthdate ?? '').isEmpty
                            ? 'Chưa cập nhật'
                            : _user!.birthdate!,
                      ),
                      Divider(
                        height: 1,
                        color: Colors.white.withValues(alpha: 0.06),
                      ),
                      _buildInfoRow(
                        'Số điện thoại',
                        (_user!.phone ?? '').isEmpty
                            ? 'Chưa cập nhật'
                            : _maskPhone(_user!.phone!),
                      ),
                    ],
                  ),
                ),
              ],
            ),
    );
  }
}

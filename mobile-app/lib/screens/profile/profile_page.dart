
import 'package:flutter/material.dart';

import 'package:finance_ai_app/core/theme/app_theme.dart';
import 'package:finance_ai_app/models/user_model.dart';
import 'package:finance_ai_app/models/wallet_model.dart';
import 'package:finance_ai_app/models/transaction_model.dart';
import 'package:finance_ai_app/screens/budget/budget_screen.dart';
import 'package:finance_ai_app/screens/profile/account_info_screen.dart';
import 'package:finance_ai_app/screens/profile/savings_page.dart';
import 'package:finance_ai_app/screens/linked/linked_services_page.dart';
import 'package:finance_ai_app/screens/auth/login_page.dart';
import 'package:finance_ai_app/services/session_service.dart';
import 'package:finance_ai_app/services/social_auth_service.dart'; 
import 'package:finance_ai_app/repositories/app_repository.dart';
import 'package:finance_ai_app/widgets/dialogs/app_dialog.dart';
import 'package:finance_ai_app/widgets/dialogs/profile_bottom_sheets.dart'; 

class ProfilePage extends StatefulWidget {
  const ProfilePage({super.key});

  @override
  State<ProfilePage> createState() => _ProfilePageState();
}

class _ProfilePageState extends State<ProfilePage> {
  bool _isLoading = true;
  UserModel? _user;
  List<WalletModel> _wallets = [];
  List<TransactionModel> _recentTransactions = [];

  @override
  void initState() {
    super.initState();

    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) _load();
    });
  }

  Future<void> _load() async {
    final userId = SessionService.currentUserId;
    if (userId == null) {
      if (!mounted) return;
      setState(() => _isLoading = false);
      return;
    }
    final user = await AppRepository.instance.getCurrentUser(userId);
    final wallets = await AppRepository.instance.getWallets(userId);
    final txs = await AppRepository.instance.getTransactions(userId);
    if (!mounted) return;
    final sorted = [...txs]..sort((a, b) => b.date.compareTo(a.date));
    setState(() {
      _isLoading = false;
      _user = user;
      _wallets = wallets;
      _recentTransactions = sorted.take(20).toList();
    });
  }

  String _initials(String name) {
    final parts = name.trim().split(RegExp(r'\s+')).where((p) => p.isNotEmpty).toList();
    if (parts.isEmpty) return '?';
    final first = parts.first.isNotEmpty ? parts.first[0] : '';
    final last = parts.length > 1 && parts.last.isNotEmpty ? parts.last[0] : '';
    return (first + last).toUpperCase();
  }

@override
  Widget build(BuildContext context) {
    if (_isLoading) {
      return _buildSkeletonLoading();
    }

    final name = _user?.fullName ?? 'Người dùng';
    final email = _user?.email ?? '';

    return Scaffold(
      backgroundColor: Colors.transparent,
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.only(bottom: 100),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              _buildProfileHeader(name, email), 
              const SizedBox(height: 24),
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 16),
                child: Column(
                  children: [
                    _buildSection(
                      title: 'Tài khoản',
                      icon: Icons.manage_accounts_rounded,
                      items: [
                        _SectionItem(
                          icon: Icons.person_rounded,
                          label: 'Thông tin tài khoản',
                          onTap: () => Navigator.push(
                            context,
                            MaterialPageRoute(builder: (_) => const AccountInfoScreen()),
                          ).then((_) => _load()),
                        ),
                      ],
                    ),
                    const SizedBox(height: 12),
                    _buildSection(
                      title: 'Tiện ích',
                      icon: Icons.widgets_rounded,
                      items: [
                        _SectionItem(icon: Icons.account_balance_rounded, label: 'Nguồn tiền', onTap: () => ProfileBottomSheets.showBankSources(context, _wallets)),
                        _SectionItem(icon: Icons.link_rounded, label: 'Dịch vụ liên kết', onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const LinkedServicesPage()))),
                        _SectionItem(icon: Icons.savings_rounded, label: 'Sổ tiết kiệm', onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const SavingsPage()))),
                        _SectionItem(icon: Icons.pie_chart_rounded, label: 'Ngân sách', onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const BudgetScreen()))),
                      ],
                    ),
                    const SizedBox(height: 12),
                    _buildSection(
                      title: 'Cài đặt',
                      icon: Icons.settings_rounded,
                      items: [
                        _SectionItem(icon: Icons.language_rounded, label: 'Ngôn ngữ', subtitle: 'Tiếng Việt', onTap: () => ProfileBottomSheets.showLanguagePicker(context)),
                        _SectionItem(icon: Icons.notifications_rounded, label: 'Thông báo', onTap: () => ProfileBottomSheets.showNotificationSettings(context)),
                        _SectionItem(icon: Icons.lock_rounded, label: 'Bảo mật & Quyền riêng tư', onTap: () => ProfileBottomSheets.showSecurityMenu(context, _recentTransactions)),
                        _SectionItem(icon: Icons.palette_rounded, label: 'Giao diện', subtitle: AppTheme.themeNotifier.value == ThemeMode.light ? 'Sáng' : 'Tối', onTap: () => ProfileBottomSheets.showThemePicker(context, () => setState(() {}))),
                      ],
                    ),
                    const SizedBox(height: 12),
                    _buildSection(
                      title: 'Hỗ trợ',
                      icon: Icons.help_rounded,
                      items: [
                        _SectionItem(icon: Icons.help_outline_rounded, label: 'Hướng dẫn sử dụng', onTap: () => ProfileBottomSheets.showHelp(context)),
                        _SectionItem(icon: Icons.feedback_rounded, label: 'Gửi phản hồi', onTap: () => ProfileBottomSheets.showFeedback(context)),
                        _SectionItem(icon: Icons.info_outline_rounded, label: 'Về ứng dụng', subtitle: 'v1.0.0', onTap: () => ProfileBottomSheets.showAbout(context)),
                      ],
                    ),
                    const SizedBox(height: 12),
                    _buildSection(
                      title: '',
                      icon: Icons.more_horiz_rounded,
                      hideHeader: true,
                      items: [
                        _SectionItem(
                          icon: Icons.logout_rounded,
                          label: 'Đăng xuất',
                          color: AppTheme.dangerColor,
                          onTap: () async {
                            final ok = await AppDialog.confirm(context, title: 'Đăng xuất?', message: 'Bạn có chắc chắn muốn thoát?', confirmText: 'Đăng xuất', danger: true);
                            if (!ok) return;
                            await SocialAuthService.instance.signOut(); 
                            await SessionService.clearSession();
                            if (!context.mounted) return;
                            Navigator.of(context).pushAndRemoveUntil(MaterialPageRoute(builder: (_) => const LoginScreen()), (route) => false);
                          },
                        ),
                      ],
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildProfileHeader(String name, String email) {
    final initials = _initials(name);
    return Column(
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 8, 16, 0),
          child: Row(
            children: [
              Expanded(
                child: Text(
                  'Cá nhân',
                  textAlign: TextAlign.center,
                  style: TextStyle(color: AppTheme.textPrimary, fontSize: 18, fontWeight: FontWeight.bold),
                ),
              ),
            ],
          ),
        ),
        const SizedBox(height: 16),
        CircleAvatar(
          radius: 44,
          backgroundColor: AppTheme.primary,
          child: Text(initials, style: const TextStyle(color: Colors.white, fontSize: 28, fontWeight: FontWeight.bold)),
        ),
        const SizedBox(height: 12),
        Text(name, style: const TextStyle(color: AppTheme.textPrimary, fontSize: 20, fontWeight: FontWeight.bold)),
        const SizedBox(height: 2),
        Text(email, style: const TextStyle(color: AppTheme.textGray, fontSize: 13)),
      ],
    );
  }
  Widget _buildSection({required String title, required IconData icon, required List<_SectionItem> items, bool hideHeader = false}) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        if (!hideHeader) Padding(
          padding: const EdgeInsets.only(bottom: 8, left: 4),
          child: Row(children: [Icon(icon, color: AppTheme.mintAccent, size: 17), const SizedBox(width: 6), Text(title, style: const TextStyle(color: AppTheme.textWhite, fontSize: 14, fontWeight: FontWeight.bold))]),
        ),
        Container(
          decoration: BoxDecoration(color: AppTheme.navyLight, borderRadius: BorderRadius.circular(16)),
          child: Column(
            children: items.asMap().entries.map((e) {
              final item = e.value;
              return Column(
                children: [
                  ListTile(
                    leading: Icon(item.icon, color: item.color ?? AppTheme.mintAccent, size: 22),
                    title: Text(item.label, style: TextStyle(color: item.color ?? AppTheme.textWhite, fontSize: 14)),
                    subtitle: item.subtitle != null ? Text(item.subtitle!, style: const TextStyle(color: AppTheme.textGray, fontSize: 12)) : null,
                    trailing: Icon(Icons.chevron_right_rounded, color: AppTheme.textGray.withValues(alpha: 0.5), size: 18),
                    onTap: item.onTap,
                  ),
                  if (e.key < items.length - 1) Divider(height: 1, indent: 60, endIndent: 16, color: AppTheme.textGray.withValues(alpha: 0.1)),
                ],
              );
            }).toList(),
          ),
        ),
      ],
    );
  }


  Widget _buildSkeletonLoading() {
    return Scaffold(
      backgroundColor: Colors.transparent,
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 20),
          physics: const NeverScrollableScrollPhysics(),
          children: [
            Align(alignment: Alignment.center, child: _buildBox(width: 88, height: 88, shape: BoxShape.circle)),
            const SizedBox(height: 12),
            Align(alignment: Alignment.center, child: _buildBox(width: 150, height: 20)),
            const SizedBox(height: 32),
            _buildBox(height: 150, borderRadius: 16),
            const SizedBox(height: 24),
            _buildBox(height: 200, borderRadius: 16),
          ],
        ),
      ),
    );
  }

  Widget _buildBox({double? width, double? height, BoxShape shape = BoxShape.rectangle, double borderRadius = 0}) {
    return Container(
      width: width, height: height,
      decoration: BoxDecoration(color: AppTheme.navyLight.withValues(alpha: 0.6), shape: shape, borderRadius: shape == BoxShape.rectangle ? BorderRadius.circular(borderRadius) : null),
    );
  }
}

class _SectionItem {
  final IconData icon;
  final String label;
  final String? subtitle;
  final Color? color;
  final VoidCallback onTap;
  const _SectionItem({required this.icon, required this.label, required this.onTap, this.subtitle, this.color});
}
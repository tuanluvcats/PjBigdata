import 'package:finance_ai_app/services/remote_api_helper.dart';
import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:finance_ai_app/core/theme/app_theme.dart';
import 'package:finance_ai_app/models/user_model.dart';
import 'package:finance_ai_app/models/wallet_model.dart';
import 'package:finance_ai_app/models/transaction_model.dart';
import 'package:finance_ai_app/screens/profile/account_info_screen.dart';
import 'package:finance_ai_app/screens/profile/savings_page.dart';
import 'package:finance_ai_app/screens/transfer/transfer_page.dart';
import 'package:finance_ai_app/screens/linked/linked_services_page.dart';
import 'package:finance_ai_app/screens/auth/login_page.dart';
import 'package:finance_ai_app/services/session_service.dart';
import 'package:finance_ai_app/repositories/app_repository.dart';
import 'package:finance_ai_app/widgets/dialogs/app_dialog.dart';

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
    _load();
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
      return const Scaffold(
        backgroundColor: Colors.transparent,
        body: Center(child: CircularProgressIndicator(color: AppTheme.mintAccent)),
      );
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
                          ).then((_) {
                            WidgetsBinding.instance.addPostFrameCallback((_) {
                              if (mounted) _load();
                            });
                          }),
                        ),
                      ],
                    ),
                    const SizedBox(height: 12),
                    _buildSection(
                      title: 'Tiện ích',
                      icon: Icons.widgets_rounded,
                      items: [
                        _SectionItem(
                          icon: Icons.account_balance_rounded,
                          label: 'Nguồn tiền',
                          onTap: () => _showBankSources(context),
                        ),
                        _SectionItem(
                          icon: Icons.link_rounded,
                          label: 'Dịch vụ liên kết',
                          onTap: () => _showLinkedServices(context),
                        ),
                        _SectionItem(
                          icon: Icons.savings_rounded,
                          label: 'Sổ tiết kiệm',
                          onTap: () => Navigator.push(context,
                              MaterialPageRoute(builder: (_) => const SavingsPage())),
                        ),
                        _SectionItem(
                          icon: Icons.swap_horiz_rounded,
                          label: 'Chuyển khoản',
                          onTap: () => Navigator.push(
                            context,
                            MaterialPageRoute(builder: (_) => const TransferPage()),
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 12),
                    _buildSection(
                      title: 'Cài đặt',
                      icon: Icons.settings_rounded,
                      items: [
                        _SectionItem(
                          icon: Icons.language_rounded,
                          label: 'Ngôn ngữ',
                          subtitle: 'Tiếng Việt',
                          onTap: () => _showLanguagePicker(context),
                        ),
                        _SectionItem(
                          icon: Icons.notifications_rounded,
                          label: 'Thông báo',
                          onTap: () => _showNotificationSettings(context),
                        ),
                        _SectionItem(
                          icon: Icons.lock_rounded,
                          label: 'Bảo mật & Quyền riêng tư',
                          onTap: () => _showSecurityMenu(context),
                        ),
                        _SectionItem(
                          icon: Icons.palette_rounded,
                          label: 'Giao diện',
                          subtitle: AppTheme.themeNotifier.value == ThemeMode.light ? 'Sáng' : 'Tối',
                          onTap: () => _showThemePicker(context),
                        ),
                      ],
                    ),
                    const SizedBox(height: 12),
                    _buildSection(
                      title: 'Hỗ trợ',
                      icon: Icons.help_rounded,
                      items: [
                        _SectionItem(
                          icon: Icons.help_outline_rounded,
                          label: 'Hướng dẫn sử dụng',
                          onTap: () => _showHelp(context),
                        ),
                        _SectionItem(
                          icon: Icons.feedback_rounded,
                          label: 'Gửi phản hồi',
                          onTap: () => _showFeedback(context),
                        ),
                        _SectionItem(
                          icon: Icons.info_outline_rounded,
                          label: 'Về ứng dụng',
                          subtitle: 'v1.0.0',
                          onTap: () => _showAbout(context),
                        ),
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
                            final ok = await AppDialog.confirm(
                              context,
                              title: 'Đăng xuất?',
                              message: 'Bạn có chắc chắn muốn đăng xuất khỏi tài khoản?',
                              confirmText: 'Đăng xuất',
                              danger: true,
                            );
                            if (!ok) return;
                            await SessionService.clearSession();
                            if (!context.mounted) return;
                            Navigator.of(context).pushAndRemoveUntil(
                              MaterialPageRoute(builder: (_) => const LoginScreen()),
                              (route) => false,
                            );
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

  void _showLanguagePicker(BuildContext context) {
    showModalBottomSheet(
      context: context,
      backgroundColor: AppTheme.navyLight,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
      ),
      builder: (ctx) => SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text('Ngôn ngữ',
                  style: TextStyle(
                      color: AppTheme.textWhite,
                      fontSize: 18,
                      fontWeight: FontWeight.bold)),
              const SizedBox(height: 16),
              _langOption(ctx, 'Tiếng Việt', '🇻🇳', selected: true),
              _langOption(ctx, 'English', '🇺🇸', selected: false),
            ],
          ),
        ),
      ),
    );
  }

  Widget _langOption(BuildContext ctx, String label, String flag,
      {required bool selected}) {
    return ListTile(
      leading: Text(flag, style: const TextStyle(fontSize: 24)),
      title: Text(label,
          style: TextStyle(
              color: selected ? AppTheme.mintAccent : AppTheme.textWhite,
              fontWeight: selected ? FontWeight.bold : FontWeight.normal)),
      trailing: selected
          ? const Icon(Icons.check_rounded, color: AppTheme.mintAccent)
          : null,
      onTap: () {
        Navigator.pop(ctx);
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Đã chọn: $label')),
        );
      },
    );
  }

  void _showNotificationSettings(BuildContext context) {
    bool pushEnabled = true;
    bool budgetAlert = true;
    showModalBottomSheet(
      context: context,
      backgroundColor: AppTheme.navyLight,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
      ),
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setModalState) => SafeArea(
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text('Cài đặt thông báo',
                    style: TextStyle(
                        color: AppTheme.textWhite,
                        fontSize: 18,
                        fontWeight: FontWeight.bold)),
                const SizedBox(height: 8),
                SwitchListTile(
                  title: const Text('Thông báo đẩy',
                      style: TextStyle(color: AppTheme.textWhite)),
                  subtitle: const Text('Nhận thông báo từ ứng dụng',
                      style: TextStyle(color: AppTheme.textGray, fontSize: 12)),
                  value: pushEnabled,
                  activeTrackColor: AppTheme.mintAccent,
                  onChanged: (v) => setModalState(() => pushEnabled = v),
                ),
                SwitchListTile(
                  title: const Text('Cảnh báo ngân sách',
                      style: TextStyle(color: AppTheme.textWhite)),
                  subtitle: const Text('Thông báo khi gần vượt ngân sách',
                      style: TextStyle(color: AppTheme.textGray, fontSize: 12)),
                  value: budgetAlert,
                  activeTrackColor: AppTheme.mintAccent,
                  onChanged: (v) => setModalState(() => budgetAlert = v),
                ),
                const SizedBox(height: 8),
                SizedBox(
                  width: double.infinity,
                  child: ElevatedButton(
                    style: ElevatedButton.styleFrom(
                      backgroundColor: AppTheme.mintAccent,
                      foregroundColor: AppTheme.navyDark,
                      shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(12)),
                    ),
                    onPressed: () {
                      Navigator.pop(ctx);
                      ScaffoldMessenger.of(context).showSnackBar(
                        const SnackBar(content: Text('Đã lưu cài đặt thông báo.')),
                      );
                    },
                    child: const Text('Lưu'),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  void _showHelp(BuildContext context) {
    showModalBottomSheet(
      context: context,
      backgroundColor: AppTheme.navyLight,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
      ),
      isScrollControlled: true,
      builder: (ctx) => SafeArea(
        child: DraggableScrollableSheet(
          expand: false,
          builder: (_, controller) => ListView(
            controller: controller,
            padding: const EdgeInsets.all(24),
            children: [
              const Text('Hướng dẫn sử dụng',
                  style: TextStyle(
                      color: AppTheme.textWhite,
                      fontSize: 18,
                      fontWeight: FontWeight.bold)),
              const SizedBox(height: 16),
              ...[
                ('📊 Dashboard', 'Xem tổng quan tài sản, thu chi tháng và biểu đồ dòng tiền.'),
                ('📒 Sổ sách', 'Thêm, lọc và xem chi tiết giao dịch theo ngày, lịch hoặc tháng.'),
                ('📷 Quét hóa đơn', 'Nhấn nút tròn giữa thanh điều hướng để quét hóa đơn bằng AI.'),
                ('🤖 Phân tích AI', 'Xem xu hướng chi tiêu và mẫu hành vi tài chính được phân tích tự động.'),
                ('💸 Chuyển tiền', 'Chuyển/nhận tiền qua số điện thoại hoặc mã QR.'),
              ].map((item) => Padding(
                padding: const EdgeInsets.only(bottom: 16),
                child: Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(item.$1,
                        style: const TextStyle(
                            color: AppTheme.mintAccent,
                            fontWeight: FontWeight.bold)),
                    const SizedBox(width: 8),
                    Expanded(
                      child: Text(item.$2,
                          style: const TextStyle(
                              color: AppTheme.textGray, fontSize: 13)),
                    ),
                  ],
                ),
              )),
            ],
          ),
        ),
      ),
    );
  }

 void _showFeedback(BuildContext context) {
    int selectedStars = 5; 
    final commentController = TextEditingController();
    bool isSubmitting = false;

    showDialog(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setDialogState) => AlertDialog(
          backgroundColor: AppTheme.navyDark,
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
          title: const Text(
            'Đánh giá ứng dụng ⭐',
            style: TextStyle(color: AppTheme.textWhite, fontWeight: FontWeight.bold),
            textAlign: TextAlign.center,
          ),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const Text(
                'Ý kiến của bạn sẽ giúp hệ thống ngày càng hoàn thiện hơn!',
                style: TextStyle(color: AppTheme.textGray, fontSize: 13),
                textAlign: TextAlign.center,
              ),
              const SizedBox(height: 20),
              
              Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: List.generate(5, (index) {
                  final starValue = index + 1;
                  return IconButton(
                    icon: Icon(
                      starValue <= selectedStars ? Icons.star_rounded : Icons.star_outline_rounded,
                      color: Colors.amber,
                      size: 36,
                    ),
                    onPressed: () {
                      setDialogState(() {
                        selectedStars = starValue;
                      });
                    },
                  );
                }),
              ),
              const SizedBox(height: 16),

              TextField(
                controller: commentController,
                style: const TextStyle(color: AppTheme.textWhite),
                maxLines: 3,
                decoration: InputDecoration(
                  hintText: 'Nhập cảm nhận hoặc góp ý...',
                  hintStyle: const TextStyle(color: AppTheme.textGray, fontSize: 13),
                  filled: true,
                  fillColor: AppTheme.navyLight,
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
              child: const Text('Để sau', style: TextStyle(color: AppTheme.textGray)),
            ),
            ElevatedButton(
              style: ElevatedButton.styleFrom(
                backgroundColor: AppTheme.mintAccent,
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
              ),
              onPressed: isSubmitting ? null : () async {
                setDialogState(() => isSubmitting = true); 
                
                final success = await RemoteApiHelper.instance.submitAppReview(
                  selectedStars,
                  commentController.text.trim(),
                );
                
                if (context.mounted) {
                  Navigator.pop(ctx); // Đóng popup
                  if (success) {
                    ScaffoldMessenger.of(context).showSnackBar(
                      const SnackBar(content: Text('Cảm ơn bạn đã gửi đánh giá góp ý!')),
                    );
                  } else {
                    ScaffoldMessenger.of(context).showSnackBar(
                      const SnackBar(content: Text('Gửi đánh giá thất bại, vui lòng thử lại sau.')),
                    );
                  }
                }
              },
              child: isSubmitting 
                ? const SizedBox(width: 16, height: 16, child: CircularProgressIndicator(color: AppTheme.navyDark, strokeWidth: 2))
                : const Text('Gửi ngay', style: TextStyle(color: AppTheme.navyDark, fontWeight: FontWeight.bold)),
            ),
          ],
        ),
      ),
    );
  }

  void _showAbout(BuildContext context) {
    showAboutDialog(
      context: context,
      applicationName: 'DonutPay',
      applicationVersion: 'v1.0.0',
      applicationLegalese: '© 2026 Nhom7 - HUIT',
      children: const [
        SizedBox(height: 12),
        Text(
          'Ứng dụng quản lý tài chính cá nhân thông minh với AI phân tích chi tiêu.',
          style: TextStyle(fontSize: 13),
        ),
      ],
    );
  }

  void _showThemePicker(BuildContext context) async {
    await showModalBottomSheet(
      context: context,
      backgroundColor: AppTheme.navyLight,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
      ),
      builder: (ctx) => SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text('Giao diện',
                  style: TextStyle(
                      color: AppTheme.textWhite,
                      fontSize: 18,
                      fontWeight: FontWeight.bold)),
              const SizedBox(height: 16),
              _buildThemeOption(ctx, 'Tối', Icons.dark_mode_rounded, ThemeMode.dark),
              _buildThemeOption(ctx, 'Sáng', Icons.light_mode_rounded, ThemeMode.light),
            ],
          ),
        ),
      ),
    );
    if (mounted) setState(() {});
  }

  Widget _buildThemeOption(BuildContext ctx, String label, IconData icon, ThemeMode mode) {
    final selected = AppTheme.themeNotifier.value == mode;
    return ListTile(
      leading: Icon(icon, color: selected ? AppTheme.mintAccent : AppTheme.textGray),
      title: Text(label,
          style: TextStyle(
              color: selected ? AppTheme.mintAccent : AppTheme.textWhite,
              fontWeight: selected ? FontWeight.bold : FontWeight.normal)),
      trailing: selected ? const Icon(Icons.check_rounded, color: AppTheme.mintAccent) : null,
      onTap: () async {
        AppTheme.themeNotifier.value = mode;
        final prefs = await SharedPreferences.getInstance();
        await prefs.setString('app_theme', mode == ThemeMode.light ? 'light' : 'dark');
        if (ctx.mounted) Navigator.pop(ctx);
      },
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
                  style: TextStyle(
                    color: AppTheme.textPrimary,
                    fontSize: 18,
                    fontWeight: FontWeight.bold,
                  ),
                ),
              ),
            ],
          ),
        ),
        const SizedBox(height: 16),
        CircleAvatar(
          radius: 44,
          backgroundColor: AppTheme.primary,
          child: Text(
            initials,
            style: const TextStyle(
              color: Colors.white,
              fontSize: 28,
              fontWeight: FontWeight.bold,
            ),
          ),
        ),
        const SizedBox(height: 12),
        Text(
          name,
          style: const TextStyle(
            color: AppTheme.textPrimary,
            fontSize: 20,
            fontWeight: FontWeight.bold,
          ),
        ),
        const SizedBox(height: 2),
        Text(
          email,
          style: const TextStyle(color: AppTheme.textGray, fontSize: 13),
        ),
      ],
    );
  }

  void _showSecurityMenu(BuildContext context) {
    showModalBottomSheet(
      context: context,
      backgroundColor: AppTheme.navyLight,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
      ),
      builder: (ctx) => SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text('Bảo mật & Quyền riêng tư',
                  style: TextStyle(
                      color: AppTheme.textWhite,
                      fontSize: 18,
                      fontWeight: FontWeight.bold)),
              const SizedBox(height: 16),
              ListTile(
                leading: Container(
                  padding: const EdgeInsets.all(8),
                  decoration: BoxDecoration(
                    color: AppTheme.mintAccent.withValues(alpha: 0.12),
                    borderRadius: BorderRadius.circular(10),
                  ),
                  child: const Icon(Icons.pin_rounded, color: AppTheme.mintAccent, size: 20),
                ),
                title: const Text('Tạo / Đổi mã PIN',
                    style: TextStyle(color: AppTheme.textWhite, fontWeight: FontWeight.w500)),
                subtitle: const Text('Bảo vệ ứng dụng bằng mã 4 số',
                    style: TextStyle(color: AppTheme.textGray, fontSize: 12)),
                trailing: Icon(Icons.chevron_right_rounded,
                    color: AppTheme.textGray.withValues(alpha: 0.5)),
                onTap: () {
                  Navigator.pop(ctx);
                  _showPinSetup(context);
                },
              ),
              Divider(height: 1, indent: 60, endIndent: 16,
                  color: AppTheme.textGray.withValues(alpha: 0.1)),
              ListTile(
                leading: Container(
                  padding: const EdgeInsets.all(8),
                  decoration: BoxDecoration(
                    color: AppTheme.mintAccent.withValues(alpha: 0.12),
                    borderRadius: BorderRadius.circular(10),
                  ),
                  child: const Icon(Icons.history_rounded, color: AppTheme.mintAccent, size: 20),
                ),
                title: const Text('Lịch sử hoạt động',
                    style: TextStyle(color: AppTheme.textWhite, fontWeight: FontWeight.w500)),
                subtitle: const Text('Xem các giao dịch gần đây',
                    style: TextStyle(color: AppTheme.textGray, fontSize: 12)),
                trailing: Icon(Icons.chevron_right_rounded,
                    color: AppTheme.textGray.withValues(alpha: 0.5)),
                onTap: () {
                  Navigator.pop(ctx);
                  _showActivityLog(context);
                },
              ),
            ],
          ),
        ),
      ),
    );
  }

  void _showPinSetup(BuildContext context) {
    final pinCtrl = TextEditingController();
    final confirmCtrl = TextEditingController();
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: AppTheme.navyLight,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
        title: const Text('Tạo / Đổi mã PIN',
            style: TextStyle(color: AppTheme.textWhite, fontWeight: FontWeight.bold)),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            TextField(
              controller: pinCtrl,
              keyboardType: TextInputType.number,
              obscureText: true,
              maxLength: 4,
              style: const TextStyle(color: AppTheme.textWhite, letterSpacing: 8),
              decoration: InputDecoration(
                labelText: 'Mã PIN mới',
                labelStyle: const TextStyle(color: AppTheme.textGray),
                filled: true,
                fillColor: AppTheme.navyDark,
                counterStyle: const TextStyle(color: AppTheme.textGray),
                border: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(12),
                  borderSide: BorderSide.none,
                ),
              ),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: confirmCtrl,
              keyboardType: TextInputType.number,
              obscureText: true,
              maxLength: 4,
              style: const TextStyle(color: AppTheme.textWhite, letterSpacing: 8),
              decoration: InputDecoration(
                labelText: 'Xác nhận mã PIN',
                labelStyle: const TextStyle(color: AppTheme.textGray),
                filled: true,
                fillColor: AppTheme.navyDark,
                counterStyle: const TextStyle(color: AppTheme.textGray),
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
            child: const Text('Hủy', style: TextStyle(color: AppTheme.textGray)),
          ),
          ElevatedButton(
            style: ElevatedButton.styleFrom(
              backgroundColor: AppTheme.mintAccent,
              foregroundColor: AppTheme.navyDark,
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
            ),
            onPressed: () async {
              final pin = pinCtrl.text.trim();
              final confirm = confirmCtrl.text.trim();
              if (pin.length != 4) {
                ScaffoldMessenger.of(context).showSnackBar(
                  const SnackBar(content: Text('Mã PIN phải đúng 4 số.')),
                );
                return;
              }
              if (pin != confirm) {
                ScaffoldMessenger.of(context).showSnackBar(
                  const SnackBar(content: Text('Mã PIN không khớp.')),
                );
                return;
              }
              final prefs = await SharedPreferences.getInstance();
              await prefs.setString('app_pin', pin);
              if (!ctx.mounted) return;
              Navigator.pop(ctx);
              ScaffoldMessenger.of(context).showSnackBar(
                const SnackBar(content: Text('Đã lưu mã PIN thành công!')),
              );
            },
            child: const Text('Lưu'),
          ),
        ],
      ),
    );
  }

  void _showActivityLog(BuildContext context) {
    final formatCurrency = NumberFormat.currency(locale: 'vi_VN', symbol: 'đ', decimalDigits: 0);
    final formatDate = DateFormat('dd/MM/yyyy HH:mm');
    showModalBottomSheet(
      context: context,
      backgroundColor: AppTheme.navyLight,
      isScrollControlled: true,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
      ),
      builder: (ctx) => SafeArea(
        child: DraggableScrollableSheet(
          expand: false,
          initialChildSize: 0.6,
          maxChildSize: 0.9,
          builder: (_, controller) => Column(
            children: [
              Padding(
                padding: const EdgeInsets.fromLTRB(24, 20, 24, 12),
                child: Row(
                  children: [
                    const Icon(Icons.history_rounded, color: AppTheme.mintAccent, size: 20),
                    const SizedBox(width: 8),
                    const Text('Lịch sử hoạt động',
                        style: TextStyle(
                            color: AppTheme.textWhite,
                            fontSize: 17,
                            fontWeight: FontWeight.bold)),
                    const Spacer(),
                    Text('${_recentTransactions.length} giao dịch',
                        style: const TextStyle(color: AppTheme.textGray, fontSize: 13)),
                  ],
                ),
              ),
              Expanded(
                child: _recentTransactions.isEmpty
                    ? const Center(
                        child: Text('Chưa có giao dịch nào.',
                            style: TextStyle(color: AppTheme.textGray)))
                    : ListView.separated(
                        controller: controller,
                        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
                        itemCount: _recentTransactions.length,
                        separatorBuilder: (_, _) => Divider(
                          height: 1,
                          indent: 60,
                          endIndent: 16,
                          color: AppTheme.textGray.withValues(alpha: 0.1),
                        ),
                        itemBuilder: (_, i) {
                          final tx = _recentTransactions[i];
                          return ListTile(
                            leading: Container(
                              padding: const EdgeInsets.all(8),
                              decoration: BoxDecoration(
                                color: tx.color.withValues(alpha: 0.15),
                                borderRadius: BorderRadius.circular(10),
                              ),
                              child: Icon(tx.icon, color: tx.color, size: 20),
                            ),
                            title: Text(tx.title,
                                style: const TextStyle(
                                    color: AppTheme.textWhite,
                                    fontSize: 14,
                                    fontWeight: FontWeight.w500)),
                            subtitle: Text(formatDate.format(tx.date),
                                style: const TextStyle(
                                    color: AppTheme.textGray, fontSize: 12)),
                            trailing: Text(
                              '${tx.isExpense ? "−" : "+"} ${formatCurrency.format(tx.amount)}',
                              style: TextStyle(
                                color: tx.isExpense
                                    ? AppTheme.dangerColor
                                    : AppTheme.mintAccent,
                                fontWeight: FontWeight.bold,
                                fontSize: 13,
                              ),
                            ),
                          );
                        },
                      ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  void _showBankSources(BuildContext context) {
    final formatCurrency = NumberFormat.currency(
        locale: 'vi_VN', symbol: '₫', decimalDigits: 0);
    showModalBottomSheet(
      context: context,
      backgroundColor: AppTheme.navyLight,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
      ),
      builder: (ctx) => SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text('Nguồn tiền',
                  style: TextStyle(
                      color: AppTheme.textWhite,
                      fontSize: 18,
                      fontWeight: FontWeight.bold)),
              const SizedBox(height: 4),
              const Text('Các ví đã liên kết với tài khoản',
                  style: TextStyle(color: AppTheme.textGray, fontSize: 13)),
              const SizedBox(height: 16),
              if (_wallets.isEmpty)
                const Padding(
                  padding: EdgeInsets.symmetric(vertical: 12),
                  child: Text('Chưa có ví nào.',
                      style: TextStyle(color: AppTheme.textGray)),
                )
              else
                ..._wallets.map((w) => ListTile(
                      contentPadding: EdgeInsets.zero,
                      leading: Container(
                        padding: const EdgeInsets.all(8),
                        decoration: BoxDecoration(
                          color: Color(w.colorValue).withValues(alpha: 0.15),
                          borderRadius: BorderRadius.circular(10),
                        ),
                        child: Icon(Icons.account_balance_wallet_rounded,
                            color: Color(w.colorValue), size: 20),
                      ),
                      title: Text(w.name,
                          style: const TextStyle(
                              color: AppTheme.textWhite, fontWeight: FontWeight.w500)),
                      trailing: Text(formatCurrency.format(w.balance),
                          style: const TextStyle(
                              color: AppTheme.mintAccent, fontWeight: FontWeight.bold)),
                    )),
              const SizedBox(height: 8),
              SizedBox(
                width: double.infinity,
                child: OutlinedButton.icon(
                  onPressed: () {
                    Navigator.pop(ctx);
                    ScaffoldMessenger.of(context).showSnackBar(
                      const SnackBar(content: Text('Tính năng sắp ra mắt')),
                    );
                  },
                  icon: const Icon(Icons.add_rounded, color: AppTheme.mintAccent),
                  label: const Text('Thêm nguồn tiền',
                      style: TextStyle(color: AppTheme.mintAccent)),
                  style: OutlinedButton.styleFrom(
                    side: BorderSide(color: AppTheme.mintAccent.withValues(alpha: 0.4)),
                    shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(12)),
                    padding: const EdgeInsets.symmetric(vertical: 12),
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  void _showLinkedServices(BuildContext context) {
    Navigator.push(
      context,
      MaterialPageRoute(builder: (_) => const LinkedServicesPage()),
    );
  }

  Widget _buildSection({
    required String title,
    required IconData icon,
    required List<_SectionItem> items,
    bool hideHeader = false,
  }) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        if (!hideHeader) ...[
          Padding(
            padding: const EdgeInsets.only(bottom: 8, left: 4),
            child: Row(
              children: [
                Icon(icon, color: AppTheme.mintAccent, size: 17),
                const SizedBox(width: 6),
                Text(title,
                    style: const TextStyle(
                      color: AppTheme.textWhite,
                      fontSize: 14,
                      fontWeight: FontWeight.bold,
                    )),
              ],
            ),
          ),
        ],
        Container(
          decoration: BoxDecoration(
            color: AppTheme.navyLight,
            borderRadius: BorderRadius.circular(16),
          ),
          child: Column(
            children: items.asMap().entries.map((entry) {
              final idx = entry.key;
              final item = entry.value;
              final iconColor = item.color ?? AppTheme.mintAccent;
              return Column(
                children: [
                  ListTile(
                    leading: Icon(item.icon, color: iconColor, size: 22),
                    title: Text(item.label,
                        style: TextStyle(
                          color: item.color ?? AppTheme.textWhite,
                          fontSize: 14,
                          fontWeight: FontWeight.w500,
                        )),
                    subtitle: item.subtitle != null
                        ? Text(item.subtitle!,
                            style: const TextStyle(
                                color: AppTheme.textGray, fontSize: 12))
                        : null,
                    trailing: Icon(Icons.chevron_right_rounded,
                        color: AppTheme.textGray.withValues(alpha: 0.5),
                        size: 18),
                    onTap: item.onTap,
                    contentPadding:
                        const EdgeInsets.symmetric(horizontal: 16, vertical: 2),
                  ),
                  if (idx < items.length - 1)
                    Divider(
                      height: 1,
                      indent: 60,
                      endIndent: 16,
                      color: AppTheme.textGray.withValues(alpha: 0.1),
                    ),
                ],
              );
            }).toList(),
          ),
        ),
      ],
    );
  }
}

class _SectionItem {
  final IconData icon;
  final String label;
  final String? subtitle;
  final Color? color;
  final VoidCallback onTap;

  const _SectionItem({
    required this.icon,
    required this.label,
    required this.onTap,
    this.subtitle,
    this.color,
  });
}

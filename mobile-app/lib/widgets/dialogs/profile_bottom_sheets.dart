import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:finance_ai_app/core/theme/app_theme.dart';
import 'package:finance_ai_app/models/wallet_model.dart';
import 'package:finance_ai_app/models/transaction_model.dart';
import 'package:finance_ai_app/services/remote_api_helper.dart';

class ProfileBottomSheets {
  static void showLanguagePicker(BuildContext context) {
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
              const Text(
                'Ngôn ngữ',
                style: TextStyle(
                  color: AppTheme.textWhite,
                  fontSize: 18,
                  fontWeight: FontWeight.bold,
                ),
              ),
              const SizedBox(height: 16),
              _langOption(ctx, 'Tiếng Việt', '🇻🇳', selected: true),
              _langOption(ctx, 'English', '🇺🇸', selected: false),
            ],
          ),
        ),
      ),
    );
  }

  static Widget _langOption(
    BuildContext ctx,
    String label,
    String flag, {
    required bool selected,
  }) {
    return ListTile(
      leading: Text(flag, style: const TextStyle(fontSize: 24)),
      title: Text(
        label,
        style: TextStyle(
          color: selected ? AppTheme.mintAccent : AppTheme.textWhite,
          fontWeight: selected ? FontWeight.bold : FontWeight.normal,
        ),
      ),
      trailing: selected
          ? const Icon(Icons.check_rounded, color: AppTheme.mintAccent)
          : null,
      onTap: () {
        Navigator.pop(ctx);
        ScaffoldMessenger.of(ctx).showSnackBar(
          SnackBar(content: Text('Tính năng đang phát triển: $label')),
        );
      },
    );
  }

  static void showNotificationSettings(BuildContext context) {
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
                const Text(
                  'Cài đặt thông báo',
                  style: TextStyle(
                    color: AppTheme.textWhite,
                    fontSize: 18,
                    fontWeight: FontWeight.bold,
                  ),
                ),
                const SizedBox(height: 8),
                SwitchListTile(
                  title: const Text(
                    'Thông báo đẩy',
                    style: TextStyle(color: AppTheme.textWhite),
                  ),
                  value: pushEnabled,
                  activeTrackColor: AppTheme.mintAccent,
                  onChanged: (v) => setModalState(() => pushEnabled = v),
                ),
                SwitchListTile(
                  title: const Text(
                    'Cảnh báo ngân sách',
                    style: TextStyle(color: AppTheme.textWhite),
                  ),
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
                    ),
                    onPressed: () {
                      Navigator.pop(ctx);
                      ScaffoldMessenger.of(context).showSnackBar(
                        const SnackBar(
                          content: Text('Đã lưu cài đặt thông báo.'),
                        ),
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

  static void showThemePicker(
    BuildContext context,
    VoidCallback onThemeChanged,
  ) async {
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
              const Text(
                'Giao diện',
                style: TextStyle(
                  color: AppTheme.textWhite,
                  fontSize: 18,
                  fontWeight: FontWeight.bold,
                ),
              ),
              const SizedBox(height: 16),
              _buildThemeOption(
                ctx,
                'Tối',
                Icons.dark_mode_rounded,
                ThemeMode.dark,
              ),
              _buildThemeOption(
                ctx,
                'Sáng',
                Icons.light_mode_rounded,
                ThemeMode.light,
              ),
            ],
          ),
        ),
      ),
    );
    onThemeChanged();
  }

  static Widget _buildThemeOption(
    BuildContext ctx,
    String label,
    IconData icon,
    ThemeMode mode,
  ) {
    final selected = AppTheme.themeNotifier.value == mode;
    return ListTile(
      leading: Icon(
        icon,
        color: selected ? AppTheme.mintAccent : AppTheme.textGray,
      ),
      title: Text(
        label,
        style: TextStyle(
          color: selected ? AppTheme.mintAccent : AppTheme.textWhite,
        ),
      ),
      trailing: selected
          ? const Icon(Icons.check_rounded, color: AppTheme.mintAccent)
          : null,
      onTap: () async {
        AppTheme.themeNotifier.value = mode;
        final prefs = await SharedPreferences.getInstance();
        await prefs.setString(
          'app_theme',
          mode == ThemeMode.light ? 'light' : 'dark',
        );
        if (ctx.mounted) Navigator.pop(ctx);
      },
    );
  }

  static void showBankSources(BuildContext context, List<WalletModel> wallets) {
    final formatCurrency = NumberFormat.currency(
      locale: 'vi_VN',
      symbol: '₫',
      decimalDigits: 0,
    );
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
              const Text(
                'Nguồn tiền',
                style: TextStyle(
                  color: AppTheme.textWhite,
                  fontSize: 18,
                  fontWeight: FontWeight.bold,
                ),
              ),
              const SizedBox(height: 16),
              if (wallets.isEmpty)
                const Text(
                  'Chưa có ví nào.',
                  style: TextStyle(color: AppTheme.textGray),
                )
              else
                ...wallets.map(
                  (w) => ListTile(
                    contentPadding: EdgeInsets.zero,
                    leading: Icon(
                      Icons.account_balance_wallet_rounded,
                      color: Color(w.colorValue),
                    ),
                    title: Text(
                      w.name,
                      style: const TextStyle(color: AppTheme.textWhite),
                    ),
                    trailing: Text(
                      formatCurrency.format(w.balance),
                      style: const TextStyle(color: AppTheme.mintAccent),
                    ),
                  ),
                ),
            ],
          ),
        ),
      ),
    );
  }

  static void showActivityLog(
    BuildContext context,
    List<TransactionModel> recentTransactions,
  ) {
    final formatCurrency = NumberFormat.currency(
      locale: 'vi_VN',
      symbol: 'đ',
      decimalDigits: 0,
    );
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
                padding: const EdgeInsets.all(24),
                child: Text(
                  'Lịch sử hoạt động (${recentTransactions.length})',
                  style: const TextStyle(
                    color: AppTheme.textWhite,
                    fontSize: 17,
                    fontWeight: FontWeight.bold,
                  ),
                ),
              ),
              Expanded(
                child: ListView.builder(
                  controller: controller,
                  itemCount: recentTransactions.length,
                  itemBuilder: (_, i) {
                    final tx = recentTransactions[i];
                    return ListTile(
                      leading: Icon(tx.icon, color: tx.color),
                      title: Text(
                        tx.title,
                        style: const TextStyle(color: AppTheme.textWhite),
                      ),
                      subtitle: Text(
                        formatDate.format(tx.date),
                        style: const TextStyle(color: AppTheme.textGray),
                      ),
                      trailing: Text(
                        '${tx.isExpense ? "-" : "+"} ${formatCurrency.format(tx.amount)}',
                        style: TextStyle(
                          color: tx.isExpense
                              ? AppTheme.dangerColor
                              : AppTheme.mintAccent,
                          fontWeight: FontWeight.bold,
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

  static void showSecurityMenu(
    BuildContext context,
    List<TransactionModel> recentTransactions,
  ) {
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
              const Text(
                'Bảo mật & Quyền riêng tư',
                style: TextStyle(
                  color: AppTheme.textWhite,
                  fontSize: 18,
                  fontWeight: FontWeight.bold,
                ),
              ),
              const SizedBox(height: 16),
              ListTile(
                leading: Container(
                  padding: const EdgeInsets.all(8),
                  decoration: BoxDecoration(
                    color: AppTheme.mintAccent.withValues(alpha: 0.12),
                    borderRadius: BorderRadius.circular(10),
                  ),
                  child: const Icon(
                    Icons.pin_rounded,
                    color: AppTheme.mintAccent,
                    size: 20,
                  ),
                ),
                title: const Text(
                  'Tạo / Đổi mã PIN',
                  style: TextStyle(
                    color: AppTheme.textWhite,
                    fontWeight: FontWeight.w500,
                  ),
                ),
                subtitle: const Text(
                  'Bảo vệ ứng dụng bằng mã 4 số',
                  style: TextStyle(color: AppTheme.textGray, fontSize: 12),
                ),
                trailing: Icon(
                  Icons.chevron_right_rounded,
                  color: AppTheme.textGray.withValues(alpha: 0.5),
                ),
                onTap: () {
                  Navigator.pop(ctx);
                  showPinSetup(context);
                },
              ),
              Divider(
                height: 1,
                indent: 60,
                endIndent: 16,
                color: AppTheme.textGray.withValues(alpha: 0.1),
              ),
              ListTile(
                leading: Container(
                  padding: const EdgeInsets.all(8),
                  decoration: BoxDecoration(
                    color: AppTheme.mintAccent.withValues(alpha: 0.12),
                    borderRadius: BorderRadius.circular(10),
                  ),
                  child: const Icon(
                    Icons.history_rounded,
                    color: AppTheme.mintAccent,
                    size: 20,
                  ),
                ),
                title: const Text(
                  'Lịch sử hoạt động',
                  style: TextStyle(
                    color: AppTheme.textWhite,
                    fontWeight: FontWeight.w500,
                  ),
                ),
                subtitle: const Text(
                  'Xem các giao dịch gần đây',
                  style: TextStyle(color: AppTheme.textGray, fontSize: 12),
                ),
                trailing: Icon(
                  Icons.chevron_right_rounded,
                  color: AppTheme.textGray.withValues(alpha: 0.5),
                ),
                onTap: () {
                  Navigator.pop(ctx);
                  showActivityLog(context, recentTransactions);
                },
              ),
            ],
          ),
        ),
      ),
    );
  }

  static void showPinSetup(BuildContext context) {
    final pinCtrl = TextEditingController();
    final confirmCtrl = TextEditingController();
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: AppTheme.navyLight,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
        title: const Text(
          'Tạo / Đổi mã PIN',
          style: TextStyle(
            color: AppTheme.textWhite,
            fontWeight: FontWeight.bold,
          ),
        ),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            TextField(
              controller: pinCtrl,
              keyboardType: TextInputType.number,
              obscureText: true,
              maxLength: 4,
              style: const TextStyle(
                color: AppTheme.textWhite,
                letterSpacing: 8,
              ),
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
              style: const TextStyle(
                color: AppTheme.textWhite,
                letterSpacing: 8,
              ),
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
            child: const Text(
              'Hủy',
              style: TextStyle(color: AppTheme.textGray),
            ),
          ),
          ElevatedButton(
            style: ElevatedButton.styleFrom(
              backgroundColor: AppTheme.mintAccent,
              foregroundColor: AppTheme.navyDark,
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(10),
              ),
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

  static void showHelp(BuildContext context) {
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
              const Text(
                'Hướng dẫn sử dụng',
                style: TextStyle(
                  color: AppTheme.textWhite,
                  fontSize: 18,
                  fontWeight: FontWeight.bold,
                ),
              ),
              const SizedBox(height: 16),
              ...[
                (
                  '📊 Dashboard',
                  'Xem tổng quan tài sản, thu chi tháng và biểu đồ dòng tiền.',
                ),
                (
                  '📒 Sổ sách',
                  'Thêm, lọc và xem chi tiết giao dịch theo ngày, lịch hoặc tháng.',
                ),
                (
                  '📷 Quét hóa đơn',
                  'Nhấn nút tròn giữa thanh điều hướng để quét hóa đơn bằng AI.',
                ),
                (
                  '🤖 Phân tích AI',
                  'Xem xu hướng chi tiêu và mẫu hành vi tài chính được phân tích tự động.',
                ),
                (
                  '💸 Chuyển tiền',
                  'Chuyển/nhận tiền qua số điện thoại hoặc mã QR.',
                ),
              ].map(
                (item) => Padding(
                  padding: const EdgeInsets.only(bottom: 16),
                  child: Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        item.$1,
                        style: const TextStyle(
                          color: AppTheme.mintAccent,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                      const SizedBox(width: 8),
                      Expanded(
                        child: Text(
                          item.$2,
                          style: const TextStyle(
                            color: AppTheme.textGray,
                            fontSize: 13,
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  static void showFeedback(BuildContext context) {
    int selectedStars = 5;
    final commentController = TextEditingController();
    bool isSubmitting = false;

    showDialog(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setDialogState) => AlertDialog(
          backgroundColor: AppTheme.navyDark,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(20),
          ),
          title: const Text(
            'Đánh giá ứng dụng ⭐',
            style: TextStyle(
              color: AppTheme.textWhite,
              fontWeight: FontWeight.bold,
            ),
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
                      starValue <= selectedStars
                          ? Icons.star_rounded
                          : Icons.star_outline_rounded,
                      color: Colors.amber,
                      size: 36,
                    ),
                    onPressed: () =>
                        setDialogState(() => selectedStars = starValue),
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
                  hintStyle: const TextStyle(
                    color: AppTheme.textGray,
                    fontSize: 13,
                  ),
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
              child: const Text(
                'Để sau',
                style: TextStyle(color: AppTheme.textGray),
              ),
            ),
            ElevatedButton(
              style: ElevatedButton.styleFrom(
                backgroundColor: AppTheme.mintAccent,
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(12),
                ),
              ),
              onPressed: isSubmitting
                  ? null
                  : () async {
                      setDialogState(() => isSubmitting = true);
                      final success = await RemoteApiHelper.instance
                          .submitAppReview(
                            selectedStars,
                            commentController.text.trim(),
                          );
                      if (context.mounted) {
                        Navigator.pop(ctx);
                        ScaffoldMessenger.of(context).showSnackBar(
                          SnackBar(
                            content: Text(
                              success
                                  ? 'Cảm ơn bạn đã gửi đánh giá góp ý!'
                                  : 'Gửi đánh giá thất bại, vui lòng thử lại sau.',
                            ),
                          ),
                        );
                      }
                    },
              child: isSubmitting
                  ? const SizedBox(
                      width: 16,
                      height: 16,
                      child: CircularProgressIndicator(
                        color: AppTheme.navyDark,
                        strokeWidth: 2,
                      ),
                    )
                  : const Text(
                      'Gửi ngay',
                      style: TextStyle(
                        color: AppTheme.navyDark,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
            ),
          ],
        ),
      ),
    );
  }

  static void showAbout(BuildContext context) {
    showAboutDialog(
      context: context,
      applicationName: 'LedgerMind',
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
}

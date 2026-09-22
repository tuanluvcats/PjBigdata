import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:finance_ai_app/core/theme/app_theme.dart';
import 'package:finance_ai_app/models/wallet_model.dart';

class DashboardBottomSheets {
  static void showNotifications(BuildContext context) {
    showModalBottomSheet(
      context: context,
      backgroundColor: AppTheme.navyLight,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
      ),
      builder: (ctx) {
        return SafeArea(
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Container(
                  width: 40,
                  height: 4,
                  decoration: BoxDecoration(
                    color: AppTheme.textGray.withValues(alpha: 0.3),
                    borderRadius: BorderRadius.circular(10),
                  ),
                ),
                const SizedBox(height: 20),
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    const Text(
                      'Thông báo',
                      style: TextStyle(
                        color: AppTheme.textWhite,
                        fontSize: 18,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                    TextButton(
                      onPressed: () {
                        if (ctx.mounted) Navigator.pop(ctx);
                      },
                      child: const Text(
                        'Đọc tất cả',
                        style: TextStyle(
                          color: AppTheme.mintAccent,
                          fontSize: 13,
                        ),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 16),
                FutureBuilder<List<Map<String, dynamic>>>(
                  future: Future.value(const <Map<String, dynamic>>[]),
                  builder: (context, snapshot) {
                    if (snapshot.connectionState == ConnectionState.waiting) {
                      return const Padding(
                        padding: EdgeInsets.all(24),
                        child: CircularProgressIndicator(
                          color: AppTheme.mintAccent,
                        ),
                      );
                    }
                    final notifications = snapshot.data ?? [];
                    if (notifications.isEmpty) {
                      return Column(
                        children: [
                          Icon(
                            Icons.notifications_off_outlined,
                            color: AppTheme.textGray.withValues(alpha: 0.5),
                            size: 48,
                          ),
                          const SizedBox(height: 12),
                          const Text(
                            'Không có thông báo mới.',
                            style: TextStyle(color: AppTheme.textGray),
                          ),
                          const SizedBox(height: 8),
                        ],
                      );
                    }
                    return ListView.builder(
                      shrinkWrap: true,
                      itemCount: notifications.length,
                      itemBuilder: (_, i) => ListTile(
                        leading: const Icon(
                          Icons.circle_notifications_rounded,
                          color: AppTheme.mintAccent,
                        ),
                        title: Text(
                          notifications[i]['title']?.toString() ?? '',
                          style: const TextStyle(
                            color: AppTheme.textWhite,
                            fontSize: 14,
                          ),
                        ),
                        subtitle: Text(
                          notifications[i]['body']?.toString() ?? '',
                          style: const TextStyle(
                            color: AppTheme.textGray,
                            fontSize: 12,
                          ),
                        ),
                      ),
                    );
                  },
                ),
              ],
            ),
          ),
        );
      },
    );
  }

  static void showBankConnect(BuildContext context) {
    const banks = [
      (Icons.account_balance_rounded, 'Vietcombank', 'VCB', Color(0xFF007236)),
      (Icons.account_balance_rounded, 'VietinBank', 'CTG', Color(0xFF0066B3)),
      (Icons.account_balance_rounded, 'BIDV', 'BIDV', Color(0xFF0079B9)),
      (Icons.account_balance_rounded, 'Agribank', 'AGB', Color(0xFF028A3D)),
      (Icons.account_balance_rounded, 'Techcombank', 'TCB', Color(0xFFE30613)),
      (Icons.account_balance_rounded, 'MB Bank', 'MBB', Color(0xFF001D7E)),
      (Icons.account_balance_rounded, 'ACB', 'ACB', Color(0xFF0055A5)),
      (Icons.account_balance_rounded, 'VPBank', 'VPB', Color(0xFF006633)),
      (Icons.account_balance_rounded, 'TPBank', 'TPB', Color(0xFF8B1A1A)),
      (Icons.account_balance_rounded, 'Sacombank', 'STB', Color(0xFF003DA5)),
    ];
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
          initialChildSize: 0.55,
          maxChildSize: 0.85,
          builder: (_, controller) => Column(
            children: [
              Padding(
                padding: const EdgeInsets.fromLTRB(24, 20, 24, 12),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text(
                      'Kết nối ngân hàng',
                      style: TextStyle(
                        color: AppTheme.textWhite,
                        fontSize: 18,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                    const SizedBox(height: 4),
                    const Text(
                      'Liên kết tài khoản ngân hàng để đồng bộ giao dịch',
                      style: TextStyle(color: AppTheme.textGray, fontSize: 13),
                    ),
                  ],
                ),
              ),
              Expanded(
                child: ListView.separated(
                  controller: controller,
                  padding: const EdgeInsets.symmetric(
                    horizontal: 16,
                    vertical: 4,
                  ),
                  itemCount: banks.length,
                  separatorBuilder: (_, _) => Divider(
                    height: 1,
                    indent: 60,
                    endIndent: 16,
                    color: AppTheme.textGray.withValues(alpha: 0.08),
                  ),
                  itemBuilder: (_, i) {
                    final b = banks[i];
                    return ListTile(
                      leading: Container(
                        padding: const EdgeInsets.all(8),
                        decoration: BoxDecoration(
                          color: b.$4.withValues(alpha: 0.15),
                          borderRadius: BorderRadius.circular(10),
                        ),
                        child: Icon(b.$1, color: b.$4, size: 20),
                      ),
                      title: Text(
                        b.$2,
                        style: const TextStyle(
                          color: AppTheme.textWhite,
                          fontWeight: FontWeight.w500,
                        ),
                      ),
                      subtitle: Text(
                        b.$3,
                        style: const TextStyle(
                          color: AppTheme.textGray,
                          fontSize: 12,
                        ),
                      ),
                      trailing: Container(
                        padding: const EdgeInsets.symmetric(
                          horizontal: 10,
                          vertical: 4,
                        ),
                        decoration: BoxDecoration(
                          color: AppTheme.textGray.withValues(alpha: 0.1),
                          borderRadius: BorderRadius.circular(8),
                        ),
                        child: const Text(
                          'Kết nối',
                          style: TextStyle(
                            color: AppTheme.textGray,
                            fontSize: 11,
                          ),
                        ),
                      ),
                      onTap: () {
                        Navigator.pop(ctx);
                        ScaffoldMessenger.of(context).showSnackBar(
                          const SnackBar(content: Text('Tính năng sắp ra mắt')),
                        );
                      },
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

  static void showBankSources(BuildContext context, List<WalletModel> wallets) {
    final fmtCur = NumberFormat.currency(
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
              const SizedBox(height: 4),
              const Text(
                'Các ví đã liên kết với tài khoản',
                style: TextStyle(color: AppTheme.textGray, fontSize: 13),
              ),
              const SizedBox(height: 16),
              if (wallets.isEmpty)
                const Padding(
                  padding: EdgeInsets.symmetric(vertical: 12),
                  child: Text(
                    'Chưa có ví nào.',
                    style: TextStyle(color: AppTheme.textGray),
                  ),
                )
              else
                ...wallets.map(
                  (w) => ListTile(
                    contentPadding: EdgeInsets.zero,
                    leading: Container(
                      padding: const EdgeInsets.all(8),
                      decoration: BoxDecoration(
                        color: Color(w.colorValue).withValues(alpha: 0.15),
                        borderRadius: BorderRadius.circular(10),
                      ),
                      child: Icon(
                        Icons.account_balance_wallet_rounded,
                        color: Color(w.colorValue),
                        size: 20,
                      ),
                    ),
                    title: Text(
                      w.name,
                      style: const TextStyle(
                        color: AppTheme.textWhite,
                        fontWeight: FontWeight.w500,
                      ),
                    ),
                    trailing: Text(
                      fmtCur.format(w.balance),
                      style: const TextStyle(
                        color: AppTheme.mintAccent,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                  ),
                ),
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
                  icon: const Icon(
                    Icons.add_rounded,
                    color: AppTheme.mintAccent,
                  ),
                  label: const Text(
                    'Thêm nguồn tiền',
                    style: TextStyle(color: AppTheme.mintAccent),
                  ),
                  style: OutlinedButton.styleFrom(
                    side: BorderSide(
                      color: AppTheme.mintAccent.withValues(alpha: 0.4),
                    ),
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(12),
                    ),
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

  static void showAllFeatures(
    BuildContext context, {

    required VoidCallback onSavings,
    required VoidCallback onBankSources,
    required VoidCallback onLinkedServices,
  }) {
    final features = [
      (
        Icons.savings_rounded,
        'Sổ tiết kiệm',
        'Quản lý mục tiêu tiết kiệm',
        onSavings,
      ),
      (
        Icons.account_balance_wallet_rounded,
        'Nguồn tiền',
        'Xem ví và số dư',
        onBankSources,
      ),
      (
        Icons.link_rounded,
        'Dịch vụ liên kết',
        'MoMo, ZaloPay, ngân hàng...',
        onLinkedServices,
      ),
    ];

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
                'Tất cả tính năng',
                style: TextStyle(
                  color: AppTheme.textWhite,
                  fontSize: 18,
                  fontWeight: FontWeight.bold,
                ),
              ),
              const SizedBox(height: 16),
              GridView.count(
                shrinkWrap: true,
                crossAxisCount: 4,
                crossAxisSpacing: 12,
                mainAxisSpacing: 16,
                childAspectRatio: 0.53,
                physics: const NeverScrollableScrollPhysics(),
                children: features.map((f) {
                  return GestureDetector(
                    onTap: f.$4,
                    child: Column(
                      children: [
                        Container(
                          width: 56,
                          height: 56,
                          decoration: BoxDecoration(
                            color: AppTheme.mintAccent.withValues(alpha: 0.12),
                            borderRadius: BorderRadius.circular(16),
                          ),
                          child: Icon(
                            f.$1,
                            color: AppTheme.mintAccent,
                            size: 26,
                          ),
                        ),
                        const SizedBox(height: 8),
                        Text(
                          f.$2,
                          textAlign: TextAlign.center,
                          style: const TextStyle(
                            color: AppTheme.textWhite,
                            fontSize: 11,
                            fontWeight: FontWeight.w500,
                          ),
                        ),
                        const SizedBox(height: 2),
                        Text(
                          f.$3,
                          textAlign: TextAlign.center,
                          maxLines: 2,
                          overflow: TextOverflow.ellipsis,
                          style: const TextStyle(
                            color: AppTheme.textGray,
                            fontSize: 10,
                            height: 1.2,
                          ),
                        ),
                      ],
                    ),
                  );
                }).toList(),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

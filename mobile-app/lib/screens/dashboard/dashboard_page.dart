import 'dart:ui';
import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:finance_ai_app/core/theme/app_theme.dart';
import 'package:finance_ai_app/models/user_model.dart';
import 'package:finance_ai_app/models/transaction_model.dart';
import 'package:finance_ai_app/models/wallet_model.dart';
import 'package:finance_ai_app/models/budget_model.dart';
import 'package:finance_ai_app/services/session_service.dart';
import 'package:finance_ai_app/services/sqflite_helper.dart';
import 'package:finance_ai_app/repositories/app_repository.dart';
import 'package:finance_ai_app/screens/transfer/transfer_page.dart';
import 'package:finance_ai_app/screens/profile/savings_page.dart';
import 'package:finance_ai_app/screens/linked/linked_services_page.dart';

class DashboardPage extends StatefulWidget {
  const DashboardPage({super.key});

  @override
  State<DashboardPage> createState() => DashboardPageState();
}

class DashboardPageState extends State<DashboardPage> {
  void reload() => _load();

  bool _isLoading = true;
  bool _balanceHidden = false;
  UserModel? _user;
  List<TransactionModel> _transactions = [];
  List<WalletModel> _wallets = [];
  List<BudgetModel> _budgets = [];

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() => _isLoading = true);
    final userId = SessionService.currentUserId;
    if (userId == null) {
      setState(() => _isLoading = false);
      return;
    }

    final now = DateTime.now();
    try {
      final user = await AppRepository.instance.getCurrentUser(userId);
      final txs = await AppRepository.instance.getTransactions(userId);
      final wallets = await AppRepository.instance.getWallets(userId);
      final budgets = await SqfliteHelper.instance.getBudgetsByUserIdAndMonth(
        userId,
        now.month,
        now.year,
      );

      if (!mounted) return;
      setState(() {
        _user = user;
        _transactions = txs;
        _wallets = wallets;
        _budgets = budgets;
        _isLoading = false;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() => _isLoading = false);
    }
  }

  double get _totalAssets {
    return _transactions.fold(
      0.0,
      (sum, tx) => sum + (tx.isExpense ? -tx.amount : tx.amount),
    );
  }

  List<TransactionModel> get _monthlyTransactions {
    final now = DateTime.now();
    return _transactions
        .where((tx) => tx.date.year == now.year && tx.date.month == now.month)
        .toList();
  }

  double get _monthlyIncome {
    return _monthlyTransactions
        .where((tx) => !tx.isExpense)
        .fold(0.0, (sum, tx) => sum + tx.amount);
  }

  double get _monthlyExpense {
    return _monthlyTransactions
        .where((tx) => tx.isExpense)
        .fold(0.0, (sum, tx) => sum + tx.amount);
  }

  List<TransactionModel> get _recentTransactions {
    return _transactions.take(5).toList();
  }

  double get _totalBudgetLimit {
    return _budgets.fold(0.0, (sum, b) => sum + b.limitAmount);
  }

  bool get _isBudgetExceeded =>
      _budgets.isNotEmpty && _monthlyExpense > _totalBudgetLimit;

  DateTime get _prevMonth {
    final now = DateTime.now();
    return DateTime(now.year, now.month - 1);
  }

  double get _prevMonthlyIncome {
    final p = _prevMonth;
    return _transactions
        .where(
          (tx) =>
              !tx.isExpense &&
              tx.date.year == p.year &&
              tx.date.month == p.month,
        )
        .fold(0.0, (sum, tx) => sum + tx.amount);
  }

  double get _prevMonthlyExpense {
    final p = _prevMonth;
    return _transactions
        .where(
          (tx) =>
              tx.isExpense &&
              tx.date.year == p.year &&
              tx.date.month == p.month,
        )
        .fold(0.0, (sum, tx) => sum + tx.amount);
  }

  void _showNotifications() {
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

  String _formatCurrency(double amount) {
    return NumberFormat.currency(
      locale: 'vi_VN',
      symbol: 'đ',
      decimalDigits: 0,
    ).format(amount);
  }

  @override
  Widget build(BuildContext context) {
    if (_isLoading) {
      return Stack(
        children: [
          Positioned(
            top: -100,
            left: -100,
            child: ImageFiltered(
              imageFilter: ImageFilter.blur(sigmaX: 80, sigmaY: 80),
              child: Container(
                width: 300,
                height: 300,
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  color: AppTheme.mintAccent.withValues(alpha: 0.15),
                ),
              ),
            ),
          ),
          const SafeArea(
            child: Center(
              child: CircularProgressIndicator(color: AppTheme.mintAccent),
            ),
          ),
        ],
      );
    }

    return Stack(
      children: [
        Positioned(
          top: -100,
          left: -100,
          child: ImageFiltered(
            imageFilter: ImageFilter.blur(sigmaX: 80, sigmaY: 80),
            child: Container(
              width: 300,
              height: 300,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                color: AppTheme.mintAccent.withValues(alpha: 0.15),
              ),
            ),
          ),
        ),
        SafeArea(
          child: RefreshIndicator(
            onRefresh: _load,
            color: AppTheme.mintAccent,
            backgroundColor: AppTheme.navyLight,
            child: CustomScrollView(
              physics: const AlwaysScrollableScrollPhysics(),
              slivers: [
                SliverPadding(
                  padding: const EdgeInsets.only(bottom: 100),
                  sliver: SliverList(
                    delegate: SliverChildListDelegate([
                      _buildHeader(),
                      if (_isBudgetExceeded) _buildBudgetWarning(),
                      const SizedBox(height: 24),
                      _buildUtilityGrid(),
                      const SizedBox(height: 24),
                      _buildRecentTransactions(),
                    ]),
                  ),
                ),
              ],
            ),
          ),
        ),
      ],
    );
  }

  Widget _buildHeader() {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 10),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text(
                    'Xin chào,',
                    style: TextStyle(color: AppTheme.textGray, fontSize: 14),
                  ),
                  Text(
                    _user?.fullName ?? 'User',
                    style: const TextStyle(
                      color: AppTheme.textWhite,
                      fontSize: 20,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                ],
              ),
              CircleAvatar(
                backgroundColor: AppTheme.navyLight,
                child: IconButton(
                  icon: const Icon(
                    Icons.notifications_outlined,
                    color: AppTheme.textWhite,
                  ),
                  onPressed: () => WidgetsBinding.instance.addPostFrameCallback(
                    (_) => _showNotifications(),
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 24),
          Container(
            padding: const EdgeInsets.all(24),
            decoration: BoxDecoration(
              gradient: AppTheme.purpleCardGradient,
              borderRadius: BorderRadius.circular(24),
              boxShadow: [
                BoxShadow(
                  color: AppTheme.primary.withValues(alpha: 0.30),
                  blurRadius: 24,
                  offset: const Offset(0, 12),
                ),
              ],
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Text(
                      'Tổng tài sản hiện tại',
                      style: TextStyle(
                        color: Colors.white.withValues(alpha: 0.75),
                        fontSize: 14,
                      ),
                    ),
                    const Spacer(),
                    GestureDetector(
                      onTap: () =>
                          setState(() => _balanceHidden = !_balanceHidden),
                      child: Icon(
                        _balanceHidden
                            ? Icons.visibility_off_rounded
                            : Icons.visibility_rounded,
                        color: Colors.white.withValues(alpha: 0.75),
                        size: 20,
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 8),
                AnimatedSwitcher(
                  duration: const Duration(milliseconds: 250),
                  transitionBuilder: (child, anim) =>
                      FadeTransition(opacity: anim, child: child),
                  child: Text(
                    _balanceHidden ? '••••••••' : _formatCurrency(_totalAssets),
                    key: ValueKey(_balanceHidden),
                    style: const TextStyle(
                      color: Colors.white,
                      fontSize: 36,
                      fontWeight: FontWeight.w800,
                    ),
                  ),
                ),
                const SizedBox(height: 16),
                _buildTrendCapsule(),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildTrendCapsule() {
    final curNet = _monthlyIncome - _monthlyExpense;
    final prevNet = _prevMonthlyIncome - _prevMonthlyExpense;
    final diff = prevNet == 0
        ? (curNet > 0 ? 100.0 : (curNet < 0 ? -100.0 : 0.0))
        : (curNet - prevNet) / prevNet.abs() * 100;
    final up = diff >= 0;
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Container(
          padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
          decoration: BoxDecoration(
            color: Colors.white.withValues(alpha: 0.18),
            borderRadius: BorderRadius.circular(20),
          ),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(
                up ? Icons.arrow_upward_rounded : Icons.arrow_downward_rounded,
                color: Colors.white,
                size: 14,
              ),
              const SizedBox(width: 4),
              Text(
                '${diff.abs().toStringAsFixed(1)}%',
                style: const TextStyle(
                  color: Colors.white,
                  fontWeight: FontWeight.bold,
                  fontSize: 13,
                ),
              ),
            ],
          ),
        ),
        const SizedBox(width: 8),
        Text(
          'so với tháng trước',
          style: TextStyle(
            color: Colors.white.withValues(alpha: 0.75),
            fontSize: 12,
          ),
        ),
      ],
    );
  }

  Widget _buildBudgetWarning() {
    final over = _monthlyExpense - _totalBudgetLimit;
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 8),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        decoration: BoxDecoration(
          color: AppTheme.dangerColor.withValues(alpha: 0.15),
          borderRadius: BorderRadius.circular(16),
          border: Border.all(
            color: AppTheme.dangerColor.withValues(alpha: 0.4),
          ),
        ),
        child: Row(
          children: [
            const Icon(
              Icons.warning_amber_rounded,
              color: AppTheme.dangerColor,
              size: 24,
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text(
                    'Ngân sách tháng này đã vượt!',
                    style: TextStyle(
                      color: AppTheme.dangerColor,
                      fontWeight: FontWeight.bold,
                      fontSize: 13,
                    ),
                  ),
                  const SizedBox(height: 2),
                  Text(
                    'Bạn đã chi vượt ${_formatCurrency(over)} so với ngân sách',
                    style: const TextStyle(
                      color: AppTheme.textGray,
                      fontSize: 12,
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  void _showBankConnect(BuildContext context) {
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

  Future<void> _showLinkedServices(BuildContext context) async {
    final synced = await Navigator.push(
      context,
      MaterialPageRoute(builder: (_) => const LinkedServicesPage()),
    );
    if (synced == true) reload();
  }

  void _showBankSources(BuildContext context) {
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
              if (_wallets.isEmpty)
                const Padding(
                  padding: EdgeInsets.symmetric(vertical: 12),
                  child: Text(
                    'Chưa có ví nào.',
                    style: TextStyle(color: AppTheme.textGray),
                  ),
                )
              else
                ..._wallets.map(
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

  void _showAllFeatures(BuildContext context) {
    final features = [
      (
        Icons.swap_horiz_rounded,
        'Chuyển khoản',
        'Chuyển tiền giữa các ví',
        () {
          Navigator.pop(context);
          Navigator.push(
            context,
            MaterialPageRoute(builder: (_) => const TransferPage()),
          );
        },
      ),
      (
        Icons.savings_rounded,
        'Sổ tiết kiệm',
        'Quản lý mục tiêu tiết kiệm',
        () {
          Navigator.pop(context);
          Navigator.push(
            context,
            MaterialPageRoute(builder: (_) => const SavingsPage()),
          );
        },
      ),
      (
        Icons.account_balance_wallet_rounded,
        'Nguồn tiền',
        'Xem ví và số dư',
        () {
          Navigator.pop(context);
          _showBankSources(context);
        },
      ),
      (
        Icons.link_rounded,
        'Dịch vụ liên kết',
        'MoMo, ZaloPay, ngân hàng...',
        () {
          Navigator.pop(context);
          _showLinkedServices(context);
        },
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

  Widget _buildUtilityGrid() {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 20),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text(
            'Tiện ích',
            style: TextStyle(
              color: AppTheme.textWhite,
              fontSize: 18,
              fontWeight: FontWeight.bold,
            ),
          ),
          const SizedBox(height: 16),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              _buildUtilityIcon(
                Icons.swap_horiz_rounded,
                'Chuyển\ntiền',
                onTap: () => Navigator.push(
                  context,
                  MaterialPageRoute(builder: (_) => const TransferPage()),
                ),
              ),
              _buildUtilityIcon(
                Icons.savings_rounded,
                'Sổ tiết kiệm',
                onTap: () => Navigator.push(
                  context,
                  MaterialPageRoute(builder: (_) => const SavingsPage()),
                ),
              ),
              _buildUtilityIcon(
                Icons.account_balance_rounded,
                'Kết nối\nngân hàng',
                onTap: () => _showBankConnect(context),
              ),
              _buildUtilityIcon(
                Icons.grid_view_rounded,
                'Tất cả',
                onTap: () => _showAllFeatures(context),
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildUtilityIcon(IconData icon, String label, {VoidCallback? onTap}) {
    return GestureDetector(
      onTap: onTap,
      child: Column(
        children: [
          Container(
            width: 56,
            height: 56,
            decoration: BoxDecoration(
              color: AppTheme.navyLight,
              borderRadius: BorderRadius.circular(16),
            ),
            child: Icon(icon, color: AppTheme.mintAccent, size: 28),
          ),
          const SizedBox(height: 8),
          Text(
            label,
            textAlign: TextAlign.center,
            style: const TextStyle(
              color: AppTheme.textGray,
              fontSize: 12,
              height: 1.2,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildRecentTransactions() {
    final recent = _recentTransactions;
    final formatCurrency = NumberFormat.currency(
      locale: 'vi_VN',
      symbol: 'đ',
      decimalDigits: 0,
    );

    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 20),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text(
            'Giao dịch gần đây',
            style: TextStyle(
              color: AppTheme.textWhite,
              fontSize: 18,
              fontWeight: FontWeight.bold,
            ),
          ),
          const SizedBox(height: 16),
          if (recent.isEmpty)
            Container(
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: AppTheme.navyLight,
                borderRadius: BorderRadius.circular(20),
              ),
              child: const Center(
                child: Text(
                  'Chưa có giao dịch nào.',
                  style: TextStyle(color: AppTheme.textGray),
                ),
              ),
            )
          else
            ...recent.map((tx) {
              return Padding(
                padding: const EdgeInsets.only(bottom: 12),
                child: _buildTxItem(
                  tx.title,
                  _formatTime(tx.date),
                  '${tx.isExpense ? "- " : "+ "}${formatCurrency.format(tx.amount)}',
                  tx.icon,
                  tx.color,
                ),
              );
            }),
        ],
      ),
    );
  }

  String _formatTime(DateTime date) {
    final now = DateTime.now();
    if (DateUtils.isSameDay(date, now)) {
      return 'Hôm nay, ${DateFormat('HH:mm').format(date)}';
    } else if (DateUtils.isSameDay(
      date,
      now.subtract(const Duration(days: 1)),
    )) {
      return 'Hôm qua, ${DateFormat('HH:mm').format(date)}';
    } else if (now.difference(date).inDays < 7) {
      return DateFormat('EEEE, HH:mm', 'vi_VN').format(date);
    } else {
      return DateFormat('dd/MM/yyyy, HH:mm').format(date);
    }
  }

  Widget _buildTxItem(
    String title,
    String date,
    String amount,
    IconData icon,
    Color iconColor,
  ) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AppTheme.navyLight,
        borderRadius: BorderRadius.circular(20),
      ),
      child: Row(
        children: [
          Container(
            padding: const EdgeInsets.all(12),
            decoration: BoxDecoration(
              color: iconColor.withValues(alpha: 0.15),
              borderRadius: BorderRadius.circular(16),
            ),
            child: Icon(icon, color: iconColor),
          ),
          const SizedBox(width: 16),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  title,
                  style: const TextStyle(
                    color: AppTheme.textWhite,
                    fontWeight: FontWeight.bold,
                    fontSize: 16,
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  date,
                  style: const TextStyle(
                    color: AppTheme.textGray,
                    fontSize: 12,
                  ),
                ),
              ],
            ),
          ),
          Text(
            amount,
            style: const TextStyle(
              color: AppTheme.textWhite,
              fontWeight: FontWeight.bold,
              fontSize: 16,
            ),
          ),
        ],
      ),
    );
  }
}

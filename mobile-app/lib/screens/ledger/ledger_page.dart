import 'package:finance_ai_app/services/remote_api_helper.dart';
import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:excel/excel.dart';
import 'package:fl_chart/fl_chart.dart';
import 'package:share_plus/share_plus.dart';
import 'dart:async';
import 'dart:io';

import 'package:finance_ai_app/config/app_config.dart';
import 'package:finance_ai_app/core/theme/app_theme.dart';
import 'package:finance_ai_app/models/sync_status.dart';
import 'package:finance_ai_app/models/transaction_model.dart';
import 'package:finance_ai_app/models/budget_model.dart';
import 'package:finance_ai_app/models/budget_progress.dart';
import 'package:finance_ai_app/services/session_service.dart';
import 'package:finance_ai_app/repositories/app_repository.dart';
import 'package:finance_ai_app/screens/ledger/edit_transaction_page.dart';
import 'package:finance_ai_app/widgets/budget/budget_alert_presenter.dart';
import 'package:finance_ai_app/widgets/budget/budget_section.dart';
import 'package:finance_ai_app/widgets/dialogs/app_dialog.dart';
import 'package:finance_ai_app/widgets/receipt/receipt_status_badge.dart';
import 'package:finance_ai_app/widgets/sync/sync_status_badge.dart';

import 'add_transaction_page.dart';

enum LedgerPeriod { week, month }

class LedgerPage extends StatefulWidget {
  const LedgerPage({super.key});

  @override
  State<LedgerPage> createState() => LedgerPageState();
}

class LedgerPageState extends State<LedgerPage> {
  bool _isLoading = true;

  /// True when the list of the server could not be loaded: [_transactions] then holds only the transactions of this
  /// device that are waiting to be sent, which must stay reachable so that they can be resent.
  bool _transactionsFailed = false;
  List<TransactionModel> _transactions = [];

  /// clientIds of the transactions being resent, so that a second tap on the same one cannot start a second send.
  final Set<String> _resendingClientIds = {};
  List<BudgetModel> _budgets = [];
  bool _budgetsFailed = false;
  List<BudgetProgress> _budgetProgress = [];

  DateTime _selectedDate = DateTime.now();
  LedgerPeriod _period = LedgerPeriod.month;

  bool _showExpenses = true;
  bool _showIncome = true;
  String _sortOption = 'date_desc'; 
  double? _filterMinAmount;
  double? _filterMaxAmount;
  bool _isSearching = false;
  String _searchQuery = '';
  final TextEditingController _searchController = TextEditingController();

  static final _fmtCur =
      NumberFormat.currency(locale: 'vi_VN', symbol: 'đ', decimalDigits: 0);

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    final userId = SessionService.currentUserId;
    if (userId == null) {
      if (!mounted) return;
      setState(() {
        _isLoading = false;
        _transactions = [];
        _budgets = [];
      });
      return;
    }

    // Started together: the budgets do not depend on the transactions, so the wait is that of the slower request.
    final budgetsRequest = AppRepository.instance.tryGetBudgets(userId);
    List<TransactionModel> txs;
    var transactionsFailed = false;
    try {
      txs = await AppRepository.instance.getTransactions(userId);
    } catch (e) {
      debugPrint('Lỗi tải giao dịch: $e');
      transactionsFailed = true;
      txs = await AppRepository.instance.getUnsyncedTransactions(userId);
    }
    final budgets = await budgetsRequest;

    if (!mounted) return;
    setState(() {
      _isLoading = false;
      _transactionsFailed = transactionsFailed;
      _transactions = txs;
      _budgets = budgets ?? [];
      _budgetsFailed = budgets == null;
      _refreshBudgetProgress();
    });
    if (budgets != null) {
      // Not awaited, so a dialog waiting for the user does not hold up the pull-to-refresh.
      unawaited(BudgetAlertPresenter.showNew(
        context,
        userId: userId,
        progresses: BudgetProgress.activeOn(DateTime.now(), budgets, txs),
      ));
    }
  }

  void reload() => _load();

  /// The budgets that overlap the period on screen, each measured over its own dates. It is kept in state because a
  /// keystroke in the search box rebuilds the page, and that must not recompute it.
  void _refreshBudgetProgress() {
    final from = _period == LedgerPeriod.week
        ? _weekStart
        : DateTime(_selectedDate.year, _selectedDate.month, 1);
    final to = _period == LedgerPeriod.week
        ? _weekEnd.subtract(const Duration(days: 1))
        : DateTime(_selectedDate.year, _selectedDate.month + 1, 0);
    _budgetProgress = BudgetProgress.overlapping(from, to, _budgets, _transactions);
  }

  DateTime get _weekStart {
    final d = _selectedDate;
    final base = DateTime(d.year, d.month, d.day);
    return base.subtract(Duration(days: (d.weekday - 1) % 7));
  }

  DateTime get _weekEnd => _weekStart.add(const Duration(days: 7));

  bool _inSelectedPeriod(DateTime date) {
    if (_period == LedgerPeriod.week) {
      return !date.isBefore(_weekStart) && date.isBefore(_weekEnd);
    }
    return date.year == _selectedDate.year &&
        date.month == _selectedDate.month;
  }

  String get _periodLabel {
    if (_period == LedgerPeriod.week) {
      final e = _weekEnd.subtract(const Duration(days: 1));
      return '${DateFormat('dd/MM').format(_weekStart)} - ${DateFormat('dd/MM/yyyy').format(e)}';
    }
    return DateFormat('MMMM yyyy', 'vi_VN').format(_selectedDate);
  }

List<TransactionModel> get _periodTransactions {
    final q = _searchQuery.toLowerCase().trim();
    final list = _transactions.where((tx) {
      final inPeriod = _inSelectedPeriod(tx.date);
      final matchType = (_showExpenses && tx.isExpense) || (_showIncome && !tx.isExpense);
      final matchSearch = q.isEmpty || tx.title.toLowerCase().contains(q) || tx.category.toLowerCase().contains(q);

      final matchMin = _filterMinAmount == null || tx.amount >= _filterMinAmount!;
      final matchMax = _filterMaxAmount == null || tx.amount <= _filterMaxAmount!;
      
      return inPeriod && matchType && matchSearch && matchMin && matchMax;
    }).toList();

    list.sort((a, b) {
      if (_sortOption == 'date_asc') return a.date.compareTo(b.date);
      if (_sortOption == 'amount_desc') return b.amount.compareTo(a.amount);
      if (_sortOption == 'amount_asc') return a.amount.compareTo(b.amount);
      return b.date.compareTo(a.date); 
    });
    return list;
  }

  double get _periodIncome => _periodTransactions
      .where((tx) => !tx.isExpense)
      .fold(0.0, (s, tx) => s + tx.amount);

  double get _periodExpense => _periodTransactions
      .where((tx) => tx.isExpense)
      .fold(0.0, (s, tx) => s + tx.amount);

  void _shiftPeriod(int dir) {
    setState(() {
      if (_period == LedgerPeriod.week) {
        _selectedDate = _selectedDate.add(Duration(days: 7 * dir));
      } else {
        _selectedDate =
            DateTime(_selectedDate.year, _selectedDate.month + dir, 1);
      }
      _refreshBudgetProgress();
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: _isSearching
            ? TextField(
                controller: _searchController,
                autofocus: true,
                style: const TextStyle(color: AppTheme.textPrimary),
                decoration: const InputDecoration(
                  hintText: 'Tìm tên, danh mục...',
                  hintStyle: TextStyle(color: AppTheme.textGray),
                  border: InputBorder.none,
                ),
                onChanged: (v) => setState(() => _searchQuery = v),
              )
            : const Text('Sổ sách',
                style: TextStyle(fontWeight: FontWeight.bold, fontSize: 22)),
        leading: _isSearching
            ? IconButton(
                icon: const Icon(Icons.close_rounded),
                onPressed: () => setState(() {
                  _isSearching = false;
                  _searchQuery = '';
                  _searchController.clear();
                }),
              )
            : IconButton(
                icon: const Icon(Icons.search_rounded),
                onPressed: () => setState(() => _isSearching = true),
              ),
        actions: [
          IconButton(
            icon: Icon(
              Icons.tune_rounded,
              color: (!_showExpenses || !_showIncome)
                  ? AppTheme.primary
                  : AppTheme.textGray,
            ),
            onPressed: _showTypePicker,
          ),
          IconButton(
            icon: const Icon(Icons.add_box_rounded, color: AppTheme.primary),
            onPressed: () {
              Navigator.push<bool>(
                context,
                MaterialPageRoute(
                  builder: (_) => AddTransactionPage(initialDate: _selectedDate),
                ),
              ).then((added) {
                if (added == true) {
                  WidgetsBinding.instance.addPostFrameCallback((_) {
                    if (mounted) _load();
                  });
                }
              });
            },
          ),
        ],
      ),
      body: _isLoading
          ? const Center(child: CircularProgressIndicator())
          : RefreshIndicator(
              onRefresh: _load,
              child: ListView(
                padding: const EdgeInsets.fromLTRB(16, 8, 16, 100),
                children: [
                  if (_transactionsFailed) _buildLoadFailedNotice(),
                  _buildDateStepper(),
                  const SizedBox(height: 16),
                  _buildPeriodHeader(),
                  const SizedBox(height: 16),
                  _buildChartCard(),
                  const SizedBox(height: 16),
                  _buildStatCards(),
                  const SizedBox(height: 24),
                  _buildRecentList(),
                  const SizedBox(height: 24),
                  _buildSummaryBlock(),
                ],
              ),
            ),
    );
  }

  Widget _buildDateStepper() {
    return Center(
      child: InkWell(
        onTap: _pickFullDate,
        borderRadius: BorderRadius.circular(16),
        child: Container(
          padding: const EdgeInsets.symmetric(horizontal: 22, vertical: 10),
          decoration: BoxDecoration(
            color: Colors.white,
            borderRadius: BorderRadius.circular(16),
            boxShadow: AppTheme.softShadow,
          ),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              const Icon(Icons.calendar_today_rounded,
                  size: 16, color: AppTheme.primary),
              const SizedBox(width: 8),
              const Text(
                'Lịch',
                style: TextStyle(
                  color: AppTheme.textPrimary,
                  fontWeight: FontWeight.bold,
                  fontSize: 15,
                ),
              ),
              const SizedBox(width: 8),
              Text(
                DateFormat('dd/MM/yyyy').format(_selectedDate),
                style: const TextStyle(color: AppTheme.textGray, fontSize: 13),
              ),
            ],
          ),
        ),
      ),
    );
  }

  String _wdShort(DateTime d) {
    const w = ['T2', 'T3', 'T4', 'T5', 'T6', 'T7', 'CN'];
    return w[d.weekday - 1];
  }

  Future<void> _pickFullDate() async {
    final picked = await showDatePicker(
      context: context,
      initialDate: _selectedDate,
      firstDate: DateTime(2020),
      lastDate: DateTime(2100),
      builder: (ctx, child) => Theme(
        data: Theme.of(ctx).copyWith(
          colorScheme: const ColorScheme.light(
            primary: AppTheme.primary,
            onPrimary: Colors.white,
            onSurface: AppTheme.textPrimary,
          ),
        ),
        child: child!,
      ),
    );
    if (picked == null) return;
    final monthChanged = picked.month != _selectedDate.month ||
        picked.year != _selectedDate.year;
    setState(() {
      _selectedDate = picked;
      _refreshBudgetProgress();
    });
    if (monthChanged) _load();
  }

  Widget _circleBtn(IconData icon, VoidCallback onTap) {
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(20),
      child: Container(
        width: 34,
        height: 34,
        decoration: BoxDecoration(color: Colors.white, shape: BoxShape.circle, boxShadow: AppTheme.softShadow),
        child: Icon(icon, size: 20, color: AppTheme.textPrimary),
      ),
    );
  }

  Widget _buildPeriodHeader() {
    return Row(
      children: [
        _circleBtn(Icons.chevron_left_rounded, () => _shiftPeriod(-1)),
        const SizedBox(width: 12),
        Expanded(
          child: Text(
            _periodLabel,
            textAlign: TextAlign.center,
            style: const TextStyle(
              color: AppTheme.textGray,
              fontSize: 13,
              fontWeight: FontWeight.w500,
            ),
          ),
        ),
        const SizedBox(width: 12),
        _circleBtn(Icons.chevron_right_rounded, () => _shiftPeriod(1)),
        const SizedBox(width: 8),
        Container(
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
          decoration: BoxDecoration(
            color: AppTheme.mintLight,
            borderRadius: BorderRadius.circular(20),
          ),
          child: DropdownButtonHideUnderline(
            child: DropdownButton<LedgerPeriod>(
              value: _period,
              isDense: true,
              icon: const Icon(Icons.keyboard_arrow_down_rounded,
                  color: AppTheme.primary, size: 18),
              style: const TextStyle(
                color: AppTheme.primary,
                fontWeight: FontWeight.w600,
                fontSize: 13,
              ),
              dropdownColor: Colors.white,
              items: const [
                DropdownMenuItem(
                    value: LedgerPeriod.week, child: Text('Tuần')),
                DropdownMenuItem(
                    value: LedgerPeriod.month, child: Text('Tháng')),
              ],
              onChanged: (v) {
                if (v != null) {
                  setState(() {
                    _period = v;
                    _refreshBudgetProgress();
                  });
                }
              },
            ),
          ),
        ),
      ],
    );
  }

  Widget _buildChartCard() {
    return Container(
      padding: const EdgeInsets.fromLTRB(16, 20, 16, 12),
      decoration: AppTheme.card,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              _legendDot(AppTheme.positive, 'Thu'),
              const SizedBox(width: 16),
              _legendDot(AppTheme.dangerColor, 'Chi'),
            ],
          ),
          const SizedBox(height: 20),
          AnimatedSwitcher(
            duration: const Duration(milliseconds: 300),
            transitionBuilder: (child, anim) =>
                FadeTransition(opacity: anim, child: child),
            child: SizedBox(
              key: ValueKey('${_period.name}_${_selectedDate.month}_${_selectedDate.year}'),
              height: 180,
              child: _buildBarChart(),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildBarChart() {
    final List<double> income = [];
    final List<double> expense = [];
    final List<String> labels = [];

    if (_period == LedgerPeriod.week) {
      for (int i = 0; i < 7; i++) {
        final day = _weekStart.add(Duration(days: i));
        income.add(_sumOn(day, false, isDay: true));
        expense.add(_sumOn(day, true, isDay: true));
        labels.add(_wdShort(day));
      }
    } else {
      for (int i = 5; i >= 0; i--) {
        final m = DateTime(_selectedDate.year, _selectedDate.month - i);
        income.add(_sumOn(m, false, isDay: false));
        expense.add(_sumOn(m, true, isDay: false));
        labels.add('Th${m.month}');
      }
    }

    double maxV = 1;
    for (final v in [...income, ...expense]) {
      if (v > maxV) maxV = v;
    }

    return BarChart(
      BarChartData(
        maxY: maxV * 1.2,
        barTouchData: BarTouchData(enabled: false),
        gridData: FlGridData(
          show: true,
          drawVerticalLine: false,
          horizontalInterval: maxV / 2,
          getDrawingHorizontalLine: (v) => FlLine(
            color: AppTheme.textGray.withValues(alpha: 0.15),
            strokeWidth: 1,
          ),
        ),
        borderData: FlBorderData(show: false),
        titlesData: FlTitlesData(
          leftTitles:
              const AxisTitles(sideTitles: SideTitles(showTitles: false)),
          rightTitles:
              const AxisTitles(sideTitles: SideTitles(showTitles: false)),
          topTitles:
              const AxisTitles(sideTitles: SideTitles(showTitles: false)),
          bottomTitles: AxisTitles(
            sideTitles: SideTitles(
              showTitles: true,
              reservedSize: 24,
              getTitlesWidget: (value, meta) {
                final i = value.toInt();
                if (i < 0 || i >= labels.length) return const SizedBox();
                return Padding(
                  padding: const EdgeInsets.only(top: 6),
                  child: Text(labels[i],
                      style: const TextStyle(
                          color: AppTheme.textGray, fontSize: 11)),
                );
              },
            ),
          ),
        ),
        barGroups: List.generate(
          labels.length,
          (i) => BarChartGroupData(
            x: i,
            barsSpace: 4,
            barRods: [
              BarChartRodData(
                toY: income[i],
                color: AppTheme.positive,
                width: 7,
                borderRadius: BorderRadius.circular(4),
              ),
              BarChartRodData(
                toY: expense[i],
                color: AppTheme.dangerColor,
                width: 7,
                borderRadius: BorderRadius.circular(4),
              ),
            ],
          ),
        ),
      ),
    );
  }

  double _sumOn(DateTime ref, bool expense, {required bool isDay}) {
    return _transactions
        .where((t) {
          if (t.isExpense != expense) return false;
          if (isDay) return DateUtils.isSameDay(t.date, ref);
          return t.date.year == ref.year && t.date.month == ref.month;
        })
        .fold(0.0, (s, t) => s + t.amount);
  }

  Widget _buildStatCards() {
    return Row(
      children: [
        Expanded(
          child: _statCard(
            icon: Icons.south_west_rounded,
            label: 'Thu nhập',
            amount: _periodIncome,
            color: AppTheme.positive,
          ),
        ),
        const SizedBox(width: 12),
        Expanded(
          child: _statCard(
            icon: Icons.north_east_rounded,
            label: 'Chi tiêu',
            amount: _periodExpense,
            color: AppTheme.dangerColor,
          ),
        ),
      ],
    );
  }

  Widget _statCard({
    required IconData icon,
    required String label,
    required double amount,
    required Color color,
  }) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: AppTheme.card,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            padding: const EdgeInsets.all(8),
            decoration: BoxDecoration(
              color: color.withValues(alpha: 0.15),
              borderRadius: BorderRadius.circular(12),
            ),
            child: Icon(icon, color: color, size: 20),
          ),
          const SizedBox(height: 12),
          FittedBox(
            fit: BoxFit.scaleDown,
            alignment: Alignment.centerLeft,
            child: Text(_fmtCur.format(amount),
                style: const TextStyle(
                    color: AppTheme.textPrimary,
                    fontWeight: FontWeight.bold,
                    fontSize: 18)),
          ),
          const SizedBox(height: 2),
          Text(label,
              style: const TextStyle(color: AppTheme.textGray, fontSize: 13)),
        ],
      ),
    );
  }

  Widget _legendDot(Color c, String label) {
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Container(
          width: 10,
          height: 10,
          decoration:
              BoxDecoration(color: c, borderRadius: BorderRadius.circular(3)),
        ),
        const SizedBox(width: 6),
        Text(label,
            style: const TextStyle(color: AppTheme.textGray, fontSize: 12)),
      ],
    );
  }

  Widget _buildRecentList() {
    final txs = _periodTransactions;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Text('Giao dịch gần đây',
            style: TextStyle(
                color: AppTheme.textPrimary,
                fontWeight: FontWeight.bold,
                fontSize: 16)),
        const SizedBox(height: 12),
        if (txs.isEmpty)
          Container(
            padding: const EdgeInsets.all(16),
            decoration: AppTheme.card,
            child: const Text('Không có giao dịch trong kỳ này.',
                style: TextStyle(color: AppTheme.textGray)),
          )
        else
          ...txs.map((tx) => Padding(
                padding: const EdgeInsets.only(bottom: 10),
                child: Container(
                  padding: const EdgeInsets.all(12),
                  decoration: AppTheme.card,
                child: InkWell(
                    onTap: () => _showTransactionDetails(tx), 
                    borderRadius: BorderRadius.circular(12),
                    child: Row(
                      children: [
                        Container(
                          padding: const EdgeInsets.all(10),
                          decoration: BoxDecoration(
                          color: tx.color.withValues(alpha: 0.15),
                          borderRadius: BorderRadius.circular(12),
                        ),
                        child: Icon(tx.icon, color: tx.color, size: 22),
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(tx.title,
                                style: const TextStyle(
                                    color: AppTheme.textPrimary,
                                    fontWeight: FontWeight.w600)),
                            const SizedBox(height: 2),
                            Text(
                              '${tx.category} • ${DateFormat('dd/MM HH:mm').format(tx.date)}',
                              style: const TextStyle(
                                  color: AppTheme.textGray, fontSize: 12),
                            ),
                            if (tx.receiptStatus != null) ...[
                              const SizedBox(height: 4),
                              ReceiptStatusBadge(status: tx.receiptStatus!),
                            ],
                            if (tx.isUnsynced) ...[
                              const SizedBox(height: 4),
                              SyncStatusBadge(status: tx.syncStatus),
                            ],
                          ],
                        ),
                      ),
                      Text(
                        '${tx.isExpense ? "- " : "+ "}${_fmtCur.format(tx.amount)}',
                        style: TextStyle(
                            color: tx.isExpense
                                ? AppTheme.dangerColor
                                : AppTheme.positive,
                            fontWeight: FontWeight.bold),
                      ),
                    ],
                  ),
                ),
              ))),
      ],
    );
  }

  Widget _buildSummaryBlock() {
    final balance = _periodIncome - _periodExpense;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        BudgetSection(progresses: _budgetProgress, loadFailed: _budgetsFailed),
        const SizedBox(height: 24),
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            const Text('Tổng kết',
                style: TextStyle(
                    color: AppTheme.textPrimary,
                    fontWeight: FontWeight.bold,
                    fontSize: 16)),
            TextButton.icon(
              onPressed: _exportToExcel,
              icon: const Icon(Icons.download_rounded, size: 18),
              label: const Text('Excel'),
              style: TextButton.styleFrom(foregroundColor: AppTheme.primary),
            ),
          ],
        ),
        const SizedBox(height: 12),
        Container(
          padding: const EdgeInsets.all(16),
          decoration: AppTheme.card,
          child: Column(
            children: [
              _summaryRow('Tổng thu', _periodIncome, AppTheme.positive),
              const SizedBox(height: 8),
              _summaryRow('Tổng chi', _periodExpense, AppTheme.dangerColor),
              const Divider(height: 24),
              _summaryRow('Số dư', balance,
                  balance >= 0 ? AppTheme.positive : AppTheme.dangerColor,
                  bold: true),
            ],
          ),
        ),
      ],
    );
  }

  Widget _summaryRow(String label, double amount, Color color,
      {bool bold = false}) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        Text(label,
            style: TextStyle(
                color: bold ? AppTheme.textPrimary : AppTheme.textGray,
                fontWeight: bold ? FontWeight.bold : FontWeight.normal)),
        Text(_fmtCur.format(amount),
            style: TextStyle(color: color, fontWeight: FontWeight.bold)),
      ],
    );
  }

  void _showTypePicker() {
    showModalBottomSheet(
      context: context,
      backgroundColor: AppTheme.navyLight,
      isScrollControlled: true,
      shape: const RoundedRectangleBorder(borderRadius: BorderRadius.vertical(top: Radius.circular(24))),
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setModalState) => SafeArea(
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text('Bộ lọc & Sắp xếp thông minh', style: TextStyle(color: AppTheme.textWhite, fontSize: 18, fontWeight: FontWeight.bold)),
                const SizedBox(height: 20),
                
                const Text('1. Loại giao dịch', style: TextStyle(color: AppTheme.mintAccent, fontWeight: FontWeight.bold)),
                Wrap(
                  spacing: 8,
                  children: [
                    ChoiceChip(
                      label: const Text('Tất cả'), selected: _showExpenses && _showIncome,
                      onSelected: (_) => setModalState(() { _showExpenses = true; _showIncome = true; }),
                    ),
                    ChoiceChip(
                      label: const Text('Chỉ Khoản Chi'), selected: _showExpenses && !_showIncome,
                      onSelected: (_) => setModalState(() { _showExpenses = true; _showIncome = false; }),
                    ),
                    ChoiceChip(
                      label: const Text('Chỉ Khoản Thu'), selected: !_showExpenses && _showIncome,
                      onSelected: (_) => setModalState(() { _showExpenses = false; _showIncome = true; }),
                    ),
                  ],
                ),
                const SizedBox(height: 16),

                const Text('2. Khoảng tiền', style: TextStyle(color: AppTheme.mintAccent, fontWeight: FontWeight.bold)),
                Wrap(
                  spacing: 8,
                  children: [
                    ChoiceChip(
                      label: const Text('Mọi mức giá'), selected: _filterMaxAmount == null && _filterMinAmount == null,
                      onSelected: (_) => setModalState(() { _filterMinAmount = null; _filterMaxAmount = null; }),
                    ),
                    ChoiceChip(
                      label: const Text('< 100k'), selected: _filterMaxAmount == 100000,
                      onSelected: (_) => setModalState(() { _filterMinAmount = null; _filterMaxAmount = 100000; }),
                    ),
                    ChoiceChip(
                      label: const Text('100k - 500k'), selected: _filterMinAmount == 100000 && _filterMaxAmount == 500000,
                      onSelected: (_) => setModalState(() { _filterMinAmount = 100000; _filterMaxAmount = 500000; }),
                    ),
                    ChoiceChip(
                      label: const Text('> 500k'), selected: _filterMinAmount == 500000 && _filterMaxAmount == null,
                      onSelected: (_) => setModalState(() { _filterMinAmount = 500000; _filterMaxAmount = null; }),
                    ),
                  ],
                ),
                const SizedBox(height: 16),

                const Text('3. Sắp xếp theo', style: TextStyle(color: AppTheme.mintAccent, fontWeight: FontWeight.bold)),
                Wrap(
                  spacing: 8,
                  children: [
                    ChoiceChip(
                      label: const Text('Mới nhất'), selected: _sortOption == 'date_desc',
                      onSelected: (_) => setModalState(() => _sortOption = 'date_desc'),
                    ),
                    ChoiceChip(
                      label: const Text('Cũ nhất'), selected: _sortOption == 'date_asc',
                      onSelected: (_) => setModalState(() => _sortOption = 'date_asc'),
                    ),
                    ChoiceChip(
                      label: const Text('Giá trị cao nhất'), selected: _sortOption == 'amount_desc',
                      onSelected: (_) => setModalState(() => _sortOption = 'amount_desc'),
                    ),
                  ],
                ),
                
                const SizedBox(height: 24),
                SizedBox(
                  width: double.infinity,
                  height: 50,
                  child: ElevatedButton(
                    style: ElevatedButton.styleFrom(backgroundColor: AppTheme.mintAccent, foregroundColor: AppTheme.navyDark),
                    onPressed: () {
                      setState(() {}); 
                      Navigator.pop(ctx);
                    },
                    child: const Text('Áp dụng bộ lọc', style: TextStyle(fontWeight: FontWeight.bold)),
                  )
                )
              ],
            ),
          ),
        ),
      ),
    );
  }

  void _showTransactionDetails(TransactionModel tx) {
    showModalBottomSheet(
      context: context,
      backgroundColor: AppTheme.navyLight,
      isScrollControlled: true, 
      shape: const RoundedRectangleBorder(borderRadius: BorderRadius.vertical(top: Radius.circular(24))),
      builder: (ctx) => SafeArea(
        child: Padding(
          padding: const EdgeInsets.fromLTRB(24, 24, 24, 16), 
          child: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min, 
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Container(
                      padding: const EdgeInsets.all(12),
                      decoration: BoxDecoration(color: tx.color.withValues(alpha: 0.15), borderRadius: BorderRadius.circular(12)),
                      child: Icon(tx.icon, color: tx.color, size: 28),
                    ),
                    const SizedBox(width: 16),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(tx.title, style: const TextStyle(color: AppTheme.textWhite, fontSize: 18, fontWeight: FontWeight.bold)),
                          Text(tx.category, style: const TextStyle(color: AppTheme.textGray, fontSize: 14)),
                        ],
                      ),
                    ),
                    IconButton(onPressed: () => Navigator.pop(ctx), icon: const Icon(Icons.close, color: AppTheme.textGray)),
                  ],
                ),
                const SizedBox(height: 24),
                
                _buildDetailRow('Số tiền:', '${tx.isExpense ? "-" : "+"}${_fmtCur.format(tx.amount)}', tx.isExpense ? AppTheme.dangerColor : AppTheme.positive),
                const SizedBox(height: 12),
                _buildDetailRow('Thời gian:', DateFormat('dd/MM/yyyy - HH:mm').format(tx.date), AppTheme.textWhite),
                if (tx.receiptStatus != null) ...[
                  const SizedBox(height: 12),
                  // A Wrap, not a Row: on a narrow screen or with a large font the badge drops to its own line instead of
                  // overflowing. The full-width box keeps the label left and the badge right while both fit on one line.
                  SizedBox(
                    width: double.infinity,
                    child: Wrap(
                      alignment: WrapAlignment.spaceBetween,
                      crossAxisAlignment: WrapCrossAlignment.center,
                      spacing: 8,
                      runSpacing: 6,
                      children: [
                        const Text('Trạng thái hóa đơn:', style: TextStyle(color: AppTheme.textGray, fontSize: 15)),
                        ReceiptStatusBadge(status: tx.receiptStatus!),
                      ],
                    ),
                  ),
                ],
                if (tx.isUnsynced) ...[
                  const SizedBox(height: 12),
                  SizedBox(
                    width: double.infinity,
                    child: Wrap(
                      alignment: WrapAlignment.spaceBetween,
                      crossAxisAlignment: WrapCrossAlignment.center,
                      spacing: 8,
                      runSpacing: 6,
                      children: [
                        const Text('Đồng bộ:', style: TextStyle(color: AppTheme.textGray, fontSize: 15)),
                        SyncStatusBadge(status: tx.syncStatus),
                      ],
                    ),
                  ),
                  const SizedBox(height: 8),
                  const Text(
                    'Giao dịch chưa được máy chủ xác nhận. Khi đã có mạng, bấm "Gửi lại". Gửi lại sẽ không tạo giao dịch trùng.',
                    style: TextStyle(color: AppTheme.textGray, fontSize: 13),
                  ),
                ],
                if (tx.imageUrl != null && tx.imageUrl!.isNotEmpty) ...[
                  const SizedBox(height: 16),
                  const Text('Ảnh hóa đơn gốc:', style: TextStyle(color: AppTheme.textGray, fontSize: 14)),
                  const SizedBox(height: 8),
                  GestureDetector(
                    onTap: () {
                      Navigator.push(
                        context,
                        MaterialPageRoute(
                          builder: (_) => Scaffold(
                            backgroundColor: Colors.black,
                            appBar: AppBar(backgroundColor: Colors.black, iconTheme: const IconThemeData(color: Colors.white)),
                            body: Center(child: InteractiveViewer(child: Image.network(tx.imageUrl!))), 
                          ),
                        ),
                      );
                    },
                    child: ClipRRect(
                      borderRadius: BorderRadius.circular(12),
                      child: Container(
                        color: AppTheme.navyDark,
                        width: double.infinity,
                        height: 200, 
                        child: Image.network(
                          tx.imageUrl!,
                          fit: BoxFit.contain, 
                          loadingBuilder: (context, child, loadingProgress) {
                            if (loadingProgress == null) return child;
                            return const Center(child: CircularProgressIndicator(color: AppTheme.mintAccent));
                          },
                        ),
                      ),
                    ),
                  ),
                ],
                
                const SizedBox(height: 24),
                // The server has no copy of an unsynced transaction, so the only thing to do with it is to send it again.
                if (tx.isUnsynced) ...[
                  SizedBox(
                    width: double.infinity,
                    height: 50,
                    child: ElevatedButton.icon(
                      style: ElevatedButton.styleFrom(
                        backgroundColor: AppTheme.mintAccent,
                        foregroundColor: Colors.white,
                        elevation: 0,
                      ),
                      icon: const Icon(Icons.cloud_upload_rounded),
                      label: const Text('Gửi lại', style: TextStyle(fontWeight: FontWeight.bold)),
                      onPressed: () {
                        Navigator.pop(ctx);
                        _resendTransactionAction(tx);
                      },
                    ),
                  ),
                  const SizedBox(height: 12),
                ],
                // Editing goes through the server; offline there is no edit form, so the button is not offered.
                if (AppConfig.isFull && tx.id != null) ...[
                  SizedBox(
                    width: double.infinity,
                    height: 50,
                    child: ElevatedButton.icon(
                      style: ElevatedButton.styleFrom(
                        backgroundColor: AppTheme.mintAccent,
                        foregroundColor: Colors.white,
                        elevation: 0,
                      ),
                      icon: const Icon(Icons.edit_rounded),
                      label: const Text('Sửa giao dịch', style: TextStyle(fontWeight: FontWeight.bold)),
                      onPressed: () {
                        Navigator.pop(ctx);
                        _editTransactionAction(tx);
                      },
                    ),
                  ),
                  const SizedBox(height: 12),
                ],
                SizedBox(
                  width: double.infinity,
                  height: 50,
                  child: ElevatedButton.icon(
                    style: ElevatedButton.styleFrom(
                      backgroundColor: AppTheme.dangerColor.withValues(alpha: 0.1),
                      foregroundColor: AppTheme.dangerColor,
                      elevation: 0,
                    ),
                    icon: const Icon(Icons.delete_rounded),
                    label: const Text('Xóa giao dịch này', style: TextStyle(fontWeight: FontWeight.bold)),
                    onPressed: () {
                      Navigator.pop(ctx);
                      if (tx.isUnsynced) {
                        _deleteUnsyncedAction(tx);
                      } else {
                        _deleteTransactionAction(tx.id!);
                      }
                    },
                  ),
                ),
                const SizedBox(height: 8),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildDetailRow(String label, String value, Color valColor) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        Text(label, style: const TextStyle(color: AppTheme.textGray, fontSize: 15)),
        Text(value, style: TextStyle(color: valColor, fontSize: 16, fontWeight: FontWeight.bold)),
      ],
    );
  }

  Future<void> _editTransactionAction(TransactionModel tx) async {
    if (!mounted) return;
    final saved = await Navigator.push<bool>(
      context,
      MaterialPageRoute(builder: (_) => EditTransactionPage(transaction: tx)),
    );
    if (saved != true || !mounted) return;
    await _load();
  }

  Future<void> _deleteTransactionAction(String id) async {
    final confirm = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: AppTheme.navyDark,
        title: const Text('Xóa giao dịch?', style: TextStyle(color: AppTheme.textWhite, fontWeight: FontWeight.bold)),
        content: const Text('Hành động này sẽ hoàn lại tiền vào ví và không thể khôi phục. Bạn chắc chứ?', style: TextStyle(color: AppTheme.textGray)),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Hủy', style: TextStyle(color: AppTheme.textGray))),
          ElevatedButton(
            style: ElevatedButton.styleFrom(backgroundColor: AppTheme.dangerColor),
            onPressed: () => Navigator.pop(ctx, true), 
            child: const Text('Xóa ngay', style: TextStyle(color: Colors.white, fontWeight: FontWeight.bold))
          ),
        ],
      )
    );

    if (confirm != true) return;

    final success = await RemoteApiHelper.instance.deleteTransaction(id);
    if (success) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Đã xóa giao dịch và hoàn tiền')));
        _load(); 
      }
    } else {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Xóa thất bại, vui lòng thử lại')));
    }
  }

  /// Sends a transaction that the server has not confirmed again. It carries the clientId it was created with, so when
  /// the first send did arrive the server answers with that transaction instead of creating a second one.
  Future<void> _resendTransactionAction(TransactionModel tx) async {
    if (!mounted) return;
    final userId = SessionService.currentUserId;
    if (userId == null) {
      await AppDialog.error(context, title: 'Phiên đăng nhập hết hạn', message: 'Vui lòng đăng nhập lại.');
      return;
    }
    final clientId = tx.clientId;
    if (clientId == null || !_resendingClientIds.add(clientId)) return;

    SyncStatus? outcome;
    String? failure;
    try {
      outcome = await AppRepository.instance.resendTransaction(userId, tx);
    } on TransactionRefusedException catch (e) {
      failure = e.message;
    } catch (e) {
      debugPrint('Lỗi gửi lại giao dịch: $e');
      failure = 'Không gửi lại được giao dịch. Vui lòng thử lại.';
    } finally {
      _resendingClientIds.remove(clientId);
    }
    if (!mounted) return;

    if (failure != null) {
      await AppDialog.error(context, title: 'Gửi lại thất bại', message: failure);
    } else if (outcome == SyncStatus.synced) {
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Đã gửi giao dịch lên máy chủ')));
    } else {
      await AppDialog.warning(
        context,
        title: 'Vẫn chưa gửi được',
        message: 'Chưa kết nối được máy chủ. Giao dịch vẫn được giữ trên thiết bị, bạn có thể gửi lại sau.',
      );
    }
    if (!mounted) return;
    await _load();
  }

  /// Removes a transaction that the server never confirmed from the device only: there is nothing on the server to
  /// delete, and its local row number is not a server id.
  Future<void> _deleteUnsyncedAction(TransactionModel tx) async {
    if (!mounted) return;
    final userId = SessionService.currentUserId;
    final clientId = tx.clientId;
    if (userId == null || clientId == null) return;

    final confirmed = await AppDialog.confirm(
      context,
      title: 'Xóa giao dịch chưa gửi?',
      message: 'Giao dịch này chưa được máy chủ xác nhận. Xóa khỏi thiết bị và không gửi nữa? Hành động này không thể khôi phục.',
      confirmText: 'Xóa ngay',
      danger: true,
    );
    if (!confirmed || !mounted) return;

    await AppRepository.instance.deleteUnsyncedTransaction(userId, clientId);
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Đã xóa giao dịch chưa gửi')));
    await _load();
  }

  Widget _buildLoadFailedNotice() {
    return Container(
      margin: const EdgeInsets.only(bottom: 16),
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: AppTheme.warningColor.withValues(alpha: 0.12),
        borderRadius: BorderRadius.circular(12),
      ),
      child: const Row(
        children: [
          Icon(Icons.cloud_off_rounded, color: AppTheme.warningColor, size: 20),
          SizedBox(width: 10),
          Expanded(
            child: Text(
              'Không tải được danh sách từ máy chủ. Chỉ hiển thị các giao dịch chưa gửi trên thiết bị. Kéo xuống để thử lại.',
              style: TextStyle(color: AppTheme.textPrimary, fontSize: 13),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildTypeOption(BuildContext ctx, String label, IconData icon,
      bool expense, bool income) {
    final selected = _showExpenses == expense && _showIncome == income;
    return ListTile(
      contentPadding: EdgeInsets.zero,
      leading: Icon(icon,
          color: selected ? AppTheme.primary : AppTheme.textGray),
      title: Text(label,
          style: TextStyle(
              color: selected ? AppTheme.primary : AppTheme.textPrimary,
              fontWeight: selected ? FontWeight.bold : FontWeight.normal)),
      trailing: selected
          ? const Icon(Icons.check_rounded, color: AppTheme.primary)
          : null,
      onTap: () {
        setState(() {
          _showExpenses = expense;
          _showIncome = income;
        });
        Navigator.pop(ctx);
      },
    );
  }

  Future<void> _exportToExcel() async {
    final monthName = DateFormat('MMMM yyyy', 'vi_VN').format(_selectedDate);

    var excel = Excel.createExcel();
    var sheet = excel['Chi tiêu tháng $monthName'];

    sheet.appendRow([
      TextCellValue('Ngày'),
      TextCellValue('Danh mục'),
      TextCellValue('Tiền chi'),
      TextCellValue('Tiền thu'),
    ]);

    double totalExpense = 0;
    double totalIncome = 0;

    final monthTxs = _transactions.where((tx) =>
        tx.date.year == _selectedDate.year &&
        tx.date.month == _selectedDate.month).toList();

    for (var tx in monthTxs) {
      final dateStr = DateFormat('dd/MM/yyyy').format(tx.date);
      if (tx.isExpense) {
        totalExpense += tx.amount;
        sheet.appendRow([
          TextCellValue(dateStr),
          TextCellValue(tx.category),
          DoubleCellValue(tx.amount),
          TextCellValue(''),
        ]);
      } else {
        totalIncome += tx.amount;
        sheet.appendRow([
          TextCellValue(dateStr),
          TextCellValue(tx.category),
          TextCellValue(''),
          DoubleCellValue(tx.amount),
        ]);
      }
    }

    sheet.appendRow([]);
    sheet.appendRow([
      TextCellValue('Tổng cộng'),
      TextCellValue(''),
      DoubleCellValue(totalExpense),
      DoubleCellValue(totalIncome),
    ]);
    sheet.appendRow([
      TextCellValue('Số dư'),
      TextCellValue(''),
      TextCellValue(''),
      DoubleCellValue(totalIncome - totalExpense),
    ]);

    sheet.appendRow([]);
    sheet.appendRow([TextCellValue('NGÂN SÁCH THÁNG $monthName')]);
    sheet.appendRow([
      TextCellValue('Danh mục'),
      TextCellValue('Giới hạn'),
      TextCellValue('Đã tiêu'),
      TextCellValue('Còn lại'),
    ]);

    final monthStart = DateTime(_selectedDate.year, _selectedDate.month, 1);
    final monthEnd = DateTime(_selectedDate.year, _selectedDate.month + 1, 0);
    for (final progress in BudgetProgress.overlapping(monthStart, monthEnd, _budgets, _transactions)) {
      sheet.appendRow([
        TextCellValue(progress.budget.categoryName ?? BudgetModel.allCategoriesLabel),
        DoubleCellValue(progress.limit),
        DoubleCellValue(progress.spent),
        DoubleCellValue(progress.remaining),
      ]);
    }

    final directory = Directory.systemTemp;
    final fileName =
        'chi_tieu_$monthName${DateFormat('ddMMyyyy').format(DateTime.now())}.xlsx';
    final file = File('${directory.path}/$fileName');
    final bytes = excel.encode();
    if (bytes != null) {
      await file.writeAsBytes(bytes);
      if (!mounted) return;
      await SharePlus.instance.share(
        ShareParams(
          files: [XFile(file.path)],
          fileNameOverrides: [fileName],
          subject: 'Chi tiêu tháng $monthName',
          text: 'Báo cáo chi tiêu tháng $monthName',
        ),
      );
    }
  }
}

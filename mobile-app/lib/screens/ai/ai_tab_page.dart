import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:fl_chart/fl_chart.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:finance_ai_app/config/app_config.dart';
import 'package:finance_ai_app/core/theme/app_theme.dart';
import 'package:finance_ai_app/models/transaction_model.dart';
import 'package:finance_ai_app/services/remote_api_helper.dart';
import 'package:finance_ai_app/services/session_service.dart';
import 'package:finance_ai_app/services/app_repository.dart';

class AITabPage extends StatefulWidget {
  const AITabPage({super.key});

  @override
  State<AITabPage> createState() => _AITabPageState();
}

class _AITabPageState extends State<AITabPage> {
  bool _isLoading = true;
  List<TransactionModel> _transactions = [];
  List<Map<String, dynamic>> _spadePatterns = [];
  List<Map<String, dynamic>> _savingsGoals = [];
  bool _spadeLoading = false;
  Map<String, dynamic>? _budgetAdvice;
  bool _adviceLoading = false;
  List<Map<String, dynamic>> _budgets = [];

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    final userId = SessionService.currentUserId;
    if (userId == null) {
      setState(() => _isLoading = false);
      return;
    }
    List<TransactionModel> txs = [];
    try {
      txs = await AppRepository.instance.getTransactions(userId);
    } catch (_) {}
    
    final prefs = await SharedPreferences.getInstance();
    List<Map<String, dynamic>> goals = [];
    try {
      final raw = prefs.getString('savings_goals') ?? '[]';
      goals = (jsonDecode(raw) as List).cast<Map<String, dynamic>>();
    } catch (_) {}
    
    if (!mounted) return;
    setState(() {
      _transactions = txs;
      _savingsGoals = goals;
      _isLoading = false;
    });
    
    _loadSpadePatterns(txs);
    _loadBudgetAdvice(txs);
    _loadBudgets();
  }

  Future<void> _loadBudgets() async {
   if (mounted) setState(() => _budgets = []);
  }

  Map<String, dynamic>? _pickUrgentGoal() {
    final active = _savingsGoals.where((g) {
      final target = ((g['target'] as num?) ?? 0).toDouble();
      final current = ((g['current'] as num?) ?? 0).toDouble();
      return target > current;
    }).toList();
    if (active.isEmpty) return null;
    final now = DateTime.now();
    Map<String, dynamic>? urgent;
    int minDays = 999999;
    for (final g in active) {
      final days = _daysLeftOf(g, now);
      if (days != null && days > 0 && days < minDays) {
        minDays = days;
        urgent = g;
      }
    }
    return urgent ?? active.first;
  }

  int? _daysLeftOf(Map<String, dynamic> g, DateTime now) {
    final dl = g['deadline']?.toString() ?? '';
    if (dl.isEmpty) return null;
    try {
      final p = dl.split('/');
      final d = DateTime(int.parse(p[2]), int.parse(p[1]), int.parse(p[0]));
      return d.difference(now).inDays;
    } catch (_) {
      return null;
    }
  }

  Future<void> _loadBudgetAdvice(List<TransactionModel> txs) async {  
    final goal = _pickUrgentGoal();
    if (goal == null) return;
    
    setState(() => _adviceLoading = true);
    try {
      final data = txs.map((t) => {
        'category': t.category,
        'amount': t.amount,
        'date': t.date.toIso8601String(),
        'isExpense': t.isExpense,
      }).toList();
      
      final target = ((goal['target'] as num?) ?? 0).toDouble();
      final current = ((goal['current'] as num?) ?? 0).toDouble();
      
      final days = _daysLeftOf(goal, DateTime.now());
      final monthsLeft = (days != null && days > 0) ? (days / 30.0).ceil() : 1; 
      
      final advice = await RemoteApiHelper.instance.recommendBudget(
        transactions: data,
        target: target,
        current: current,
        monthsLeft: monthsLeft,
      );
      if (mounted) setState(() => _budgetAdvice = advice);
    } catch (_) {
    } finally {
      if (mounted) setState(() => _adviceLoading = false);
    }
  }

  Future<void> _loadSpadePatterns(List<TransactionModel> txs) async {
    setState(() => _spadeLoading = true);
    try {
      if (AppConfig.isFull) {
        final data = txs.map((t) => {
          'category': t.category,
          'amount': t.amount,
          'date': t.date.toIso8601String(),
          'isExpense': t.isExpense,
        }).toList();
        final patterns = await RemoteApiHelper.instance.analyzePatterns(data);
        if (mounted) setState(() => _spadePatterns = patterns);
      } else {
        final patterns = _computeLocalPatterns(txs);
        if (mounted) setState(() => _spadePatterns = patterns);
      }
    } catch (_) {
    } finally {
      if (mounted) setState(() => _spadeLoading = false);
    }
  }

  List<Map<String, dynamic>> _computeLocalPatterns(List<TransactionModel> txs) {
    final expTxs = txs.where((t) => t.isExpense).toList()
      ..sort((a, b) => a.date.compareTo(b.date));

    final Map<String, int> pairCount = {};
    final Map<String, double> pairWeight = {};
    final totalAmount = expTxs.fold(0.0, (s, t) => s + t.amount);

    for (int i = 0; i < expTxs.length - 1; i++) {
      final a = expTxs[i].category;
      final b = expTxs[i + 1].category;
      final diff = expTxs[i + 1].date.difference(expTxs[i].date).inDays;
      if (diff <= 7 && a != b) {
        final key = '$a → $b';
        pairCount[key] = (pairCount[key] ?? 0) + 1;
        final w = totalAmount > 0
            ? (expTxs[i].amount + expTxs[i + 1].amount) / totalAmount
            : 0.0;
        pairWeight[key] = (pairWeight[key] ?? 0.0) + w;
      }
    }

    final entries = pairCount.entries.toList()
      ..sort((a, b) => (pairWeight[b.key] ?? 0).compareTo(pairWeight[a.key] ?? 0));

    return entries.take(5).map((e) => {
      'pattern': e.key,
      'count': e.value,
      'wsupport': ((pairWeight[e.key] ?? 0) * 100).toStringAsFixed(1) + '%',
    }).toList();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.transparent,
      appBar: AppBar(
        title: const Text(
          'Phân tích AI',
          style: TextStyle(fontWeight: FontWeight.bold, fontSize: 22),
        ),
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh_rounded, color: AppTheme.mintAccent),
            onPressed: _load,
          ),
        ],
      ),
      body: _isLoading
          ? const Center(
              child: CircularProgressIndicator(color: AppTheme.mintAccent),
            )
          : RefreshIndicator(
              onRefresh: _load,
              color: AppTheme.mintAccent,
              backgroundColor: AppTheme.navyLight,
              child: _buildContent(),
            ),
    );
  }

  Widget _buildContent() {
    if (_transactions.isEmpty) {
      return ListView(
        padding: const EdgeInsets.all(20),
        children: [
          _buildInsightCard(
            icon: Icons.ac_unit_rounded, 
            iconColor: Colors.blueAccent,
            title: 'Bạn chưa nhập chi tiêu.',
            subtitle: 'Hãy nhập giao dịch thực tế để hệ thống WCM-SPADE khai phá quy luật và gợi ý cho bạn nhé!',
          ),
          const SizedBox(height: 24),
          _buildSpadeSection(),
          const SizedBox(height: 32),
          _buildBudgetAdviceSection(),
          const SizedBox(height: 100),
        ],
      );
    }
    return ListView(
      padding: const EdgeInsets.all(20),
      children: [
        _buildSafeToSpendCard(),
        const SizedBox(height: 16),
        _buildInsightCard(
          icon: Icons.trending_up_rounded,
          iconColor: AppTheme.mintAccent,
          title: 'Xu hướng chi tiêu',
          subtitle: _getSpendingTrend(),
        ),
        const SizedBox(height: 16),
        _buildInsightCard(
          icon: Icons.category_rounded,
          iconColor: Colors.orange,
          title: 'Danh mục chi tiêu nhiều nhất',
          subtitle: _getTopCategory(),
        ),
        const SizedBox(height: 16),
        _buildInsightCard(
          icon: Icons.savings_rounded,
          iconColor: Colors.amber,
          title: 'Mẹo tiết kiệm',
          subtitle: _getSavingTip(),
        ),
        const SizedBox(height: 16),
        _buildInsightCard(
          icon: Icons.water_drop_rounded,
          iconColor: Colors.blue,
          title: 'So sánh tuần',
          subtitle: _getWeeklyComparison(),
        ),
        const SizedBox(height: 24),
        const Text(
          'Biểu đồ thu chi theo tuần',
          style: TextStyle(
            color: AppTheme.textWhite,
            fontSize: 18,
            fontWeight: FontWeight.bold,
          ),
        ),
        const SizedBox(height: 16),
        _buildWeeklyChart(),
        const SizedBox(height: 24),
        _build503020Card(),
        const SizedBox(height: 24),
        _buildBudgetGuardSection(),
        const SizedBox(height: 32),
        _buildSpadeSection(),
        const SizedBox(height: 32),
        _buildBudgetAdviceSection(),
        const SizedBox(height: 100),
      ],
    );
  }

  Widget _buildBudgetAdviceSection() {
    if (_adviceLoading) {
      return const Center(child: CircularProgressIndicator(color: AppTheme.primary));
    }
    final advice = _budgetAdvice;
    if (advice == null) return const SizedBox.shrink();
    final recs = (advice['recommendations'] as List?) ?? [];
    final summary = advice['summary']?.toString() ?? '';
    if (summary.isEmpty && recs.isEmpty) return const SizedBox.shrink();

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            const Text(
              'Gợi ý phân bổ tiền',
              style: TextStyle(
                color: AppTheme.textWhite,
                fontSize: 18,
                fontWeight: FontWeight.bold,
              ),
            ),
            const SizedBox(width: 8),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
              decoration: BoxDecoration(
                color: Colors.amber.withValues(alpha: 0.2),
                borderRadius: BorderRadius.circular(8),
                border: Border.all(color: Colors.amber.withValues(alpha: 0.4)),
              ),
              child: const Text('WCM-SPADE',
                  style: TextStyle(color: Colors.amber, fontSize: 11, fontWeight: FontWeight.bold)),
            ),
          ],
        ),
        const SizedBox(height: 8),
        const Text(
          'Đề xuất cắt/giữ từng mục để đạt mục tiêu tiết kiệm',
          style: TextStyle(color: AppTheme.textGray, fontSize: 13),
        ),
        const SizedBox(height: 16),
        if (summary.isNotEmpty)
          Container(
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              color: AppTheme.navyLight,
              borderRadius: BorderRadius.circular(16),
              border: Border.all(color: Colors.amber.withValues(alpha: 0.25)),
            ),
            child: Row(
              children: [
                const Icon(Icons.tips_and_updates_rounded, color: Colors.amber, size: 24),
                const SizedBox(width: 12),
                Expanded(
                  child: Text(
                    summary,
                    style: const TextStyle(color: AppTheme.textWhite, fontSize: 13, height: 1.4),
                  ),
                ),
              ],
            ),
          ),
        if (recs.isNotEmpty) const SizedBox(height: 12),
        ...recs.map((r) {
          final m = Map<String, dynamic>.from(r as Map);
          final tag = m['tag']?.toString() ?? '';
          final action = m['action']?.toString() ?? '';
          final current = ((m['current'] as num?) ?? 0).toDouble();
          final suggested = ((m['suggested'] as num?) ?? 0).toDouble();
          final delta = ((m['delta'] as num?) ?? 0).toDouble();
          final reason = m['reason']?.toString() ?? '';
          final isCut = delta < 0;
          return Padding(
            padding: const EdgeInsets.only(bottom: 12),
            child: Container(
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: AppTheme.navyLight,
                borderRadius: BorderRadius.circular(16),
                border: Border.all(
                    color: (isCut ? Colors.redAccent : AppTheme.mintAccent).withValues(alpha: 0.25)),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Icon(
                        isCut ? Icons.trending_down_rounded : Icons.trending_up_rounded,
                        color: isCut ? Colors.redAccent : AppTheme.mintAccent,
                        size: 22,
                      ),
                      const SizedBox(width: 10),
                      Expanded(
                        child: Text(
                          '$action · $tag',
                          style: const TextStyle(
                            color: AppTheme.textWhite,
                            fontWeight: FontWeight.bold,
                            fontSize: 15,
                          ),
                        ),
                      ),
                      Text(
                        '${delta > 0 ? '+' : ''}${_fmt(delta)}',
                        style: TextStyle(
                          color: isCut ? Colors.redAccent : AppTheme.mintAccent,
                          fontWeight: FontWeight.bold,
                          fontSize: 14,
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 6),
                  Text(
                    '${_fmt(current)} → ${_fmt(suggested)} / tháng',
                    style: const TextStyle(color: AppTheme.textGray, fontSize: 13),
                  ),
                  if (reason.isNotEmpty) ...[
                    const SizedBox(height: 4),
                    Text(reason,
                        style: const TextStyle(color: AppTheme.textGray, fontSize: 12)),
                  ],
                ],
              ),
            ),
          );
        }),
      ],
    );
  }

  Widget _buildSpadeSection() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            const Text(
              'Mẫu Chi Tiêu Tuần Tự',
              style: TextStyle(
                color: AppTheme.textWhite,
                fontSize: 18,
                fontWeight: FontWeight.bold,
              ),
            ),
            const SizedBox(width: 8),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
              decoration: BoxDecoration(
                color: AppTheme.primary.withValues(alpha: 0.2),
                borderRadius: BorderRadius.circular(8),
                border: Border.all(color: AppTheme.primary.withValues(alpha: 0.4)),
              ),
              child: const Text('WCM-SPADE',
                  style: TextStyle(color: AppTheme.primary, fontSize: 11, fontWeight: FontWeight.bold)),
            ),
          ],
        ),
        const SizedBox(height: 8),
        const Text(
          'Hành vi chi tiêu tuần tự thường gặp của bạn',
          style: TextStyle(color: AppTheme.textGray, fontSize: 13),
        ),
        const SizedBox(height: 16),
        if (_spadeLoading)
          const Center(child: CircularProgressIndicator(color: AppTheme.primary))
        else if (_spadePatterns.isEmpty)
          _buildInsightCard(
            icon: Icons.auto_graph_rounded,
            iconColor: AppTheme.primary,
            title: 'Chưa đủ dữ liệu',
            subtitle: 'Thêm ít nhất 3 giao dịch để xem mẫu chi tiêu tuần tự.',
          )
        else
          ...List.generate(_spadePatterns.length, (i) {
            final p = _spadePatterns[i];
            return Padding(
              padding: const EdgeInsets.only(bottom: 12),
              child: Container(
                padding: const EdgeInsets.all(16),
                decoration: BoxDecoration(
                  color: AppTheme.navyLight,
                  borderRadius: BorderRadius.circular(16),
                  border: Border.all(color: AppTheme.primary.withValues(alpha: 0.2)),
                ),
                child: Row(
                  children: [
                    Container(
                      width: 36,
                      height: 36,
                      decoration: BoxDecoration(
                        color: AppTheme.primary.withValues(alpha: 0.15),
                        borderRadius: BorderRadius.circular(10),
                      ),
                      child: Center(
                        child: Text(
                          '#${i + 1}',
                          style: const TextStyle(
                            color: AppTheme.primary,
                            fontWeight: FontWeight.bold,
                            fontSize: 13,
                          ),
                        ),
                      ),
                    ),
                    const SizedBox(width: 14),
                  Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            _formatAiPattern(p['pattern']?.toString() ?? ''),
                            style: const TextStyle(
                              color: AppTheme.textWhite,
                              fontWeight: FontWeight.w500,
                              fontSize: 15,
                            ),
                          ),
                          const SizedBox(height: 4),
                          Text(
                            'Tần suất: ${p['count']} lần  ·  Độ hỗ trợ (W-Sup): ${p['wsupport']}',
                            style: const TextStyle(
                              color: AppTheme.mintAccent, 
                              fontSize: 12,
                              fontStyle: FontStyle.italic,
                            ),
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
              ),
            );
          }),
      ],
    );
  }

  Widget _buildSafeToSpendCard() {
    final now = DateTime.now();
    double income = 0, expense = 0;
    for (final tx in _transactions) {
      if (tx.date.year != now.year || tx.date.month != now.month) continue;
      if (tx.isExpense) {
        expense += tx.amount;
      } else {
        income += tx.amount;
      }
    }
    if (income <= 0) {
      return _buildInsightCard(
        icon: Icons.account_balance_wallet_rounded,
        iconColor: AppTheme.mintAccent,
        title: 'Hôm nay tiêu được bao nhiêu?',
        subtitle:
            'Chưa ghi nhận thu nhập tháng này. Thêm khoản thu để tính số tiền an toàn mỗi ngày.',
      );
    }

    double goalNeed = 0;
    final goal = _pickUrgentGoal();
    if (goal != null) {
      final target = ((goal['target'] as num?) ?? 0).toDouble();
      final current = ((goal['current'] as num?) ?? 0).toDouble();
      final days = _daysLeftOf(goal, now);
      final monthsLeft = (days != null && days > 0) ? (days / 30.0).ceil() : 1;
      goalNeed = ((target - current) / monthsLeft).clamp(0, double.infinity);
    }

    final daysInMonth = DateTime(now.year, now.month + 1, 0).day;
    final daysLeft = (daysInMonth - now.day + 1).clamp(1, 31);
    final safePerDay = (income - expense - goalNeed) / daysLeft;
    final ok = safePerDay >= 0;
    final color = ok ? AppTheme.mintAccent : Colors.redAccent;

    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: AppTheme.navyLight,
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: color.withValues(alpha: 0.35)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(Icons.account_balance_wallet_rounded, color: color, size: 22),
              const SizedBox(width: 8),
              const Text('Hôm nay tiêu được bao nhiêu?',
                  style: TextStyle(
                      color: AppTheme.textWhite, fontWeight: FontWeight.bold, fontSize: 16)),
            ],
          ),
          const SizedBox(height: 10),
          Text(
            '${_fmt(safePerDay)} / ngày',
            style: TextStyle(color: color, fontSize: 26, fontWeight: FontWeight.bold),
          ),
          const SizedBox(height: 6),
          Text(
            ok
                ? 'Thu ${_fmt(income)} − đã chi ${_fmt(expense)}'
                    '${goalNeed > 0 ? ' − để dành ${_fmt(goalNeed)} cho mục tiêu' : ''}, '
                    'chia đều $daysLeft ngày còn lại của tháng.'
                : 'Bạn đã chi vượt phần an toàn của tháng'
                    '${goalNeed > 0 ? ' (sau khi trừ ${_fmt(goalNeed)} cho mục tiêu tiết kiệm)' : ''}. '
                    'Hạn chế chi tiêu không thiết yếu đến hết tháng.',
            style: const TextStyle(color: AppTheme.textGray, fontSize: 13, height: 1.4),
          ),
        ],
      ),
    );
  }

  static const Set<String> _needsCategories = {'Ăn uống', 'Di chuyển', 'Siêu thị', 'Sinh hoạt'};

  Widget _build503020Card() {
    final now = DateTime.now();
    double income = 0, needs = 0, wants = 0;
    for (final tx in _transactions) {
      if (tx.date.year != now.year || tx.date.month != now.month) continue;
      if (!tx.isExpense) {
        income += tx.amount;
        continue;
      }
      if (_needsCategories.contains(tx.category)) {
        needs += tx.amount;
      } else {
        wants += tx.amount;
      }
    }
    if (income <= 0) return const SizedBox.shrink();

    final savings = income - needs - wants;
    final needsPct = needs / income * 100;
    final wantsPct = wants / income * 100;
    final savePct = savings / income * 100;

    Widget row(String label, double pct, double targetPct, Color c, {bool atLeast = false}) {
      final over = atLeast ? pct < targetPct : pct > targetPct;
      return Padding(
        padding: const EdgeInsets.only(bottom: 10),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Expanded(
                  child: Text(label,
                      style: const TextStyle(color: AppTheme.textWhite, fontSize: 13)),
                ),
                Text(
                  '${pct.toStringAsFixed(0)}% / ${atLeast ? '≥' : '≤'}${targetPct.toStringAsFixed(0)}%',
                  style: TextStyle(
                      color: over ? Colors.redAccent : c,
                      fontSize: 12,
                      fontWeight: FontWeight.bold),
                ),
              ],
            ),
            const SizedBox(height: 4),
            ClipRRect(
              borderRadius: BorderRadius.circular(4),
              child: LinearProgressIndicator(
                value: (pct / 100).clamp(0.0, 1.0),
                minHeight: 6,
                backgroundColor: AppTheme.navyDark,
                valueColor: AlwaysStoppedAnimation(over ? Colors.redAccent : c),
              ),
            ),
          ],
        ),
      );
    }

    String advice;
    if (needsPct > 50 && needsPct >= wantsPct) {
      advice =
          'Nhóm thiết yếu đang chiếm ${needsPct.toStringAsFixed(0)}% thu nhập (chuẩn ≤50%). Rà lại Sinh hoạt/Siêu thị xem có khoản nào dồn được.';
    } else if (wantsPct > 30) {
      advice =
          'Nhóm mong muốn (Giải trí, Khác) chiếm ${wantsPct.toStringAsFixed(0)}% thu nhập (chuẩn ≤30%) — đây là chỗ dễ cắt nhất.';
    } else if (savePct < 20) {
      advice =
          'Bạn mới để dành được ${savePct.toStringAsFixed(0)}% thu nhập (chuẩn ≥20%). Tăng dần mỗi tháng một chút.';
    } else {
      advice = 'Cơ cấu chi tiêu tháng này đạt chuẩn 50/30/20. Giữ nhịp này!';
    }

    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AppTheme.navyLight,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: Colors.blueAccent.withValues(alpha: 0.25)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text('Sức khỏe chi tiêu — quy tắc 50/30/20',
              style: TextStyle(
                  color: AppTheme.textWhite, fontWeight: FontWeight.bold, fontSize: 15)),
          const SizedBox(height: 4),
          const Text('Thiết yếu: Ăn uống, Di chuyển, Siêu thị, Sinh hoạt · Mong muốn: Giải trí, Khác',
              style: TextStyle(color: AppTheme.textGray, fontSize: 11)),
          const SizedBox(height: 12),
          row('Thiết yếu', needsPct, 50, Colors.blueAccent),
          row('Mong muốn', wantsPct, 30, Colors.orangeAccent),
          row('Tiết kiệm (phần dư)', savePct, 20, AppTheme.mintAccent, atLeast: true),
          const SizedBox(height: 2),
          Text(advice,
              style: const TextStyle(color: AppTheme.textGray, fontSize: 12, height: 1.4)),
        ],
      ),
    );
  }

  Widget _buildBudgetGuardSection() {
    final now = DateTime.now();
    final recentCats = <String>{};

    for (final tx in _transactions) {
      if (tx.isExpense && now.difference(tx.date).inDays < 7) {
        recentCats.add(tx.category);
      }
    }

    final spadeWarnings = <Widget>[];
    final warnedCons = <String>{};

    for (final p in _spadePatterns) {
      if (spadeWarnings.length >= 2) break; 

      final parts = (p['pattern']?.toString() ?? '').split(' → ');
      if (parts.length < 2) continue;
      
      final antes = parts.first.split('+').map((s) => s.trim()).toList();
      final cons = parts.last.split('+').first.trim();
      
      final hitAnte = antes.firstWhere((a) => recentCats.contains(a), orElse: () => '');
      if (hitAnte.isEmpty || cons.isEmpty || cons == hitAnte) continue;
      if (!warnedCons.add(cons)) continue;

      String txt = 'Thuật toán WCM-SPADE phát hiện luật [$hitAnte → $cons]: '
                   'Sau khi chi tiền cho $hitAnte, bạn có xu hướng sẽ chi cho $cons trong vài ngày tới. '
                   'Hãy chú ý kiểm soát hầu bao để không lạm lẹm vào khoản tiết kiệm!';

      spadeWarnings.add(Padding(
        padding: const EdgeInsets.only(bottom: 10),
        child: Container(
          padding: const EdgeInsets.all(14),
          decoration: BoxDecoration(
            color: AppTheme.navyLight,
            borderRadius: BorderRadius.circular(12),
            border: Border.all(color: AppTheme.primary.withValues(alpha: 0.35)),
          ),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Icon(Icons.psychology_alt_rounded, color: AppTheme.primary, size: 28),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text('Dự báo hành vi · Khai phá chuỗi',
                        style: TextStyle(color: AppTheme.primary, fontSize: 12, fontWeight: FontWeight.bold)),
                    const SizedBox(height: 6),
                    Text(txt, style: const TextStyle(color: AppTheme.textWhite, fontSize: 13, height: 1.4)),
                  ],
                ),
              ),
            ],
          ),
        ),
      ));
    }

    if (spadeWarnings.isEmpty) return const SizedBox.shrink();

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Text('Cảnh báo thói quen tương lai',
          style: TextStyle(color: AppTheme.textWhite, fontSize: 18, fontWeight: FontWeight.bold),
        ),
        const SizedBox(height: 8),
        const Text('AI dự đoán các khoản chi sắp tới dựa trên lịch sử tuần tự của bạn.',
          style: TextStyle(color: AppTheme.textGray, fontSize: 13),
        ),
        const SizedBox(height: 16),
        ...spadeWarnings,
      ],
    );
  }

  String _getSpendingTrend() {
    final now = DateTime.now();
    final thisMonth = _transactions.where((tx) =>
        tx.date.year == now.year && tx.date.month == now.month && tx.isExpense);
    final lastMonth = _transactions.where((tx) {
      final lm = DateTime(now.year, now.month - 1);
      return tx.date.year == lm.year && tx.date.month == lm.month && tx.isExpense;
    });

    final thisTotal = thisMonth.fold(0.0, (s, tx) => s + tx.amount);
    final lastTotal = lastMonth.fold(0.0, (s, tx) => s + tx.amount);

    if (thisTotal == 0 && lastTotal == 0) {
      return 'Chưa có đủ dữ liệu để so sánh.';
    }
    if (lastTotal == 0) return 'Tháng này bạn đã chi ${_fmt(thisTotal)}.';
    final diff = ((thisTotal - lastTotal) / lastTotal * 100);
    if (diff > 0) {
      return 'Chi tiêu tháng này tăng ${diff.abs().toStringAsFixed(1)}% so với tháng trước.';
    } else if (diff < 0) {
      return 'Chi tiêu tháng này giảm ${diff.abs().toStringAsFixed(1)}% so với tháng trước. Tốt lắm!';
    }
    return 'Chi tiêu tháng này ngang với tháng trước.';
  }

  String _getTopCategory() {
    final now = DateTime.now();
    final monthTxs = _transactions.where((tx) =>
        tx.date.year == now.year && tx.date.month == now.month && tx.isExpense);

    if (monthTxs.isEmpty) return 'Chưa có chi tiêu tháng này.';

    final Map<String, double> catTotals = {};
    for (var tx in monthTxs) {
      catTotals[tx.category] = (catTotals[tx.category] ?? 0) + tx.amount;
    }

    final sorted = catTotals.entries.toList()..sort((a, b) => b.value.compareTo(a.value));
    final top = sorted.first;
    final total = monthTxs.fold(0.0, (s, t) => s + t.amount);
    return '${top.key} chiếm ${_fmt(top.value)} (${(top.value / total * 100).toStringAsFixed(0)}% tổng chi).';
  }

  String _getSavingTip() {
    final now = DateTime.now();
    final monthIncome = _transactions
        .where((tx) => tx.date.year == now.year && tx.date.month == now.month && !tx.isExpense)
        .fold(0.0, (s, tx) => s + tx.amount);
    final monthExpense = _transactions
        .where((tx) => tx.date.year == now.year && tx.date.month == now.month && tx.isExpense)
        .fold(0.0, (s, tx) => s + tx.amount);
    final surplus = monthIncome - monthExpense;

    final active = _savingsGoals.where((g) {
      final target = ((g['target'] as num?) ?? 0).toDouble();
      final current = ((g['current'] as num?) ?? 0).toDouble();
      return target > current;
    }).toList();

    if (active.isEmpty) {
      if (_savingsGoals.isNotEmpty) {
        return 'Bạn đã hoàn thành tất cả mục tiêu tiết kiệm! Hãy đặt mục tiêu mới trong Sổ tiết kiệm.';
      }
      final total = monthExpense;
      if (surplus > 0) {
        return 'Tháng này bạn đang thặng dư ${_fmt(surplus)}. Hãy mở Sổ tiết kiệm và đặt mục tiêu để tận dụng khoản này!';
      }
      if (total > 0) {
        return 'Chi tiêu tháng này ${_fmt(total)}. Thêm mục tiêu trong Sổ tiết kiệm để có kế hoạch rõ ràng hơn.';
      }
      return 'Chưa có dữ liệu. Hãy thêm giao dịch và đặt mục tiêu trong Sổ tiết kiệm.';
    }

    Map<String, dynamic>? urgentGoal;
    int minDaysLeft = 999999;
    for (final g in active) {
      final dl = g['deadline']?.toString() ?? '';
      if (dl.isNotEmpty) {
        try {
          final parts = dl.split('/');
          final deadlineDate = DateTime(int.parse(parts[2]), int.parse(parts[1]), int.parse(parts[0]));
          final days = deadlineDate.difference(now).inDays;
          if (days > 0 && days < minDaysLeft) {
            minDaysLeft = days;
            urgentGoal = g;
          }
        } catch (_) {}
      }
    }
    urgentGoal ??= active.first;

    final title = urgentGoal['title']?.toString() ?? 'Mục tiêu';
    final target = ((urgentGoal['target'] as num?) ?? 0).toDouble();
    final current = ((urgentGoal['current'] as num?) ?? 0).toDouble();
    final remaining = target - current;

    if (minDaysLeft < 999999) {
      final monthsLeft = (minDaysLeft / 30.0).ceil().clamp(1, 999);
      final neededPerMonth = remaining / monthsLeft;
      if (surplus >= neededPerMonth) {
        return '"$title": cần thêm ${_fmt(remaining)} trong $monthsLeft tháng (${_fmt(neededPerMonth)}/tháng). Thặng dư hiện tại ${_fmt(surplus)}/tháng - bạn đang đi đúng hướng!';
      } else {
        final shortfall = neededPerMonth - surplus;
        return '"$title": cần ${_fmt(neededPerMonth)}/tháng để kịp hạn. Hiện thiếu ${_fmt(shortfall)}/tháng - hãy cắt giảm chi tiêu không cần thiết.';
      }
    }

    final suggested = (remaining * 0.1).clamp(10000.0, double.infinity);
    return '"$title": còn ${_fmt(remaining)} nữa. Hãy để dành ít nhất ${_fmt(suggested)}/tháng để đạt mục tiêu sớm hơn.';
  }

  String _getWeeklyComparison() {
    final now = DateTime.now();
    final firstDayOfMonth = DateTime(now.year, now.month, 1);
    final week2Start = firstDayOfMonth.add(const Duration(days: 7));
    final week3Start = week2Start.add(const Duration(days: 7));
    final week4Start = week3Start.add(const Duration(days: 7));

    double sumExpenses(DateTime start, DateTime end) {
      return _transactions
          .where((tx) =>
              tx.isExpense &&
              tx.date.isAfter(start.subtract(const Duration(seconds: 1))) &&
              tx.date.isBefore(end))
          .fold(0.0, (s, tx) => s + tx.amount);
    }

    final w1 = sumExpenses(firstDayOfMonth, week2Start);
    final w2 = sumExpenses(week2Start, week3Start);
    final w3 = sumExpenses(week3Start, week4Start);
    final w4 = sumExpenses(week4Start, DateTime(now.year, now.month + 1, 1));

    if (w1 == 0 && w2 == 0 && w3 == 0 && w4 == 0) {
      return 'Chưa có dữ liệu tuần này.';
    }

    final weeks = ['Tuần 1', 'Tuần 2', 'Tuần 3', 'Tuần 4'];
    final amounts = [w1, w2, w3, w4];
    final highest = amounts.indexOf(amounts.reduce((a, b) => a > b ? a : b));
    return '${weeks[highest]} có chi tiêu cao nhất: ${_fmt(amounts[highest])}. Chi tiêu ổn định.';
  }

  Widget _buildWeeklyChart() {
    final now = DateTime.now();
    final firstDayOfMonth = DateTime(now.year, now.month, 1);
    final week2Start = firstDayOfMonth.add(const Duration(days: 7));
    final week3Start = week2Start.add(const Duration(days: 7));
    final week4Start = week3Start.add(const Duration(days: 7));

    double sumExpenses(DateTime start, DateTime end) {
      return _transactions
          .where((tx) =>
              tx.isExpense &&
              tx.date.isAfter(start.subtract(const Duration(seconds: 1))) &&
              tx.date.isBefore(end))
          .fold(0.0, (s, tx) => s + tx.amount);
    }

    final weekExpenses = [
      sumExpenses(firstDayOfMonth, week2Start),
      sumExpenses(week2Start, week3Start),
      sumExpenses(week3Start, week4Start),
      sumExpenses(week4Start, DateTime(now.year, now.month + 1, 1)),
    ];

    final maxY = (weekExpenses.fold(100.0, (m, v) => v > m ? v : m) * 1.2).clamp(100.0, double.infinity);

    return Container(
      height: 200,
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: AppTheme.navyLight,
        borderRadius: BorderRadius.circular(24),
      ),
      child: BarChart(
        BarChartData(
          alignment: BarChartAlignment.spaceAround,
          borderData: FlBorderData(show: false),
          gridData: FlGridData(
            show: true, drawVerticalLine: false,
            horizontalInterval: maxY / 4,
            getDrawingHorizontalLine: (v) => FlLine(
              color: AppTheme.textGray.withValues(alpha: 0.1), strokeWidth: 1,
            ),
          ),
          titlesData: FlTitlesData(
            leftTitles: const AxisTitles(sideTitles: SideTitles(showTitles: false)),
            topTitles: const AxisTitles(sideTitles: SideTitles(showTitles: false)),
            rightTitles: const AxisTitles(sideTitles: SideTitles(showTitles: false)),
            bottomTitles: AxisTitles(
              sideTitles: SideTitles(
                showTitles: true,
                getTitlesWidget: (v, _) {
                  const weeks = ['W1', 'W2', 'W3', 'W4'];
                  return Padding(
                    padding: const EdgeInsets.only(top: 8),
                    child: Text(weeks[v.toInt()],
                        style: const TextStyle(color: AppTheme.textGray, fontSize: 12)),
                  );
                },
              ),
            ),
          ),
          barGroups: List.generate(4, (i) {
            return BarChartGroupData(
              x: i,
              barRods: [
                BarChartRodData(
                  toY: weekExpenses[i],
                  color: AppTheme.mintAccent,
                  width: 20,
                  borderRadius: BorderRadius.circular(6),
                  backDrawRodData: BackgroundBarChartRodData(
                    show: true, toY: maxY, color: AppTheme.navyDark,
                  ),
                ),
              ],
            );
          }),
        ),
      ),
    );
  }

  Widget _buildInsightCard({
    required IconData icon,
    required Color iconColor,
    required String title,
    required String subtitle,
  }) {
    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: AppTheme.navyLight,
        borderRadius: BorderRadius.circular(20),
      ),
      child: Row(
        children: [
          Container(
            padding: const EdgeInsets.all(14),
            decoration: BoxDecoration(
              color: iconColor.withValues(alpha: 0.15),
              borderRadius: BorderRadius.circular(16),
            ),
            child: Icon(icon, color: iconColor, size: 28),
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
                  subtitle,
                  style: const TextStyle(color: AppTheme.textGray, fontSize: 13),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  String _fmt(double amount) {
    return NumberFormat.currency(locale: 'vi_VN', symbol: 'đ', decimalDigits: 0)
        .format(amount);
  }
  
  String _formatAiPattern(String rawPattern) {
    Map<String, String> dict = {
      'an uong': 'Ăn uống', 'di lai': 'Đi lại', 'hoa don': 'Hóa đơn',
      'suc khoe': 'Sức khỏe', 'giao duc': 'Giáo dục', 'nha o': 'Nhà ở',
      'dien nuoc': 'Điện nước', 'internet': 'Internet', 'xang': 'Xăng',
      'y te': 'Y tế', 'du lich': 'Du lịch', 'giai tri': 'Giải trí',
      'mua sam': 'Mua sắm', 'ca phe': 'Cà phê', 'an vat': 'Ăn vặt',
      'lam dep': 'Làm đẹp', 'game': 'Game', 'shopping': 'Shopping',
      'quan ao': 'Quần áo', 'do choi': 'Đồ chơi', 'khac': 'Khác'
    };

    String result = rawPattern;
    dict.forEach((key, value) {
      result = result.replaceAll(key, value);
    });
    return result;
  }
}
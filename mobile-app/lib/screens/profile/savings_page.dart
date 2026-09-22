import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:finance_ai_app/core/theme/app_theme.dart';
import 'package:finance_ai_app/services/remote_api_helper.dart';
import 'package:finance_ai_app/services/session_service.dart';

class SavingsPage extends StatefulWidget {
  const SavingsPage({super.key});

  @override
  State<SavingsPage> createState() => _SavingsPageState();
}

class _SavingsPageState extends State<SavingsPage> {
  List<Map<String, dynamic>> _goals = [];
  bool _isLoading = true;

  final _fmtCur = NumberFormat.currency(
    locale: 'vi_VN',
    symbol: '₫',
    decimalDigits: 0,
  );

  @override
  void initState() {
    super.initState();
    Future.delayed(const Duration(milliseconds: 300), () {
      if (mounted) _load();
    });
  }

  Future<void> _load() async {
    try {
      final goals = await RemoteApiHelper.instance.fetchSavings();
      if (!mounted) return;
      setState(() {
        _goals = goals;
        _isLoading = false;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _goals = [];
        _isLoading = false;
      });
    }
  }

  double get _totalSaved =>
      _goals.fold(0.0, (s, g) => s + ((g['current'] as num?) ?? 0).toDouble());
  double get _totalTarget =>
      _goals.fold(0.0, (s, g) => s + ((g['target'] as num?) ?? 0).toDouble());

  void _addGoal() {
    final titleCtrl = TextEditingController();
    final targetCtrl = TextEditingController();
    final currentCtrl = TextEditingController();
    final deadlineCtrl = TextEditingController();

    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: AppTheme.navyLight,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
        title: const Text(
          'Thêm mục tiêu tiết kiệm',
          style: TextStyle(
            color: AppTheme.textWhite,
            fontWeight: FontWeight.bold,
          ),
        ),
        content: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              _field(titleCtrl, 'Tên mục tiêu', TextInputType.text),
              const SizedBox(height: 10),
              _field(targetCtrl, 'Số tiền mục tiêu (₫)', TextInputType.number),
              const SizedBox(height: 10),
              _field(currentCtrl, 'Đã tiết kiệm (₫)', TextInputType.number),
              const SizedBox(height: 10),
              _field(deadlineCtrl, 'Hạn chót (dd/mm/yyyy)', TextInputType.text),
            ],
          ),
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
              final title = titleCtrl.text.trim();
              final target =
                  double.tryParse(
                    targetCtrl.text.replaceAll(RegExp(r'[^\d]'), ''),
                  ) ??
                  0;
              if (title.isEmpty || target <= 0) return;

              final current =
                  double.tryParse(
                    currentCtrl.text.replaceAll(RegExp(r'[^\d]'), ''),
                  ) ??
                  0;

              await RemoteApiHelper.instance.addSaving({
                'title': title,
                'target': target,
                'current': current,
                'deadline': deadlineCtrl.text.trim(),
              });
              SessionService.needRefreshAI.value = true;
              _load();
              if (!ctx.mounted) return;
              Navigator.pop(ctx);
            },
            child: const Text('Thêm'),
          ),
        ],
      ),
    );
  }

  void _editAmount(int index) {
    final goal = _goals[index];
    final ctrl = TextEditingController(
      text: ((goal['current'] as num?) ?? 0).toInt().toString(),
    );

    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: AppTheme.navyLight,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
        title: Text(
          'Cập nhật: ${goal['title']}',
          style: const TextStyle(
            color: AppTheme.textWhite,
            fontWeight: FontWeight.bold,
          ),
        ),
        content: _field(ctrl, 'Số tiền đã tiết kiệm (₫)', TextInputType.number),
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
              final val =
                  double.tryParse(ctrl.text.replaceAll(RegExp(r'[^\d]'), '')) ??
                  0;
              await RemoteApiHelper.instance.updateSavingCurrent(
                goal['id'].toString(),
                val,
              );
              SessionService.needRefreshAI.value = true;
              _load();
              if (!ctx.mounted) return;
              Navigator.pop(ctx);
            },
            child: const Text('Lưu'),
          ),
        ],
      ),
    );
  }

  Future<void> _deleteGoal(int index) async {
    final removed = _goals[index];
    setState(() => _goals.removeAt(index));
    await RemoteApiHelper.instance.deleteSaving(removed['id'].toString());
    SessionService.needRefreshAI.value = true;
    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Đã xóa mục tiêu: ${removed['title']}')),
      );
    }
  }

  TextField _field(
    TextEditingController ctrl,
    String label,
    TextInputType type,
  ) {
    return TextField(
      controller: ctrl,
      keyboardType: type,
      style: const TextStyle(color: AppTheme.textWhite),
      decoration: InputDecoration(
        labelText: label,
        labelStyle: const TextStyle(color: AppTheme.textGray, fontSize: 13),
        filled: true,
        fillColor: AppTheme.navyDark,
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: BorderSide.none,
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppTheme.navyDark,
      appBar: AppBar(
        backgroundColor: Colors.transparent,
        elevation: 0,
        iconTheme: const IconThemeData(color: AppTheme.textWhite),
        title: const Text(
          'Sổ tiết kiệm',
          style: TextStyle(
            color: AppTheme.textWhite,
            fontWeight: FontWeight.bold,
            fontSize: 20,
          ),
        ),
        actions: [
          IconButton(
            icon: const Icon(Icons.add_rounded, color: AppTheme.mintAccent),
            onPressed: _addGoal,
          ),
        ],
      ),
      body: _isLoading
          ? _buildSkeletonLoading()
          : RefreshIndicator(
              onRefresh: _load,
              color: AppTheme.mintAccent,
              backgroundColor: AppTheme.navyLight,
              child: ListView(
                padding: const EdgeInsets.only(bottom: 32),
                children: [
                  _buildSummaryCard(),
                  const SizedBox(height: 24),
                  _buildActionGrid(),
                  const SizedBox(height: 24),
                  _buildGoalsList(),
                ],
              ),
            ),
    );
  }

  Widget _buildSummaryCard() {
    final progress = _totalTarget > 0
        ? (_totalSaved / _totalTarget).clamp(0.0, 1.0)
        : 0.0;

    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 20),
      child: Container(
        padding: const EdgeInsets.all(24),
        decoration: BoxDecoration(
          gradient: const LinearGradient(
            colors: [Color(0xFF0A2540), AppTheme.mintAccent],
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
          ),
          borderRadius: BorderRadius.circular(24),
          boxShadow: [
            BoxShadow(
              color: AppTheme.mintAccent.withValues(alpha: 0.25),
              blurRadius: 24,
              offset: const Offset(0, 8),
            ),
          ],
        ),
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
                      'Tổng đã tiết kiệm',
                      style: TextStyle(color: Colors.white70, fontSize: 13),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      _fmtCur.format(_totalSaved),
                      style: const TextStyle(
                        color: Colors.white,
                        fontSize: 28,
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                  ],
                ),
                Container(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 12,
                    vertical: 6,
                  ),
                  decoration: BoxDecoration(
                    color: Colors.white.withValues(alpha: 0.2),
                    borderRadius: BorderRadius.circular(20),
                  ),
                  child: Text(
                    '${_goals.length} mục tiêu',
                    style: const TextStyle(
                      color: Colors.white,
                      fontSize: 12,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 20),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text(
                  'Mục tiêu: ${_fmtCur.format(_totalTarget)}',
                  style: const TextStyle(color: Colors.white70, fontSize: 12),
                ),
                Text(
                  '${(progress * 100).toStringAsFixed(0)}%',
                  style: const TextStyle(
                    color: Colors.white,
                    fontWeight: FontWeight.bold,
                    fontSize: 12,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 8),
            ClipRRect(
              borderRadius: BorderRadius.circular(4),
              child: LinearProgressIndicator(
                value: progress,
                backgroundColor: Colors.white.withValues(alpha: 0.3),
                valueColor: const AlwaysStoppedAnimation(Colors.white),
                minHeight: 6,
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildActionGrid() {
    final actions = [
      (Icons.add_circle_rounded, 'Thêm\nmục tiêu', _addGoal),
      (
        Icons.savings_rounded,
        'Nạp tiền',
        () => ScaffoldMessenger.of(
          context,
        ).showSnackBar(const SnackBar(content: Text('Tính năng sắp ra mắt'))),
      ),
      (
        Icons.account_balance_wallet_rounded,
        'Rút tiền',
        () => ScaffoldMessenger.of(
          context,
        ).showSnackBar(const SnackBar(content: Text('Tính năng sắp ra mắt'))),
      ),
      (
        Icons.share_rounded,
        'Chia sẻ',
        () => ScaffoldMessenger.of(
          context,
        ).showSnackBar(const SnackBar(content: Text('Tính năng sắp ra mắt'))),
      ),
    ];

    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 20),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: actions
            .map(
              (a) => GestureDetector(
                onTap: a.$3,
                child: Column(
                  children: [
                    Container(
                      width: 64,
                      height: 64,
                      decoration: BoxDecoration(
                        color: AppTheme.navyLight,
                        borderRadius: BorderRadius.circular(18),
                      ),
                      child: Icon(a.$1, color: AppTheme.mintAccent, size: 28),
                    ),
                    const SizedBox(height: 8),
                    Text(
                      a.$2,
                      textAlign: TextAlign.center,
                      style: const TextStyle(
                        color: AppTheme.textGray,
                        fontSize: 11,
                        height: 1.3,
                      ),
                    ),
                  ],
                ),
              ),
            )
            .toList(),
      ),
    );
  }

  Widget _buildGoalsList() {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 20),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              const Text(
                'Danh sách mục tiêu',
                style: TextStyle(
                  color: AppTheme.textWhite,
                  fontSize: 18,
                  fontWeight: FontWeight.bold,
                ),
              ),
              if (_goals.isNotEmpty)
                Text(
                  '${_goals.length} mục tiêu',
                  style: const TextStyle(
                    color: AppTheme.textGray,
                    fontSize: 12,
                  ),
                ),
            ],
          ),
          const SizedBox(height: 12),
          if (_goals.isEmpty)
            Container(
              width: double.infinity,
              padding: const EdgeInsets.all(32),
              decoration: BoxDecoration(
                color: AppTheme.navyLight,
                borderRadius: BorderRadius.circular(20),
              ),
              child: const Column(
                children: [
                  Icon(
                    Icons.savings_outlined,
                    color: AppTheme.textGray,
                    size: 48,
                  ),
                  SizedBox(height: 12),
                  Text(
                    'Chưa có mục tiêu nào.',
                    style: TextStyle(color: AppTheme.textGray),
                  ),
                  SizedBox(height: 4),
                  Text(
                    'Nhấn "+" để tạo mục tiêu tiết kiệm.',
                    style: TextStyle(color: AppTheme.textGray, fontSize: 12),
                  ),
                ],
              ),
            )
          else
            ...List.generate(_goals.length, (i) {
              final g = _goals[i];
              final target = ((g['target'] as num?) ?? 0).toDouble();
              final current = ((g['current'] as num?) ?? 0).toDouble();
              final progress = target > 0
                  ? (current / target).clamp(0.0, 1.0)
                  : 0.0;
              final isComplete = progress >= 1.0;
              final pctText = '${(progress * 100).toStringAsFixed(0)}%';
              final goalId = g['id']?.toString() ?? '$i';

              return Padding(
                padding: const EdgeInsets.only(bottom: 12),
                child: Dismissible(
                  key: Key(goalId),
                  direction: DismissDirection.endToStart,
                  onDismissed: (_) => _deleteGoal(i),
                  background: Container(
                    decoration: BoxDecoration(
                      color: AppTheme.dangerColor.withValues(alpha: 0.85),
                      borderRadius: BorderRadius.circular(20),
                    ),
                    alignment: Alignment.centerRight,
                    padding: const EdgeInsets.only(right: 20),
                    child: const Icon(
                      Icons.delete_rounded,
                      color: Colors.white,
                    ),
                  ),
                  child: GestureDetector(
                    onTap: () => _editAmount(i),
                    onLongPress: () => _deleteGoal(i),
                    child: Container(
                      padding: const EdgeInsets.all(16),
                      decoration: BoxDecoration(
                        color: AppTheme.navyLight,
                        borderRadius: BorderRadius.circular(20),
                      ),
                      child: Column(
                        children: [
                          Row(
                            children: [
                              Container(
                                padding: const EdgeInsets.all(10),
                                decoration: BoxDecoration(
                                  color: AppTheme.mintAccent.withValues(
                                    alpha: 0.12,
                                  ),
                                  borderRadius: BorderRadius.circular(14),
                                ),
                                child: Icon(
                                  isComplete
                                      ? Icons.check_circle_rounded
                                      : Icons.savings_rounded,
                                  color: AppTheme.mintAccent,
                                  size: 22,
                                ),
                              ),
                              const SizedBox(width: 12),
                              Expanded(
                                child: Column(
                                  crossAxisAlignment: CrossAxisAlignment.start,
                                  children: [
                                    Text(
                                      g['title']?.toString() ?? '',
                                      style: const TextStyle(
                                        color: AppTheme.textWhite,
                                        fontWeight: FontWeight.bold,
                                      ),
                                    ),
                                    if ((g['deadline']?.toString() ?? '')
                                        .isNotEmpty)
                                      Text(
                                        'Hạn: ${g['deadline']}',
                                        style: const TextStyle(
                                          color: AppTheme.textGray,
                                          fontSize: 11,
                                        ),
                                      ),
                                  ],
                                ),
                              ),
                              Column(
                                crossAxisAlignment: CrossAxisAlignment.end,
                                children: [
                                  Text(
                                    _fmtCur.format(current),
                                    style: const TextStyle(
                                      color: AppTheme.mintAccent,
                                      fontWeight: FontWeight.bold,
                                      fontSize: 14,
                                    ),
                                  ),
                                  Text(
                                    pctText,
                                    style: TextStyle(
                                      color: isComplete
                                          ? AppTheme.mintAccent
                                          : AppTheme.textGray,
                                      fontSize: 11,
                                      fontWeight: FontWeight.w600,
                                    ),
                                  ),
                                ],
                              ),
                            ],
                          ),
                          const SizedBox(height: 12),
                          Row(
                            children: [
                              Expanded(
                                child: ClipRRect(
                                  borderRadius: BorderRadius.circular(4),
                                  child: LinearProgressIndicator(
                                    value: progress,
                                    backgroundColor: AppTheme.textGray
                                        .withValues(alpha: 0.2),
                                    valueColor: AlwaysStoppedAnimation(
                                      isComplete
                                          ? AppTheme.mintAccent
                                          : Colors.orangeAccent,
                                    ),
                                    minHeight: 6,
                                  ),
                                ),
                              ),
                              const SizedBox(width: 12),
                              Container(
                                padding: const EdgeInsets.symmetric(
                                  horizontal: 10,
                                  vertical: 4,
                                ),
                                decoration: BoxDecoration(
                                  color: AppTheme.mintAccent.withValues(
                                    alpha: 0.12,
                                  ),
                                  borderRadius: BorderRadius.circular(8),
                                ),
                                child: const Text(
                                  'Cập nhật',
                                  style: TextStyle(
                                    color: AppTheme.mintAccent,
                                    fontSize: 11,
                                    fontWeight: FontWeight.w600,
                                  ),
                                ),
                              ),
                            ],
                          ),
                          const SizedBox(height: 6),
                          Row(
                            mainAxisAlignment: MainAxisAlignment.spaceBetween,
                            children: [
                              Text(
                                _fmtCur.format(current),
                                style: const TextStyle(
                                  color: AppTheme.textGray,
                                  fontSize: 11,
                                ),
                              ),
                              Text(
                                '/ ${_fmtCur.format(target)}',
                                style: const TextStyle(
                                  color: AppTheme.textGray,
                                  fontSize: 11,
                                ),
                              ),
                            ],
                          ),
                        ],
                      ),
                    ),
                  ),
                ),
              );
            }),
        ],
      ),
    );
  }

  Widget _buildSkeletonLoading() {
    return ListView(
      padding: const EdgeInsets.only(bottom: 32, top: 16),
      physics: const NeverScrollableScrollPhysics(),
      children: [
        _buildSkeletonBox(
          height: 180,
          margin: const EdgeInsets.symmetric(horizontal: 20),
        ),
        const SizedBox(height: 24),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 20),
          child: Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: List.generate(
              4,
              (_) => Column(
                children: [
                  _buildSkeletonBox(
                    width: 64,
                    height: 64,
                    shape: BoxShape.rectangle,
                    borderRadius: 18,
                  ),
                  const SizedBox(height: 8),
                  _buildSkeletonBox(width: 40, height: 10, borderRadius: 4),
                ],
              ),
            ),
          ),
        ),
        const SizedBox(height: 32),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 20),
          child: _buildSkeletonBox(width: 150, height: 20, borderRadius: 6),
        ),
        const SizedBox(height: 16),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 20),
          child: Column(
            children: List.generate(
              3,
              (_) => Padding(
                padding: const EdgeInsets.only(bottom: 12),
                child: _buildSkeletonBox(height: 90, borderRadius: 20),
              ),
            ),
          ),
        ),
      ],
    );
  }

  Widget _buildSkeletonBox({
    double? width,
    double? height,
    EdgeInsetsGeometry? margin,
    BoxShape shape = BoxShape.rectangle,
    double borderRadius = 20,
  }) {
    return Container(
      width: width,
      height: height,
      margin: margin,
      decoration: BoxDecoration(
        color: AppTheme.navyLight.withValues(alpha: 0.6),
        shape: shape,
        borderRadius: shape == BoxShape.rectangle
            ? BorderRadius.circular(borderRadius)
            : null,
      ),
    );
  }
}

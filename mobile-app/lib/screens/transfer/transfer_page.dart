import 'package:flutter/material.dart';
import 'package:intl/intl.dart';

import 'package:finance_ai_app/core/theme/app_theme.dart';
import 'package:finance_ai_app/models/wallet_model.dart';
import 'package:finance_ai_app/services/session_service.dart';
import 'package:finance_ai_app/services/app_repository.dart';

class TransferPage extends StatefulWidget {
  const TransferPage({super.key});

  @override
  State<TransferPage> createState() => _TransferPageState();
}

class _TransferPageState extends State<TransferPage> {
  List<WalletModel> _wallets = [];
  WalletModel? _selectedWallet;
  bool _loading = true;
  bool _saving = false;

  final _amountCtrl = TextEditingController();
  final _noteCtrl = TextEditingController();

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void dispose() {
    _amountCtrl.dispose();
    _noteCtrl.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    final userId = SessionService.currentUserId;
    if (userId == null) {
      setState(() => _loading = false);
      return;
    }
    final wallets = await AppRepository.instance.getWallets(userId);
    if (!mounted) return;
    setState(() {
      _wallets = wallets;
      _selectedWallet = wallets.isNotEmpty ? wallets.first : null;
      _loading = false;
    });
  }

  String _fmt(double v) =>
      NumberFormat.currency(locale: 'vi_VN', symbol: '₫', decimalDigits: 0)
          .format(v);

  Future<void> _confirm() async {
    final userId = SessionService.currentUserId;
    final wallet = _selectedWallet;
    if (userId == null || wallet == null) return;

    final amountStr =
        _amountCtrl.text.trim().replaceAll(RegExp(r'[^\d]'), '');
    final amount = double.tryParse(amountStr) ?? 0;

    if (amount <= 0) {
      _snack('Vui lòng nhập số tiền hợp lệ.');
      return;
    }
    if (amount > wallet.balance) {
      _snack('Số dư không đủ để thực hiện giao dịch.');
      return;
    }

    setState(() => _saving = true);
    try {
      final note = _noteCtrl.text.trim();
      await AppRepository.instance.addTransaction(
        userId: userId,
        amount: amount,
        isExpense: true,
        category: 'Chuyển khoản',
        note: note.isEmpty ? 'Chuyển khoản' : note,
      );

      if (!mounted) return;
      _snack('Chuyển khoản thành công!');
      _amountCtrl.clear();
      _noteCtrl.clear();
      await _load();
    } catch (e) {
      if (!mounted) return;
      _snack('Chuyển khoản thất bại: $e');
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  void _snack(String msg) {
    ScaffoldMessenger.of(context)
        .showSnackBar(SnackBar(content: Text(msg)));
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppTheme.navyDark,
      appBar: AppBar(
        backgroundColor: AppTheme.navyDark,
        title: const Text('Chuyển khoản',
            style: TextStyle(
                color: AppTheme.textWhite, fontWeight: FontWeight.bold)),
      ),
      body: _loading
          ? const Center(
              child: CircularProgressIndicator(color: AppTheme.mintAccent))
          : SingleChildScrollView(
              padding: const EdgeInsets.all(20),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text('Ví thanh toán',
                      style: TextStyle(
                          color: AppTheme.textGray, fontSize: 13)),
                  const SizedBox(height: 8),
                  if (_wallets.isEmpty)
                    Container(
                      padding: const EdgeInsets.all(16),
                      decoration: BoxDecoration(
                        color: AppTheme.navyLight,
                        borderRadius: BorderRadius.circular(12),
                      ),
                      child: const Text('Chưa có ví nào.',
                          style: TextStyle(color: AppTheme.textGray)),
                    )
                  else
                    Container(
                      padding: const EdgeInsets.symmetric(
                          horizontal: 16, vertical: 4),
                      decoration: BoxDecoration(
                        color: AppTheme.navyLight,
                        borderRadius: BorderRadius.circular(12),
                      ),
                      child: DropdownButtonHideUnderline(
                        child: DropdownButton<WalletModel>(
                          value: _selectedWallet,
                          isExpanded: true,
                          dropdownColor: AppTheme.navyLight,
                          style: const TextStyle(color: AppTheme.textWhite),
                          items: _wallets
                              .map((w) => DropdownMenuItem(
                                    value: w,
                                    child: Row(
                                      children: [
                                        Icon(
                                          Icons
                                              .account_balance_wallet_rounded,
                                          color: Color(w.colorValue),
                                          size: 20,
                                        ),
                                        const SizedBox(width: 10),
                                        Expanded(
                                          child: Text(w.name,
                                              style: const TextStyle(
                                                  color:
                                                      AppTheme.textWhite)),
                                        ),
                                        Text(_fmt(w.balance),
                                            style: const TextStyle(
                                                color: AppTheme.mintAccent,
                                                fontSize: 13)),
                                      ],
                                    ),
                                  ))
                              .toList(),
                          onChanged: (w) =>
                              setState(() => _selectedWallet = w),
                        ),
                      ),
                    ),
                  if (_selectedWallet != null) ...[
                    const SizedBox(height: 8),
                    Container(
                      padding: const EdgeInsets.symmetric(
                          horizontal: 12, vertical: 8),
                      decoration: BoxDecoration(
                        color:
                            AppTheme.mintAccent.withValues(alpha: 0.08),
                        borderRadius: BorderRadius.circular(10),
                        border: Border.all(
                            color: AppTheme.mintAccent
                                .withValues(alpha: 0.2)),
                      ),
                      child: Row(
                        children: [
                          const Icon(Icons.info_outline_rounded,
                              color: AppTheme.mintAccent, size: 16),
                          const SizedBox(width: 8),
                          Text(
                            'Số dư khả dụng: ${_fmt(_selectedWallet!.balance)}',
                            style: const TextStyle(
                                color: AppTheme.mintAccent, fontSize: 13),
                          ),
                        ],
                      ),
                    ),
                  ],
                  const SizedBox(height: 24),
                  const Text('Số tiền (₫)',
                      style: TextStyle(
                          color: AppTheme.textGray, fontSize: 13)),
                  const SizedBox(height: 8),
                  _buildField(
                    controller: _amountCtrl,
                    hint: '100.000',
                    keyboardType: TextInputType.number,
                    icon: Icons.attach_money_rounded,
                  ),
                  const SizedBox(height: 12),
                  Wrap(
                    spacing: 8,
                    runSpacing: 8,
                    children: [50000, 100000, 200000, 500000].map((v) {
                      return GestureDetector(
                        onTap: () => setState(
                            () => _amountCtrl.text = v.toString()),
                        child: Container(
                          padding: const EdgeInsets.symmetric(
                              horizontal: 14, vertical: 8),
                          decoration: BoxDecoration(
                            color: AppTheme.navyLight,
                            borderRadius: BorderRadius.circular(20),
                            border: Border.all(
                                color: AppTheme.mintAccent
                                    .withValues(alpha: 0.4)),
                          ),
                          child: Text(_fmt(v.toDouble()),
                              style: const TextStyle(
                                  color: AppTheme.mintAccent,
                                  fontWeight: FontWeight.w500)),
                        ),
                      );
                    }).toList(),
                  ),
                  const SizedBox(height: 24),
                  const Text('Nội dung chuyển khoản',
                      style: TextStyle(
                          color: AppTheme.textGray, fontSize: 13)),
                  const SizedBox(height: 8),
                  _buildField(
                    controller: _noteCtrl,
                    hint: 'VD: Trả nợ, mua sắm, ăn uống...',
                    icon: Icons.note_outlined,
                  ),
                  const SizedBox(height: 40),
                  SizedBox(
                    width: double.infinity,
                    child: ElevatedButton(
                      onPressed: _saving ? null : _confirm,
                      style: ElevatedButton.styleFrom(
                        backgroundColor: AppTheme.mintAccent,
                        foregroundColor: Colors.white,
                        padding:
                            const EdgeInsets.symmetric(vertical: 16),
                        shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(14)),
                      ),
                      child: _saving
                          ? const SizedBox(
                              height: 20,
                              width: 20,
                              child: CircularProgressIndicator(
                                  color: Colors.white, strokeWidth: 2),
                            )
                          : const Text('Xác nhận chuyển khoản',
                              style: TextStyle(
                                  fontSize: 16,
                                  fontWeight: FontWeight.bold)),
                    ),
                  ),
                ],
              ),
            ),
    );
  }

  Widget _buildField({
    required TextEditingController controller,
    required String hint,
    TextInputType keyboardType = TextInputType.text,
    required IconData icon,
  }) {
    return TextField(
      controller: controller,
      keyboardType: keyboardType,
      style: const TextStyle(color: AppTheme.textWhite),
      decoration: InputDecoration(
        hintText: hint,
        hintStyle: const TextStyle(color: AppTheme.textGray),
        prefixIcon: Icon(icon, color: AppTheme.mintAccent),
        filled: true,
        fillColor: AppTheme.navyLight,
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: BorderSide.none,
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: const BorderSide(color: AppTheme.mintAccent),
        ),
      ),
    );
  }
}

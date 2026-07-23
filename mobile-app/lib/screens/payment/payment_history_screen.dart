import 'package:flutter/material.dart';
import 'package:intl/intl.dart';

import 'package:finance_ai_app/config/app_config.dart';
import 'package:finance_ai_app/core/theme/app_theme.dart';
import 'package:finance_ai_app/services/remote_api_helper.dart';

class PaymentHistoryScreen extends StatefulWidget {
  const PaymentHistoryScreen({super.key});

  @override
  State<PaymentHistoryScreen> createState() => _PaymentHistoryScreenState();
}

class _PaymentHistoryScreenState extends State<PaymentHistoryScreen> {
  List<Map<String, dynamic>> _orders = [];
  bool _loading = true;
  String? _error;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    if (!AppConfig.isFull) {
      setState(() {
        _loading = false;
        _error = 'Lịch sử nạp/rút chỉ có ở chế độ FULL (cần backend).';
      });
      return;
    }
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final list = await RemoteApiHelper.instance.paymentHistory();
      if (!mounted) return;
      setState(() {
        _orders = list;
        _loading = false;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _error = 'Tải lịch sử lỗi: $e';
        _loading = false;
      });
    }
  }

  Future<void> _cancel(String orderCode) async {
    try {
      await RemoteApiHelper.instance.cancelPayment(orderCode);
      _snack('Đã hủy đơn $orderCode');
      await _load();
    } catch (e) {
      _snack('Hủy lỗi: $e');
    }
  }

  void _snack(String m) => ScaffoldMessenger.of(context)
      .showSnackBar(SnackBar(content: Text(m)));

  String _fmt(dynamic v) {
    final n = (v is num) ? v.toDouble() : double.tryParse('$v') ?? 0;
    return NumberFormat.currency(locale: 'vi_VN', symbol: '₫', decimalDigits: 0)
        .format(n);
  }

  String _date(dynamic iso) {
    final d = DateTime.tryParse('$iso');
    if (d == null) return '';
    return DateFormat('dd/MM/yyyy HH:mm').format(d);
  }

  Color _statusColor(String s) {
    switch (s) {
      case 'SUCCESS':
        return Colors.green;
      case 'FAILED':
        return Colors.redAccent;
      case 'CANCELLED':
        return Colors.grey;
      default:
        return Colors.orangeAccent;
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppTheme.navyDark,
      appBar: AppBar(
        backgroundColor: AppTheme.navyDark,
        title: const Text('Lịch sử nạp/rút',
            style: TextStyle(
                color: AppTheme.textWhite, fontWeight: FontWeight.bold)),
        iconTheme: const IconThemeData(color: AppTheme.textWhite),
      ),
      body: _loading
          ? const Center(
              child: CircularProgressIndicator(color: AppTheme.mintAccent))
          : _error != null
              ? Center(
                  child: Padding(
                  padding: const EdgeInsets.all(24),
                  child: Text(_error!,
                      textAlign: TextAlign.center,
                      style: const TextStyle(color: AppTheme.textGray)),
                ))
              : _orders.isEmpty
                  ? const Center(
                      child: Text('Chưa có giao dịch nạp/rút.',
                          style: TextStyle(color: AppTheme.textGray)))
                  : RefreshIndicator(
                      onRefresh: _load,
                      child: ListView.separated(
                        padding: const EdgeInsets.all(16),
                        itemCount: _orders.length,
                        separatorBuilder: (_, _) => const SizedBox(height: 10),
                        itemBuilder: (_, i) => _tile(_orders[i]),
                      ),
                    ),
    );
  }

  Widget _tile(Map<String, dynamic> o) {
    final type = '${o['type']}';
    final status = '${o['status']}';
    final isDeposit = type == 'DEPOSIT';
    final canCancel = status == 'PENDING';
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: AppTheme.navyLight,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(
                isDeposit
                    ? Icons.arrow_downward_rounded
                    : Icons.arrow_upward_rounded,
                color: isDeposit ? Colors.green : Colors.orangeAccent,
                size: 20,
              ),
              const SizedBox(width: 8),
              Text(isDeposit ? 'Nạp tiền' : 'Rút tiền',
                  style: const TextStyle(
                      color: AppTheme.textWhite, fontWeight: FontWeight.bold)),
              const Spacer(),
              Text('${isDeposit ? '+' : '-'}${_fmt(o['amount'])}',
                  style: TextStyle(
                      color: isDeposit ? Colors.green : Colors.orangeAccent,
                      fontWeight: FontWeight.bold)),
            ],
          ),
          const SizedBox(height: 6),
          Row(
            children: [
              Text('${o['provider'] ?? ''}',
                  style: const TextStyle(
                      color: AppTheme.mintAccent, fontSize: 12)),
              const SizedBox(width: 10),
              Container(
                padding:
                    const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                decoration: BoxDecoration(
                  color: _statusColor(status).withValues(alpha: 0.18),
                  borderRadius: BorderRadius.circular(8),
                ),
                child: Text(status,
                    style: TextStyle(
                        color: _statusColor(status),
                        fontSize: 11,
                        fontWeight: FontWeight.w600)),
              ),
              const Spacer(),
              Text(_date(o['createdAt']),
                  style:
                      const TextStyle(color: AppTheme.textGray, fontSize: 11)),
            ],
          ),
          Text('Mã: ${o['orderCode']}',
              style: const TextStyle(color: AppTheme.textGray, fontSize: 11)),
          if (canCancel) ...[
            const SizedBox(height: 6),
            Align(
              alignment: Alignment.centerRight,
              child: TextButton.icon(
                onPressed: () => _cancel('${o['orderCode']}'),
                icon: const Icon(Icons.close_rounded,
                    size: 16, color: Colors.redAccent),
                label: const Text('Hủy đơn',
                    style: TextStyle(color: Colors.redAccent)),
              ),
            ),
          ],
        ],
      ),
    );
  }
}

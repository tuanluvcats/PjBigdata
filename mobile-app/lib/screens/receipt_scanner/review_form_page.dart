import 'dart:io';
import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:intl/intl.dart';

import 'package:finance_ai_app/config/app_config.dart';
import 'package:finance_ai_app/core/constants/transaction_categories.dart';
import 'package:finance_ai_app/core/theme/app_theme.dart';
import 'package:finance_ai_app/models/receipt_item_model.dart';
import 'package:finance_ai_app/models/transaction_model.dart';
import 'package:finance_ai_app/services/remote_api_helper.dart';
import 'package:finance_ai_app/services/session_service.dart';
import 'package:finance_ai_app/services/sqflite_helper.dart';

class ReviewFormPage extends StatefulWidget {
  final String imagePath;

  const ReviewFormPage({super.key, required this.imagePath});

  @override
  State<ReviewFormPage> createState() => _ReviewFormPageState();
}

class _ReviewFormPageState extends State<ReviewFormPage> {
  late ReceiptModel _receipt;
  bool _isProcessing = true;
  bool _isSaving = false;
  double _donutTotal = 0;
  Map<String, dynamic>? _rawScan;
  String? _imageUrl;

  late TextEditingController _storeNameController;
  late TextEditingController _dateController;
  String _selectedCategory = 'Ăn uống';

  @override
  void initState() {
    super.initState();

    _receipt = ReceiptModel(storeName: '', date: '', items: [], tax: 0);
    _storeNameController = TextEditingController();
    _dateController = TextEditingController(
        text: DateFormat('dd/MM/yyyy HH:mm').format(DateTime.now()));

    _callDonutApi();
  }

  Future<void> _callDonutApi() async {
    try {
      final result = await RemoteApiHelper.instance.scanReceipt(widget.imagePath);
      if (AppConfig.isFull) {
        try {
          _imageUrl = await RemoteApiHelper.instance.uploadImage(widget.imagePath);
          debugPrint('Upload anh receipt OK: $_imageUrl');
        } catch (e) {
          debugPrint('Upload anh receipt LOI: $e');
        }
      }

      final seller = (result['seller'] is Map)
          ? (result['seller'] as Map).cast<String, dynamic>()
          : <String, dynamic>{};
      final storeName = (seller['seller_name'] ?? '').toString();
      final timestamp = (seller['timestamp'] ?? '').toString();

      double total = 0;
      final n = seller['total_cost_number'];
      if (n is num) {
        total = n.toDouble();
      } else {
        final costStr = (seller['total_cost'] ?? '0').toString();
        total = double.tryParse(costStr.replaceAll(RegExp(r'[^\d.]'), '')) ?? 0.0;
      }

      if (!mounted) return;
      setState(() {
        _rawScan = result;
        if (storeName.isNotEmpty) _storeNameController.text = storeName;
        if (timestamp.isNotEmpty) _dateController.text = timestamp;
        _donutTotal = total;
        _receipt = ReceiptModel(
          storeName: storeName,
          date: timestamp,
          items: [],
          tax: 0,
        );
        _isProcessing = false;
      });

      if (total == 0 && mounted) {
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(
            content: Text('Donut chưa đọc được tổng tiền từ ảnh — vui lòng nhập tay.')));
      }
    } catch (e) {
      if (!mounted) return;
      setState(() => _isProcessing = false);
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(
          content: Text('Quét hóa đơn thất bại (kiểm tra AI service :8000). Nhập tay.')));
    }
  }

  @override
  void dispose() {
    _storeNameController.dispose();
    _dateController.dispose();
    super.dispose();
  }

  Future<void> _saveToDatabase() async {
    final userId = SessionService.currentUserId;
    if (userId == null) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Vui lòng đăng nhập lại.')),
      );
      return;
    }

    setState(() => _isSaving = true);

    DateTime parsedDate;
    try {
      parsedDate = DateFormat('dd/MM/yyyy HH:mm').parse(_dateController.text.trim());
    } catch (_) {
      parsedDate = DateTime.now();
    }

    final title = _storeNameController.text.trim().isEmpty
        ? _selectedCategory
        : _storeNameController.text.trim();

    final amount = _donutTotal > 0 ? _donutTotal : _receipt.grandTotal;
    final transaction = TransactionModel.draft(
      title: title,
      category: _selectedCategory,
      amount: amount,
      date: parsedDate,
      isExpense: true,
    );

    try {
      if (AppConfig.isFull) {
        await RemoteApiHelper.instance.addTransaction({
          'amount': transaction.amount,
          'note': transaction.title,
          'location': transaction.title.isEmpty ? 'Hóa đơn quét' : transaction.title,
          'paymentMethod': 'WALLET',
          'transactionDate': transaction.date.toIso8601String(),
          'categoryName': transaction.category,
          'isExpense': true,
          if (_rawScan != null) 'rawReceiptData': jsonEncode(_rawScan),
          if (_imageUrl != null) 'imageUrl': _imageUrl,
        });
      } else {
        await SqfliteHelper.instance.insertTransaction(transaction, userId);
      }
      if (!mounted) return;
      Navigator.pop(context, true);
    } catch (e) {
      if (!mounted) return;
      setState(() => _isSaving = false);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Lưu thất bại: $e')),
      );
    }
  }

  Future<void> _pickCategory() async {
    if (TransactionCategories.expenseSelectable.isEmpty) {
      await TransactionCategories.loadFromApi();
    }
    final categories = TransactionCategories.expenseSelectable;
    final picked = await showModalBottomSheet<String>(
      context: context,
      backgroundColor: AppTheme.navyLight,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
      ),
      builder: (context) {
        return SafeArea(
          child: ListView(
            shrinkWrap: true,
            padding: const EdgeInsets.symmetric(vertical: 8),
            children: [
              const Padding(
                padding: EdgeInsets.fromLTRB(20, 12, 20, 8),
                child: Text(
                  'Chọn danh mục chi',
                  style: TextStyle(
                    color: AppTheme.textWhite,
                    fontSize: 16,
                    fontWeight: FontWeight.bold,
                  ),
                ),
              ),
              ...categories.map((cat) {
                final selected = cat == _selectedCategory;
                return ListTile(
                  leading: Icon(
                    TransactionCategories.iconFor(cat),
                    color: selected ? AppTheme.mintAccent : AppTheme.textGray,
                  ),
                  title: Text(
                    cat,
                    style: TextStyle(
                      color: selected ? AppTheme.textWhite : AppTheme.textGray,
                      fontWeight: selected ? FontWeight.w700 : FontWeight.normal,
                    ),
                  ),
                  trailing: selected
                      ? const Icon(Icons.check_rounded, color: AppTheme.mintAccent)
                      : null,
                  onTap: () => Navigator.pop(context, cat),
                );
              }),
            ],
          ),
        );
      },
    );

    if (!mounted || picked == null) return;
    setState(() => _selectedCategory = picked);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Xác nhận hóa đơn')),
      body: _isProcessing ? _buildProcessingState() : _buildFormContent(),
    );
  }

  Widget _buildProcessingState() {
    return const Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          CircularProgressIndicator(color: AppTheme.mintAccent),
          SizedBox(height: 24),
          Text(
            'Đang phân tích hóa đơn...',
            style: TextStyle(
              color: AppTheme.textWhite,
              fontSize: 18,
              fontWeight: FontWeight.bold,
            ),
          ),
          SizedBox(height: 8),
          Text(
            'Vui lòng đợi trong giây lát',
            style: TextStyle(color: AppTheme.textGray, fontSize: 14),
          ),
        ],
      ),
    );
  }

  Widget _buildFormContent() {
    final formatCurrency = NumberFormat.currency(locale: 'vi_VN', symbol: 'đ', decimalDigits: 0);

    return SingleChildScrollView(
      padding: const EdgeInsets.all(20),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [

          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              ClipRRect(
                borderRadius: BorderRadius.circular(12),
                child: Image.file(
                  File(widget.imagePath),
                  width: 80,
                  height: 120,
                  fit: BoxFit.cover,
                  errorBuilder: (_, __, ___) => Container(
                    width: 80,
                    height: 120,
                    decoration: BoxDecoration(
                      color: AppTheme.navyLight,
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: const Icon(Icons.image_not_supported, color: AppTheme.textGray),
                  ),
                ),
              ),
              const SizedBox(width: 16),
              const Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      'Trích xuất thành công',
                      style: TextStyle(
                        color: AppTheme.mintAccent,
                        fontWeight: FontWeight.bold,
                        fontSize: 16,
                      ),
                    ),
                    SizedBox(height: 8),
                    Text(
                      'Vui lòng kiểm tra lại thông tin bên dưới và đối chiếu với ảnh gốc trước khi lưu.',
                      style: TextStyle(
                        color: AppTheme.textGray,
                        fontSize: 13,
                        height: 1.4,
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
          const SizedBox(height: 32),

          _buildSectionTitle('Thông tin chung'),
          _buildTextField('Tên cửa hàng', _storeNameController, Icons.storefront),
          const SizedBox(height: 16),
          _buildTextField('Ngày giao dịch', _dateController, Icons.calendar_today),
          const SizedBox(height: 16),

          const Text('Danh mục', style: TextStyle(color: AppTheme.textGray, fontSize: 14)),
          const SizedBox(height: 8),
          InkWell(
            onTap: _pickCategory,
            child: Container(
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: AppTheme.navyLight,
                borderRadius: BorderRadius.circular(12),
                border: Border.all(color: AppTheme.textGray.withValues(alpha: 0.3)),
              ),
              child: Row(
                children: [
                  Icon(TransactionCategories.iconFor(_selectedCategory), color: AppTheme.textGray),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Text(
                      _selectedCategory,
                      style: const TextStyle(color: AppTheme.textWhite, fontSize: 16),
                    ),
                  ),
                  const Icon(Icons.chevron_right_rounded, color: AppTheme.textGray),
                ],
              ),
            ),
          ),

          const SizedBox(height: 24),
          _buildSectionTitle('Tổng kết'),
          Card(
            child: Padding(
              padding: const EdgeInsets.all(16.0),
              child: Column(
                children: [
                  _buildTotalRow('Tạm tính:', formatCurrency.format(_donutTotal)),
                  const SizedBox(height: 8),
                  _buildTotalRow('Thuế VAT/Khác:', formatCurrency.format(_receipt.tax)),
                  Padding(
                    padding: const EdgeInsets.symmetric(vertical: 12),
                    child: Divider(height: 1, color: Colors.white.withValues(alpha: 0.1)),
                  ),
                  _buildTotalRow(
                    'TỔNG TIỀN:',
                    formatCurrency.format(_donutTotal),
                    isGrandTotal: true,
                  ),
                ],
              ),
            ),
          ),

          const SizedBox(height: 40),
          SizedBox(
            width: double.infinity,
            height: 56,
            child: ElevatedButton(
              style: ElevatedButton.styleFrom(
                backgroundColor: AppTheme.mintAccent,
                foregroundColor: AppTheme.navyDark,
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
              ),
              onPressed: _isSaving ? null : _saveToDatabase,
              child: _isSaving
                  ? const SizedBox(
                      width: 24,
                      height: 24,
                      child: CircularProgressIndicator(
                        color: AppTheme.navyDark,
                        strokeWidth: 2,
                      ),
                    )
                  : const Text(
                      'Xác nhận & Lưu CSDL',
                      style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
                    ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildSectionTitle(String title) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: Text(
        title,
        style: const TextStyle(
          fontWeight: FontWeight.bold,
          fontSize: 16,
          color: AppTheme.textWhite,
        ),
      ),
    );
  }

  Widget _buildTextField(String label, TextEditingController controller, IconData icon) {
    return TextFormField(
      controller: controller,
      style: const TextStyle(color: AppTheme.textWhite),
      decoration: InputDecoration(
        labelText: label,
        labelStyle: const TextStyle(color: AppTheme.textGray),
        prefixIcon: Icon(icon, color: AppTheme.textGray),
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: BorderSide(color: AppTheme.textGray.withValues(alpha: 0.3)),
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: BorderSide(color: AppTheme.textGray.withValues(alpha: 0.3)),
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: const BorderSide(color: AppTheme.mintAccent),
        ),
        filled: true,
        fillColor: AppTheme.navyLight,
      ),
    );
  }

  Widget _buildTotalRow(String label, String value, {bool isGrandTotal = false}) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        Text(
          label,
          style: TextStyle(
            color: isGrandTotal ? AppTheme.textWhite : AppTheme.textGray,
            fontSize: isGrandTotal ? 16 : 14,
            fontWeight: isGrandTotal ? FontWeight.bold : FontWeight.normal,
          ),
        ),
        Text(
          value,
          style: TextStyle(
            color: isGrandTotal ? AppTheme.dangerColor : AppTheme.textWhite,
            fontSize: isGrandTotal ? 20 : 14,
            fontWeight: FontWeight.bold,
          ),
        ),
      ],
    );
  }
}

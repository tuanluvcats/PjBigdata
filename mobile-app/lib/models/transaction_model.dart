import 'package:flutter/material.dart';

class TransactionModel {
  final String? id;
  final String title;
  final double amount;
  final DateTime date;
  final String category;
  final bool isExpense;
  final String iconName;
  final int? walletId;
  final String? imageUrl;
  TransactionModel({
    this.id,
    required this.title,
    required this.amount,
    required this.date,
    required this.category,
    required this.isExpense,
    this.iconName = 'default',
    this.walletId,
    this.imageUrl,
  });

  factory TransactionModel.draft({
    required String title,
    required String category,
    required double amount,
    required DateTime date,
    required bool isExpense,
  }) {
    return TransactionModel(
      title: title,
      category: category,
      amount: amount,
      date: date,
      isExpense: isExpense,
      iconName: category,
    );
  }

  Color get color =>
      isExpense ? const Color(0xFFF43F5E) : const Color(0xFF10B981);

  IconData get icon => isExpense ? Icons.money_off : Icons.attach_money;

  Map<String, dynamic> toMap() {
    return {
      'title': title,
      'amount': amount,
      'date': date.toIso8601String(),
      'category': category,
      'isExpense': isExpense,
      'icon_name': iconName,
    };
  }

  factory TransactionModel.fromMap(Map<String, dynamic> map) {
    return TransactionModel(
      id: map['id']?.toString(),
      title: map['title'] ?? '',
      amount: (map['amount'] ?? 0).toDouble(),
      date: DateTime.parse(map['date']),
      category: map['category'] ?? 'Khác',
      isExpense: map['isExpense'] ?? true,
      iconName: map['icon_name'] ?? 'default',
    );
  }

  Map<String, dynamic> toDbMap(int userId) {
    return {
      'user_id': userId,
      'title': title,
      'amount': amount,
      'date': date.toIso8601String(),
      'category': category,
      'is_expense': isExpense ? 1 : 0,
      'icon_name': iconName,
      'wallet_id': walletId,
    };
  }

  factory TransactionModel.fromDbMap(Map<String, dynamic> map) {
    return TransactionModel(
      id: map['id']?.toString(),
      title: map['title'] ?? '',
      amount: (map['amount'] ?? 0).toDouble(),
      date: DateTime.parse(map['date']),
      category: map['category'] ?? 'Khác',
      isExpense: map['is_expense'] == 1,
      iconName: map['icon_name'] ?? 'default',
      walletId: map['wallet_id'] as int?,
    );
  }
}

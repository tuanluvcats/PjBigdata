import 'package:flutter/foundation.dart';

import 'package:finance_ai_app/config/app_config.dart';
import 'package:finance_ai_app/models/budget_model.dart';
import 'package:finance_ai_app/models/category_model.dart';
import 'package:finance_ai_app/models/transaction_model.dart';
import 'package:finance_ai_app/models/user_model.dart';
import 'package:finance_ai_app/models/wallet_model.dart';
import 'package:finance_ai_app/services/remote_api_helper.dart';
import 'package:finance_ai_app/services/sqflite_helper.dart';

class AppRepository {
  static final AppRepository instance = AppRepository._();
  AppRepository._();

  /// How the backend labels expense categories in GET /api/categories.
  static const String _serverExpenseType = 'EXPENSE';

  Future<List<TransactionModel>> getTransactions(int userId) {
    if (AppConfig.isFull) return RemoteApiHelper.instance.fetchTransactions();
    return SqfliteHelper.instance.getTransactionsByUserId(userId);
  }

  Future<UserModel?> getCurrentUser(int userId) async {
    if (AppConfig.isFull) {
      final me = await RemoteApiHelper.instance.fetchMe();
      final email = (me['email'] ?? '').toString();
      return UserModel(
        id: (me['id'] as num?)?.toInt(),
        email: email,
        password: '',
        fullName: email.isNotEmpty ? email.split('@').first : 'Người dùng',
        phone: me['phone']?.toString(),
      );
    }
    return SqfliteHelper.instance.getUserById(userId);
  }

  Future<List<WalletModel>> getWallets(int userId) async {
    final txs = await getTransactions(userId);
    final bal = txs.fold<double>(
        0.0, (s, t) => s + (t.isExpense ? -t.amount : t.amount));
    return [WalletModel(name: 'Ví chính', balance: bal, userId: userId)];
  }

  Future<Map<String, dynamic>> getHomeSummary(int userId) async {
    final results = await Future.wait([
      getCurrentUser(userId),
      getTransactions(userId),
    ]);

    final user = results[0] as UserModel?;
    final txs = results[1] as List<TransactionModel>;

    final bal = txs.fold<double>(
        0.0, (s, t) => s + (t.isExpense ? -t.amount : t.amount));
    final wallets = [WalletModel(name: 'Ví chính', balance: bal, userId: userId)];

    return {
      'user': user,
      'transactions': txs,
      'wallets': wallets,
    };
  }

  Future<void> addTransaction({
    required int userId,
    required double amount,
    required bool isExpense,
    required String category,
    String? note,
  }) async {
    if (AppConfig.isFull) {
      await RemoteApiHelper.instance.addTransaction({
        'amount': amount,
        'isExpense': isExpense,
        'categoryName': category,
        'location': (note != null && note.isNotEmpty) ? note : category,
        'paymentMethod': 'Ví chính',
        'transactionDate': DateTime.now().toIso8601String(),
        if (note != null) 'note': note,
      });
    } else {
      final tx = TransactionModel.draft(
        title: (note != null && note.isNotEmpty) ? note : category,
        category: category,
        amount: amount,
        date: DateTime.now(),
        isExpense: isExpense,
      );
      await SqfliteHelper.instance.insertTransaction(tx, userId);
    }
  }

  /// All the budgets of the user, newest first: from the server, or from the local table (which only stores monthly
  /// budgets). Callers pick the ones of a period themselves, see BudgetModel.coversDay and BudgetModel.overlaps.
  Future<List<BudgetModel>> getBudgets(int userId) {
    if (AppConfig.isFull) return RemoteApiHelper.instance.getBudgets();
    return SqfliteHelper.instance.getBudgetsByUserId(userId);
  }

  /// Like [getBudgets] for screens that show budgets next to other data: a failure comes back as null, so the rest of
  /// the screen still loads and can say that the budgets are missing.
  Future<List<BudgetModel>?> tryGetBudgets(int userId) async {
    try {
      return await getBudgets(userId);
    } catch (e) {
      debugPrint('Lỗi tải ngân sách: $e');
      return null;
    }
  }

  /// Creates the budget, or updates it when it already has an id, and returns the stored copy.
  Future<BudgetModel> saveBudget(int userId, BudgetModel budget) async {
    if (AppConfig.isFull) {
      return budget.id == null
          ? RemoteApiHelper.instance.createBudget(budget)
          : RemoteApiHelper.instance.updateBudget(budget);
    }
    final local = budget.copyWith(userId: userId);
    if (local.id == null) {
      final id = await SqfliteHelper.instance.insertBudget(local);
      return local.copyWith(id: id);
    }
    await SqfliteHelper.instance.updateBudget(local);
    return local;
  }

  Future<void> deleteBudget(int budgetId) async {
    if (AppConfig.isFull) {
      await RemoteApiHelper.instance.deleteBudget(budgetId);
    } else {
      await SqfliteHelper.instance.deleteBudget(budgetId);
    }
  }

  /// The expense categories a budget can be limited to, from the server or from the local table; empty when none load.
  Future<List<BudgetCategory>> getExpenseCategories(int userId) async {
    if (AppConfig.isFull) {
      final categories = await RemoteApiHelper.instance.fetchCategories();
      return [
        for (final category in categories)
          if (category['type'] == _serverExpenseType)
            (id: (category['id'] as num).toInt(), name: category['name'].toString()),
      ];
    }
    final categories = await SqfliteHelper.instance.getCategoriesByUserId(userId);
    return [
      for (final category in categories)
        if (category.type == CategoryType.expense && category.id != null)
          (id: category.id!, name: category.name),
    ];
  }
}
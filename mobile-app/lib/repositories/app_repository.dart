import 'package:finance_ai_app/config/app_config.dart';
import 'package:finance_ai_app/models/transaction_model.dart';
import 'package:finance_ai_app/models/user_model.dart';
import 'package:finance_ai_app/models/wallet_model.dart';
import 'package:finance_ai_app/services/remote_api_helper.dart';
import 'package:finance_ai_app/services/sqflite_helper.dart';

class AppRepository {
  static final AppRepository instance = AppRepository._();
  AppRepository._();

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
}
import 'package:flutter_test/flutter_test.dart';

import 'package:finance_ai_app/models/receipt_status.dart';
import 'package:finance_ai_app/models/transaction_model.dart';

void main() {
  group('ReceiptStatus.fromServer', () {
    test('SUCCESS and FAILED map to their own state, whatever the letter case or the spaces', () {
      expect(ReceiptStatus.fromServer('SUCCESS'), ReceiptStatus.success);
      expect(ReceiptStatus.fromServer('success'), ReceiptStatus.success);
      expect(ReceiptStatus.fromServer(' Failed '), ReceiptStatus.failed);
      expect(ReceiptStatus.fromServer('FAILED'), ReceiptStatus.failed);
    });

    test('a receipt that is scanned, being read or waiting for an administrator is processing', () {
      for (final name in ['PENDING', 'WAITING_FOR_REVIEW', 'PROCESSING', 'pending']) {
        expect(ReceiptStatus.fromServer(name), ReceiptStatus.processing, reason: name);
      }
    });

    test('nothing is shown for no receipt, a withdrawn one or a status the app does not know', () {
      for (final Object? raw in [null, '', '   ', 'CANCELED', 'SOMETHING_NEW', 42, true]) {
        expect(ReceiptStatus.fromServer(raw), isNull, reason: '$raw');
      }
    });

    test('the badge texts are the ones of the task', () {
      expect(ReceiptStatus.processing.label, 'Đang xử lý');
      expect(ReceiptStatus.success.label, 'Hoàn tất');
      expect(ReceiptStatus.failed.label, 'Lỗi · cần xác nhận lại');
    });
  });

  group('TransactionModel.fromJson', () {
    test('reads a transaction of the server together with the state of its receipt', () {
      final tx = TransactionModel.fromJson({
        'id': 12,
        'title': 'Cà phê',
        'amount': 45000,
        'date': '2026-09-20T08:30:00',
        'category': 'Ăn uống',
        'isExpense': true,
        'imageUrl': 'https://img.example/receipt.jpg',
        'receiptStatus': 'FAILED',
      });

      expect(tx.id, '12');
      expect(tx.title, 'Cà phê');
      expect(tx.amount, 45000.0);
      expect(tx.date, DateTime(2026, 9, 20, 8, 30));
      expect(tx.category, 'Ăn uống');
      expect(tx.iconName, 'Ăn uống');
      expect(tx.isExpense, isTrue);
      expect(tx.imageUrl, 'https://img.example/receipt.jpg');
      expect(tx.receiptStatus, ReceiptStatus.failed);
    });

    test('a transaction without a receipt has no receipt state, which is what the server sends today', () {
      final tx = TransactionModel.fromJson({
        'id': 1,
        'title': 'Lương',
        'amount': 9000000,
        'date': '2026-09-01T09:00:00',
        'category': 'Thu nhập',
        'isExpense': false,
      });

      expect(tx.receiptStatus, isNull);
      expect(tx.imageUrl, isNull);
      expect(tx.isExpense, isFalse);
    });

    test('an unknown receipt status is ignored instead of breaking the list', () {
      final tx = TransactionModel.fromJson({'id': 1, 'amount': 1, 'category': 'Khác', 'receiptStatus': 'NEW_STATE'});

      expect(tx.receiptStatus, isNull);
    });

    test('falls back the way the list always did: the category as title, "Khác" as category, now as date', () {
      final before = DateTime.now();
      final tx = TransactionModel.fromJson({'id': 3});

      expect(tx.title, '');
      expect(tx.category, 'Khác');
      expect(tx.amount, 0.0);
      expect(tx.isExpense, isFalse);
      expect(tx.date.isBefore(before), isFalse);

      final noTitle = TransactionModel.fromJson({'id': 4, 'category': 'Đi lại'});
      expect(noTitle.title, 'Đi lại');
    });
  });

  group('offline (SQLite) mapping', () {
    test('never carries the receipt state, so local inserts and reads keep their columns', () {
      final tx = TransactionModel(
        title: 'Cà phê',
        amount: 45000,
        date: DateTime(2026, 9, 20),
        category: 'Ăn uống',
        isExpense: true,
        receiptStatus: ReceiptStatus.success,
      );

      expect(
        tx.toDbMap(1).keys,
        unorderedEquals(['user_id', 'title', 'amount', 'date', 'category', 'is_expense', 'icon_name', 'wallet_id']),
      );
      expect(
        tx.toMap().keys,
        unorderedEquals(['title', 'amount', 'date', 'category', 'isExpense', 'icon_name']),
      );
    });

    test('rows read from SQLite have no receipt state', () {
      final tx = TransactionModel.fromDbMap({
        'id': 3,
        'title': 'Cà phê',
        'amount': 45000,
        'date': '2026-09-20T08:30:00.000',
        'category': 'Ăn uống',
        'is_expense': 1,
        'icon_name': 'Ăn uống',
        'wallet_id': null,
      });

      expect(tx.receiptStatus, isNull);
      expect(tx.isExpense, isTrue);
    });
  });
}

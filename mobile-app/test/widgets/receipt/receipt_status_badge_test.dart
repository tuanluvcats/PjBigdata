import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:finance_ai_app/core/theme/app_theme.dart';
import 'package:finance_ai_app/models/receipt_status.dart';
import 'package:finance_ai_app/widgets/receipt/receipt_status_badge.dart';

Future<void> _pump(WidgetTester tester, ReceiptStatus status, {double? width}) {
  final badge = ReceiptStatusBadge(status: status);
  return tester.pumpWidget(
    MaterialApp(
      home: Scaffold(
        body: Align(
          alignment: Alignment.topLeft,
          child: width == null ? badge : SizedBox(width: width, child: badge),
        ),
      ),
    ),
  );
}

Color? _labelColor(WidgetTester tester, String label) =>
    tester.widget<Text>(find.text(label)).style?.color;

void main() {
  testWidgets(
    'processing shows "Đang xử lý" with a sync icon in the warning colour',
    (tester) async {
      await _pump(tester, ReceiptStatus.processing);

      expect(find.text('Đang xử lý'), findsOneWidget);
      expect(find.byIcon(Icons.sync_rounded), findsOneWidget);
      expect(_labelColor(tester, 'Đang xử lý'), AppTheme.warningColor);
    },
  );

  testWidgets(
    'success shows "Hoàn tất" with a check icon in the positive colour',
    (tester) async {
      await _pump(tester, ReceiptStatus.success);

      expect(find.text('Hoàn tất'), findsOneWidget);
      expect(find.byIcon(Icons.check_circle_rounded), findsOneWidget);
      expect(_labelColor(tester, 'Hoàn tất'), AppTheme.positive);
    },
  );

  testWidgets(
    'failed shows "Lỗi · cần xác nhận lại" with an error icon in the danger colour',
    (tester) async {
      await _pump(tester, ReceiptStatus.failed);

      expect(find.text('Lỗi · cần xác nhận lại'), findsOneWidget);
      expect(find.byIcon(Icons.error_rounded), findsOneWidget);
      expect(
        _labelColor(tester, 'Lỗi · cần xác nhận lại'),
        AppTheme.dangerColor,
      );
    },
  );

  testWidgets(
    'in a very narrow space the label is cut on one line instead of overflowing',
    (tester) async {
      await _pump(tester, ReceiptStatus.failed, width: 80);

      expect(tester.takeException(), isNull);
      expect(
        tester.getSize(find.byType(ReceiptStatusBadge)).width,
        lessThanOrEqualTo(80),
      );
    },
  );
}

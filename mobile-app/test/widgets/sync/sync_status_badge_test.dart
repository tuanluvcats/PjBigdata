import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:finance_ai_app/core/theme/app_theme.dart';
import 'package:finance_ai_app/models/sync_status.dart';
import 'package:finance_ai_app/widgets/sync/sync_status_badge.dart';

Future<void> _pump(WidgetTester tester, SyncStatus status, {double? width}) {
  final badge = SyncStatusBadge(status: status);
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

Color? _labelColor(WidgetTester tester, String label) => tester.widget<Text>(find.text(label)).style?.color;

void main() {
  testWidgets('pending shows "Chờ gửi" with an upload icon in the warning colour', (tester) async {
    await _pump(tester, SyncStatus.pending);

    expect(find.text('Chờ gửi'), findsOneWidget);
    expect(find.byIcon(Icons.cloud_upload_outlined), findsOneWidget);
    expect(_labelColor(tester, 'Chờ gửi'), AppTheme.warningColor);
  });

  testWidgets('error shows "Gửi lỗi" with a cloud-off icon in the danger colour', (tester) async {
    await _pump(tester, SyncStatus.error);

    expect(find.text('Gửi lỗi'), findsOneWidget);
    expect(find.byIcon(Icons.cloud_off_rounded), findsOneWidget);
    expect(_labelColor(tester, 'Gửi lỗi'), AppTheme.dangerColor);
  });

  testWidgets('synced shows "Đã đồng bộ" with a check icon in the positive colour', (tester) async {
    await _pump(tester, SyncStatus.synced);

    expect(find.text('Đã đồng bộ'), findsOneWidget);
    expect(find.byIcon(Icons.cloud_done_rounded), findsOneWidget);
    expect(_labelColor(tester, 'Đã đồng bộ'), AppTheme.positive);
  });

  testWidgets('in a very narrow space the label is cut on one line instead of overflowing', (tester) async {
    await _pump(tester, SyncStatus.error, width: 60);

    expect(tester.takeException(), isNull);
    expect(tester.getSize(find.byType(SyncStatusBadge)).width, lessThanOrEqualTo(60));
  });
}

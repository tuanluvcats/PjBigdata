import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:finance_ai_app/core/extensions/build_context_extension.dart';
import 'package:finance_ai_app/core/theme/app_theme.dart';
import 'package:finance_ai_app/l10n/app_localizations.dart';

void main() {
  late BuildContext captured;

  Widget app({
    Locale locale = const Locale('vi'),
    ThemeData? theme,
    bool localized = true,
  }) => MaterialApp(
    locale: localized ? locale : null,
    theme: theme,
    localizationsDelegates: localized
        ? AppLocalizations.localizationsDelegates
        : null,
    supportedLocales: localized
        ? AppLocalizations.supportedLocales
        : const [Locale('en', 'US')],
    home: Builder(
      builder: (context) {
        captured = context;
        return const SizedBox();
      },
    ),
  );

  testWidgets('context.loc gives the strings of the current language', (
    tester,
  ) async {
    await tester.pumpWidget(app());
    expect(captured.loc.settings, 'Cài đặt');

    await tester.pumpWidget(app(locale: const Locale('en')));
    expect(captured.loc.settings, 'Settings');
  });

  testWidgets(
    'context.loc says what is missing when the app has no AppLocalizations',
    (tester) async {
      await tester.pumpWidget(app(localized: false));

      expect(
        () => captured.loc,
        throwsA(
          isA<FlutterError>().having(
            (e) => e.message,
            'message',
            contains('localizationsDelegates'),
          ),
        ),
      );
    },
  );

  testWidgets('context.colors reads SemanticColors from the current theme', (
    tester,
  ) async {
    const custom = SemanticColors(
      incomeColor: Color(0xFF00FF00),
      expenseColor: Color(0xFFFF0000),
    );

    await tester.pumpWidget(app(theme: ThemeData(extensions: const [custom])));

    expect(captured.colors, same(custom));
  });

  testWidgets(
    'context.colors fails an assert in debug builds when the theme has no SemanticColors',
    (tester) async {
      await tester.pumpWidget(app(theme: ThemeData()));

      expect(() => captured.colors, throwsAssertionError);
    },
  );
}

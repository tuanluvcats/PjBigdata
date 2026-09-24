import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:finance_ai_app/core/theme/app_theme.dart';

List<TextStyle?> _allStyles(TextTheme theme) => [
  theme.displayLarge,
  theme.displayMedium,
  theme.displaySmall,
  theme.headlineLarge,
  theme.headlineMedium,
  theme.headlineSmall,
  theme.titleLarge,
  theme.titleMedium,
  theme.titleSmall,
  theme.bodyLarge,
  theme.bodyMedium,
  theme.bodySmall,
  theme.labelLarge,
  theme.labelMedium,
  theme.labelSmall,
];

void main() {
  // The themes load Poppins through google_fonts; inside testWidgets that load never reaches the network.
  testWidgets(
    'both themes are Material 3 and carry the semantic colours of their brightness',
    (tester) async {
      final light = AppTheme.lightTheme;
      final dark = AppTheme.darkTheme;

      expect(light.useMaterial3, isTrue);
      expect(dark.useMaterial3, isTrue);
      expect(light.brightness, Brightness.light);
      expect(dark.brightness, Brightness.dark);
      expect(light.extension<SemanticColors>(), SemanticColors.light);
      expect(dark.extension<SemanticColors>(), SemanticColors.dark);
    },
  );

  testWidgets('the light theme keeps the brand colours', (tester) async {
    final light = AppTheme.lightTheme;

    expect(light.colorScheme.primary, AppTheme.primary);
    expect(light.scaffoldBackgroundColor, AppTheme.bgLight);
    expect(light.cardTheme.color, Colors.white);
    expect(light.appBarTheme.backgroundColor, AppTheme.bgLight);
  });

  testWidgets(
    'status bar and navigation bar icons are dark on the light theme and light on the dark theme',
    (tester) async {
      final lightBars = AppTheme.lightTheme.appBarTheme.systemOverlayStyle;
      final darkBars = AppTheme.darkTheme.appBarTheme.systemOverlayStyle;

      expect(lightBars, AppTheme.lightOverlayStyle);
      expect(darkBars, AppTheme.darkOverlayStyle);
      expect(lightBars!.statusBarIconBrightness, Brightness.dark);
      expect(lightBars.systemNavigationBarIconBrightness, Brightness.dark);
      expect(darkBars!.statusBarIconBrightness, Brightness.light);
      expect(darkBars.systemNavigationBarIconBrightness, Brightness.light);
      for (final bars in [lightBars, darkBars]) {
        expect(bars.statusBarColor, isNotNull);
        expect(bars.systemNavigationBarColor, isNotNull);
      }
      expect(
        AppTheme.overlayStyleFor(Brightness.light),
        AppTheme.lightOverlayStyle,
      );
      expect(
        AppTheme.overlayStyleFor(Brightness.dark),
        AppTheme.darkOverlayStyle,
      );
    },
  );

  testWidgets('every text style uses tabular figures so that amounts line up', (
    tester,
  ) async {
    for (final theme in [AppTheme.lightTheme, AppTheme.darkTheme]) {
      for (final style in [
        ..._allStyles(theme.textTheme),
        ..._allStyles(theme.primaryTextTheme),
      ]) {
        expect(
          style?.fontFeatures,
          contains(const FontFeature.tabularFigures()),
        );
      }
    }
  });

  group('SemanticColors', () {
    const green = Color(0xFF00FF00);
    const red = Color(0xFFFF0000);
    const colors = SemanticColors(incomeColor: green, expenseColor: red);

    test('copyWith replaces only the colours it is given', () {
      expect(
        colors.copyWith(expenseColor: green),
        const SemanticColors(incomeColor: green, expenseColor: green),
      );
      expect(colors.copyWith(), colors);
    });

    test('lerp blends both colours, and keeps its own without a target', () {
      const black = SemanticColors(
        incomeColor: Color(0xFF000000),
        expenseColor: Color(0xFF000000),
      );

      final half = colors.lerp(black, 0.5);

      expect(half.incomeColor, Color.lerp(green, const Color(0xFF000000), 0.5));
      expect(half.expenseColor, Color.lerp(red, const Color(0xFF000000), 0.5));
      expect(colors.lerp(null, 0.5), same(colors));
    });

    test('two instances with the same colours are equal', () {
      expect(
        const SemanticColors(incomeColor: green, expenseColor: red),
        colors,
      );
      expect(
        const SemanticColors(incomeColor: green, expenseColor: red).hashCode,
        colors.hashCode,
      );
    });
  });
}

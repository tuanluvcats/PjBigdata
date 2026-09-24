import 'package:flutter/material.dart';

import 'package:finance_ai_app/core/theme/app_theme.dart';
import 'package:finance_ai_app/l10n/app_localizations.dart';

extension BuildContextExtension on BuildContext {
  AppLocalizations get loc {
    final localizations = Localizations.of<AppLocalizations>(
      this,
      AppLocalizations,
    );
    if (localizations == null) {
      throw FlutterError(
        'context.loc found no AppLocalizations: pass AppLocalizations.localizationsDelegates to the MaterialApp.',
      );
    }
    return localizations;
  }

  SemanticColors get colors {
    final theme = Theme.of(this);
    final colors = theme.extension<SemanticColors>();
    assert(
      colors != null,
      'context.colors: SemanticColors is not registered in ThemeData.extensions.',
    );
    return colors ??
        (theme.brightness == Brightness.dark
            ? SemanticColors.dark
            : SemanticColors.light);
  }
}

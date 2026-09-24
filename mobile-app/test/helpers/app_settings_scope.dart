import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';

import 'package:finance_ai_app/cubits/language_cubit.dart';
import 'package:finance_ai_app/cubits/theme_cubit.dart';
import 'package:finance_ai_app/l10n/app_localizations.dart';
import 'package:finance_ai_app/repositories/settings_repository.dart';

import 'fake_settings_repository.dart';

/// Puts [home] under what DonutPayApp gives every screen: the theme and language cubits and the app's localizations.
Widget withAppSettings(Widget home, {SettingsRepository? settings}) {
  final repository = settings ?? FakeSettingsRepository();
  return MultiBlocProvider(
    providers: [
      BlocProvider(create: (_) => ThemeCubit(repository)),
      BlocProvider(create: (_) => LanguageCubit(repository)),
    ],
    child: MaterialApp(
      locale: repository.language.locale,
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      home: home,
    ),
  );
}

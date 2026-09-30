// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for English (`en`).
class AppLocalizationsEn extends AppLocalizations {
  AppLocalizationsEn([String locale = 'en']) : super(locale);

  @override
  String get settings => 'Settings';

  @override
  String get language => 'Language';

  @override
  String get theme => 'Theme';

  @override
  String get dark => 'Dark';

  @override
  String get light => 'Light';

  @override
  String get system => 'System';

  @override
  String get systemThemeDescription => 'Follows your device setting';

  @override
  String get english => 'English';

  @override
  String get vietnamese => 'Tiếng Việt';

  @override
  String get settingsSaveFailed =>
      'Couldn\'t save this setting. The change applies until you close the app.';

  @override
  String get notificationsTitle => 'Notifications';

  @override
  String get notificationsMarkAllRead => 'Mark all as read';

  @override
  String get notificationsEmpty => 'You have no notifications yet.';

  @override
  String get notificationsLoadFailed => 'Couldn\'t load your notifications.';

  @override
  String get notificationsLoadMoreFailed =>
      'Couldn\'t load more notifications.';

  @override
  String get notificationsRetry => 'Try again';

  @override
  String get notificationsMarkReadFailed =>
      'Couldn\'t mark the notification as read. Please try again.';

  @override
  String get notificationsMarkAllReadFailed =>
      'Couldn\'t mark your notifications as read. Please try again.';

  @override
  String get notificationsDetailUnavailable =>
      'This notification can\'t be opened yet.';

  @override
  String get notificationsUnread => 'Unread';

  @override
  String get notificationTypeBudgetWarning => 'Budget almost used up';

  @override
  String get notificationTypeBudgetExceeded => 'Budget exceeded';

  @override
  String get notificationTypeTransactionAlert => 'Transaction alert';

  @override
  String get notificationTypeReceiptProcessed => 'Receipt processed';

  @override
  String get notificationTypeOther => 'Notification';

  @override
  String get sessionEnded =>
      'Your session has ended, for example because your password was changed. Please sign in again.';
}

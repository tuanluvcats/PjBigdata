import 'package:flutter/material.dart';

import 'package:finance_ai_app/core/theme/app_theme.dart';

class AppDialog {
  static Future<void> success(
    BuildContext context, {
    required String title,
    String? message,
    String buttonText = 'OK',
  }) =>
      _show(
        context,
        icon: Icons.check_circle_rounded,
        accent: AppTheme.positive,
        title: title,
        message: message,
        confirmText: buttonText,
      ).then((_) {});

  static Future<void> error(
    BuildContext context, {
    required String title,
    String? message,
    String buttonText = 'Đã hiểu',
  }) =>
      _show(
        context,
        icon: Icons.error_rounded,
        accent: AppTheme.dangerColor,
        title: title,
        message: message,
        confirmText: buttonText,
      ).then((_) {});

  static Future<void> info(
    BuildContext context, {
    required String title,
    String? message,
    String buttonText = 'OK',
  }) =>
      _show(
        context,
        icon: Icons.info_rounded,
        accent: AppTheme.primary,
        title: title,
        message: message,
        confirmText: buttonText,
      ).then((_) {});

  static Future<bool> confirm(
    BuildContext context, {
    required String title,
    String? message,
    String confirmText = 'Đồng ý',
    String cancelText = 'Hủy',
    bool danger = false,
  }) async {
    final r = await _show(
      context,
      icon: danger ? Icons.warning_amber_rounded : Icons.help_outline_rounded,
      accent: danger ? AppTheme.dangerColor : AppTheme.primary,
      title: title,
      message: message,
      confirmText: confirmText,
      cancelText: cancelText,
    );
    return r == true;
  }

  static Future<bool?> _show(
    BuildContext context, {
    required IconData icon,
    required Color accent,
    required String title,
    String? message,
    required String confirmText,
    String? cancelText,
  }) {
    return showGeneralDialog<bool>(
      context: context,
      barrierDismissible: cancelText == null,
      barrierLabel: 'dialog',
      barrierColor: Colors.black54,
      transitionDuration: const Duration(milliseconds: 240),
      transitionBuilder: (ctx, anim, _, child) {
        final curved =
            CurvedAnimation(parent: anim, curve: Curves.easeOutBack);
        return Opacity(
          opacity: anim.value.clamp(0.0, 1.0),
          child: Transform.scale(scale: 0.85 + 0.15 * curved.value, child: child),
        );
      },
      pageBuilder: (ctx, _, _) => Dialog(
        backgroundColor: Colors.white,
        elevation: 0,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(24)),
        insetPadding: const EdgeInsets.symmetric(horizontal: 32),
        child: Padding(
          padding: const EdgeInsets.fromLTRB(24, 28, 24, 20),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Container(
                width: 64,
                height: 64,
                decoration: BoxDecoration(
                  color: accent.withValues(alpha: 0.12),
                  shape: BoxShape.circle,
                ),
                child: Icon(icon, color: accent, size: 34),
              ),
              const SizedBox(height: 18),
              Text(
                title,
                textAlign: TextAlign.center,
                style: const TextStyle(
                  color: AppTheme.textPrimary,
                  fontSize: 18,
                  fontWeight: FontWeight.bold,
                ),
              ),
              if (message != null) ...[
                const SizedBox(height: 8),
                Text(
                  message,
                  textAlign: TextAlign.center,
                  style: const TextStyle(
                    color: AppTheme.textSecondary,
                    fontSize: 14,
                    height: 1.4,
                  ),
                ),
              ],
              const SizedBox(height: 24),
              Row(
                children: [
                  if (cancelText != null) ...[
                    Expanded(
                      child: OutlinedButton(
                        onPressed: () => Navigator.pop(ctx, false),
                        style: OutlinedButton.styleFrom(
                          foregroundColor: AppTheme.textSecondary,
                          side: BorderSide(
                              color: AppTheme.textGray.withValues(alpha: 0.4)),
                          padding: const EdgeInsets.symmetric(vertical: 14),
                          shape: RoundedRectangleBorder(
                              borderRadius: BorderRadius.circular(14)),
                        ),
                        child: Text(cancelText),
                      ),
                    ),
                    const SizedBox(width: 12),
                  ],
                  Expanded(
                    child: ElevatedButton(
                      onPressed: () => Navigator.pop(ctx, true),
                      style: ElevatedButton.styleFrom(
                        backgroundColor: accent,
                        foregroundColor: Colors.white,
                        elevation: 0,
                        padding: const EdgeInsets.symmetric(vertical: 14),
                        shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(14)),
                      ),
                      child: Text(confirmText),
                    ),
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}

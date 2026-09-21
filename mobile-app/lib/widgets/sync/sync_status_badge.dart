import 'package:flutter/material.dart';

import 'package:finance_ai_app/core/theme/app_theme.dart';
import 'package:finance_ai_app/models/sync_status.dart';

/// Small pill telling whether the server has a transaction that was created on this device. Callers show it only for a
/// transaction that is not synced (see TransactionModel.isUnsynced), so a transaction the server has stays without one.
class SyncStatusBadge extends StatelessWidget {
  final SyncStatus status;

  const SyncStatusBadge({super.key, required this.status});

  Color get _color => switch (status) {
        SyncStatus.pending => AppTheme.warningColor,
        SyncStatus.synced => AppTheme.positive,
        SyncStatus.error => AppTheme.dangerColor,
      };

  IconData get _icon => switch (status) {
        SyncStatus.pending => Icons.cloud_upload_outlined,
        SyncStatus.synced => Icons.cloud_done_rounded,
        SyncStatus.error => Icons.cloud_off_rounded,
      };

  @override
  Widget build(BuildContext context) {
    final color = _color;
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.12),
        borderRadius: BorderRadius.circular(999),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(_icon, size: 13, color: color),
          const SizedBox(width: 4),
          Flexible(
            child: Text(
              status.label,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: TextStyle(color: color, fontSize: 11, fontWeight: FontWeight.w600),
            ),
          ),
        ],
      ),
    );
  }
}

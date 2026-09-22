import 'package:flutter/material.dart';

import 'package:finance_ai_app/core/theme/app_theme.dart';
import 'package:finance_ai_app/models/spending_range.dart';

/// The time filter above the spending chart: which period the report covers, the days that period spans, and a button
/// that runs the analysis again. It only reports what the user tapped; the screen keeps the state and calls the API.
class SpendingRangeFilter extends StatelessWidget {
  static const String weekLabel = 'Tuần này';
  static const String monthLabel = 'Tháng này';
  static const String customLabel = 'Tùy chọn';
  static const String refreshLabel = 'Làm mới';

  static const double _optionSpacing = 8;
  static const double _refreshIconSize = 18;

  const SpendingRangeFilter({
    super.key,
    required this.range,
    required this.isRefreshing,
    required this.onPeriodSelected,
    required this.onRefresh,
  });

  /// The range on screen; its period is the selected option and its dates are shown under the options.
  final SpendingRange range;

  /// True while the analysis runs: the refresh button then shows a spinner and cannot be pressed.
  final bool isRefreshing;

  /// Called with the option the user tapped, also when it is the one already selected (the screen decides what that
  /// means: for [SpendingPeriod.custom] it opens the date picker again).
  final ValueChanged<SpendingPeriod> onPeriodSelected;

  final VoidCallback onRefresh;

  static String _labelOf(SpendingPeriod period) {
    switch (period) {
      case SpendingPeriod.week:
        return weekLabel;
      case SpendingPeriod.month:
        return monthLabel;
      case SpendingPeriod.custom:
        return customLabel;
    }
  }

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Wrap(
          spacing: _optionSpacing,
          runSpacing: _optionSpacing,
          children: [
            for (final period in SpendingPeriod.values) _buildOption(period),
          ],
        ),
        const SizedBox(height: 4),
        Row(
          children: [
            Expanded(
              child: Text(
                range.label,
                style: const TextStyle(color: AppTheme.textGray, fontSize: 12),
              ),
            ),
            _buildRefreshButton(),
          ],
        ),
      ],
    );
  }

  Widget _buildOption(SpendingPeriod period) {
    final selected = range.period == period;
    return ChoiceChip(
      label: Text(_labelOf(period)),
      selected: selected,
      onSelected: (_) => onPeriodSelected(period),
      showCheckmark: false,
      selectedColor: AppTheme.mintLight,
      backgroundColor: Colors.white,
      side: BorderSide(
        color: selected
            ? AppTheme.primary
            : AppTheme.textGray.withValues(alpha: 0.3),
      ),
      shape: const StadiumBorder(),
      labelStyle: TextStyle(
        color: selected ? AppTheme.primary : AppTheme.textGray,
        fontSize: 13,
        fontWeight: FontWeight.w600,
      ),
    );
  }

  Widget _buildRefreshButton() {
    return TextButton(
      onPressed: isRefreshing ? null : onRefresh,
      style: TextButton.styleFrom(
        foregroundColor: AppTheme.primary,
        visualDensity: VisualDensity.compact,
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          isRefreshing
              ? const SizedBox(
                  width: _refreshIconSize,
                  height: _refreshIconSize,
                  child: CircularProgressIndicator(
                    strokeWidth: 2,
                    color: AppTheme.primary,
                  ),
                )
              : const Icon(Icons.refresh_rounded, size: _refreshIconSize),
          const SizedBox(width: 6),
          const Text(
            refreshLabel,
            style: TextStyle(fontSize: 13, fontWeight: FontWeight.w600),
          ),
        ],
      ),
    );
  }
}

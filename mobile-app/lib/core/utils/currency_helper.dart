class CurrencyHelper {

  static String formatVND(double amount, {bool showSign = true}) {
    final isNegative = amount < 0;
    final abs = amount.abs();

    final formatted = abs
        .toStringAsFixed(0)
        .replaceAllMapped(
          RegExp(r'(\d{1,3})(?=(\d{3})+(?!\d))'),
          (m) => '${m[1]},',
        );

    if (formatted == '0') return '0 đ';

    if (!showSign) {
      return '$formatted đ';
    }

    if (isNegative) {
      return '-$formatted đ';
    }

    return '+$formatted đ';
  }
}

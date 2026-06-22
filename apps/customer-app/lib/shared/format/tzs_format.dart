import 'package:intl/intl.dart';

/// TZS currency formatter.
/// Returns "TZS 5,000" (no decimals).
String tzs(int amount) {
  final fmt = NumberFormat('#,###', 'sw_TZ');
  return 'TZS ${fmt.format(amount)}';
}

/// Credit count formatter.
/// Returns "150 credits" (no TZS prefix — credits are the unit).
String credits(int count) {
  final fmt = NumberFormat('#,###', 'sw_TZ');
  return '${fmt.format(count)} credits';
}

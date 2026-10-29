/// Formats integer paise as ₹ with Indian digit grouping, e.g. 12345678 -> ₹1,23,456.78
String formatPaise(int paise) {
  final sign = paise < 0 ? '-' : '';
  final abs = paise.abs();
  final rupees = (abs ~/ 100).toString();
  final fraction = (abs % 100).toString().padLeft(2, '0');
  return '$sign₹${_indianGrouping(rupees)}.$fraction';
}

String _indianGrouping(String digits) {
  if (digits.length <= 3) return digits;
  final head = digits.substring(0, digits.length - 3);
  final tail = digits.substring(digits.length - 3);
  final groups = <String>[];
  for (var i = head.length; i > 0; i -= 2) {
    groups.insert(0, head.substring(i - 2 < 0 ? 0 : i - 2, i));
  }
  return '${groups.join(',')},$tail';
}

/// Parses a number with up to 2 decimals ("12", "12.5", "12.50") into hundredths (1250). Null if invalid.
/// Used for rupees -> paise and for percentages -> basis points.
int? parseHundredths(String input) {
  final m = RegExp(r'^(\d{1,12})(?:\.(\d{1,2}))?$').firstMatch(input.trim());
  if (m == null) return null;
  return int.parse(m.group(1)!) * 100 + int.parse((m.group(2) ?? '').padRight(2, '0'));
}

/// Inverse of [parseHundredths] for filling text fields: 1250 -> "12.5", 1200 -> "12".
String hundredthsToInput(int hundredths) {
  final whole = hundredths ~/ 100;
  final frac = hundredths % 100;
  if (frac == 0) return '$whole';
  return '$whole.${frac.toString().padLeft(2, '0').replaceFirst(RegExp(r'0$'), '')}';
}

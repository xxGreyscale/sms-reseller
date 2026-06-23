import 'package:flutter/material.dart';

// ---------------------------------------------------------------------------
// GSM-7 character set (basic + extended)
// ---------------------------------------------------------------------------

/// Full GSM-7 basic charset (128 chars).
const _gsm7Basic = r'@£$¥èéùìòÇ'
    '\n'
    'Øø'
    '\r'
    'ÅåΔ_ΦΓΛΩΠΨΣΘΞ'
    '\x1B'
    'ÆæßÉ !"#¤%&\'()*+,-./0123456789:;<=>?¡ABCDEFGHIJKLMNOPQRSTUVWXYZ'
    'ÄÖÑÜ§¿abcdefghijklmnopqrstuvwxyz'
    'äöñüà';

/// GSM-7 extended charset (counts as 2 in single-part; 2 chars used).
const _gsm7Extended = r'{}[]|^~\€';

bool _isGsm7Char(String ch) {
  return _gsm7Basic.contains(ch) || _gsm7Extended.contains(ch);
}

// ---------------------------------------------------------------------------
// SmsCountResult
// ---------------------------------------------------------------------------

class SmsCountResult {
  /// Total character count.
  final int charCount;

  /// Maximum characters per part for the encoding.
  /// Single-part: 160 (GSM-7) or 70 (UCS-2).
  /// Multi-part:  153 (GSM-7) or 67 (UCS-2).
  final int maxPerPart;

  /// Number of SMS parts required.
  final int partCount;

  /// True if any character requires UCS-2 encoding.
  final bool isUcs2;

  const SmsCountResult({
    required this.charCount,
    required this.maxPerPart,
    required this.partCount,
    required this.isUcs2,
  });

  /// Display string matching UI-SPEC format:
  /// GSM-7  single: "120/160 · 1 SMS"
  /// GSM-7  multi:  "170/306 · 2 SMS"
  /// UCS-2  single: "45/70 · 1 SMS (UCS-2)"
  /// UCS-2  multi:  "75/134 · 2 SMS (UCS-2)"
  String get displayString {
    final capacity = maxPerPart * partCount;
    final parts = '$charCount/$capacity · $partCount SMS';
    return isUcs2 ? '$parts (UCS-2)' : parts;
  }
}

// ---------------------------------------------------------------------------
// SmsCounter — pure Dart, no widgets
// ---------------------------------------------------------------------------

class SmsCounter {
  SmsCounter._();

  static SmsCountResult count(String text) {
    if (text.isEmpty) {
      return const SmsCountResult(
        charCount: 0,
        maxPerPart: 160,
        partCount: 1,
        isUcs2: false,
      );
    }

    bool ucs2 = false;
    for (final ch in text.characters) {
      if (!_isGsm7Char(ch)) {
        ucs2 = true;
        break;
      }
    }

    // Use grapheme cluster count (handles emoji/multi-codepoint chars correctly).
    final charCount = text.characters.length;

    if (ucs2) {
      const singleMax = 70;
      const multiMax = 67;
      if (charCount <= singleMax) {
        return SmsCountResult(
          charCount: charCount,
          maxPerPart: singleMax,
          partCount: 1,
          isUcs2: true,
        );
      } else {
        final parts = (charCount / multiMax).ceil();
        return SmsCountResult(
          charCount: charCount,
          maxPerPart: multiMax,
          partCount: parts,
          isUcs2: true,
        );
      }
    } else {
      const singleMax = 160;
      const multiMax = 153;
      if (charCount <= singleMax) {
        return SmsCountResult(
          charCount: charCount,
          maxPerPart: singleMax,
          partCount: 1,
          isUcs2: false,
        );
      } else {
        final parts = (charCount / multiMax).ceil();
        return SmsCountResult(
          charCount: charCount,
          maxPerPart: multiMax,
          partCount: parts,
          isUcs2: false,
        );
      }
    }
  }
}

// ---------------------------------------------------------------------------
// SmsCharCounter widget
// ---------------------------------------------------------------------------

/// Displays the SMS character count in real-time.
///
/// Shows:
///   "120/160 · 1 SMS"             (GSM-7)
///   "⚠ 45/70 · 1 SMS (UCS-2)"    (UCS-2, error color, non-blocking)
class SmsCharCounter extends StatelessWidget {
  final String text;

  const SmsCharCounter({super.key, required this.text});

  @override
  Widget build(BuildContext context) {
    final result = SmsCounter.count(text);
    final theme = Theme.of(context);
    final color =
        result.isUcs2 ? theme.colorScheme.error : theme.colorScheme.onSurface;
    final label = result.isUcs2
        ? '⚠ ${result.displayString}'
        : result.displayString;

    return Text(
      label,
      style: theme.textTheme.labelMedium?.copyWith(color: color),
    );
  }
}

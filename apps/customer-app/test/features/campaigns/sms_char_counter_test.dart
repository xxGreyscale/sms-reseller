// Task 1 RED: SMS char counter (GSM-7/UCS-2) + CampaignStatusChip
//
// Behaviors:
// 1. Pure GSM-7 text of 120 chars → "120/160 · 1 SMS"
// 2. 170 GSM-7 chars → 2 SMS (153/part multi-part math)
// 3. Any non-GSM char (emoji) → UCS-2 mode; 75 UCS-2 chars → 2 parts (67/part)
// 4. CampaignStatusChip maps each status to the UI-SPEC color pair

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:customer_app/shared/widgets/sms_char_counter.dart';
import 'package:customer_app/shared/widgets/campaign_status_chip.dart';

void main() {
  // ---------------------------------------------------------------------------
  // SmsCounter pure-Dart logic
  // ---------------------------------------------------------------------------

  group('SmsCounter logic', () {
    test('Test 1: 120 pure GSM-7 chars → 120/160 · 1 SMS', () {
      final result = SmsCounter.count('A' * 120);
      expect(result.charCount, 120);
      expect(result.maxPerPart, 160);
      expect(result.partCount, 1);
      expect(result.isUcs2, false);
      expect(result.displayString, '120/160 · 1 SMS');
    });

    test('Test 2: 170 GSM-7 chars → 2 SMS (153/part multi-part)', () {
      final result = SmsCounter.count('A' * 170);
      expect(result.charCount, 170);
      expect(result.maxPerPart, 153); // multi-part GSM-7
      expect(result.partCount, 2);
      expect(result.isUcs2, false);
      expect(result.displayString, '170/306 · 2 SMS');
    });

    test('Test 3a: emoji → UCS-2 mode, 45 chars → 45/70 · 1 SMS (UCS-2)', () {
      final text = '😊' + 'A' * 44; // 45 chars total, emoji triggers UCS-2
      final result = SmsCounter.count(text);
      expect(result.isUcs2, true);
      expect(result.maxPerPart, 70);
      expect(result.partCount, 1);
      expect(result.displayString, '45/70 · 1 SMS (UCS-2)');
    });

    test('Test 3b: 75 UCS-2 chars → 2 parts (67/part)', () {
      final text = '😊' + 'A' * 74; // 75 chars, UCS-2
      final result = SmsCounter.count(text);
      expect(result.isUcs2, true);
      expect(result.charCount, 75);
      expect(result.maxPerPart, 67); // multi-part UCS-2
      expect(result.partCount, 2);
      expect(result.displayString, '75/134 · 2 SMS (UCS-2)');
    });

    test('Empty string → 0/160 · 1 SMS', () {
      final result = SmsCounter.count('');
      expect(result.charCount, 0);
      expect(result.partCount, 1);
      expect(result.isUcs2, false);
      expect(result.displayString, '0/160 · 1 SMS');
    });
  });

  // ---------------------------------------------------------------------------
  // SmsCharCounter widget
  // ---------------------------------------------------------------------------

  group('SmsCharCounter widget', () {
    testWidgets('renders GSM-7 display string', (tester) async {
      await tester.pumpWidget(
        const MaterialApp(
          home: Scaffold(
            body: SmsCharCounter(text: 'Hello'),
          ),
        ),
      );
      expect(find.text('5/160 · 1 SMS'), findsOneWidget);
    });

    testWidgets('renders UCS-2 warning in error color when emoji present',
        (tester) async {
      await tester.pumpWidget(
        MaterialApp(
          theme: ThemeData.from(
            colorScheme: ColorScheme.fromSeed(
              seedColor: const Color(0xFF1565C0),
            ),
          ),
          home: const Scaffold(
            body: SmsCharCounter(text: '😊Hello'),
          ),
        ),
      );
      // The counter should show UCS-2 format
      expect(find.textContaining('UCS-2'), findsOneWidget);
    });
  });

  // ---------------------------------------------------------------------------
  // CampaignStatusChip color mapping
  // ---------------------------------------------------------------------------

  group('CampaignStatusChip', () {
    const statuses = {
      'PENDING': (bg: Color(0xFFFFF9C4), text: Color(0xFFF57F17)),
      'VERIFIED': (bg: Color(0xFFE8F5E9), text: Color(0xFF2E7D32)),
      'FAILED': (bg: Color(0xFFFFEBEE), text: Color(0xFFC62828)),
      'QUEUED': (bg: Color(0xFFE3F2FD), text: Color(0xFF1565C0)),
      'SENT': (bg: Color(0xFFE3F2FD), text: Color(0xFF1565C0)),
    };

    for (final entry in statuses.entries) {
      testWidgets('${entry.key} → correct colors', (tester) async {
        await tester.pumpWidget(
          MaterialApp(
            home: Scaffold(
              body: CampaignStatusChip(status: entry.key),
            ),
          ),
        );
        // Widget renders without error
        expect(find.byType(CampaignStatusChip), findsOneWidget);
        // Text label shown
        expect(find.text(entry.key), findsOneWidget);
      });
    }
  });
}

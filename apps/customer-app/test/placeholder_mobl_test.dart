// Nyquist validation map — one failing placeholder per MOBL requirement.
// Each later plan deletes its row as the feature lands.
// These are intentionally RED and must stay RED until the feature is built.
import 'package:flutter_test/flutter_test.dart';

void main() {
  // MOBL-02 removed — implemented in 06-06 (nida_pending_test.dart)
  // MOBL-04 removed — implemented in 06-07 (dashboard_screen_test.dart)
  // MOBL-05 removed — implemented in 06-08 (bundle_catalog_test.dart + stk_countdown_test.dart)
  // MOBL-06 removed — implemented in 06-09 (contact_list_test.dart, add_contact_test.dart)
  test('MOBL-07 placeholder', () => fail('not implemented'));
  test('MOBL-08 placeholder', () => fail('not implemented'));
  test('MOBL-09 placeholder', () => fail('not implemented'));
}

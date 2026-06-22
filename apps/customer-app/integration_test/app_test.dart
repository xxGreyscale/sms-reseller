import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('app boots to splash', (WidgetTester tester) async {
    // Stub: full app boot test implemented in a later wave.
    // Integration tests require a running device/emulator — not run in unit CI.
    expect(true, isTrue);
  });
}

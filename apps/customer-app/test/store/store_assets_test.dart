// store_assets_test.dart — MOBL-09 repo-deliverable coverage.
//
// Submission to the stores is an external, manual step (D-02/D-03). What CAN be
// verified in CI is that every repo-side store deliverable exists and is wired:
// signing config with debug fallback, EN+SW metadata, privacy policy, submission
// checklist, the CI workflow, and the e2e integration spine.
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

void main() {
  // Tests run with CWD = apps/customer-app.
  File appFile(String rel) => File(rel);
  File repoFile(String rel) => File('../../$rel');

  group('MOBL-09 store deliverables present', () {
    test('EN + SW store metadata for both platforms', () {
      for (final p in const [
        'store/metadata/android/en-US/full_description.txt',
        'store/metadata/android/sw/full_description.txt',
        'store/metadata/ios/en-US/description.txt',
        'store/metadata/ios/sw/description.txt',
      ]) {
        final f = appFile(p);
        expect(f.existsSync(), isTrue, reason: 'missing $p');
        expect(f.readAsStringSync().trim().length, greaterThan(50),
            reason: '$p looks empty');
      }
    });

    test('privacy policy declares NIN + payment data collection', () {
      final f = appFile('store/PRIVACY_POLICY.md');
      expect(f.existsSync(), isTrue);
      final body = f.readAsStringSync();
      expect(body.contains('NIDA'), isTrue);
      expect(body.toLowerCase().contains('payment'), isTrue);
    });

    test('submission checklist enumerates both stores', () {
      final f = appFile('store/SUBMISSION_CHECKLIST.md');
      expect(f.existsSync(), isTrue);
      final body = f.readAsStringSync();
      expect(body.contains('Google Play'), isTrue);
      expect(body.contains('App Store Connect'), isTrue);
    });

    test('android release signing falls back to debug when no keystore', () {
      final gradle =
          appFile('android/app/build.gradle.kts').readAsStringSync();
      expect(gradle.contains('signingConfigs.getByName("debug")'), isTrue);
      expect(gradle.contains('key.properties'), isTrue);
      expect(appFile('android/key.properties.template').existsSync(), isTrue);
    });

    test('CI workflow builds the AAB', () {
      final wf = repoFile('.github/workflows/customer-app.yml');
      expect(wf.existsSync(), isTrue);
      final body = wf.readAsStringSync();
      expect(body.contains('flutter'), isTrue);
      expect(body.contains('build appbundle'), isTrue);
    });

    test('e2e integration spine uses the integration_test SDK', () {
      final e2e = appFile('integration_test/app_test.dart').readAsStringSync();
      expect(
          e2e.contains('package:integration_test/integration_test.dart'),
          isTrue);
      expect(e2e.contains('IntegrationTestWidgetsFlutterBinding'), isTrue);
    });
  });
}

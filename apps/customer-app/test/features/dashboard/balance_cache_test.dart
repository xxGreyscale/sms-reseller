// balance_cache_test.dart — TDD GREEN
// Tests for the Hive cache-read + online-write balance pattern (D-05).
import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hive_ce/hive.dart';
import 'package:mocktail/mocktail.dart';

import 'package:customer_app/features/dashboard/balance_provider.dart';

// ---------------------------------------------------------------------------
// Mocks
// ---------------------------------------------------------------------------

class MockDio extends Mock implements Dio {}

class MockBox extends Mock implements Box<int> {}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

void main() {
  setUpAll(() {
    registerFallbackValue(RequestOptions(path: ''));
  });

  late MockDio mockDio;
  late MockBox mockBox;

  setUp(() {
    mockDio = MockDio();
    mockBox = MockBox();
  });

  group('fetchBalance', () {
    test('online: fetches from API, writes to Hive, returns value', () async {
      // Arrange: Mock Dio response — availableCredits = 150
      when(() => mockDio.get('/api/v1/wallet/balance')).thenAnswer(
        (_) async => Response(
          data: {'availableCredits': 150},
          statusCode: 200,
          requestOptions: RequestOptions(path: ''),
        ),
      );
      when(() => mockBox.put('availableCredits', 150))
          .thenAnswer((_) async {});

      // Act
      final result = await fetchBalance(dio: mockDio, box: mockBox);

      // Assert
      expect(result, 150);
      verify(() => mockBox.put('availableCredits', 150)).called(1);
    });

    test(
        'offline with cached balance: returns cached value, does not throw',
        () async {
      // Arrange: Dio throws connectionError; Hive has cached value 75
      when(() => mockDio.get(any())).thenThrow(
        DioException(
          requestOptions: RequestOptions(path: ''),
          type: DioExceptionType.connectionError,
        ),
      );
      when(() => mockBox.get('availableCredits')).thenReturn(75);

      // Act: must NOT throw even though Dio threw
      final result = await fetchBalance(dio: mockDio, box: mockBox);

      // Assert
      expect(result, 75);
    });

    test('offline with NO cache: rethrows DioException', () async {
      // Arrange: Dio throws; no cache
      when(() => mockDio.get(any())).thenThrow(
        DioException(
          requestOptions: RequestOptions(path: ''),
          type: DioExceptionType.connectionError,
        ),
      );
      when(() => mockBox.get('availableCredits')).thenReturn(null);

      // Act + Assert: must throw because no cache
      await expectLater(
        () => fetchBalance(dio: mockDio, box: mockBox),
        throwsA(isA<DioException>()),
      );
    });
  });
}

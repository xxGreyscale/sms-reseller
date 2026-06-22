// balance_cache_test.dart — TDD RED
// Tests for the Hive cache-read + online-write balance pattern (D-05).
import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hive_ce/hive.dart';
import 'package:mocktail/mocktail.dart';

import 'package:customer_app/features/dashboard/balance_provider.dart';

// ---------------------------------------------------------------------------
// Mocks
// ---------------------------------------------------------------------------

class MockBox extends Mock implements Box<int> {}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

void main() {
  late MockBox mockBox;

  setUp(() {
    mockBox = MockBox();
  });

  group('BalanceFetcher', () {
    test('online: fetches from API, writes to Hive, returns value', () async {
      // Arrange: Mock Dio response — availableCredits = 150
      final mockDio = MockDio();
      when(() => mockDio.get(any())).thenAnswer(
        (_) async => Response(
          data: {'availableCredits': 150},
          statusCode: 200,
          requestOptions: RequestOptions(path: ''),
        ),
      );
      when(() => mockBox.put('availableCredits', 150))
          .thenAnswer((_) async {});
      when(() => mockBox.get('availableCredits')).thenReturn(150);

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
      final mockDio = MockDio();
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
      final mockDio = MockDio();
      when(() => mockDio.get(any())).thenThrow(
        DioException(
          requestOptions: RequestOptions(path: ''),
          type: DioExceptionType.connectionError,
        ),
      );
      when(() => mockBox.get('availableCredits')).thenReturn(null);

      // Act + Assert: must throw because no cache
      expect(
        () async => fetchBalance(dio: mockDio, box: mockBox),
        throwsA(isA<DioException>()),
      );
    });
  });
}

// ---------------------------------------------------------------------------
// Lightweight MockDio — only .get() is needed
// ---------------------------------------------------------------------------

class MockDio extends Mock implements Dio {}

import 'package:customer_app/core/dio/auth_interceptor.dart';
import 'package:customer_app/core/storage/secure_storage.dart';
import 'package:dio/dio.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';

// ---------------------------------------------------------------------------
// Mocks
// ---------------------------------------------------------------------------

class MockFlutterSecureStorage extends Mock implements FlutterSecureStorage {}

class MockDio extends Mock implements Dio {}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

RequestOptions _opts({String path = '/test', String method = 'GET'}) =>
    RequestOptions(path: path, method: method);

DioException _dioError(RequestOptions opts, int statusCode) => DioException(
      requestOptions: opts,
      response: Response(
        requestOptions: opts,
        statusCode: statusCode,
      ),
      type: DioExceptionType.badResponse,
    );

class _CapturingRequestHandler extends RequestInterceptorHandler {
  RequestOptions? captured;

  @override
  void next(RequestOptions options) {
    captured = options;
  }
}

class _CapturingErrorHandler extends ErrorInterceptorHandler {
  Response<dynamic>? resolved;
  DioException? rejected;

  @override
  void resolve(Response<dynamic> response,
      {bool callFollowingResponseInterceptor = false}) {
    resolved = response;
  }

  @override
  void reject(DioException error,
      {bool callFollowingErrorInterceptor = false}) {
    rejected = error;
  }

  @override
  void next(DioException err) {
    rejected = err;
  }
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

void main() {
  late MockFlutterSecureStorage mockStorage;
  late MockDio mockTokenDio;
  late AuthInterceptor interceptor;

  setUp(() {
    mockStorage = MockFlutterSecureStorage();
    mockTokenDio = MockDio();
    interceptor = AuthInterceptor(
      storage: mockStorage,
      tokenDio: mockTokenDio,
    );
    registerFallbackValue(RequestOptions(path: '/'));
    registerFallbackValue(Options());
  });

  group('AuthInterceptor', () {
    // -----------------------------------------------------------------------
    // Test 1: Bearer token is attached
    // -----------------------------------------------------------------------
    test('attaches Authorization header when access token is stored', () async {
      when(() => mockStorage.read(key: kAccessTokenKey))
          .thenAnswer((_) async => 'my_access_token');

      final opts = _opts();
      final handler = _CapturingRequestHandler();

      interceptor.onRequest(opts, handler);
      await Future<void>.delayed(Duration.zero);

      expect(
        handler.captured?.headers['Authorization'],
        equals('Bearer my_access_token'),
      );
    });

    // -----------------------------------------------------------------------
    // Test 2: 401 → refresh once → retry resolves
    // -----------------------------------------------------------------------
    test('on 401 calls refresh endpoint, writes new tokens, retries request',
        () async {
      const oldAccess = 'old_access';
      const newAccess = 'new_access';
      const newRefresh = 'new_refresh';

      // First read returns old token (for the "has it already been refreshed?" check)
      when(() => mockStorage.read(key: kAccessTokenKey))
          .thenAnswer((_) async => oldAccess);
      when(() => mockStorage.read(key: kRefreshTokenKey))
          .thenAnswer((_) async => 'old_refresh');
      when(() => mockStorage.write(key: kAccessTokenKey, value: newAccess))
          .thenAnswer((_) async {});
      when(() => mockStorage.write(key: kRefreshTokenKey, value: newRefresh))
          .thenAnswer((_) async {});

      when(() => mockTokenDio.post('/auth/refresh', data: any(named: 'data')))
          .thenAnswer((_) async => Response(
                requestOptions: _opts(path: '/auth/refresh'),
                statusCode: 200,
                data: {'accessToken': newAccess, 'refreshToken': newRefresh},
              ));

      when(() => mockTokenDio.fetch(any())).thenAnswer((_) async => Response(
            requestOptions: _opts(),
            statusCode: 200,
            data: 'ok',
          ));

      final opts = _opts();
      opts.headers['Authorization'] = 'Bearer $oldAccess';
      final err = _dioError(opts, 401);
      final handler = _CapturingErrorHandler();

      interceptor.onError(err, handler);
      await Future<void>.delayed(const Duration(milliseconds: 100));

      expect(handler.resolved?.statusCode, equals(200));
      verify(() => mockTokenDio.post('/auth/refresh', data: any(named: 'data')))
          .called(1);
      verify(() => mockStorage.write(key: kAccessTokenKey, value: newAccess))
          .called(1);
    });

    // -----------------------------------------------------------------------
    // Test 3: Concurrent 401s trigger exactly ONE refresh call
    // -----------------------------------------------------------------------
    test('concurrent 401s trigger only one refresh call', () async {
      const oldAccess = 'old_access';
      const newAccess = 'new_access';
      const newRefresh = 'new_refresh';

      // Simulate storage: initially returns old_access; after refresh returns new_access
      var currentAccessInStorage = oldAccess;
      when(() => mockStorage.read(key: kAccessTokenKey))
          .thenAnswer((_) async => currentAccessInStorage);
      when(() => mockStorage.read(key: kRefreshTokenKey))
          .thenAnswer((_) async => 'old_refresh');
      when(() => mockStorage.write(key: kAccessTokenKey, value: newAccess))
          .thenAnswer((_) async {
        currentAccessInStorage = newAccess;
      });
      when(() => mockStorage.write(key: kRefreshTokenKey, value: newRefresh))
          .thenAnswer((_) async {});

      int refreshCallCount = 0;
      when(() => mockTokenDio.post('/auth/refresh', data: any(named: 'data')))
          .thenAnswer((_) async {
        refreshCallCount++;
        await Future<void>.delayed(const Duration(milliseconds: 20));
        return Response(
          requestOptions: _opts(path: '/auth/refresh'),
          statusCode: 200,
          data: {'accessToken': newAccess, 'refreshToken': newRefresh},
        );
      });

      when(() => mockTokenDio.fetch(any())).thenAnswer((_) async => Response(
            requestOptions: _opts(),
            statusCode: 200,
            data: 'ok',
          ));

      final handler1 = _CapturingErrorHandler();
      final handler2 = _CapturingErrorHandler();

      // Both requests use oldAccess, so both 401s have the same stale token
      final opts1 = _opts(path: '/a');
      opts1.headers['Authorization'] = 'Bearer $oldAccess';
      final opts2 = _opts(path: '/b');
      opts2.headers['Authorization'] = 'Bearer $oldAccess';

      interceptor.onError(_dioError(opts1, 401), handler1);
      interceptor.onError(_dioError(opts2, 401), handler2);

      await Future<void>.delayed(const Duration(milliseconds: 300));

      // QueuedInterceptor serialises — only 1 refresh call should happen
      expect(refreshCallCount, equals(1));
      expect(handler1.resolved, isNotNull);
      expect(handler2.resolved, isNotNull);
    });

    // -----------------------------------------------------------------------
    // Test 4: Refresh failure → clear storage, reject original error
    // -----------------------------------------------------------------------
    test('refresh failure clears storage and rejects original error', () async {
      const oldAccess = 'old_access';

      when(() => mockStorage.read(key: kAccessTokenKey))
          .thenAnswer((_) async => oldAccess);
      when(() => mockStorage.read(key: kRefreshTokenKey))
          .thenAnswer((_) async => 'old_refresh');
      when(() => mockStorage.deleteAll()).thenAnswer((_) async {});

      when(() => mockTokenDio.post('/auth/refresh', data: any(named: 'data')))
          .thenThrow(DioException(
        requestOptions: _opts(path: '/auth/refresh'),
        type: DioExceptionType.connectionError,
      ));

      final opts = _opts();
      opts.headers['Authorization'] = 'Bearer $oldAccess';
      final err = _dioError(opts, 401);
      final handler = _CapturingErrorHandler();

      interceptor.onError(err, handler);
      await Future<void>.delayed(const Duration(milliseconds: 100));

      expect(handler.rejected, isNotNull);
      verify(() => mockStorage.deleteAll()).called(1);
    });
  });
}

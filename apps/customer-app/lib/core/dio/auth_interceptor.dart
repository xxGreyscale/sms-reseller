import 'package:dio/dio.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:customer_app/core/storage/secure_storage.dart';

/// QueuedInterceptor that attaches Bearer token and handles refresh-on-401.
/// Uses a separate [_tokenDio] instance to avoid infinite recursion (Pitfall 2).
class AuthInterceptor extends QueuedInterceptor {
  final FlutterSecureStorage _storage;
  final Dio _tokenDio;

  AuthInterceptor({
    required FlutterSecureStorage storage,
    required Dio tokenDio,
  })  : _storage = storage,
        _tokenDio = tokenDio;

  @override
  void onRequest(
      RequestOptions options, RequestInterceptorHandler handler) async {
    final token = await _storage.read(key: kAccessTokenKey);
    if (token != null) {
      options.headers['Authorization'] = 'Bearer $token';
    }
    handler.next(options);
  }

  @override
  void onError(DioException err, ErrorInterceptorHandler handler) async {
    if (err.response?.statusCode != 401) {
      return handler.next(err);
    }

    final refreshToken = await _storage.read(key: kRefreshTokenKey);
    if (refreshToken == null) {
      await _storage.deleteAll();
      return handler.reject(err);
    }

    try {
      final resp = await _tokenDio.post(
        '/auth/refresh',
        data: {'refreshToken': refreshToken},
      );
      final newAccess = resp.data['accessToken'] as String;
      final newRefresh = resp.data['refreshToken'] as String;
      await _storage.write(key: kAccessTokenKey, value: newAccess);
      await _storage.write(key: kRefreshTokenKey, value: newRefresh);

      // Retry original request with new token
      err.requestOptions.headers['Authorization'] = 'Bearer $newAccess';
      final retryResp = await _tokenDio.fetch(err.requestOptions);
      return handler.resolve(retryResp);
    } catch (_) {
      await _storage.deleteAll();
      return handler.reject(err);
    }
  }
}

import 'dart:async';

import 'package:dio/dio.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:customer_app/core/storage/secure_storage.dart';

/// QueuedInterceptor that attaches Bearer token and handles refresh-on-401.
/// Uses a separate [_tokenDio] instance to avoid infinite recursion (Pitfall 2).
///
/// An [_refreshFuture] ensures concurrent 401s trigger only ONE refresh call:
/// the first 401 sets a pending Future before any await; subsequent 401s
/// await that same Future and re-use the refreshed token.
class AuthInterceptor extends QueuedInterceptor {
  final FlutterSecureStorage _storage;
  final Dio _tokenDio;

  Future<String>? _refreshFuture;

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

    // If a refresh is already in flight, wait for it and retry.
    if (_refreshFuture != null) {
      try {
        final newAccess = await _refreshFuture!;
        err.requestOptions.headers['Authorization'] = 'Bearer $newAccess';
        final retryResp = await _tokenDio.fetch(err.requestOptions);
        return handler.resolve(retryResp);
      } catch (_) {
        return handler.reject(err);
      }
    }

    // Gate subsequent 401s BEFORE any await.
    _refreshFuture = _doRefresh();

    try {
      final newAccess = await _refreshFuture!;
      err.requestOptions.headers['Authorization'] = 'Bearer $newAccess';
      final retryResp = await _tokenDio.fetch(err.requestOptions);
      return handler.resolve(retryResp);
    } catch (_) {
      return handler.reject(err);
    } finally {
      _refreshFuture = null;
    }
  }

  Future<String> _doRefresh() async {
    final refreshToken = await _storage.read(key: kRefreshTokenKey);
    if (refreshToken == null) {
      await _storage.deleteAll();
      throw Exception('No refresh token');
    }

    try {
      final resp = await _tokenDio.post(
        '/api/v1/auth/refresh',
        data: {'refreshToken': refreshToken},
      );
      final newAccess = resp.data['accessToken'] as String;
      final newRefresh = resp.data['refreshToken'] as String;
      await _storage.write(key: kAccessTokenKey, value: newAccess);
      await _storage.write(key: kRefreshTokenKey, value: newRefresh);
      return newAccess;
    } catch (e) {
      await _storage.deleteAll();
      rethrow;
    }
  }
}

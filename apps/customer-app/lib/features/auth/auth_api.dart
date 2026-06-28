import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:customer_app/core/dio/dio_client.dart';
import 'package:customer_app/core/storage/secure_storage.dart';
import 'package:customer_app/core/auth/auth_notifier.dart';

// ---------------------------------------------------------------------------
// Custom exceptions
// ---------------------------------------------------------------------------

class InvalidCredentialsException implements Exception {
  const InvalidCredentialsException();
}

class NinTakenException implements Exception {
  const NinTakenException();
}

// ---------------------------------------------------------------------------
// Auth API
// ---------------------------------------------------------------------------

/// Auth API client — wraps register and login HTTP calls, persists tokens,
/// and updates AuthNotifier state.
class AuthApi {
  final Dio _dio;
  final FlutterSecureStorage _storage;
  final AuthNotifier _authNotifier;

  AuthApi({
    required Dio dio,
    required FlutterSecureStorage storage,
    required AuthNotifier authNotifier,
  })  : _dio = dio,
        _storage = storage,
        _authNotifier = authNotifier;

  /// POST /auth/register
  /// On success: writes accessToken to secure storage, sets AuthState.pending.
  Future<void> register({
    required String fullName,
    required String phone,
    required String email,
    required String nin,
    required String password,
  }) async {
    try {
      final resp = await _dio.post<Map<String, dynamic>>(
        '/api/v1/auth/register',
        data: {
          'fullName': fullName,
          'phone': phone,
          'email': email,
          'nin': nin,
          'password': password,
        },
      );

      final data = resp.data!;
      final accessToken = data['accessToken'] as String;

      await _storage.write(key: kAccessTokenKey, value: accessToken);
      await _authNotifier.setPending(accessToken: accessToken);
    } on DioException catch (e) {
      if (e.response?.statusCode == 409) {
        throw const NinTakenException();
      }
      rethrow;
    }
  }

  /// POST /auth/login
  /// On success: writes accessToken + refreshToken to secure storage,
  /// sets AuthState based on returned status.
  Future<void> login({
    required String email,
    required String password,
    String deviceId = 'flutter-app',
  }) async {
    try {
      final resp = await _dio.post<Map<String, dynamic>>(
        '/api/v1/auth/login',
        data: {
          'email': email,
          'password': password,
          'deviceId': deviceId,
        },
      );

      final data = resp.data!;
      final accessToken = data['accessToken'] as String;
      final refreshToken = data['refreshToken'] as String;
      final status = data['status'] as String?;

      await _storage.write(key: kAccessTokenKey, value: accessToken);
      await _storage.write(key: kRefreshTokenKey, value: refreshToken);

      if (status == 'VERIFIED') {
        await _authNotifier.setVerified(
          accessToken: accessToken,
          refreshToken: refreshToken,
        );
      } else {
        await _authNotifier.setPending(accessToken: accessToken);
      }
    } on DioException catch (e) {
      if (e.response?.statusCode == 401 || e.response?.statusCode == 403) {
        throw const InvalidCredentialsException();
      }
      rethrow;
    }
  }
}

final authApiProvider = Provider<AuthApi>((ref) {
  return AuthApi(
    dio: ref.read(tokenDioProvider),
    storage: ref.read(secureStorageProvider),
    authNotifier: ref.read(authNotifierProvider.notifier),
  );
});

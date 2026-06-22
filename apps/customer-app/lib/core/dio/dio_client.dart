import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:customer_app/core/dio/auth_interceptor.dart';
import 'package:customer_app/core/storage/secure_storage.dart';

const String _defaultBaseUrl = String.fromEnvironment(
  'API_BASE_URL',
  defaultValue: 'http://localhost:8080',
);

/// Token-only Dio that does NOT have the AuthInterceptor (to avoid recursion).
final tokenDioProvider = Provider<Dio>((ref) {
  final dio = Dio(BaseOptions(
    baseUrl: _defaultBaseUrl,
    connectTimeout: const Duration(seconds: 10),
    receiveTimeout: const Duration(seconds: 30),
    contentType: 'application/json',
  ));
  return dio;
});

/// Main application Dio with AuthInterceptor attached.
final dioProvider = Provider<Dio>((ref) {
  final storage = ref.watch(secureStorageProvider);
  final tokenDio = ref.watch(tokenDioProvider);

  final dio = Dio(BaseOptions(
    baseUrl: _defaultBaseUrl,
    connectTimeout: const Duration(seconds: 10),
    receiveTimeout: const Duration(seconds: 30),
    contentType: 'application/json',
  ));

  dio.interceptors.add(
    AuthInterceptor(storage: storage, tokenDio: tokenDio),
  );

  return dio;
});

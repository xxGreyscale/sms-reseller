import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:customer_app/core/auth/auth_state.dart';
import 'package:customer_app/core/storage/secure_storage.dart';
import 'package:customer_app/core/dio/dio_client.dart';

/// AuthNotifier reads tokens from secure storage on build and derives auth state.
/// Exposes [refreshAndCheckStatus] for PENDING polling and [signOut].
class AuthNotifier extends AsyncNotifier<AuthState> {
  @override
  Future<AuthState> build() async {
    final storage = ref.read(secureStorageProvider);
    final accessToken = await storage.read(key: kAccessTokenKey);
    final refreshToken = await storage.read(key: kRefreshTokenKey);

    if (accessToken == null) {
      return const AuthState.unauthenticated();
    }

    // Decode verification_status from JWT payload without network call.
    // Structure: header.payload.signature (base64url encoded)
    try {
      final parts = accessToken.split('.');
      if (parts.length != 3) return const AuthState.unauthenticated();

      // Pad base64url to a valid base64 string
      String padded = parts[1];
      while (padded.length % 4 != 0) {
        padded += '=';
      }
      final payloadStr = String.fromCharCodes(
        _base64UrlDecode(parts[1]),
      );
      final claimsStart = payloadStr.indexOf('{');
      if (claimsStart < 0) return const AuthState.unauthenticated();

      // Simple extraction of verification_status claim without full JSON parsing
      final statusMatch = RegExp(r'"verification_status"\s*:\s*"([^"]+)"')
          .firstMatch(payloadStr);
      final status = statusMatch?.group(1);

      if (status == 'VERIFIED' && refreshToken != null) {
        return AuthState.verified(
          accessToken: accessToken,
          refreshToken: refreshToken,
        );
      }
      return AuthState.pending(accessToken: accessToken);
    } catch (_) {
      return const AuthState.unauthenticated();
    }
  }

  static List<int> _base64UrlDecode(String input) {
    String normalized = input.replaceAll('-', '+').replaceAll('_', '/');
    while (normalized.length % 4 != 0) {
      normalized += '=';
    }
    // Manually decode base64 using Dart core
    final bytes = <int>[];
    const chars =
        'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=';
    int i = 0;
    while (i < normalized.length) {
      final c1 = chars.indexOf(normalized[i++]);
      final c2 = chars.indexOf(normalized[i++]);
      final c3 = chars.indexOf(normalized[i++]);
      final c4 = chars.indexOf(normalized[i++]);
      bytes.add((c1 << 2) | (c2 >> 4));
      if (c3 != 64) bytes.add(((c2 & 0xf) << 4) | (c3 >> 2));
      if (c4 != 64) bytes.add(((c3 & 0x3) << 6) | c4);
    }
    return bytes;
  }

  /// Calls GET /auth/me (non-rotating, D-13) and updates state based on returned status.
  /// Used by PendingPollerNotifier (D-09, Pattern 4).
  Future<void> refreshAndCheckStatus() async {
    try {
      final dio = ref.read(dioProvider);
      final resp = await dio.get('/auth/me');
      final status = resp.data['status'] as String?;

      if (status == 'VERIFIED') {
        final storage = ref.read(secureStorageProvider);
        final accessToken = await storage.read(key: kAccessTokenKey) ?? '';
        final refreshToken = await storage.read(key: kRefreshTokenKey) ?? '';
        state = AsyncData(AuthState.verified(
          accessToken: accessToken,
          refreshToken: refreshToken,
        ));
      }
      // PENDING_VERIFICATION or REJECTED: no state change — remain pending
    } catch (_) {
      // Swallow transient errors — the poller will retry on next tick
    }
  }

  Future<void> signOut() async {
    final storage = ref.read(secureStorageProvider);
    await storage.deleteAll();
    state = const AsyncData(AuthState.unauthenticated());
  }

  Future<void> setVerified({
    required String accessToken,
    required String refreshToken,
  }) async {
    final storage = ref.read(secureStorageProvider);
    await storage.write(key: kAccessTokenKey, value: accessToken);
    await storage.write(key: kRefreshTokenKey, value: refreshToken);
    state = AsyncData(AuthState.verified(
      accessToken: accessToken,
      refreshToken: refreshToken,
    ));
  }

  Future<void> setPending({required String accessToken}) async {
    final storage = ref.read(secureStorageProvider);
    await storage.write(key: kAccessTokenKey, value: accessToken);
    state = AsyncData(AuthState.pending(accessToken: accessToken));
  }
}

final authNotifierProvider =
    AsyncNotifierProvider<AuthNotifier, AuthState>(AuthNotifier.new);

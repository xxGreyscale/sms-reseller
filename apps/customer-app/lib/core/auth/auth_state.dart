import 'package:freezed_annotation/freezed_annotation.dart';

part 'auth_state.freezed.dart';

@freezed
sealed class AuthState with _$AuthState {
  const factory AuthState.unauthenticated() = Unauthenticated;

  const factory AuthState.pending({
    required String accessToken,
  }) = Pending;

  const factory AuthState.verified({
    required String accessToken,
    required String refreshToken,
  }) = Verified;
}

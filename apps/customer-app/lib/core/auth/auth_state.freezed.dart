// GENERATED CODE - DO NOT MODIFY BY HAND
// coverage:ignore-file
// ignore_for_file: type=lint
// ignore_for_file: unused_element, deprecated_member_use, deprecated_member_use_from_same_package, use_function_type_syntax_for_parameters, unnecessary_const, avoid_init_to_null, invalid_override_different_default_values_named, prefer_expression_function_bodies, annotate_overrides, invalid_annotation_target, unnecessary_question_mark

part of 'auth_state.dart';

// **************************************************************************
// FreezedGenerator
// **************************************************************************

// dart format off
T _$identity<T>(T value) => value;

/// @nodoc
mixin _$AuthState {
  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType && other is AuthState);
  }

  @override
  int get hashCode => runtimeType.hashCode;

  @override
  String toString() {
    return 'AuthState()';
  }
}

/// @nodoc
class $AuthStateCopyWith<$Res> {
  $AuthStateCopyWith(AuthState _, $Res Function(AuthState) __);
}

/// Adds pattern-matching-related methods to [AuthState].
extension AuthStatePatterns on AuthState {
  /// A variant of `map` that fallback to returning `orElse`.
  ///
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case final Subclass value:
  ///     return ...;
  ///   case _:
  ///     return orElse();
  /// }
  /// ```

  @optionalTypeArgs
  TResult maybeMap<TResult extends Object?>({
    TResult Function(Unauthenticated value)? unauthenticated,
    TResult Function(Pending value)? pending,
    TResult Function(Verified value)? verified,
    required TResult orElse(),
  }) {
    final _that = this;
    switch (_that) {
      case Unauthenticated() when unauthenticated != null:
        return unauthenticated(_that);
      case Pending() when pending != null:
        return pending(_that);
      case Verified() when verified != null:
        return verified(_that);
      case _:
        return orElse();
    }
  }

  /// A `switch`-like method, using callbacks.
  ///
  /// Callbacks receives the raw object, upcasted.
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case final Subclass value:
  ///     return ...;
  ///   case final Subclass2 value:
  ///     return ...;
  /// }
  /// ```

  @optionalTypeArgs
  TResult map<TResult extends Object?>({
    required TResult Function(Unauthenticated value) unauthenticated,
    required TResult Function(Pending value) pending,
    required TResult Function(Verified value) verified,
  }) {
    final _that = this;
    switch (_that) {
      case Unauthenticated():
        return unauthenticated(_that);
      case Pending():
        return pending(_that);
      case Verified():
        return verified(_that);
    }
  }

  /// A variant of `map` that fallback to returning `null`.
  ///
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case final Subclass value:
  ///     return ...;
  ///   case _:
  ///     return null;
  /// }
  /// ```

  @optionalTypeArgs
  TResult? mapOrNull<TResult extends Object?>({
    TResult? Function(Unauthenticated value)? unauthenticated,
    TResult? Function(Pending value)? pending,
    TResult? Function(Verified value)? verified,
  }) {
    final _that = this;
    switch (_that) {
      case Unauthenticated() when unauthenticated != null:
        return unauthenticated(_that);
      case Pending() when pending != null:
        return pending(_that);
      case Verified() when verified != null:
        return verified(_that);
      case _:
        return null;
    }
  }

  /// A variant of `when` that fallback to an `orElse` callback.
  ///
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case Subclass(:final field):
  ///     return ...;
  ///   case _:
  ///     return orElse();
  /// }
  /// ```

  @optionalTypeArgs
  TResult maybeWhen<TResult extends Object?>({
    TResult Function()? unauthenticated,
    TResult Function(String accessToken)? pending,
    TResult Function(String accessToken, String refreshToken)? verified,
    required TResult orElse(),
  }) {
    final _that = this;
    switch (_that) {
      case Unauthenticated() when unauthenticated != null:
        return unauthenticated();
      case Pending() when pending != null:
        return pending(_that.accessToken);
      case Verified() when verified != null:
        return verified(_that.accessToken, _that.refreshToken);
      case _:
        return orElse();
    }
  }

  /// A `switch`-like method, using callbacks.
  ///
  /// As opposed to `map`, this offers destructuring.
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case Subclass(:final field):
  ///     return ...;
  ///   case Subclass2(:final field2):
  ///     return ...;
  /// }
  /// ```

  @optionalTypeArgs
  TResult when<TResult extends Object?>({
    required TResult Function() unauthenticated,
    required TResult Function(String accessToken) pending,
    required TResult Function(String accessToken, String refreshToken) verified,
  }) {
    final _that = this;
    switch (_that) {
      case Unauthenticated():
        return unauthenticated();
      case Pending():
        return pending(_that.accessToken);
      case Verified():
        return verified(_that.accessToken, _that.refreshToken);
    }
  }

  /// A variant of `when` that fallback to returning `null`
  ///
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case Subclass(:final field):
  ///     return ...;
  ///   case _:
  ///     return null;
  /// }
  /// ```

  @optionalTypeArgs
  TResult? whenOrNull<TResult extends Object?>({
    TResult? Function()? unauthenticated,
    TResult? Function(String accessToken)? pending,
    TResult? Function(String accessToken, String refreshToken)? verified,
  }) {
    final _that = this;
    switch (_that) {
      case Unauthenticated() when unauthenticated != null:
        return unauthenticated();
      case Pending() when pending != null:
        return pending(_that.accessToken);
      case Verified() when verified != null:
        return verified(_that.accessToken, _that.refreshToken);
      case _:
        return null;
    }
  }
}

/// @nodoc

class Unauthenticated implements AuthState {
  const Unauthenticated();

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType && other is Unauthenticated);
  }

  @override
  int get hashCode => runtimeType.hashCode;

  @override
  String toString() {
    return 'AuthState.unauthenticated()';
  }
}

/// @nodoc

class Pending implements AuthState {
  const Pending({required this.accessToken});

  final String accessToken;

  /// Create a copy of AuthState
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  @pragma('vm:prefer-inline')
  $PendingCopyWith<Pending> get copyWith =>
      _$PendingCopyWithImpl<Pending>(this, _$identity);

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is Pending &&
            (identical(other.accessToken, accessToken) ||
                other.accessToken == accessToken));
  }

  @override
  int get hashCode => Object.hash(runtimeType, accessToken);

  @override
  String toString() {
    return 'AuthState.pending(accessToken: $accessToken)';
  }
}

/// @nodoc
abstract mixin class $PendingCopyWith<$Res>
    implements $AuthStateCopyWith<$Res> {
  factory $PendingCopyWith(Pending value, $Res Function(Pending) _then) =
      _$PendingCopyWithImpl;
  @useResult
  $Res call({String accessToken});
}

/// @nodoc
class _$PendingCopyWithImpl<$Res> implements $PendingCopyWith<$Res> {
  _$PendingCopyWithImpl(this._self, this._then);

  final Pending _self;
  final $Res Function(Pending) _then;

  /// Create a copy of AuthState
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  $Res call({
    Object? accessToken = null,
  }) {
    return _then(Pending(
      accessToken: null == accessToken
          ? _self.accessToken
          : accessToken // ignore: cast_nullable_to_non_nullable
              as String,
    ));
  }
}

/// @nodoc

class Verified implements AuthState {
  const Verified({required this.accessToken, required this.refreshToken});

  final String accessToken;
  final String refreshToken;

  /// Create a copy of AuthState
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  @pragma('vm:prefer-inline')
  $VerifiedCopyWith<Verified> get copyWith =>
      _$VerifiedCopyWithImpl<Verified>(this, _$identity);

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is Verified &&
            (identical(other.accessToken, accessToken) ||
                other.accessToken == accessToken) &&
            (identical(other.refreshToken, refreshToken) ||
                other.refreshToken == refreshToken));
  }

  @override
  int get hashCode => Object.hash(runtimeType, accessToken, refreshToken);

  @override
  String toString() {
    return 'AuthState.verified(accessToken: $accessToken, refreshToken: $refreshToken)';
  }
}

/// @nodoc
abstract mixin class $VerifiedCopyWith<$Res>
    implements $AuthStateCopyWith<$Res> {
  factory $VerifiedCopyWith(Verified value, $Res Function(Verified) _then) =
      _$VerifiedCopyWithImpl;
  @useResult
  $Res call({String accessToken, String refreshToken});
}

/// @nodoc
class _$VerifiedCopyWithImpl<$Res> implements $VerifiedCopyWith<$Res> {
  _$VerifiedCopyWithImpl(this._self, this._then);

  final Verified _self;
  final $Res Function(Verified) _then;

  /// Create a copy of AuthState
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  $Res call({
    Object? accessToken = null,
    Object? refreshToken = null,
  }) {
    return _then(Verified(
      accessToken: null == accessToken
          ? _self.accessToken
          : accessToken // ignore: cast_nullable_to_non_nullable
              as String,
      refreshToken: null == refreshToken
          ? _self.refreshToken
          : refreshToken // ignore: cast_nullable_to_non_nullable
              as String,
    ));
  }
}

// dart format on

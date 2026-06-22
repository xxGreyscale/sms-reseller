import 'dart:async';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:customer_app/core/auth/auth_notifier.dart';

/// Auto-polls GET /auth/me every 10 seconds while on the PENDING screen (D-09, D-13).
///
/// On VERIFIED: AuthNotifier.refreshAndCheckStatus() sets AuthState.verified →
/// go_router refreshListenable fires → redirect to /dashboard.
///
/// Transient errors are swallowed inside AuthNotifier.refreshAndCheckStatus().
/// Timer is cancelled via ref.onDispose.
class PendingPollerNotifier extends Notifier<void> {
  Timer? _timer;

  @override
  void build() {
    _timer = Timer.periodic(const Duration(seconds: 10), (_) {
      _poll();
    });

    ref.onDispose(() {
      _timer?.cancel();
      _timer = null;
    });
  }

  /// Perform a single poll. Public so tests can trigger manually.
  Future<void> pollNow() => _poll();

  Future<void> _poll() async {
    await ref.read(authNotifierProvider.notifier).refreshAndCheckStatus();
  }
}

final pendingPollerNotifierProvider =
    NotifierProvider<PendingPollerNotifier, void>(PendingPollerNotifier.new);

import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:customer_app/core/auth/auth_notifier.dart';
import 'package:customer_app/core/auth/auth_state.dart';

/// A [ChangeNotifier] that notifies go_router whenever the [authNotifierProvider]
/// changes value so the router re-evaluates the redirect on every auth-state transition.
class GoRouterRefreshNotifier extends ChangeNotifier {
  GoRouterRefreshNotifier(Ref ref) {
    ref.listen<AsyncValue<AuthState>>(authNotifierProvider, (_, __) {
      notifyListeners();
    });
  }
}

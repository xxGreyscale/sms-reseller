// payment_provider.dart — STK push initiation + status polling (MOBL-05b)
//
// PaymentState: idle | pending | confirmed | expired
// PaymentNotifier: initiate() → POST /api/v1/payments; pollStatus() → GET /api/v1/payments/{id}
// On CONFIRMED: invalidate balanceProvider.
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:customer_app/core/dio/dio_client.dart';
import 'package:customer_app/features/dashboard/balance_provider.dart';
import 'package:customer_app/features/payments/payment_api.dart';

// ---------------------------------------------------------------------------
// PaymentState (sealed-like class via named constructors)
// ---------------------------------------------------------------------------

enum _PaymentStatus { idle, pending, confirmed, expired }

class PaymentState {
  final _PaymentStatus _status;
  final String? paymentId;
  final int timeoutSeconds;
  final int smsCount;

  const PaymentState._({
    required _PaymentStatus status,
    this.paymentId,
    this.timeoutSeconds = 120,
    this.smsCount = 0,
  }) : _status = status;

  factory PaymentState.idle() =>
      const PaymentState._(status: _PaymentStatus.idle);

  factory PaymentState.pending({
    required String paymentId,
    required int timeoutSeconds,
  }) =>
      PaymentState._(
        status: _PaymentStatus.pending,
        paymentId: paymentId,
        timeoutSeconds: timeoutSeconds,
      );

  factory PaymentState.confirmed({required int smsCount}) => PaymentState._(
        status: _PaymentStatus.confirmed,
        smsCount: smsCount,
      );

  factory PaymentState.expired() =>
      const PaymentState._(status: _PaymentStatus.expired);

  bool get isIdle => _status == _PaymentStatus.idle;
  bool get isPending => _status == _PaymentStatus.pending;
  bool get isConfirmed => _status == _PaymentStatus.confirmed;
  bool get isExpired => _status == _PaymentStatus.expired;
}

// ---------------------------------------------------------------------------
// PaymentNotifier
// ---------------------------------------------------------------------------

class PaymentNotifier extends Notifier<PaymentState> {
  @override
  PaymentState build() => PaymentState.idle();

  /// POST /api/v1/payments — initiates STK push, seeds countdown from response.
  Future<void> initiate(StkPurchaseArgs args) async {
    try {
      final dio = ref.read(dioProvider);
      final response = await initiatePayment(
        dio: dio,
        bundleId: args.bundleId,
        msisdn: args.msisdn,
        provider: args.provider,
      );
      state = PaymentState.pending(
        paymentId: response.id,
        timeoutSeconds: response.timeoutSeconds,
      );
    } catch (_) {
      state = PaymentState.expired();
    }
  }

  /// GET /api/v1/payments/{id} — poll status every 5s.
  Future<void> pollStatus() async {
    final currentState = state;
    if (!currentState.isPending) return;
    final paymentId = currentState.paymentId;
    if (paymentId == null) return;

    try {
      final dio = ref.read(dioProvider);
      final response =
          await getPaymentStatus(dio: dio, paymentId: paymentId);
      if (response.status == 'CONFIRMED') {
        // Refresh balance
        ref.invalidate(balanceProvider);
        // Derive smsCount from state args (not in status response — use bundleArgs stored)
        // The screen passes smsCount via args; we use 0 as fallback here, screen knows real value
        state = PaymentState.confirmed(smsCount: 0);
      } else if (response.status == 'EXPIRED') {
        state = PaymentState.expired();
      }
    } catch (_) {
      // Swallow poll errors — retry on next tick
    }
  }

  /// Called by CountdownWidget when the clock reaches 0.
  void markExpired() {
    state = PaymentState.expired();
  }
}

// ---------------------------------------------------------------------------
// Provider
// ---------------------------------------------------------------------------

final paymentNotifierProvider =
    NotifierProvider<PaymentNotifier, PaymentState>(PaymentNotifier.new);

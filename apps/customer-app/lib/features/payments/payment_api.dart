// payment_api.dart — Riverpod providers for the Payments API
//
// Exposes:
//   bundlesProvider  → GET /api/v1/bundles
//   initiatePayment  → POST /api/v1/payments
//   getPaymentStatus → GET /api/v1/payments/{id}
import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:customer_app/core/dio/dio_client.dart';

// ---------------------------------------------------------------------------
// Models
// ---------------------------------------------------------------------------

class BundleItem {
  final String id;
  final String name;
  final int smsCount;
  final int priceTzs;
  final bool active;

  const BundleItem({
    required this.id,
    required this.name,
    required this.smsCount,
    required this.priceTzs,
    required this.active,
  });

  factory BundleItem.fromJson(Map<String, dynamic> json) => BundleItem(
        id: json['id'] as String,
        name: json['name'] as String,
        smsCount: json['smsCount'] as int,
        priceTzs: json['priceTzs'] as int,
        active: json['active'] as bool,
      );
}

class PaymentInitResponse {
  final String id;
  final String status;
  final int timeoutSeconds;

  const PaymentInitResponse({
    required this.id,
    required this.status,
    required this.timeoutSeconds,
  });

  factory PaymentInitResponse.fromJson(Map<String, dynamic> json) =>
      PaymentInitResponse(
        id: json['id'] as String,
        status: json['status'] as String,
        timeoutSeconds: (json['timeoutSeconds'] as num).toInt(),
      );
}

class PaymentStatusResponse {
  final String id;
  final String status;

  const PaymentStatusResponse({required this.id, required this.status});

  factory PaymentStatusResponse.fromJson(Map<String, dynamic> json) =>
      PaymentStatusResponse(
        id: json['id'] as String,
        status: json['status'] as String,
      );
}

// ---------------------------------------------------------------------------
// Providers
// ---------------------------------------------------------------------------

/// Fetches and returns active bundles sorted ascending by priceTzs.
final bundlesProvider =
    FutureProvider<List<Map<String, dynamic>>>((ref) async {
  final dio = ref.read(dioProvider);
  final response = await dio.get('/api/v1/bundles');
  final list = (response.data as List).cast<Map<String, dynamic>>();
  list.sort((a, b) =>
      (a['priceTzs'] as int).compareTo(b['priceTzs'] as int));
  return list;
});

// ---------------------------------------------------------------------------
// API functions (not Riverpod providers — called imperatively from providers)
// ---------------------------------------------------------------------------

/// POST /api/v1/payments — initiates an STK push.
Future<PaymentInitResponse> initiatePayment({
  required Dio dio,
  required String bundleId,
  required String msisdn,
  required String provider,
}) async {
  final response = await dio.post('/api/v1/payments', data: {
    'bundleId': bundleId,
    'msisdn': msisdn,
    'provider': provider,
  });
  return PaymentInitResponse.fromJson(
      response.data as Map<String, dynamic>);
}

/// GET /api/v1/payments/{id} — polls payment status.
Future<PaymentStatusResponse> getPaymentStatus({
  required Dio dio,
  required String paymentId,
}) async {
  final response = await dio.get('/api/v1/payments/$paymentId');
  return PaymentStatusResponse.fromJson(
      response.data as Map<String, dynamic>);
}

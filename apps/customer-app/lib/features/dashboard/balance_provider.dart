import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:hive_ce/hive.dart';
import 'package:customer_app/core/dio/dio_client.dart';
import 'package:customer_app/core/hive/hive_boxes.dart';

/// Balance result carrying the credit count and whether it came from cache.
class BalanceResult {
  final int credits;
  final bool isStale;

  const BalanceResult({required this.credits, required this.isStale});
}

/// Hive cache-read + online-write balance pattern (D-05).
///
/// 1. Fetch GET /api/v1/wallet/balance from API.
/// 2. Write-through to Hive box on success.
/// 3. On error: return cached value from Hive if present, else rethrow.
///
/// Accepts injectable [dio] and [box] for unit testing.
/// Returns the credit count. Throws [DioException] if offline AND no cache.
Future<int> fetchBalance({
  required Dio dio,
  required Box<int> box,
}) async {
  try {
    final response = await dio.get('/api/v1/wallet/balance');
    final balance =
        (response.data as Map<String, dynamic>)['availableCredits'] as int;
    await box.put('availableCredits', balance);
    return balance;
  } catch (e) {
    final cached = box.get('availableCredits');
    if (cached != null) return cached;
    rethrow;
  }
}

// ---------------------------------------------------------------------------
// Riverpod providers
// ---------------------------------------------------------------------------

/// Returns [BalanceResult] with credit count + staleness flag.
/// Online path: isStale = false. Offline cached path: isStale = true.
final balanceProvider = FutureProvider<BalanceResult>((ref) async {
  final dio = ref.read(dioProvider);
  final box = balanceBox;
  try {
    final response = await dio.get('/api/v1/wallet/balance');
    final balance =
        (response.data as Map<String, dynamic>)['availableCredits'] as int;
    await box.put('availableCredits', balance);
    return BalanceResult(credits: balance, isStale: false);
  } catch (e) {
    final cached = box.get('availableCredits');
    if (cached != null) {
      return BalanceResult(credits: cached, isStale: true);
    }
    rethrow;
  }
});

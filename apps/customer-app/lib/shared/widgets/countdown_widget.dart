// countdown_widget.dart — 2-minute STK countdown ring (MOBL-05b)
//
// Owns a Timer.periodic(1s) seeded from [timeoutSeconds]. Displays an 80dp
// progress ring with a centered mm:ss label. Every 5 seconds it invokes
// [onPoll] (status poll); when the clock reaches 0 it invokes [onExpire].
// The timer is always cancelled in dispose — no poll fires after the widget
// leaves the tree (RESEARCH Pattern 7 / Anti-Pattern: Timer without cleanup).
import 'dart:async';

import 'package:flutter/material.dart';

class CountdownWidget extends StatefulWidget {
  /// Seconds to count down from — seeded from the server's `timeoutSeconds`,
  /// never hardcoded (RESEARCH anti-pattern).
  final int timeoutSeconds;

  /// Called every 5 seconds while counting (status poll trigger).
  final VoidCallback onPoll;

  /// Called once when the countdown reaches 0.
  final VoidCallback onExpire;

  const CountdownWidget({
    super.key,
    required this.timeoutSeconds,
    required this.onPoll,
    required this.onExpire,
  });

  @override
  State<CountdownWidget> createState() => _CountdownWidgetState();
}

class _CountdownWidgetState extends State<CountdownWidget> {
  late int _remaining;
  Timer? _timer;

  @override
  void initState() {
    super.initState();
    _remaining = widget.timeoutSeconds;
    _timer = Timer.periodic(const Duration(seconds: 1), _tick);
  }

  void _tick(Timer timer) {
    if (!mounted) return;
    setState(() => _remaining = _remaining > 0 ? _remaining - 1 : 0);

    final elapsed = widget.timeoutSeconds - _remaining;
    if (_remaining == 0) {
      _timer?.cancel();
      widget.onExpire();
    } else if (elapsed > 0 && elapsed % 5 == 0) {
      widget.onPoll();
    }
  }

  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
  }

  String _format(int seconds) {
    final m = seconds ~/ 60;
    final s = (seconds % 60).toString().padLeft(2, '0');
    return '$m:$s';
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final fraction = widget.timeoutSeconds == 0
        ? 0.0
        : _remaining / widget.timeoutSeconds;

    return SizedBox(
      width: 80,
      height: 80,
      child: Stack(
        alignment: Alignment.center,
        children: [
          SizedBox(
            width: 80,
            height: 80,
            child: CircularProgressIndicator(
              value: fraction,
              strokeWidth: 6,
              color: theme.colorScheme.primary,
              backgroundColor: theme.colorScheme.surfaceContainerHighest,
            ),
          ),
          Text(
            _format(_remaining),
            style: theme.textTheme.headlineLarge,
          ),
        ],
      ),
    );
  }
}

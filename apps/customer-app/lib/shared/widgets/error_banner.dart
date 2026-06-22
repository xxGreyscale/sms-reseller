import 'package:flutter/material.dart';

/// MaterialBanner-style error widget.
/// Displays a problem message with an optional retry CTA. Dismissible.
class ErrorBanner extends StatelessWidget {
  final String message;
  final String retryLabel;
  final VoidCallback? onRetry;
  final VoidCallback? onDismiss;

  const ErrorBanner({
    super.key,
    required this.message,
    this.retryLabel = 'Try Again',
    this.onRetry,
    this.onDismiss,
  });

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    return Material(
      color: colorScheme.errorContainer,
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        child: Row(
          children: [
            Icon(Icons.error_outline, color: colorScheme.onErrorContainer, size: 20),
            const SizedBox(width: 8),
            Expanded(
              child: Text(
                message,
                style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                  color: colorScheme.onErrorContainer,
                ),
                softWrap: true,
              ),
            ),
            if (onRetry != null) ...[
              const SizedBox(width: 8),
              TextButton(
                onPressed: onRetry,
                style: TextButton.styleFrom(
                  foregroundColor: colorScheme.onErrorContainer,
                ),
                child: Text(retryLabel),
              ),
            ],
            if (onDismiss != null)
              IconButton(
                icon: Icon(Icons.close, color: colorScheme.onErrorContainer),
                onPressed: onDismiss,
                tooltip: 'Dismiss',
              ),
          ],
        ),
      ),
    );
  }
}

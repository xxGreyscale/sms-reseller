import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:customer_app/core/dio/dio_client.dart';
import 'package:customer_app/features/contacts/contact_api.dart';
import 'package:customer_app/features/contacts/contacts_provider.dart';
import 'package:customer_app/l10n/app_localizations.dart';
import 'package:customer_app/shared/widgets/error_banner.dart';
import 'package:customer_app/shared/widgets/loading_overlay.dart';

/// Add Contact Screen (MOBL-06b).
///
/// Online-only write: POST /api/v1/contacts.
/// Shows LoadingOverlay during POST.
/// On success: invalidates contactsProvider + pops.
/// On connectionError: shows ErrorBanner errorNetworkWrite + Try Again.
/// Does NOT write to Hive on failure (D-05, no silent queue).
class AddContactScreen extends ConsumerStatefulWidget {
  const AddContactScreen({super.key});

  @override
  ConsumerState<AddContactScreen> createState() => _AddContactScreenState();
}

class _AddContactScreenState extends ConsumerState<AddContactScreen> {
  final _formKey = GlobalKey<FormState>();
  final _nameController = TextEditingController();
  final _phoneController = TextEditingController();

  bool _isLoading = false;
  String? _errorMessage;

  @override
  void dispose() {
    _nameController.dispose();
    _phoneController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);

    return LoadingOverlay(
      isLoading: _isLoading,
      child: Scaffold(
        appBar: AppBar(title: Text(l10n.addContactTitle)),
        body: SingleChildScrollView(
          padding: const EdgeInsets.all(16),
          child: Form(
            key: _formKey,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                if (_errorMessage != null) ...[
                  ErrorBanner(
                    message: _errorMessage!,
                    retryLabel: l10n.errorRetryButton,
                    onRetry: _submit,
                    onDismiss: () => setState(() => _errorMessage = null),
                  ),
                  const SizedBox(height: 16),
                ],
                TextFormField(
                  key: const Key('fullNameField'),
                  controller: _nameController,
                  decoration: const InputDecoration(
                    labelText: 'Full Name',
                    border: OutlineInputBorder(),
                  ),
                  textCapitalization: TextCapitalization.words,
                  validator: (v) =>
                      (v == null || v.trim().isEmpty) ? 'Name is required' : null,
                ),
                const SizedBox(height: 16),
                TextFormField(
                  key: const Key('phoneField'),
                  controller: _phoneController,
                  decoration: const InputDecoration(
                    labelText: 'Phone Number',
                    hintText: '07xx xxx xxx',
                    border: OutlineInputBorder(),
                  ),
                  keyboardType: TextInputType.phone,
                  validator: (v) =>
                      (v == null || v.trim().isEmpty) ? 'Phone is required' : null,
                ),
                const SizedBox(height: 24),
                FilledButton(
                  key: const Key('addContactSaveButton'),
                  onPressed: _isLoading ? null : _submit,
                  child: Text(l10n.addContactSaveButton),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Future<void> _submit() async {
    if (!_formKey.currentState!.validate()) return;

    setState(() {
      _isLoading = true;
      _errorMessage = null;
    });

    try {
      final dio = ref.read(dioProvider);
      final api = ContactApi(dio);
      await api.createContact(
        name: _nameController.text.trim(),
        phone: _phoneController.text.trim(),
      );

      // Refresh the contacts list so new contact appears.
      await ref.read(contactsProvider.notifier).refresh();

      if (mounted) {
        if (context.canPop()) {
          context.pop();
        } else {
          context.go('/contacts');
        }
      }
    } on DioException catch (e) {
      if (!mounted) return;
      final l10n = AppLocalizations.of(context);
      setState(() {
        _isLoading = false;
        _errorMessage = (e.type == DioExceptionType.connectionError ||
                e.type == DioExceptionType.unknown)
            ? l10n.errorNetworkWrite
            : l10n.errorNetworkWrite;
      });
    } catch (_) {
      if (!mounted) return;
      final l10n = AppLocalizations.of(context);
      setState(() {
        _isLoading = false;
        _errorMessage = l10n.errorNetworkWrite;
      });
    }
  }
}

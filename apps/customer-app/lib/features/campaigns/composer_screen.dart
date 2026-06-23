import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import 'package:customer_app/core/router/app_router.dart';
import 'package:customer_app/features/contacts/contacts_provider.dart';
import 'package:customer_app/l10n/app_localizations.dart';
import 'package:customer_app/shared/widgets/error_banner.dart';
import 'package:customer_app/shared/widgets/loading_overlay.dart';
import 'package:customer_app/shared/widgets/sms_char_counter.dart';
import 'campaign_provider.dart';

class ComposerScreen extends ConsumerStatefulWidget {
  const ComposerScreen({super.key});

  @override
  ConsumerState<ComposerScreen> createState() => _ComposerScreenState();
}

class _ComposerScreenState extends ConsumerState<ComposerScreen> {
  final _messageController = TextEditingController();
  List<ContactItem> _selectedContacts = [];
  bool _loading = false;
  String? _error;
  bool _isInsufficientCredits = false;

  // Populated in build() via ref.watch so the picker always has the loaded list.
  List<ContactItem> _allContacts = [];

  @override
  void dispose() {
    _messageController.dispose();
    super.dispose();
  }

  bool get _canSend =>
      _selectedContacts.isNotEmpty && _messageController.text.trim().isNotEmpty;

  // ---------------------------------------------------------------------------
  // Recipient picker bottom sheet
  // ---------------------------------------------------------------------------

  Future<void> _openRecipientPicker() async {
    final allContacts = _allContacts;

    final selected = await showModalBottomSheet<List<ContactItem>>(
      context: context,
      isScrollControlled: true,
      useRootNavigator: true,
      builder: (_) => _RecipientPickerSheet(
        contacts: allContacts,
        initialSelected: _selectedContacts,
      ),
    );

    if (selected != null) {
      setState(() => _selectedContacts = selected);
    }
  }

  // ---------------------------------------------------------------------------
  // Send
  // ---------------------------------------------------------------------------

  Future<void> _send() async {
    final l10n = AppLocalizations.of(context)!;
    setState(() {
      _loading = true;
      _error = null;
      _isInsufficientCredits = false;
    });

    final notifier = ref.read(campaignSendProvider.notifier);
    final result = await notifier.sendCampaign(
      name: 'Campaign ${DateTime.now().millisecondsSinceEpoch}',
      body: _messageController.text.trim(),
      contactIds: _selectedContacts.map((c) => c.id).toList(),
    );

    if (!mounted) return;

    setState(() => _loading = false);

    switch (result) {
      case SendSuccess(:final campaignId):
        context.go('/campaigns/$campaignId');
      case SendInsufficientCredits():
        setState(() {
          _error = l10n.errorInsufficientCredits;
          _isInsufficientCredits = true;
        });
      case SendNetworkError():
        setState(() {
          _error = l10n.errorNetworkWrite;
          _isInsufficientCredits = false;
        });
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    final theme = Theme.of(context);

    // Watch contacts so _allContacts is populated as soon as provider loads.
    final contactsAsync = ref.watch(contactsProvider);
    _allContacts = contactsAsync.value?.contacts ?? [];

    return LoadingOverlay(
      isLoading: _loading,
      child: Scaffold(
        appBar: AppBar(title: Text(l10n.composerTitle)),
        body: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            // Error banner
            if (_error != null)
              Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  ErrorBanner(
                    key: const Key('composerErrorBanner'),
                    message: _error!,
                    onRetry: _isInsufficientCredits ? null : _send,
                    onDismiss: () => setState(() => _error = null),
                  ),
                  if (_isInsufficientCredits)
                    Padding(
                      padding: const EdgeInsets.symmetric(horizontal: 16),
                      child: FilledButton(
                        key: const Key('composerBuyCreditsButton'),
                        onPressed: () => context.go(kBundlesRoute),
                        child: const Text('Buy Credits'),
                      ),
                    ),
                ],
              ),
            Expanded(
              child: SingleChildScrollView(
                padding: const EdgeInsets.all(16),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    // Recipient section
                    Text('Send to', style: theme.textTheme.labelMedium),
                    const SizedBox(height: 8),
                    TextButton(
                      key: const Key('composerRecipientButton'),
                      onPressed: _openRecipientPicker,
                      child: Text(
                        _selectedContacts.isEmpty
                            ? 'Select contacts'
                            : 'Select contacts (${_selectedContacts.length} selected)',
                      ),
                    ),
                    const SizedBox(height: 16),

                    // Message section
                    TextFormField(
                      key: const Key('composerMessageField'),
                      controller: _messageController,
                      maxLines: null,
                      minLines: 5,
                      decoration: const InputDecoration(
                        hintText: 'Type your message…',
                        border: OutlineInputBorder(),
                      ),
                      onChanged: (_) => setState(() {}),
                    ),
                    const SizedBox(height: 8),

                    // Char counter
                    ValueListenableBuilder<TextEditingValue>(
                      valueListenable: _messageController,
                      builder: (_, value, __) =>
                          SmsCharCounter(text: value.text),
                    ),
                    const SizedBox(height: 24),
                  ],
                ),
              ),
            ),

            // Send button
            Padding(
              padding: const EdgeInsets.all(16),
              child: FilledButton(
                key: const Key('composerSendButton'),
                onPressed: _canSend ? _send : null,
                child: Text(l10n.composerSendButton),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

// ---------------------------------------------------------------------------
// Recipient picker bottom sheet
// ---------------------------------------------------------------------------

class _RecipientPickerSheet extends StatefulWidget {
  final List<ContactItem> contacts;
  final List<ContactItem> initialSelected;

  const _RecipientPickerSheet({
    required this.contacts,
    required this.initialSelected,
  });

  @override
  State<_RecipientPickerSheet> createState() => _RecipientPickerSheetState();
}

class _RecipientPickerSheetState extends State<_RecipientPickerSheet> {
  late final Set<String> _selectedIds;

  @override
  void initState() {
    super.initState();
    _selectedIds = widget.initialSelected.map((c) => c.id).toSet();
  }

  @override
  Widget build(BuildContext context) {
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            const Padding(
              padding: EdgeInsets.only(left: 16),
              child: Text(
                'Select contacts',
                style: TextStyle(fontSize: 16, fontWeight: FontWeight.w600),
              ),
            ),
            TextButton(
              key: const Key('composerPickerDone'),
              onPressed: () {
                final selected = widget.contacts
                    .where((c) => _selectedIds.contains(c.id))
                    .toList();
                Navigator.of(context).pop(selected);
              },
              child: const Text('Done'),
            ),
          ],
        ),
        ...widget.contacts.map((c) {
          return CheckboxListTile(
            value: _selectedIds.contains(c.id),
            title: Text(c.name),
            subtitle: Text(c.phone),
            onChanged: (checked) {
              setState(() {
                if (checked == true) {
                  _selectedIds.add(c.id);
                } else {
                  _selectedIds.remove(c.id);
                }
              });
            },
          );
        }),
      ],
    );
  }
}

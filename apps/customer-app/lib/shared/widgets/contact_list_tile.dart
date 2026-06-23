import 'package:flutter/material.dart';
import 'package:customer_app/features/contacts/contact_api.dart';

/// ListTile for a contact: avatar initial + name + phone + delete trailing icon.
///
/// The delete icon uses [colorScheme.error] foreground per UI-SPEC destructive actions.
class ContactListTile extends StatelessWidget {
  final ContactItem contact;
  final VoidCallback onDelete;

  const ContactListTile({
    super.key,
    required this.contact,
    required this.onDelete,
  });

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    final initial = contact.name.isNotEmpty
        ? contact.name[0].toUpperCase()
        : '?';

    return ListTile(
      tileColor: colorScheme.surfaceContainerHighest,
      leading: CircleAvatar(
        backgroundColor: colorScheme.primaryContainer,
        child: Text(
          initial,
          style: TextStyle(
            color: colorScheme.onPrimaryContainer,
            fontWeight: FontWeight.w600,
          ),
        ),
      ),
      title: Text(
        contact.name,
        style: Theme.of(context).textTheme.bodyMedium?.copyWith(
              fontWeight: FontWeight.w500,
            ),
        overflow: TextOverflow.ellipsis,
        softWrap: false,
      ),
      subtitle: Text(
        contact.phone,
        style: Theme.of(context).textTheme.bodySmall,
        overflow: TextOverflow.ellipsis,
        softWrap: false,
      ),
      trailing: IconButton(
        icon: const Icon(Icons.delete_outline),
        color: colorScheme.error,
        tooltip: 'Delete contact',
        onPressed: onDelete,
      ),
    );
  }
}

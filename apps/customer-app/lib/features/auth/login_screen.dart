import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:customer_app/l10n/app_localizations.dart';
import 'package:customer_app/core/router/app_router.dart';
import 'package:customer_app/features/auth/auth_api.dart';
import 'package:customer_app/shared/widgets/loading_overlay.dart';

/// Login screen — email + password, JWT session persistence.
class LoginScreen extends ConsumerStatefulWidget {
  const LoginScreen({super.key});

  @override
  ConsumerState<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends ConsumerState<LoginScreen> {
  final _formKey = GlobalKey<FormState>();
  final _emailCtrl = TextEditingController();
  final _passwordCtrl = TextEditingController();

  bool _obscurePassword = true;
  bool _isLoading = false;
  String? _loginError;

  @override
  void dispose() {
    _emailCtrl.dispose();
    _passwordCtrl.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (!(_formKey.currentState?.validate() ?? false)) return;

    setState(() {
      _isLoading = true;
      _loginError = null;
    });

    try {
      await ref.read(authApiProvider).login(
            email: _emailCtrl.text.trim(),
            password: _passwordCtrl.text,
          );
      // AuthNotifier updates state → go_router redirect fires → /dashboard or /pending
    } on InvalidCredentialsException {
      if (!mounted) return;
      final l10n = AppLocalizations.of(context);
      setState(() => _loginError = l10n.errorLoginFailed);
    } catch (_) {
      // Network/other errors surfaced generically
    } finally {
      if (mounted) setState(() => _isLoading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final colorScheme = Theme.of(context).colorScheme;
    final textTheme = Theme.of(context).textTheme;

    return LoadingOverlay(
      isLoading: _isLoading,
      child: Scaffold(
        body: SafeArea(
          child: SingleChildScrollView(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 32),
            child: Form(
              key: _formKey,
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  // Logo
                  Center(
                    child: Container(
                      width: 48,
                      height: 48,
                      decoration: BoxDecoration(
                        color: colorScheme.primaryContainer,
                        borderRadius: BorderRadius.circular(12),
                      ),
                      child: Icon(
                        Icons.message_rounded,
                        color: colorScheme.primary,
                        size: 28,
                      ),
                    ),
                  ),
                  const SizedBox(height: 24),

                  Text(
                    l10n.loginTitle,
                    style: textTheme.titleMedium?.copyWith(
                      fontWeight: FontWeight.w600,
                    ),
                    textAlign: TextAlign.center,
                  ),
                  const SizedBox(height: 32),

                  // Email field
                  TextFormField(
                    key: const Key('loginEmailField'),
                    controller: _emailCtrl,
                    keyboardType: TextInputType.emailAddress,
                    decoration: const InputDecoration(
                      labelText: 'Email',
                      border: OutlineInputBorder(),
                    ),
                    validator: (v) {
                      if (v == null || v.trim().isEmpty) return 'Required';
                      return null;
                    },
                  ),
                  const SizedBox(height: 16),

                  // Password field
                  TextFormField(
                    key: const Key('loginPasswordField'),
                    controller: _passwordCtrl,
                    obscureText: _obscurePassword,
                    decoration: InputDecoration(
                      labelText: 'Password',
                      border: const OutlineInputBorder(),
                      errorText: _loginError,
                      suffixIcon: IconButton(
                        tooltip: 'Toggle password visibility',
                        icon: Icon(_obscurePassword
                            ? Icons.visibility
                            : Icons.visibility_off),
                        onPressed: () =>
                            setState(() => _obscurePassword = !_obscurePassword),
                      ),
                    ),
                    validator: (v) =>
                        (v == null || v.isEmpty) ? 'Required' : null,
                  ),
                  const SizedBox(height: 24),

                  // Login button
                  FilledButton(
                    key: const Key('loginSubmitButton'),
                    onPressed: _isLoading ? null : _submit,
                    child: Text(l10n.loginSubmitButton),
                  ),
                  const SizedBox(height: 8),

                  // Forgot password
                  TextButton(
                    onPressed: () {
                      // TODO: navigate to forgot password screen (future plan)
                    },
                    child: Text(
                      l10n.loginForgotPassword,
                      style: TextStyle(color: colorScheme.primary),
                    ),
                  ),

                  // Register link
                  TextButton(
                    onPressed: () => context.go(kRegisterRoute),
                    child: Text(
                      l10n.loginNoAccount,
                      style: TextStyle(color: colorScheme.primary),
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}

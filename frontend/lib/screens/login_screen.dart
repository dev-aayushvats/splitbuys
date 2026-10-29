import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../api.dart';
import '../auth_state.dart';
import '../widgets.dart';
import 'ui.dart';

class LoginScreen extends StatefulWidget {
  const LoginScreen({super.key});

  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  final _email = TextEditingController();
  final _code = TextEditingController();
  bool _codeSent = false;
  bool _busy = false;

  @override
  void dispose() {
    _email.dispose();
    _code.dispose();
    super.dispose();
  }

  Future<void> _run(Future<void> Function() action) async {
    setState(() => _busy = true);
    try {
      await action();
    } on ApiException catch (e) {
      if (mounted) showError(context, e);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _sendCode() => _run(() async {
        await context.read<ApiClient>().requestOtp(_email.text.trim());
        setState(() => _codeSent = true);
      });

  // On success the router redirects away, so there is nothing else to do here.
  Future<void> _verify() => _run(() => context.read<AuthState>().verify(_email.text.trim(), _code.text.trim()));

  @override
  Widget build(BuildContext context) {
    return AuthPage(
      title: _codeSent ? 'Check your email' : 'Split bills with friends',
      subtitle: _codeSent ? 'We sent a 6-digit code to ${_email.text.trim()}' : 'Sign in with your email to get started',
      children: [
        if (!_codeSent) ...[
          TextField(
            controller: _email,
            enabled: !_busy,
            keyboardType: TextInputType.emailAddress,
            autofillHints: const [AutofillHints.email],
            decoration: const InputDecoration(labelText: 'Email'),
            onSubmitted: (_) => _sendCode(),
          ),
          const SizedBox(height: 20),
          ConfirmButton(label: 'Send code', busy: _busy, onPressed: _sendCode),
        ] else ...[
          TextField(
            controller: _code,
            enabled: !_busy,
            autofocus: true,
            keyboardType: TextInputType.number,
            textAlign: TextAlign.center,
            maxLength: 6,
            style: const TextStyle(fontSize: 26, letterSpacing: 8, fontWeight: FontWeight.w700),
            decoration: const InputDecoration(counterText: '', hintText: '------'),
            onSubmitted: (_) => _verify(),
          ),
          const SizedBox(height: 20),
          ConfirmButton(label: 'Verify', busy: _busy, onPressed: _verify),
          const SizedBox(height: 8),
          Row(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              TextButton(onPressed: _busy ? null : _sendCode, child: const Text('Resend code')),
              TextButton(
                onPressed: _busy ? null : () => setState(() => _codeSent = false),
                child: const Text('Change email'),
              ),
            ],
          ),
        ],
      ],
    );
  }
}

import 'package:flutter/material.dart';

import '../theme.dart';
import '../widgets.dart';

void showError(BuildContext context, Object error) {
  ScaffoldMessenger.of(context)
    ..hideCurrentSnackBar()
    ..showSnackBar(SnackBar(content: Text(error.toString())));
}

/// Full-screen page for sign-in style flows: logo, heading and a column of content.
class AuthPage extends StatelessWidget {
  final String title;
  final String? subtitle;
  final List<Widget> children;
  final bool showBack;

  const AuthPage({super.key, required this.title, this.subtitle, required this.children, this.showBack = false});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: showBack ? const TopBar(title: '') : null,
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(24),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Center(
                  child: Container(
                    width: 72,
                    height: 72,
                    decoration: const BoxDecoration(shape: BoxShape.circle, color: AppColors.accent),
                    child: const Icon(Icons.call_split_rounded, size: 36, color: AppColors.onAccent),
                  ),
                ),
                const SizedBox(height: 24),
                Text(title, textAlign: TextAlign.center, style: const TextStyle(fontSize: 26, fontWeight: FontWeight.w800)),
                if (subtitle != null)
                  Padding(
                    padding: const EdgeInsets.only(top: 8),
                    child: Text(subtitle!, textAlign: TextAlign.center, style: const TextStyle(color: AppColors.muted)),
                  ),
                const SizedBox(height: 28),
                ...children,
              ],
            ),
          ),
        ),
      ),
    );
  }
}

const _months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

String formatDate(DateTime t) {
  final d = t.toLocal();
  return '${d.day} ${_months[d.month - 1]}';
}

Future<bool> confirm(BuildContext context, String title, {String action = 'Delete'}) async {
  final ok = await showDialog<bool>(
    context: context,
    builder: (_) => AlertDialog(
      title: Text(title),
      actions: [
        TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancel')),
        FilledButton(
          onPressed: () => Navigator.pop(context, true),
          style: FilledButton.styleFrom(minimumSize: const Size(88, 44)),
          child: Text(action),
        ),
      ],
    ),
  );
  return ok == true;
}

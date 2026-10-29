import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import '../api.dart';
import '../models.dart';
import '../theme.dart';
import '../widgets.dart';
import 'ui.dart';

class JoinScreen extends StatefulWidget {
  final String token;

  const JoinScreen({super.key, required this.token});

  @override
  State<JoinScreen> createState() => _JoinScreenState();
}

class _JoinScreenState extends State<JoinScreen> {
  late final Future<InvitePreview> _preview = context.read<ApiClient>().previewInvite(widget.token);
  bool _busy = false;

  Future<void> _join() async {
    setState(() => _busy = true);
    try {
      final group = await context.read<ApiClient>().joinInvite(widget.token);
      if (mounted) context.go('/groups/${group.id}');
    } on ApiException catch (e) {
      if (mounted) {
        setState(() => _busy = false);
        showError(context, e);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return FutureBuilder<InvitePreview>(
      future: _preview,
      builder: (context, snapshot) {
        if (snapshot.connectionState != ConnectionState.done) {
          return const Scaffold(body: Center(child: CircularProgressIndicator()));
        }
        if (snapshot.hasError) {
          return AuthPage(title: 'Invite not found', subtitle: snapshot.error.toString(), children: [
            OutlinedButton(onPressed: () => context.go('/'), child: const Text('Go to my groups')),
          ]);
        }
        final p = snapshot.data!;
        return AuthPage(
          title: 'You are invited',
          subtitle: 'Join this group to split expenses together',
          children: [
            DashedBorder(
              child: Column(
                children: [
                  Text(p.groupName, textAlign: TextAlign.center, style: const TextStyle(fontSize: 22, fontWeight: FontWeight.w800)),
                  const SizedBox(height: 6),
                  Text('${p.memberCount} member${p.memberCount == 1 ? '' : 's'}', style: const TextStyle(color: AppColors.muted)),
                ],
              ),
            ),
            const SizedBox(height: 24),
            if (p.alreadyMember)
              ConfirmButton(label: 'Open group', onPressed: () => context.go('/groups/${p.groupId}'))
            else
              ConfirmButton(label: 'Join group', busy: _busy, onPressed: _join),
          ],
        );
      },
    );
  }
}

import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import '../api.dart';
import '../auth_state.dart';
import '../models.dart';
import '../money.dart';
import '../theme.dart';
import '../widgets.dart';
import 'ui.dart';

class GroupsScreen extends StatefulWidget {
  const GroupsScreen({super.key});

  @override
  State<GroupsScreen> createState() => _GroupsScreenState();
}

class _GroupsScreenState extends State<GroupsScreen> {
  late Future<List<GroupSummary>> _groups = _load();

  Future<List<GroupSummary>> _load() => context.read<ApiClient>().groups();

  Future<void> _refresh() async {
    setState(() => _groups = _load());
    await _groups.catchError((_) => <GroupSummary>[]);
  }

  Future<void> _create() async {
    final name = await showDialog<String>(context: context, builder: (_) => const _NameDialog());
    if (name == null || !mounted) return;
    try {
      final group = await context.read<ApiClient>().createGroup(name);
      if (mounted) await context.push('/groups/${group.id}');
      _refresh();
    } on ApiException catch (e) {
      if (mounted) showError(context, e);
    }
  }

  @override
  Widget build(BuildContext context) {
    final auth = context.watch<AuthState>();
    final me = auth.user!;
    return Scaffold(
      body: SafeArea(
        child: FutureBuilder<List<GroupSummary>>(
          future: _groups,
          builder: (context, snapshot) {
            final groups = snapshot.data;
            return RefreshIndicator(
              onRefresh: _refresh,
              child: ListView(
                padding: const EdgeInsets.fromLTRB(20, 16, 20, 32),
                children: [
                  _Header(me: me, onSignOut: auth.signOut),
                  const SizedBox(height: 20),
                  _Overview(groups: groups ?? const [], onNewGroup: _create),
                  const SizedBox(height: 24),
                  SectionTitle('Your groups', trailing: groups == null ? null : Text('${groups.length}', style: const TextStyle(color: AppColors.muted))),
                  if (groups == null && snapshot.hasError)
                    Column(children: [
                      Text(snapshot.error.toString(), textAlign: TextAlign.center),
                      TextButton(onPressed: _refresh, child: const Text('Retry')),
                    ])
                  else if (groups == null)
                    const Padding(padding: EdgeInsets.all(32), child: Center(child: CircularProgressIndicator()))
                  else if (groups.isEmpty)
                    const DashedBorder(
                      padding: EdgeInsets.all(24),
                      child: Center(
                        child: Text(
                          'No groups yet.\nCreate one, or open an invite link from a friend.',
                          textAlign: TextAlign.center,
                          style: TextStyle(color: AppColors.muted, height: 1.4),
                        ),
                      ),
                    )
                  else
                    for (final g in groups)
                      Padding(
                        padding: const EdgeInsets.only(bottom: 12),
                        child: AppTile(
                          leading: const IconBadge(Icons.groups_rounded),
                          title: g.name,
                          subtitle: '${g.memberCount} member${g.memberCount == 1 ? '' : 's'}',
                          trailing: _BalanceText(g.myBalancePaise),
                          onTap: () async {
                            await context.push('/groups/${g.id}');
                            _refresh();
                          },
                        ),
                      ),
                ],
              ),
            );
          },
        ),
      ),
    );
  }
}

class _Header extends StatelessWidget {
  final User me;
  final VoidCallback onSignOut;

  const _Header({required this.me, required this.onSignOut});

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text('Welcome back', style: TextStyle(color: AppColors.muted, fontSize: 13)),
              Text(me.label, maxLines: 1, overflow: TextOverflow.ellipsis, style: const TextStyle(fontSize: 24, fontWeight: FontWeight.w800)),
            ],
          ),
        ),
        PopupMenuButton<String>(
          onSelected: (_) => onSignOut(),
          itemBuilder: (_) => [
            PopupMenuItem(enabled: false, child: Text(me.email, style: const TextStyle(color: AppColors.muted))),
            const PopupMenuItem(value: 'out', child: Text('Sign out')),
          ],
          child: Avatar(me, size: 46),
        ),
      ],
    );
  }
}

/// Totals across all groups.
class _Overview extends StatelessWidget {
  final List<GroupSummary> groups;
  final VoidCallback onNewGroup;

  const _Overview({required this.groups, required this.onNewGroup});

  @override
  Widget build(BuildContext context) {
    final owed = groups.where((g) => g.myBalancePaise > 0).fold(0, (s, g) => s + g.myBalancePaise);
    final owe = groups.where((g) => g.myBalancePaise < 0).fold(0, (s, g) => s - g.myBalancePaise);
    final net = owed - owe;

    Widget line(String label, int paise, Color color) => Padding(
          padding: const EdgeInsets.only(bottom: 6),
          child: Row(
            children: [
              Text(label, style: const TextStyle(color: AppColors.muted, fontSize: 13)),
              const SizedBox(width: 8),
              Flexible(child: Text(formatPaise(paise), overflow: TextOverflow.ellipsis, style: TextStyle(color: color, fontWeight: FontWeight.w700))),
            ],
          ),
        );

    return Container(
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(28),
        border: Border.all(color: const Color(0xFF54445E), width: 1.5),
        gradient: const LinearGradient(
          colors: [Color(0xFF30263A), Color(0xFF181A23)],
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
        ),
      ),
      child: Row(
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text('Overview', style: TextStyle(fontSize: 18, fontWeight: FontWeight.w700)),
                const SizedBox(height: 12),
                line('You are owed', owed, AppColors.positive),
                line('You owe', owe, AppColors.negative),
                const SizedBox(height: 10),
                FilledButton(
                  onPressed: onNewGroup,
                  style: FilledButton.styleFrom(minimumSize: const Size(0, 44), padding: const EdgeInsets.symmetric(horizontal: 20)),
                  child: const Text('New group'),
                ),
              ],
            ),
          ),
          const SizedBox(width: 12),
          SizedBox(
            width: 128,
            child: DashedBorder(
              radius: 20,
              padding: const EdgeInsets.symmetric(vertical: 16, horizontal: 10),
              child: Column(
                children: [
                  const Text('Net balance', style: TextStyle(fontSize: 12, color: AppColors.muted)),
                  const SizedBox(height: 6),
                  FittedBox(
                    fit: BoxFit.scaleDown,
                    child: Text(formatPaise(net.abs()), style: const TextStyle(fontSize: 20, fontWeight: FontWeight.w800)),
                  ),
                  const SizedBox(height: 10),
                  Container(
                    width: 38,
                    height: 38,
                    decoration: BoxDecoration(shape: BoxShape.circle, color: AppColors.accent.withValues(alpha: 0.16)),
                    child: const Icon(Icons.account_balance_wallet_rounded, size: 19, color: AppColors.accent),
                  ),
                  const SizedBox(height: 8),
                  Text(
                    net == 0 ? 'all settled' : (net > 0 ? 'you are owed' : 'you owe'),
                    style: const TextStyle(fontSize: 11.5, color: AppColors.muted),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _BalanceText extends StatelessWidget {
  final int paise;

  const _BalanceText(this.paise);

  @override
  Widget build(BuildContext context) {
    if (paise == 0) return const Text('settled up', style: TextStyle(color: AppColors.muted, fontSize: 12.5));
    final owed = paise > 0;
    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.end,
      children: [
        Text(owed ? 'you are owed' : 'you owe', style: const TextStyle(fontSize: 11.5, color: AppColors.muted)),
        const SizedBox(height: 2),
        Text(
          formatPaise(paise.abs()),
          style: TextStyle(fontWeight: FontWeight.w800, color: owed ? AppColors.positive : AppColors.negative),
        ),
      ],
    );
  }
}

class _NameDialog extends StatefulWidget {
  const _NameDialog();

  @override
  State<_NameDialog> createState() => _NameDialogState();
}

class _NameDialogState extends State<_NameDialog> {
  final _controller = TextEditingController();

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  void _submit() {
    final name = _controller.text.trim();
    if (name.isNotEmpty) Navigator.pop(context, name);
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: const Text('New group'),
      content: TextField(
        controller: _controller,
        autofocus: true,
        textCapitalization: TextCapitalization.words,
        decoration: const InputDecoration(labelText: 'Group name'),
        onSubmitted: (_) => _submit(),
      ),
      actions: [
        TextButton(onPressed: () => Navigator.pop(context), child: const Text('Cancel')),
        FilledButton(onPressed: _submit, child: const Text('Create')),
      ],
    );
  }
}

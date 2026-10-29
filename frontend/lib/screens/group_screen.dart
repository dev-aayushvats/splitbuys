import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import '../api.dart';
import '../auth_state.dart';
import '../config.dart';
import '../models.dart';
import 'balances_tab.dart';
import 'expense_form_screen.dart';
import 'expenses_tab.dart';
import '../money.dart';
import '../theme.dart';
import '../widgets.dart';
import 'ui.dart';

class GroupScreen extends StatefulWidget {
  final int groupId;

  const GroupScreen({super.key, required this.groupId});

  @override
  State<GroupScreen> createState() => _GroupScreenState();
}

class _GroupScreenState extends State<GroupScreen> with SingleTickerProviderStateMixin {
  late final TabController _tabs = TabController(length: 3, vsync: this)..addListener(() => setState(() {}));
  late Future<GroupData> _data = _load();

  @override
  void dispose() {
    _tabs.dispose();
    super.dispose();
  }

  Future<GroupData> _load() async {
    final api = context.read<ApiClient>();
    final id = widget.groupId;
    final r = await Future.wait([api.group(id), api.expenses(id), api.settlements(id), api.balances(id)]);
    return GroupData(
      group: r[0] as GroupDetail,
      expenses: r[1] as List<Expense>,
      settlements: r[2] as List<Settlement>,
      balances: r[3] as Balances,
    );
  }

  void _reload() => setState(() => _data = _load());

  Future<void> _attempt(Future<void> Function() action) async {
    try {
      await action();
    } on ApiException catch (e) {
      if (mounted) showError(context, e);
    }
  }

  Future<void> _addExpense(GroupDetail group) async {
    final me = context.read<AuthState>().user!;
    final saved = await Navigator.of(context).push<bool>(
      MaterialPageRoute(builder: (_) => ExpenseFormScreen(group: group, me: me)),
    );
    if (saved == true) _reload();
  }

  Future<void> _regenerate() async {
    if (!await confirm(context, 'Reset invite link?\nThe current link will stop working.', action: 'Reset')) return;
    await _attempt(() async {
      await context.read<ApiClient>().regenerateInvite(widget.groupId);
      _reload();
    });
  }

  Future<void> _leave() async {
    if (!await confirm(context, 'Leave this group?', action: 'Leave')) return;
    await _attempt(() async {
      await context.read<ApiClient>().leaveGroup(widget.groupId);
      if (mounted) context.go('/');
    });
  }

  @override
  Widget build(BuildContext context) {
    final me = context.watch<AuthState>().user!;
    return FutureBuilder<GroupData>(
      future: _data,
      builder: (context, snapshot) {
        final data = snapshot.data;
        return Scaffold(
          appBar: TopBar(
            title: data?.group.name ?? '',
            action: data == null
                ? null
                : PopupMenuButton<String>(
                    onSelected: (v) => v == 'leave' ? _leave() : _regenerate(),
                    itemBuilder: (_) => const [
                      PopupMenuItem(value: 'reset', child: Text('Reset invite link')),
                      PopupMenuItem(value: 'leave', child: Text('Leave group')),
                    ],
                    child: const CircleShell(child: Icon(Icons.more_horiz_rounded, size: 22)),
                  ),
          ),
          floatingActionButtonLocation: FloatingActionButtonLocation.centerFloat,
          floatingActionButton: data != null && _tabs.index == 0
              ? FloatingActionButton(
                  onPressed: () => _addExpense(data.group),
                  tooltip: 'Add expense',
                  child: const Icon(Icons.add_rounded, size: 34),
                )
              : null,
          body: data != null
              ? Column(
                  children: [
                    _Summary(data: data, me: me),
                    Padding(
                      padding: const EdgeInsets.fromLTRB(20, 16, 20, 4),
                      child: PillTabs(
                        labels: const ['Expenses', 'Balances', 'Members'],
                        index: _tabs.index,
                        onChanged: (i) => _tabs.animateTo(i),
                      ),
                    ),
                    Expanded(
                      child: TabBarView(
                        controller: _tabs,
                        children: [
                          ExpensesTab(data: data, me: me, onChanged: _reload),
                          BalancesTab(data: data, me: me, onChanged: _reload),
                          _MembersTab(group: data.group, me: me),
                        ],
                      ),
                    ),
                  ],
                )
              : snapshot.hasError
                  ? Center(
                      child: Column(mainAxisSize: MainAxisSize.min, children: [
                        Text(snapshot.error.toString()),
                        TextButton(onPressed: _reload, child: const Text('Retry')),
                      ]),
                    )
                  : const Center(child: CircularProgressIndicator()),
        );
      },
    );
  }
}

/// Two stat cards: what the group has spent, and where you stand.
class _Summary extends StatelessWidget {
  final GroupData data;
  final User me;

  const _Summary({required this.data, required this.me});

  @override
  Widget build(BuildContext context) {
    final total = data.expenses.fold(0, (s, e) => s + e.amountPaise);
    final mine = data.balances.balances.where((b) => b.user.id == me.id).fold(0, (s, b) => s + b.netPaise);
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 20),
      child: Row(
        children: [
          Expanded(child: InfoCard(icon: Icons.receipt_long_rounded, label: 'Total spent', value: formatPaise(total))),
          const SizedBox(width: 12),
          Expanded(
            child: InfoCard(
              icon: Icons.account_balance_wallet_rounded,
              label: mine == 0 ? 'Your balance' : (mine > 0 ? 'You are owed' : 'You owe'),
              value: mine == 0 ? 'Settled up' : formatPaise(mine.abs()),
              valueColor: mine == 0 ? null : (mine > 0 ? AppColors.positive : AppColors.negative),
            ),
          ),
        ],
      ),
    );
  }
}

class _MembersTab extends StatelessWidget {
  final GroupDetail group;
  final User me;

  const _MembersTab({required this.group, required this.me});

  @override
  Widget build(BuildContext context) {
    final link = inviteLink(group.inviteToken);
    return ListView(
      padding: const EdgeInsets.fromLTRB(20, 16, 20, 100),
      children: [
        DashedBorder(
          child: Column(
            children: [
              const Text('Splitting with', style: TextStyle(fontWeight: FontWeight.w600)),
              const SizedBox(height: 10),
              AvatarStack(group.members, size: 44, max: 5),
              const SizedBox(height: 8),
              Text('${group.members.length} ${group.members.length == 1 ? 'person' : 'people'}',
                  style: const TextStyle(color: AppColors.muted, fontSize: 12.5)),
              const SizedBox(height: 16),
              Container(
                padding: const EdgeInsets.fromLTRB(14, 6, 6, 6),
                decoration: BoxDecoration(color: AppColors.surface, borderRadius: BorderRadius.circular(18)),
                child: Row(
                  children: [
                    Expanded(child: SelectableText(link, maxLines: 1, style: const TextStyle(fontSize: 12.5, color: AppColors.muted))),
                    FilledButton.icon(
                      onPressed: () async {
                        await Clipboard.setData(ClipboardData(text: link));
                        if (context.mounted) {
                          ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Invite link copied')));
                        }
                      },
                      style: FilledButton.styleFrom(minimumSize: const Size(0, 40), padding: const EdgeInsets.symmetric(horizontal: 14)),
                      icon: const Icon(Icons.copy_rounded, size: 16),
                      label: const Text('Copy'),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
        const SizedBox(height: 24),
        const SectionTitle('Members'),
        for (final m in group.members)
          Padding(
            padding: const EdgeInsets.only(bottom: 12),
            child: AppTile(
              leading: Avatar(m, size: 46),
              title: m.id == me.id ? '${m.label} (you)' : m.label,
              subtitle: m.name == null ? null : m.email,
            ),
          ),
      ],
    );
  }
}

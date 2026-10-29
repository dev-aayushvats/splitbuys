import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../api.dart';
import '../models.dart';
import '../money.dart';
import '../theme.dart';
import '../widgets.dart';
import 'expense_form_screen.dart';
import 'ui.dart';

/// Expenses and recorded payments, newest first.
class ExpensesTab extends StatelessWidget {
  final GroupData data;
  final User me;
  final VoidCallback onChanged;

  const ExpensesTab({super.key, required this.data, required this.me, required this.onChanged});

  @override
  Widget build(BuildContext context) {
    final items = <(DateTime, Widget)>[
      for (final e in data.expenses) (e.createdAt, _expenseTile(context, e)),
      for (final s in data.settlements) (s.createdAt, _settlementTile(context, s)),
    ]..sort((a, b) => b.$1.compareTo(a.$1));

    if (items.isEmpty) {
      return const Center(
        child: Padding(
          padding: EdgeInsets.all(32),
          child: Text('No expenses yet.\nTap + to add the first one.', textAlign: TextAlign.center, style: TextStyle(color: AppColors.muted, height: 1.4)),
        ),
      );
    }
    return ListView.separated(
      padding: const EdgeInsets.fromLTRB(20, 16, 20, 100),
      itemCount: items.length,
      separatorBuilder: (_, _) => const SizedBox(height: 12),
      itemBuilder: (_, i) => items[i].$2,
    );
  }

  Widget _expenseTile(BuildContext context, Expense e) {
    final myShare = e.shareOf(me.id);
    final String note;
    final Color? color;
    if (e.payer.id == me.id) {
      final lent = e.amountPaise - myShare;
      note = lent > 0 ? 'you lent\n${formatPaise(lent)}' : 'you paid';
      color = lent > 0 ? AppColors.positive : AppColors.muted;
    } else if (myShare > 0) {
      note = 'you owe\n${formatPaise(myShare)}';
      color = AppColors.negative;
    } else {
      note = 'not involved';
      color = AppColors.muted;
    }
    return AppTile(
      leading: const IconBadge(Icons.receipt_long_rounded),
      title: e.description,
      subtitle: '${e.payer.id == me.id ? 'You' : e.payer.label} paid ${formatPaise(e.amountPaise)} · ${formatDate(e.createdAt)}',
      trailing: Text(note, textAlign: TextAlign.end, style: TextStyle(color: color, fontWeight: FontWeight.w700, fontSize: 13)),
      onTap: () async {
        final saved = await Navigator.of(context).push<bool>(
          MaterialPageRoute(builder: (_) => ExpenseFormScreen(group: data.group, me: me, expense: e)),
        );
        if (saved == true) onChanged();
      },
    );
  }

  Widget _settlementTile(BuildContext context, Settlement s) {
    String who(User u) => u.id == me.id ? 'You' : u.label;
    return AppTile(
      leading: const IconBadge(Icons.payments_rounded, color: AppColors.positive),
      title: '${who(s.from)} paid ${s.to.id == me.id ? 'you' : s.to.label}',
      subtitle: 'Payment · ${formatDate(s.createdAt)} · tap to delete',
      trailing: Text(formatPaise(s.amountPaise), style: const TextStyle(fontWeight: FontWeight.w800, color: AppColors.positive)),
      onTap: () => _deleteSettlement(context, s),
    );
  }

  Future<void> _deleteSettlement(BuildContext context, Settlement s) async {
    if (!await confirm(context, 'Delete this payment of ${formatPaise(s.amountPaise)}?') || !context.mounted) return;
    try {
      await context.read<ApiClient>().deleteSettlement(data.group.id, s.id);
      onChanged();
    } on ApiException catch (e) {
      if (context.mounted) showError(context, e);
    }
  }
}

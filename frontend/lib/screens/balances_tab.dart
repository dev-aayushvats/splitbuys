import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../api.dart';
import '../models.dart';
import '../money.dart';
import '../theme.dart';
import '../widgets.dart';
import 'ui.dart';

class BalancesTab extends StatelessWidget {
  final GroupData data;
  final User me;
  final VoidCallback onChanged;

  const BalancesTab({super.key, required this.data, required this.me, required this.onChanged});

  String _who(User u) => u.id == me.id ? 'You' : u.label;

  @override
  Widget build(BuildContext context) {
    final plan = data.balances.settleUp;
    return ListView(
      padding: const EdgeInsets.fromLTRB(20, 16, 20, 32),
      children: [
        const SectionTitle('Settle up'),
        DashedBorder(
          padding: const EdgeInsets.all(12),
          child: plan.isEmpty
              ? const Padding(
                  padding: EdgeInsets.all(14),
                  child: Center(child: Text('Everyone is settled up.', style: TextStyle(color: AppColors.muted))),
                )
              : Column(
                  children: [
                    for (final t in plan)
                      Padding(
                        padding: const EdgeInsets.symmetric(vertical: 6),
                        child: AppTile(
                          leading: AvatarStack([t.from, t.to], size: 34, max: 2),
                          title: '${_who(t.from)} ${t.from.id == me.id ? 'pay' : 'pays'} ${t.to.id == me.id ? 'you' : t.to.label}',
                          subtitle: formatPaise(t.amountPaise),
                          trailing: FilledButton(
                            onPressed: () => _record(context, t),
                            style: FilledButton.styleFrom(minimumSize: const Size(0, 38), padding: const EdgeInsets.symmetric(horizontal: 14)),
                            child: const Text('Record', style: TextStyle(fontSize: 13)),
                          ),
                        ),
                      ),
                  ],
                ),
        ),
        const SizedBox(height: 24),
        const SectionTitle('Balances'),
        for (final b in data.balances.balances)
          Padding(
            padding: const EdgeInsets.only(bottom: 12),
            child: AppTile(
              leading: Avatar(b.user, size: 46),
              title: b.user.id == me.id ? '${b.user.label} (you)' : b.user.label,
              trailing: b.netPaise == 0
                  ? const Text('settled up', style: TextStyle(color: AppColors.muted, fontSize: 12.5))
                  : Column(
                      mainAxisSize: MainAxisSize.min,
                      crossAxisAlignment: CrossAxisAlignment.end,
                      children: [
                        Text(b.netPaise > 0 ? 'is owed' : 'owes', style: const TextStyle(fontSize: 11.5, color: AppColors.muted)),
                        Text(
                          formatPaise(b.netPaise.abs()),
                          style: TextStyle(fontWeight: FontWeight.w800, color: b.netPaise > 0 ? AppColors.positive : AppColors.negative),
                        ),
                      ],
                    ),
            ),
          ),
      ],
    );
  }

  Future<void> _record(BuildContext context, Transfer t) async {
    final paise = await showDialog<int>(context: context, builder: (_) => _PaymentDialog(transfer: t, me: me));
    if (paise == null || !context.mounted) return;
    try {
      await context.read<ApiClient>().addSettlement(data.group.id, t.from.id, t.to.id, paise);
      onChanged();
    } on ApiException catch (e) {
      if (context.mounted) showError(context, e);
    }
  }
}

class _PaymentDialog extends StatefulWidget {
  final Transfer transfer;
  final User me;

  const _PaymentDialog({required this.transfer, required this.me});

  @override
  State<_PaymentDialog> createState() => _PaymentDialogState();
}

class _PaymentDialogState extends State<_PaymentDialog> {
  late final _amount = TextEditingController(text: hundredthsToInput(widget.transfer.amountPaise));
  String? _error;

  @override
  void dispose() {
    _amount.dispose();
    super.dispose();
  }

  void _submit() {
    final paise = parseHundredths(_amount.text);
    if (paise == null || paise <= 0) {
      setState(() => _error = 'Enter a valid amount');
      return;
    }
    Navigator.pop(context, paise);
  }

  @override
  Widget build(BuildContext context) {
    final t = widget.transfer;
    String who(User u) => u.id == widget.me.id ? 'You' : u.label;
    return AlertDialog(
      title: Text('${who(t.from)} → ${who(t.to)}'),
      content: TextField(
        controller: _amount,
        autofocus: true,
        keyboardType: const TextInputType.numberWithOptions(decimal: true),
        decoration: InputDecoration(labelText: 'Amount paid', prefixText: '₹ ', errorText: _error),
        onSubmitted: (_) => _submit(),
      ),
      actions: [
        TextButton(onPressed: () => Navigator.pop(context), child: const Text('Cancel')),
        FilledButton(onPressed: _submit, child: const Text('Record')),
      ],
    );
  }
}

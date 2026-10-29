import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../api.dart';
import '../models.dart';
import '../money.dart';
import '../theme.dart';
import '../widgets.dart';
import 'ui.dart';

enum _Kind {
  equal('EQUAL', 'Equal'),
  percent('PERCENT', 'Percent'),
  exact('EXACT', 'Exact'),
  shares('SHARES', 'Shares');

  final String api;
  final String label;

  const _Kind(this.api, this.label);
}

/// Add a new expense, or edit [expense]. Pops `true` when something was saved or deleted.
class ExpenseFormScreen extends StatefulWidget {
  final GroupDetail group;
  final User me;
  final Expense? expense;

  const ExpenseFormScreen({super.key, required this.group, required this.me, this.expense});

  @override
  State<ExpenseFormScreen> createState() => _ExpenseFormScreenState();
}

class _ExpenseFormScreenState extends State<ExpenseFormScreen> {
  final _description = TextEditingController();
  final _amount = TextEditingController();
  final _values = <int, TextEditingController>{};
  final _selected = <int>{};
  late int _payerId = widget.me.id;
  _Kind _kind = _Kind.equal;
  String? _note;
  bool _busy = false;

  Expense? get _editing => widget.expense;

  @override
  void initState() {
    super.initState();
    for (final m in widget.group.members) {
      _values[m.id] = TextEditingController()..addListener(() => setState(() {}));
    }
    _description.addListener(() => setState(() {}));
    _amount.addListener(() => setState(() {}));

    final e = _editing;
    if (e == null) {
      _selected.addAll(widget.group.members.map((m) => m.id));
      return;
    }
    _description.text = e.description;
    _amount.text = hundredthsToInput(e.amountPaise);
    _payerId = e.payer.id;
    _selected.addAll(e.shares.map((s) => s.user.id).where(_values.containsKey));
    if (e.splitType == 'EQUAL') {
      _kind = _Kind.equal;
    } else {
      // Only the resulting amounts are stored, so percentage/share splits reopen as exact amounts.
      _kind = _Kind.exact;
      for (final s in e.shares) {
        _values[s.user.id]?.text = hundredthsToInput(s.amountPaise);
      }
      if (e.splitType != 'EXACT') _note = 'This was split by ${e.splitType == 'PERCENT' ? 'percentage' : 'shares'}; shown here as exact amounts.';
    }
  }

  @override
  void dispose() {
    _description.dispose();
    _amount.dispose();
    for (final c in _values.values) {
      c.dispose();
    }
    super.dispose();
  }

  int? get _totalPaise => parseHundredths(_amount.text);

  /// Per-person raw values in the unit the API expects, or null with [problem] explaining why not.
  ({List<Map<String, dynamic>>? splits, String? problem, String? hint}) _evaluate() {
    final total = _totalPaise;
    if (_description.text.trim().isEmpty) return (splits: null, problem: 'Add a description', hint: null);
    if (total == null || total <= 0) return (splits: null, problem: 'Enter a valid amount', hint: null);
    if (_selected.isEmpty) return (splits: null, problem: 'Pick at least one person', hint: null);

    final ids = widget.group.members.map((m) => m.id).where(_selected.contains).toList();
    switch (_kind) {
      case _Kind.equal:
        return (
          splits: [for (final id in ids) {'userId': id}],
          problem: null,
          hint: '${formatPaise(total ~/ ids.length)} each',
        );
      case _Kind.shares:
        final weights = <int, int>{};
        for (final id in ids) {
          final text = _values[id]!.text.trim();
          final w = text.isEmpty ? 1 : int.tryParse(text);
          if (w == null || w < 0) return (splits: null, problem: 'Shares must be whole numbers', hint: null);
          weights[id] = w;
        }
        if (weights.values.every((w) => w == 0)) return (splits: null, problem: 'At least one share must be above zero', hint: null);
        return (
          splits: [for (final id in ids) {'userId': id, 'value': weights[id]}],
          problem: null,
          hint: 'Empty fields count as 1 share',
        );
      case _Kind.percent:
      case _Kind.exact:
        final isPercent = _kind == _Kind.percent;
        final amounts = <int, int>{};
        for (final id in ids) {
          final text = _values[id]!.text.trim();
          final v = text.isEmpty ? 0 : parseHundredths(text);
          if (v == null) return (splits: null, problem: 'Check the number for ${_name(id)}', hint: null);
          amounts[id] = v;
        }
        final sum = amounts.values.fold(0, (a, b) => a + b);
        final target = isPercent ? 10000 : total;
        final diff = target - sum;
        if (diff != 0) {
          final left = isPercent ? '${hundredthsToInput(diff.abs())}%' : formatPaise(diff.abs());
          final msg = diff > 0 ? '$left left to assign' : 'Over by $left';
          return (splits: null, problem: msg, hint: null);
        }
        return (
          splits: [
            for (final id in ids) {'userId': id, 'value': isPercent ? amounts[id]! / 100 : amounts[id]},
          ],
          problem: null,
          hint: isPercent ? 'Adds up to 100%' : 'Adds up to ${formatPaise(total)}',
        );
    }
  }

  String _name(int id) => widget.group.members.firstWhere((m) => m.id == id).label;

  Future<void> _save(List<Map<String, dynamic>> splits) async {
    setState(() => _busy = true);
    final api = context.read<ApiClient>();
    final body = {
      'description': _description.text.trim(),
      'amountPaise': _totalPaise,
      'payerId': _payerId,
      'splitType': _kind.api,
      'splits': splits,
    };
    try {
      final e = _editing;
      if (e == null) {
        await api.addExpense(widget.group.id, body);
      } else {
        await api.updateExpense(widget.group.id, e.id, body);
      }
      if (mounted) Navigator.pop(context, true);
    } on ApiException catch (err) {
      if (mounted) {
        setState(() => _busy = false);
        showError(context, err);
      }
    }
  }

  Future<void> _delete() async {
    if (!await confirm(context, 'Delete this expense?') || !mounted) return;
    setState(() => _busy = true);
    try {
      await context.read<ApiClient>().deleteExpense(widget.group.id, _editing!.id);
      if (mounted) Navigator.pop(context, true);
    } on ApiException catch (err) {
      if (mounted) {
        setState(() => _busy = false);
        showError(context, err);
      }
    }
  }

  void _setKind(_Kind kind) {
    if (kind == _kind) return;
    setState(() {
      _kind = kind;
      _note = null;
      for (final c in _values.values) {
        c.clear();
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    final result = _evaluate();
    final members = widget.group.members;
    final suffix = switch (_kind) { _Kind.percent => '%', _Kind.exact => '₹', _ => null };

    return Scaffold(
      appBar: TopBar(
        title: _editing == null ? 'Add expense' : 'Edit expense',
        action: _editing == null
            ? null
            : CircleIconButton(icon: Icons.delete_outline_rounded, tooltip: 'Delete', onPressed: _busy ? null : _delete),
      ),
      body: Column(
        children: [
          Expanded(
            child: ListView(
              padding: const EdgeInsets.fromLTRB(20, 8, 20, 16),
              children: [
                DashedBorder(
                  padding: const EdgeInsets.symmetric(vertical: 18, horizontal: 16),
                  child: Column(
                    children: [
                      const Text('Total bill', style: TextStyle(color: AppColors.muted, fontSize: 13)),
                      const SizedBox(height: 4),
                      Row(
                        mainAxisAlignment: MainAxisAlignment.center,
                        crossAxisAlignment: CrossAxisAlignment.center,
                        children: [
                          const Text('₹', style: TextStyle(fontSize: 34, fontWeight: FontWeight.w800, color: AppColors.muted)),
                          const SizedBox(width: 8),
                          ConstrainedBox(
                            constraints: const BoxConstraints(minWidth: 90, maxWidth: 220),
                            child: IntrinsicWidth(
                              child: TextField(
                                key: const Key('amount'),
                                controller: _amount,
                                keyboardType: const TextInputType.numberWithOptions(decimal: true),
                                textAlign: TextAlign.center,
                                style: const TextStyle(fontSize: 34, fontWeight: FontWeight.w800),
                                decoration: const InputDecoration(
                                  hintText: '0.00',
                                  filled: false,
                                  border: InputBorder.none,
                                  enabledBorder: InputBorder.none,
                                  focusedBorder: InputBorder.none,
                                ),
                              ),
                            ),
                          ),
                        ],
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 16),
                TextField(
                  key: const Key('description'),
                  controller: _description,
                  textCapitalization: TextCapitalization.sentences,
                  decoration: const InputDecoration(labelText: 'What was it for?'),
                ),
                const SizedBox(height: 12),
                DropdownButtonFormField<int>(
                  initialValue: _payerId,
                  decoration: const InputDecoration(labelText: 'Paid by'),
                  borderRadius: BorderRadius.circular(16),
                  items: [
                    for (final m in members)
                      DropdownMenuItem(value: m.id, child: Text(m.id == widget.me.id ? '${m.label} (you)' : m.label)),
                  ],
                  onChanged: (v) => setState(() => _payerId = v ?? _payerId),
                ),
                const SizedBox(height: 24),
                const SectionTitle('Split'),
                PillTabs(
                  labels: [for (final k in _Kind.values) k.label],
                  index: _kind.index,
                  onChanged: (i) => _setKind(_Kind.values[i]),
                ),
                if (_note != null)
                  Padding(padding: const EdgeInsets.only(top: 8), child: Text(_note!, style: const TextStyle(color: AppColors.muted, fontSize: 12.5))),
                const SizedBox(height: 16),
                for (final m in members)
                  Padding(
                    padding: const EdgeInsets.only(bottom: 10),
                    child: _PersonRow(
                      user: m,
                      label: m.id == widget.me.id ? '${m.label} (you)' : m.label,
                      selected: _selected.contains(m.id),
                      onToggle: () => setState(() => _selected.contains(m.id) ? _selected.remove(m.id) : _selected.add(m.id)),
                      field: _kind != _Kind.equal && _selected.contains(m.id)
                          ? SizedBox(
                              width: 96,
                              child: TextField(
                                controller: _values[m.id],
                                keyboardType: const TextInputType.numberWithOptions(decimal: true),
                                textAlign: TextAlign.end,
                                decoration: InputDecoration(
                                  isDense: true,
                                  hintText: _kind == _Kind.shares ? '1' : '0',
                                  suffixText: suffix,
                                  contentPadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
                                ),
                              ),
                            )
                          : null,
                    ),
                  ),
                const SizedBox(height: 4),
                Center(
                  child: Text(
                    result.problem ?? result.hint ?? '',
                    textAlign: TextAlign.center,
                    style: TextStyle(color: result.problem != null ? AppColors.negative : AppColors.muted, fontWeight: FontWeight.w600),
                  ),
                ),
              ],
            ),
          ),
          SafeArea(
            top: false,
            child: Padding(
              padding: const EdgeInsets.fromLTRB(20, 4, 20, 16),
              child: ConfirmButton(
                label: _editing == null ? 'Add expense' : 'Save changes',
                busy: _busy,
                onPressed: result.splits == null ? null : () => _save(result.splits!),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _PersonRow extends StatelessWidget {
  final User user;
  final String label;
  final bool selected;
  final VoidCallback onToggle;
  final Widget? field;

  const _PersonRow({required this.user, required this.label, required this.selected, required this.onToggle, this.field});

  @override
  Widget build(BuildContext context) {
    return Material(
      color: AppColors.surface,
      borderRadius: BorderRadius.circular(22),
      child: InkWell(
        onTap: onToggle,
        borderRadius: BorderRadius.circular(22),
        child: Container(
          padding: const EdgeInsets.fromLTRB(12, 10, 14, 10),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(22),
            border: Border.all(color: selected ? AppColors.accent.withValues(alpha: 0.5) : AppColors.border.withValues(alpha: 0.6)),
          ),
          child: Row(
            children: [
              Opacity(opacity: selected ? 1 : 0.4, child: Avatar(user, size: 40)),
              const SizedBox(width: 12),
              Expanded(
                child: Text(label, maxLines: 1, overflow: TextOverflow.ellipsis, style: TextStyle(fontWeight: FontWeight.w600, color: selected ? AppColors.text : AppColors.muted)),
              ),
              if (field != null) ...[field!, const SizedBox(width: 10)],
              Icon(selected ? Icons.check_circle_rounded : Icons.radio_button_unchecked_rounded, color: selected ? AppColors.accent : AppColors.muted),
            ],
          ),
        ),
      ),
    );
  }
}

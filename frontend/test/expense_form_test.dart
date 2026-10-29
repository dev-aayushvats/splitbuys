import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:splitbuys/models.dart';
import 'package:splitbuys/screens/expense_form_screen.dart';
import 'package:splitbuys/widgets.dart';

void main() {
  final asha = User(id: 1, email: 'a@x.com', name: 'Asha');
  final bala = User(id: 2, email: 'b@x.com', name: 'Bala');
  final group = GroupDetail(id: 1, name: 'Trip', inviteToken: 't', members: [asha, bala]);

  Future<void> open(WidgetTester tester) async {
    tester.view.physicalSize = const Size(480, 1400);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    await tester.pumpWidget(MaterialApp(home: ExpenseFormScreen(group: group, me: asha)));
  }

  ConfirmButton submit(WidgetTester tester) => tester.widget<ConfirmButton>(find.byType(ConfirmButton));

  testWidgets('equal split shows per-person amount and enables submit', (tester) async {
    await open(tester);
    expect(submit(tester).onPressed, isNull);

    await tester.enterText(find.byKey(const Key('description')), 'Dinner');
    await tester.enterText(find.byKey(const Key('amount')), '100');
    await tester.pump();

    expect(find.text('₹50.00 each'), findsOneWidget);
    expect(submit(tester).onPressed, isNotNull);
  });

  testWidgets('exact split must add up to the total', (tester) async {
    await open(tester);
    await tester.enterText(find.byKey(const Key('description')), 'Cab');
    await tester.enterText(find.byKey(const Key('amount')), '100');
    await tester.tap(find.text('Exact'));
    await tester.pump();

    final fields = find.byType(TextField);
    await tester.enterText(fields.at(2), '30');
    await tester.enterText(fields.at(3), '50');
    await tester.pump();
    expect(find.text('₹20.00 left to assign'), findsOneWidget);
    expect(submit(tester).onPressed, isNull);

    await tester.enterText(fields.at(3), '70');
    await tester.pump();
    expect(find.text('Adds up to ₹100.00'), findsOneWidget);
    expect(submit(tester).onPressed, isNotNull);
  });

  testWidgets('percentages must add up to 100', (tester) async {
    await open(tester);
    await tester.enterText(find.byKey(const Key('description')), 'Rent');
    await tester.enterText(find.byKey(const Key('amount')), '100');
    await tester.tap(find.text('Percent'));
    await tester.pump();

    final fields = find.byType(TextField);
    await tester.enterText(fields.at(2), '60');
    await tester.enterText(fields.at(3), '30');
    await tester.pump();
    expect(find.text('10% left to assign'), findsOneWidget);
  });
}

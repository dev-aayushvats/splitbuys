import 'package:flutter_test/flutter_test.dart';
import 'package:splitbuys/money.dart';

void main() {
  test('formats paise with Indian grouping', () {
    expect(formatPaise(0), '₹0.00');
    expect(formatPaise(5), '₹0.05');
    expect(formatPaise(99900), '₹999.00');
    expect(formatPaise(100000), '₹1,000.00');
    expect(formatPaise(12345678), '₹1,23,456.78');
    expect(formatPaise(-250050), '-₹2,500.50');
  });

  test('parses hundredths', () {
    expect(parseHundredths('12'), 1200);
    expect(parseHundredths('12.5'), 1250);
    expect(parseHundredths(' 12.50 '), 1250);
    expect(parseHundredths('0.05'), 5);
    expect(parseHundredths('12.505'), isNull);
    expect(parseHundredths('-3'), isNull);
    expect(parseHundredths('abc'), isNull);
    expect(parseHundredths(''), isNull);
  });

  test('hundredths round-trip to input text', () {
    expect(hundredthsToInput(1250), '12.5');
    expect(hundredthsToInput(1200), '12');
    expect(hundredthsToInput(1205), '12.05');
    expect(hundredthsToInput(5), '0.05');
  });
}

import 'package:flutter/material.dart';

class AppColors {
  static const outer = Color(0xFF05060A); // page behind the phone-width column on wide screens
  static const bg = Color(0xFF0D0F15);
  static const surface = Color(0xFF171A24);
  static const surfaceHigh = Color(0xFF1F2330);
  static const border = Color(0xFF2C3244);
  static const dash = Color(0xFF4A5068);
  static const accent = Color(0xFFFFD84D);
  static const accentDeep = Color(0xFFF2C230);
  static const onAccent = Color(0xFF17140A);
  static const text = Color(0xFFF2F3F7);
  static const muted = Color(0xFF8E94A6);
  static const positive = Color(0xFF5BD68A);
  static const negative = Color(0xFFFF6B6B);
}

ThemeData buildTheme() {
  const scheme = ColorScheme.dark(
    primary: AppColors.accent,
    onPrimary: AppColors.onAccent,
    secondary: AppColors.accent,
    onSecondary: AppColors.onAccent,
    surface: AppColors.surface,
    onSurface: AppColors.text,
    error: AppColors.negative,
    outline: AppColors.border,
  );
  final base = ThemeData(brightness: Brightness.dark, colorScheme: scheme, useMaterial3: true);
  final shape = RoundedRectangleBorder(borderRadius: BorderRadius.circular(16));
  OutlineInputBorder inputBorder(Color color, [double width = 1]) => OutlineInputBorder(
        borderRadius: BorderRadius.circular(16),
        borderSide: BorderSide(color: color, width: width),
      );

  return base.copyWith(
    scaffoldBackgroundColor: AppColors.bg,
    canvasColor: AppColors.surfaceHigh,
    textTheme: base.textTheme.apply(bodyColor: AppColors.text, displayColor: AppColors.text),
    appBarTheme: const AppBarTheme(
      backgroundColor: Colors.transparent,
      elevation: 0,
      scrolledUnderElevation: 0,
      centerTitle: true,
    ),
    inputDecorationTheme: InputDecorationTheme(
      filled: true,
      fillColor: AppColors.surface,
      labelStyle: const TextStyle(color: AppColors.muted),
      hintStyle: const TextStyle(color: AppColors.muted),
      contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 16),
      border: inputBorder(AppColors.border),
      enabledBorder: inputBorder(AppColors.border),
      disabledBorder: inputBorder(AppColors.border),
      focusedBorder: inputBorder(AppColors.accent, 1.5),
      errorBorder: inputBorder(AppColors.negative),
      focusedErrorBorder: inputBorder(AppColors.negative, 1.5),
    ),
    filledButtonTheme: FilledButtonThemeData(
      style: FilledButton.styleFrom(
        backgroundColor: AppColors.accent,
        foregroundColor: AppColors.onAccent,
        disabledBackgroundColor: AppColors.surfaceHigh,
        disabledForegroundColor: AppColors.muted,
        minimumSize: const Size(64, 52),
        shape: const StadiumBorder(),
        textStyle: const TextStyle(fontWeight: FontWeight.w700, fontSize: 15),
      ),
    ),
    textButtonTheme: TextButtonThemeData(
      style: TextButton.styleFrom(foregroundColor: AppColors.accent, textStyle: const TextStyle(fontWeight: FontWeight.w600)),
    ),
    outlinedButtonTheme: OutlinedButtonThemeData(
      style: OutlinedButton.styleFrom(
        foregroundColor: AppColors.text,
        side: const BorderSide(color: AppColors.border),
        minimumSize: const Size(64, 52),
        shape: const StadiumBorder(),
      ),
    ),
    floatingActionButtonTheme: const FloatingActionButtonThemeData(
      backgroundColor: AppColors.accent,
      foregroundColor: AppColors.onAccent,
      elevation: 6,
      shape: CircleBorder(),
      sizeConstraints: BoxConstraints.tightFor(width: 64, height: 64),
    ),
    dialogTheme: DialogThemeData(
      backgroundColor: AppColors.surfaceHigh,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(28)),
      titleTextStyle: const TextStyle(fontSize: 20, fontWeight: FontWeight.w700, color: AppColors.text),
    ),
    snackBarTheme: SnackBarThemeData(
      behavior: SnackBarBehavior.floating,
      backgroundColor: AppColors.surfaceHigh,
      contentTextStyle: const TextStyle(color: AppColors.text),
      shape: shape,
    ),
    popupMenuTheme: PopupMenuThemeData(color: AppColors.surfaceHigh, shape: shape),
    dividerTheme: const DividerThemeData(color: AppColors.border, thickness: 1, space: 1),
    progressIndicatorTheme: const ProgressIndicatorThemeData(color: AppColors.accent),
    textSelectionTheme: const TextSelectionThemeData(cursorColor: AppColors.accent),
  );
}

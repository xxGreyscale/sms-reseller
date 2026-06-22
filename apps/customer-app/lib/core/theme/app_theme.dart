import 'package:flutter/material.dart';

class AppTheme {
  static ThemeData get light => ThemeData(
        useMaterial3: true,
        colorScheme: ColorScheme.fromSeed(
          seedColor: const Color(0xFF1565C0),
          brightness: Brightness.light,
        ),
        textTheme: const TextTheme(
          bodyMedium: TextStyle(fontSize: 16, fontWeight: FontWeight.w400, height: 1.5),
          labelMedium: TextStyle(fontSize: 14, fontWeight: FontWeight.w500, height: 1.4),
          titleMedium: TextStyle(fontSize: 20, fontWeight: FontWeight.w600, height: 1.3),
          headlineLarge: TextStyle(fontSize: 28, fontWeight: FontWeight.w700, height: 1.2),
        ),
      );
}

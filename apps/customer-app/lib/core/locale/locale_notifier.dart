import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';

const _kSelectedLocaleKey = 'selected_locale';

final sharedPreferencesProvider = Provider<SharedPreferences>((ref) {
  throw UnimplementedError('Override sharedPreferencesProvider before use');
});

final localeNotifierProvider =
    NotifierProvider<LocaleNotifier, Locale>(LocaleNotifier.new);

class LocaleNotifier extends Notifier<Locale> {
  static const _supported = [Locale('en'), Locale('sw')];

  @override
  Locale build() {
    final prefs = ref.read(sharedPreferencesProvider);
    final saved = prefs.getString(_kSelectedLocaleKey);
    if (saved != null) {
      return _supported.firstWhere(
        (l) => l.languageCode == saved,
        orElse: () => const Locale('en'),
      );
    }
    final deviceLang =
        WidgetsBinding.instance.platformDispatcher.locale.languageCode;
    return _supported.firstWhere(
      (l) => l.languageCode == deviceLang,
      orElse: () => const Locale('en'),
    );
  }

  Future<void> setLocale(Locale locale) async {
    state = locale;
    final prefs = ref.read(sharedPreferencesProvider);
    await prefs.setString(_kSelectedLocaleKey, locale.languageCode);
  }
}

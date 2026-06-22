import 'package:hive_ce_flutter/hive_flutter.dart';

/// Initializes Hive and opens all read-cache boxes.
/// Call this in main() before runApp.
Future<void> initHive() async {
  await Hive.initFlutter();
  await Hive.openBox<int>('balance');
  await Hive.openBox<Map>('contacts');
  await Hive.openBox<Map>('campaigns');
  await Hive.openBox<Map>('notifications');
}

/// Convenience accessors
Box<int> get balanceBox => Hive.box<int>('balance');
Box<Map> get contactsBox => Hive.box<Map>('contacts');
Box<Map> get campaignsBox => Hive.box<Map>('campaigns');
Box<Map> get notificationsBox => Hive.box<Map>('notifications');

import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:intl/intl.dart' as intl;

import 'app_localizations_en.dart';
import 'app_localizations_sw.dart';

// ignore_for_file: type=lint

/// Callers can lookup localized strings with an instance of AppLocalizations
/// returned by `AppLocalizations.of(context)`.
///
/// Applications need to include `AppLocalizations.delegate()` in their app's
/// `localizationDelegates` list, and the locales they support in the app's
/// `supportedLocales` list. For example:
///
/// ```dart
/// import 'l10n/app_localizations.dart';
///
/// return MaterialApp(
///   localizationsDelegates: AppLocalizations.localizationsDelegates,
///   supportedLocales: AppLocalizations.supportedLocales,
///   home: MyApplicationHome(),
/// );
/// ```
///
/// ## Update pubspec.yaml
///
/// Please make sure to update your pubspec.yaml to include the following
/// packages:
///
/// ```yaml
/// dependencies:
///   # Internationalization support.
///   flutter_localizations:
///     sdk: flutter
///   intl: any # Use the pinned version from flutter_localizations
///
///   # Rest of dependencies
/// ```
///
/// ## iOS Applications
///
/// iOS applications define key application metadata, including supported
/// locales, in an Info.plist file that is built into the application bundle.
/// To configure the locales supported by your app, you’ll need to edit this
/// file.
///
/// First, open your project’s ios/Runner.xcworkspace Xcode workspace file.
/// Then, in the Project Navigator, open the Info.plist file under the Runner
/// project’s Runner folder.
///
/// Next, select the Information Property List item, select Add Item from the
/// Editor menu, then select Localizations from the pop-up menu.
///
/// Select and expand the newly-created Localizations item then, for each
/// locale your application supports, add a new item and select the locale
/// you wish to add from the pop-up menu in the Value field. This list should
/// be consistent with the languages listed in the AppLocalizations.supportedLocales
/// property.
abstract class AppLocalizations {
  AppLocalizations(String locale)
      : localeName = intl.Intl.canonicalizedLocale(locale.toString());

  final String localeName;

  static AppLocalizations of(BuildContext context) {
    return Localizations.of<AppLocalizations>(context, AppLocalizations)!;
  }

  static const LocalizationsDelegate<AppLocalizations> delegate =
      _AppLocalizationsDelegate();

  /// A list of this localizations delegate along with the default localizations
  /// delegates.
  ///
  /// Returns a list of localizations delegates containing this delegate along with
  /// GlobalMaterialLocalizations.delegate, GlobalCupertinoLocalizations.delegate,
  /// and GlobalWidgetsLocalizations.delegate.
  ///
  /// Additional delegates can be added by appending to this list in
  /// MaterialApp. This list does not have to be used at all if a custom list
  /// of delegates is preferred or required.
  static const List<LocalizationsDelegate<dynamic>> localizationsDelegates =
      <LocalizationsDelegate<dynamic>>[
    delegate,
    GlobalMaterialLocalizations.delegate,
    GlobalCupertinoLocalizations.delegate,
    GlobalWidgetsLocalizations.delegate,
  ];

  /// A list of this localizations delegate's supported locales.
  static const List<Locale> supportedLocales = <Locale>[
    Locale('en'),
    Locale('sw')
  ];

  /// Onboarding last slide CTA
  ///
  /// In en, this message translates to:
  /// **'Get Started'**
  String get onboardingGetStarted;

  /// Onboarding skip button
  ///
  /// In en, this message translates to:
  /// **'Skip'**
  String get onboardingSkip;

  /// Onboarding next button
  ///
  /// In en, this message translates to:
  /// **'Next'**
  String get onboardingNext;

  /// Onboarding slide 1 heading
  ///
  /// In en, this message translates to:
  /// **'Send SMS to everyone'**
  String get onboardingSlide1Title;

  /// Onboarding slide 1 body
  ///
  /// In en, this message translates to:
  /// **'Reach all your members and customers with bulk SMS in minutes.'**
  String get onboardingSlide1Body;

  /// Onboarding slide 2 heading
  ///
  /// In en, this message translates to:
  /// **'Buy credits in minutes'**
  String get onboardingSlide2Title;

  /// Onboarding slide 2 body
  ///
  /// In en, this message translates to:
  /// **'Purchase SMS bundles instantly using mobile money — M-Pesa, Tigo, Airtel and more.'**
  String get onboardingSlide2Body;

  /// Onboarding slide 3 heading
  ///
  /// In en, this message translates to:
  /// **'Verified and trusted'**
  String get onboardingSlide3Title;

  /// Onboarding slide 3 body
  ///
  /// In en, this message translates to:
  /// **'Register with your National ID for a verified, trusted sending identity.'**
  String get onboardingSlide3Body;

  /// Registration form submit CTA
  ///
  /// In en, this message translates to:
  /// **'Create Account'**
  String get registerSubmitButton;

  /// Registration screen title
  ///
  /// In en, this message translates to:
  /// **'Create Account'**
  String get registerTitle;

  /// Registration link to login
  ///
  /// In en, this message translates to:
  /// **'Already have an account? Log in'**
  String get registerAlreadyHaveAccount;

  /// Login form submit CTA
  ///
  /// In en, this message translates to:
  /// **'Log In'**
  String get loginSubmitButton;

  /// Login screen title
  ///
  /// In en, this message translates to:
  /// **'Log In'**
  String get loginTitle;

  /// Login forgot password link
  ///
  /// In en, this message translates to:
  /// **'Forgot password?'**
  String get loginForgotPassword;

  /// Login link to registration
  ///
  /// In en, this message translates to:
  /// **'Don\'t have an account? Register'**
  String get loginNoAccount;

  /// Dashboard FAB label
  ///
  /// In en, this message translates to:
  /// **'Send SMS'**
  String get dashboardQuickSendFab;

  /// Dashboard section header for recent campaigns
  ///
  /// In en, this message translates to:
  /// **'Recent Campaigns'**
  String get dashboardRecentCampaigns;

  /// Dashboard link to campaign history
  ///
  /// In en, this message translates to:
  /// **'View all campaigns'**
  String get dashboardViewAll;

  /// Dashboard balance card label
  ///
  /// In en, this message translates to:
  /// **'SMS Credits'**
  String get dashboardBalanceLabel;

  /// Bundle catalog buy CTA
  ///
  /// In en, this message translates to:
  /// **'Buy Bundle'**
  String get bundleBuyButton;

  /// Bundle catalog screen title
  ///
  /// In en, this message translates to:
  /// **'Buy SMS Credits'**
  String get bundleCatalogTitle;

  /// STK purchase confirm CTA
  ///
  /// In en, this message translates to:
  /// **'Confirm Purchase'**
  String get purchaseConfirmButton;

  /// Add contact save CTA
  ///
  /// In en, this message translates to:
  /// **'Save Contact'**
  String get addContactSaveButton;

  /// Add contact screen title
  ///
  /// In en, this message translates to:
  /// **'Add Contact'**
  String get addContactTitle;

  /// Campaign composer send CTA
  ///
  /// In en, this message translates to:
  /// **'Send Now'**
  String get composerSendButton;

  /// Campaign composer screen title
  ///
  /// In en, this message translates to:
  /// **'New Campaign'**
  String get composerTitle;

  /// Contact list empty state heading
  ///
  /// In en, this message translates to:
  /// **'No contacts yet'**
  String get contactsEmptyHeading;

  /// Contact list empty state body
  ///
  /// In en, this message translates to:
  /// **'Add your first contact to start sending SMS.'**
  String get contactsEmptyBody;

  /// Campaign history empty state heading
  ///
  /// In en, this message translates to:
  /// **'No campaigns yet'**
  String get campaignsEmptyHeading;

  /// Campaign history empty state body
  ///
  /// In en, this message translates to:
  /// **'Send your first SMS campaign from the dashboard.'**
  String get campaignsEmptyBody;

  /// Notification feed empty state heading
  ///
  /// In en, this message translates to:
  /// **'All caught up'**
  String get notificationsEmptyHeading;

  /// Notification feed empty state body
  ///
  /// In en, this message translates to:
  /// **'No new notifications.'**
  String get notificationsEmptyBody;

  /// Bundle catalog empty state heading
  ///
  /// In en, this message translates to:
  /// **'No bundles available'**
  String get bundlesEmptyHeading;

  /// Bundle catalog empty state body
  ///
  /// In en, this message translates to:
  /// **'Please check back later or contact support.'**
  String get bundlesEmptyBody;

  /// Network error on write operation
  ///
  /// In en, this message translates to:
  /// **'No connection. Please check your internet and try again.'**
  String get errorNetworkWrite;

  /// Network error on initial load with no cache
  ///
  /// In en, this message translates to:
  /// **'Could not load data. Pull down to refresh.'**
  String get errorNetworkLoad;

  /// Stale cache indicator
  ///
  /// In en, this message translates to:
  /// **'Showing saved data · Last updated {time}'**
  String staleDataIndicator(String time);

  /// Retry button label
  ///
  /// In en, this message translates to:
  /// **'Try Again'**
  String get errorRetryButton;

  /// Login failure inline error
  ///
  /// In en, this message translates to:
  /// **'Incorrect email or password.'**
  String get errorLoginFailed;

  /// NIN already registered error
  ///
  /// In en, this message translates to:
  /// **'This National ID is already registered. Contact support if this is an error.'**
  String get errorNinTaken;

  /// Payment expired state title
  ///
  /// In en, this message translates to:
  /// **'Payment timed out'**
  String get paymentExpiredTitle;

  /// Insufficient credits error
  ///
  /// In en, this message translates to:
  /// **'Not enough SMS credits. Top up your balance to send this campaign.'**
  String get errorInsufficientCredits;

  /// NIDA unavailable message
  ///
  /// In en, this message translates to:
  /// **'Identity verification is taking longer than expected. We\'ll keep trying automatically.'**
  String get nidaUnavailableMessage;

  /// UCS-2 encoding warning in composer
  ///
  /// In en, this message translates to:
  /// **'Non-standard characters detected. Each SMS part fits 70 characters instead of 160.'**
  String get composerUcs2Warning;

  /// NIDA pending screen title
  ///
  /// In en, this message translates to:
  /// **'Verifying your identity'**
  String get pendingScreenTitle;

  /// NIDA pending screen body
  ///
  /// In en, this message translates to:
  /// **'We are confirming your National ID with NIDA. This usually takes a few minutes. You cannot send SMS until your identity is verified.'**
  String get pendingScreenBody;

  /// NIDA pending status indicator label
  ///
  /// In en, this message translates to:
  /// **'Verification in progress…'**
  String get pendingStatusLabel;

  /// Post-verification success snackbar
  ///
  /// In en, this message translates to:
  /// **'Identity verified! You have received 50 free SMS credits.'**
  String get verifiedSuccessMessage;

  /// NIDA pending logout link
  ///
  /// In en, this message translates to:
  /// **'Log out and verify later'**
  String get pendingLogoutLink;

  /// STK purchase instruction
  ///
  /// In en, this message translates to:
  /// **'Check your phone for a payment prompt from your mobile money provider. Enter your PIN to confirm.'**
  String get stkInstruction;

  /// STK countdown label
  ///
  /// In en, this message translates to:
  /// **'Time remaining'**
  String get stkCountdownLabel;

  /// STK waiting body
  ///
  /// In en, this message translates to:
  /// **'Waiting for payment confirmation…'**
  String get stkWaitingBody;

  /// STK expired state title
  ///
  /// In en, this message translates to:
  /// **'Payment timed out'**
  String get stkExpiredTitle;

  /// STK expired state body
  ///
  /// In en, this message translates to:
  /// **'The payment request expired before you confirmed. No money was deducted.'**
  String get stkExpiredBody;

  /// STK expired try again CTA
  ///
  /// In en, this message translates to:
  /// **'Try Again'**
  String get stkTryAgainButton;

  /// STK success state title
  ///
  /// In en, this message translates to:
  /// **'Payment confirmed!'**
  String get stkSuccessTitle;

  /// STK success state body
  ///
  /// In en, this message translates to:
  /// **'{smsCount} SMS credits have been added to your balance.'**
  String stkSuccessBody(int smsCount);

  /// Delete contact confirmation dialog title
  ///
  /// In en, this message translates to:
  /// **'Delete {name}? This cannot be undone.'**
  String deleteContactTitle(String name);

  /// Destructive delete button
  ///
  /// In en, this message translates to:
  /// **'Delete'**
  String get deleteButton;

  /// Cancel button
  ///
  /// In en, this message translates to:
  /// **'Cancel'**
  String get cancelButton;

  /// Logout confirmation dialog title
  ///
  /// In en, this message translates to:
  /// **'Log out?'**
  String get logoutTitle;

  /// Logout confirmation button
  ///
  /// In en, this message translates to:
  /// **'Log Out'**
  String get logoutConfirmButton;

  /// Notification feed screen title
  ///
  /// In en, this message translates to:
  /// **'Notifications'**
  String get notificationsTitle;

  /// Campaign history screen title
  ///
  /// In en, this message translates to:
  /// **'Campaigns'**
  String get campaignsTitle;

  /// Contact list screen title
  ///
  /// In en, this message translates to:
  /// **'Contacts'**
  String get contactsTitle;

  /// Settings screen title
  ///
  /// In en, this message translates to:
  /// **'Settings'**
  String get settingsTitle;

  /// Language toggle label
  ///
  /// In en, this message translates to:
  /// **'Language'**
  String get languageLabel;

  /// English language option
  ///
  /// In en, this message translates to:
  /// **'EN'**
  String get languageEnglish;

  /// Swahili language option
  ///
  /// In en, this message translates to:
  /// **'SW'**
  String get languageSwahili;
}

class _AppLocalizationsDelegate
    extends LocalizationsDelegate<AppLocalizations> {
  const _AppLocalizationsDelegate();

  @override
  Future<AppLocalizations> load(Locale locale) {
    return SynchronousFuture<AppLocalizations>(lookupAppLocalizations(locale));
  }

  @override
  bool isSupported(Locale locale) =>
      <String>['en', 'sw'].contains(locale.languageCode);

  @override
  bool shouldReload(_AppLocalizationsDelegate old) => false;
}

AppLocalizations lookupAppLocalizations(Locale locale) {
  // Lookup logic when only language code is specified.
  switch (locale.languageCode) {
    case 'en':
      return AppLocalizationsEn();
    case 'sw':
      return AppLocalizationsSw();
  }

  throw FlutterError(
      'AppLocalizations.delegate failed to load unsupported locale "$locale". This is likely '
      'an issue with the localizations generation tool. Please file an issue '
      'on GitHub with a reproducible sample app and the gen-l10n configuration '
      'that was used.');
}

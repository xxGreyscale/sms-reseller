// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for English (`en`).
class AppLocalizationsEn extends AppLocalizations {
  AppLocalizationsEn([String locale = 'en']) : super(locale);

  @override
  String get onboardingGetStarted => 'Get Started';

  @override
  String get onboardingSkip => 'Skip';

  @override
  String get onboardingNext => 'Next';

  @override
  String get onboardingSlide1Title => 'Send SMS to everyone';

  @override
  String get onboardingSlide1Body =>
      'Reach all your members and customers with bulk SMS in minutes.';

  @override
  String get onboardingSlide2Title => 'Buy credits in minutes';

  @override
  String get onboardingSlide2Body =>
      'Purchase SMS bundles instantly using mobile money — M-Pesa, Tigo, Airtel and more.';

  @override
  String get onboardingSlide3Title => 'Verified and trusted';

  @override
  String get onboardingSlide3Body =>
      'Register with your National ID for a verified, trusted sending identity.';

  @override
  String get registerSubmitButton => 'Create Account';

  @override
  String get registerTitle => 'Create Account';

  @override
  String get registerAlreadyHaveAccount => 'Already have an account? Log in';

  @override
  String get loginSubmitButton => 'Log In';

  @override
  String get loginTitle => 'Log In';

  @override
  String get loginForgotPassword => 'Forgot password?';

  @override
  String get loginNoAccount => 'Don\'t have an account? Register';

  @override
  String get dashboardQuickSendFab => 'Send SMS';

  @override
  String get dashboardRecentCampaigns => 'Recent Campaigns';

  @override
  String get dashboardViewAll => 'View all campaigns';

  @override
  String get dashboardBalanceLabel => 'SMS Credits';

  @override
  String get bundleBuyButton => 'Buy Bundle';

  @override
  String get bundleCatalogTitle => 'Buy SMS Credits';

  @override
  String get purchaseConfirmButton => 'Confirm Purchase';

  @override
  String get addContactSaveButton => 'Save Contact';

  @override
  String get addContactTitle => 'Add Contact';

  @override
  String get composerSendButton => 'Send Now';

  @override
  String get composerTitle => 'New Campaign';

  @override
  String get contactsEmptyHeading => 'No contacts yet';

  @override
  String get contactsEmptyBody =>
      'Add your first contact to start sending SMS.';

  @override
  String get campaignsEmptyHeading => 'No campaigns yet';

  @override
  String get campaignsEmptyBody =>
      'Send your first SMS campaign from the dashboard.';

  @override
  String get notificationsEmptyHeading => 'All caught up';

  @override
  String get notificationsEmptyBody => 'No new notifications.';

  @override
  String get bundlesEmptyHeading => 'No bundles available';

  @override
  String get bundlesEmptyBody => 'Please check back later or contact support.';

  @override
  String get errorNetworkWrite =>
      'No connection. Please check your internet and try again.';

  @override
  String get errorNetworkLoad => 'Could not load data. Pull down to refresh.';

  @override
  String staleDataIndicator(String time) {
    return 'Showing saved data · Last updated $time';
  }

  @override
  String get errorRetryButton => 'Try Again';

  @override
  String get errorLoginFailed => 'Incorrect email or password.';

  @override
  String get errorNinTaken =>
      'This National ID is already registered. Contact support if this is an error.';

  @override
  String get paymentExpiredTitle => 'Payment timed out';

  @override
  String get errorInsufficientCredits =>
      'Not enough SMS credits. Top up your balance to send this campaign.';

  @override
  String get nidaUnavailableMessage =>
      'Identity verification is taking longer than expected. We\'ll keep trying automatically.';

  @override
  String get composerUcs2Warning =>
      'Non-standard characters detected. Each SMS part fits 70 characters instead of 160.';

  @override
  String get pendingScreenTitle => 'Verifying your identity';

  @override
  String get pendingScreenBody =>
      'We are confirming your National ID with NIDA. This usually takes a few minutes. You cannot send SMS until your identity is verified.';

  @override
  String get pendingStatusLabel => 'Verification in progress…';

  @override
  String get verifiedSuccessMessage =>
      'Identity verified! You have received 50 free SMS credits.';

  @override
  String get pendingLogoutLink => 'Log out and verify later';

  @override
  String get stkInstruction =>
      'Check your phone for a payment prompt from your mobile money provider. Enter your PIN to confirm.';

  @override
  String get stkCountdownLabel => 'Time remaining';

  @override
  String get stkWaitingBody => 'Waiting for payment confirmation…';

  @override
  String get stkExpiredTitle => 'Payment timed out';

  @override
  String get stkExpiredBody =>
      'The payment request expired before you confirmed. No money was deducted.';

  @override
  String get stkTryAgainButton => 'Try Again';

  @override
  String get stkSuccessTitle => 'Payment confirmed!';

  @override
  String stkSuccessBody(int smsCount) {
    return '$smsCount SMS credits have been added to your balance.';
  }

  @override
  String deleteContactTitle(String name) {
    return 'Delete $name? This cannot be undone.';
  }

  @override
  String get deleteButton => 'Delete';

  @override
  String get cancelButton => 'Cancel';

  @override
  String get logoutTitle => 'Log out?';

  @override
  String get logoutConfirmButton => 'Log Out';

  @override
  String get notificationsTitle => 'Notifications';

  @override
  String get campaignsTitle => 'Campaigns';

  @override
  String get contactsTitle => 'Contacts';

  @override
  String get settingsTitle => 'Settings';

  @override
  String get languageLabel => 'Language';

  @override
  String get languageEnglish => 'EN';

  @override
  String get languageSwahili => 'SW';
}

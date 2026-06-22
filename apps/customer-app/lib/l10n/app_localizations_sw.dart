// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for Swahili (`sw`).
class AppLocalizationsSw extends AppLocalizations {
  AppLocalizationsSw([String locale = 'sw']) : super(locale);

  @override
  String get onboardingGetStarted => 'Anza Sasa';

  @override
  String get onboardingSkip => 'Ruka';

  @override
  String get onboardingNext => 'Ifuatayo';

  @override
  String get onboardingSlide1Title => 'Tuma SMS kwa kila mtu';

  @override
  String get onboardingSlide1Body =>
      'Wafikia wanachama na wateja wako wote kwa SMS nyingi kwa dakika chache.';

  @override
  String get onboardingSlide2Title => 'Nunua mikopo kwa dakika';

  @override
  String get onboardingSlide2Body =>
      'Nunua vifurushi vya SMS mara moja kwa pesa za simu — M-Pesa, Tigo, Airtel na zaidi.';

  @override
  String get onboardingSlide3Title => 'Imehakikishwa na kuaminiwa';

  @override
  String get onboardingSlide3Body =>
      'Jiandikishe na Kitambulisho chako cha Taifa kwa utambulisho wa kutuma unaothibitishwa.';

  @override
  String get registerSubmitButton => 'Fungua Akaunti';

  @override
  String get registerTitle => 'Fungua Akaunti';

  @override
  String get registerAlreadyHaveAccount => 'Una akaunti tayari? Ingia';

  @override
  String get loginSubmitButton => 'Ingia';

  @override
  String get loginTitle => 'Ingia';

  @override
  String get loginForgotPassword => 'Umesahau nywila?';

  @override
  String get loginNoAccount => 'Huna akaunti? Jiandikishe';

  @override
  String get dashboardQuickSendFab => 'Tuma SMS';

  @override
  String get dashboardRecentCampaigns => 'Kampeni za Hivi Karibuni';

  @override
  String get dashboardViewAll => 'Tazama kampeni zote';

  @override
  String get dashboardBalanceLabel => 'Mikopo ya SMS';

  @override
  String get bundleBuyButton => 'Nunua Kifurushi';

  @override
  String get bundleCatalogTitle => 'Nunua Mikopo ya SMS';

  @override
  String get purchaseConfirmButton => 'Thibitisha Ununuzi';

  @override
  String get addContactSaveButton => 'Hifadhi Mawasiliano';

  @override
  String get addContactTitle => 'Ongeza Mawasiliano';

  @override
  String get composerSendButton => 'Tuma Sasa';

  @override
  String get composerTitle => 'Kampeni Mpya';

  @override
  String get contactsEmptyHeading => 'Hakuna mawasiliano bado';

  @override
  String get contactsEmptyBody =>
      'Ongeza mawasiliano yako ya kwanza kuanza kutuma SMS.';

  @override
  String get campaignsEmptyHeading => 'Hakuna kampeni bado';

  @override
  String get campaignsEmptyBody =>
      'Tuma kampeni yako ya kwanza ya SMS kutoka dashibodi.';

  @override
  String get notificationsEmptyHeading => 'Umesoma kila kitu';

  @override
  String get notificationsEmptyBody => 'Hakuna arifa mpya.';

  @override
  String get bundlesEmptyHeading => 'Hakuna vifurushi vinavyopatikana';

  @override
  String get bundlesEmptyBody =>
      'Tafadhali angalia tena baadaye au wasiliana na msaada.';

  @override
  String get errorNetworkWrite =>
      'Hakuna muunganiko. Tafadhali angalia intaneti yako na ujaribu tena.';

  @override
  String get errorNetworkLoad =>
      'Imeshindwa kupakia data. Vuta chini kuhuisha.';

  @override
  String staleDataIndicator(String time) {
    return 'Inaonyesha data iliyohifadhiwa · Imesasishwa $time';
  }

  @override
  String get errorRetryButton => 'Jaribu Tena';

  @override
  String get errorLoginFailed => 'Barua pepe au nywila si sahihi.';

  @override
  String get errorNinTaken =>
      'Kitambulisho hiki cha Taifa kimeandikishwa tayari. Wasiliana na msaada ikiwa hii ni kosa.';

  @override
  String get paymentExpiredTitle => 'Malipo yalipita muda';

  @override
  String get errorInsufficientCredits =>
      'Mikopo ya SMS haitoshi. Ongeza salio lako kutuma kampeni hii.';

  @override
  String get nidaUnavailableMessage =>
      'Uthibitishaji wa utambulisho unachukua muda zaidi ya kawaida. Tutaendelea kujaribu kiotomatiki.';

  @override
  String get composerUcs2Warning =>
      'Herufi zisizo za kawaida zimegunduliwa. Kila sehemu ya SMS inabeba herufi 70 badala ya 160.';

  @override
  String get pendingScreenTitle => 'Tunathibitisha utambulisho wako';

  @override
  String get pendingScreenBody =>
      'Tunakagua Kitambulisho chako cha Taifa na NIDA. Hii kawaida inachukua dakika chache. Huwezi kutuma SMS mpaka utambulisho wako uthibitishwe.';

  @override
  String get pendingStatusLabel => 'Uthibitishaji unafanyika…';

  @override
  String get verifiedSuccessMessage =>
      'Utambulisho umethibitishwa! Umepata mikopo 50 ya SMS bure.';

  @override
  String get pendingLogoutLink => 'Toka na uthibitishe baadaye';

  @override
  String get stkInstruction =>
      'Angalia simu yako kwa ombi la malipo kutoka kwa mtoa huduma wako wa pesa za simu. Ingiza PIN yako kuthibitisha.';

  @override
  String get stkCountdownLabel => 'Muda uliobaki';

  @override
  String get stkWaitingBody => 'Inangoja uthibitisho wa malipo…';

  @override
  String get stkExpiredTitle => 'Malipo yalipita muda';

  @override
  String get stkExpiredBody =>
      'Ombi la malipo lilipita muda kabla ya kuthibitisha. Hakuna pesa iliyotolewa.';

  @override
  String get stkTryAgainButton => 'Jaribu Tena';

  @override
  String get stkSuccessTitle => 'Malipo yamethibitishwa!';

  @override
  String stkSuccessBody(int smsCount) {
    return 'Mikopo $smsCount ya SMS imeongezwa kwenye salio lako.';
  }

  @override
  String deleteContactTitle(String name) {
    return 'Futa $name? Hii haiwezi kutenduliwa.';
  }

  @override
  String get deleteButton => 'Futa';

  @override
  String get cancelButton => 'Ghairi';

  @override
  String get logoutTitle => 'Unataka kutoka?';

  @override
  String get logoutConfirmButton => 'Toka';

  @override
  String get notificationsTitle => 'Arifa';

  @override
  String get campaignsTitle => 'Kampeni';

  @override
  String get contactsTitle => 'Mawasiliano';

  @override
  String get settingsTitle => 'Mipangilio';

  @override
  String get languageLabel => 'Lugha';

  @override
  String get languageEnglish => 'EN';

  @override
  String get languageSwahili => 'SW';
}

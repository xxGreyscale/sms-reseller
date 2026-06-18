# Feature Research

**Domain:** Bulk SMS reseller platform (Tanzania, mobile-first, non-technical admins)
**Researched:** 2026-06-18
**Confidence:** MEDIUM — web research tools unavailable; findings drawn from training-data knowledge of the bulk SMS SaaS domain (Africa/East Africa), cross-referenced against PROJECT.md locked decisions and the stated competitor (NextSMS). Flag any claim here for validation against live competitor UIs before implementation.

---

## Feature Landscape

### Table Stakes (Users Expect These)

Features users assume exist on any bulk SMS platform. Missing these = product feels broken or untrustworthy. Users will switch to NextSMS if any of these are absent at launch.

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| User registration + login | Every SaaS has an account | LOW | NIDA verification replaces email-only signup — more complex than typical but already locked |
| SMS credit balance display | Users need to know what they have left | LOW | Persistent header/widget on all screens; wallet service already scoped |
| Send bulk SMS to a group | Core job-to-be-done; reason users come to the platform | MEDIUM | Campaigns model already scoped in messaging service |
| Contact list management (add/edit/delete) | Every SMS platform has an address book | MEDIUM | contacts service already scoped |
| Contact groups | Users organize by church congregation, school class, NGO branch, etc. | MEDIUM | groups already in scope; critical for Tanzania use case — orgs send to subsets, not everyone |
| CSV contact import | Users have rosters in Excel/Google Sheets; manual entry doesn't scale | MEDIUM | already locked; must handle Swahili names and UTF-8 correctly |
| Delivery report per campaign | "Did my message go through?" is the first question every user asks | MEDIUM | delivery status tracking already locked; show sent/delivered/failed counts |
| Credit purchase via mobile money | Tanzania is mobile-money first; card payments are niche | HIGH | Azampay already locked; covers M-Pesa/Tigo/Airtel/Halo/AzamPesa — this IS the payment infrastructure |
| Payment confirmation notification | Users need to know their top-up succeeded | LOW | notification service already scoped; triggered by payment event |
| Low-credit alert | Users must not run out mid-campaign | LOW | already locked; send at configurable threshold (suggest 10% of last purchased bundle or 50 SMS floor) |
| Message character counter / SMS count preview | Users need to know how many SMS units a message costs (160 chars = 1 SMS, 306 chars = 2 SMS) | LOW | frontend-only; critical for cost transparency — missing this causes confusion and distrust |
| Campaign history / message log | "What did I send last month?" — orgs need records | MEDIUM | campaigns list with sent-at, recipient count, message preview; already implicit in messaging service |
| Sender ID display / default numeric shortcode | Every message needs a visible sender; users need to know what recipients will see | LOW | numeric shortcode is the default before custom ID approved; already locked |
| Account profile (name, org name, phone) | Basic identity/profile page | LOW | low complexity but users expect it; feeds sender ID applications and notifications |
| Logout / session management | Security baseline | LOW | JWT + session invalidation already in identity service |
| Password reset via SMS/email | Users forget passwords; no recovery = locked out org | LOW | OTP-based reset via Redis already implied by architecture |

### Differentiators (Competitive Advantage)

Features that set this platform apart from NextSMS and generic SMS resellers in Tanzania. These are why users choose and stay.

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| NIDA identity verification at registration | Only KYC-verified bulk SMS platform in Tanzania; unlocks trust for NGOs receiving donor compliance requirements and government-adjacent orgs | HIGH | Core moat. NIDA API access is a pre-implementation blocker — must be resolved before Phase 1. Failure here unravels the acquisition hook. |
| 50 free SMS on NIDA verification | Removes cost barrier to first send; acquisition hook that NextSMS lacks | LOW | Credit grant is trivial technically; the value is the zero-friction first experience. Bonus credit expiry at 30 days creates urgency to send. |
| Mobile-first UX (not a desktop port) | Non-technical admin users send from their phone in the field — at a church event, school meeting, NGO field office | MEDIUM | Competitors are desktop-centric. This is a UX investment, not a feature toggle. Every screen must be thumb-reachable and readable on a 5" screen in poor lighting. |
| Rich analytics dashboard | NextSMS has minimal reporting. Organizations want: delivery rates over time, top-performing campaigns, credit burn rate, which groups were messaged, cost per message. This is a genuine gap in the market. | HIGH | This is called out as the explicit differentiator in PROJECT.md. Do not underinvest. Needs a dedicated analytics screen, not just a table. |
| Campaign scheduling | Send to 500 members at 8 AM on Sunday without being awake | MEDIUM | Competitors offer this but poorly. Reliable scheduled sends build trust. Show scheduled jobs in a queue with cancel option. |
| Duplicate contact removal (automatic) | NGO imports same contacts from multiple Excel exports; deduplication prevents double billing and embarrassment | MEDIUM | Already locked. Surface this to users ("12 duplicates removed") — make the trust visible, don't do it silently. |
| Credit expiry transparency | 12-month credit / 30-day bonus credit with visible countdown prevents nasty surprises | LOW | Show expiry dates on the wallet screen. Proactive alert 7 days before expiry. This prevents churn driven by "my credits disappeared." |
| Custom sender ID with approval workflow | "SOS CHURCH" instead of a number builds brand recognition; critical for churches, schools, NGOs | MEDIUM | Regulatory: TCRA requires sender ID registration. Approval workflow (submit → admin reviews → approved/rejected) is the right model. Show status clearly. |
| Prepaid bundles with volume tiers | Matches Tanzania's event-driven, unpredictable send patterns. No subscription anxiety. | LOW | The pricing ladder (Taster → Scale) is already designed well. Surface the per-SMS rate at each tier so users can see the value of buying up. |
| Swahili-aware UX | Non-technical admins in Tanzania read Swahili first; English-only UI creates friction | MEDIUM | At minimum: Swahili UI labels on the most-used screens (compose, contacts, top-up). Full i18n is complex — a pragmatic subset is enough for v1. This is a meaningful differentiator vs API-first competitors who never localized. |
| Azampay mobile money payment (multi-operator) | Users pay with whatever network they are on — M-Pesa, Tigo, Airtel, Halo, AzamPesa — no need for a debit card | HIGH | This IS the payment method for this market. The differentiator is that it works seamlessly across all operators. Polling recovery and reconciliation (already scoped) are critical to not losing payments. |
| Transparent credit deduction + receipt | Show exactly how many credits were used per campaign after sending | LOW | Post-send confirmation screen with credits used, remaining balance, delivery summary. Builds trust. |

### Anti-Features (Commonly Requested, Often Problematic)

Features that seem like good ideas but should be explicitly excluded from MVP.

| Feature | Why Requested | Why Problematic | Alternative |
|---------|---------------|-----------------|-------------|
| Public REST API | Developers want to integrate with their own systems | Target customer is non-technical; API support requires versioning, documentation, API key management, rate limiting, developer portal — doubles the surface area without serving the core user. NextSMS owns this segment anyway. | Expose internal webhook for delivery events as a v1.5 feature once customer segments are validated |
| SMS reply / two-way messaging | Users want to receive responses from their contacts | Requires inbound number allocation, reply routing, conversation threading UI — a different product. TCRA has separate licensing for two-way services. | Build dedicated "replies" screen in v2 once outbound volume is proven |
| Credit sharing between users | Multi-account orgs want a shared pool | Complex wallet semantics, audit surface, support tickets when credits go missing. Low value for the target segment (single admin per org at MVP). | Allow org accounts (one org, multiple admins) in v2 if validated |
| Monthly subscription billing | Recurring revenue is attractive | Tanzania's target users distrust subscription models — "what if I forget to cancel?" Mobile money recurring pull is also technically complex with Azampay and has lower success rates than one-time pushes. | Prepaid bundles already solve this cleanly |
| Email marketing / multi-channel | Platforms like Mailchimp add email alongside SMS | Out of domain. Target orgs don't have email lists; their members are phone-first. Adds complexity without relevance. | Keep SMS-only at v1; WhatsApp could be a v3 channel given Tanzania's WhatsApp penetration |
| Real-time chat between users | Collaboration features | Not in domain at all. Would require presence, threading, push notifications — an entirely different product surface. | None needed — this is not a collaboration tool |
| White-label reseller accounts | Resellers want to brand the platform for their own sub-customers | Introduces multi-tenancy, separate billing, branding configuration — massive complexity. Dilutes the NIDA trust story (resellers may not enforce KYC). | Evaluate only after reaching Scale-tier customers who request it |
| AI message drafting / templates with merge fields | Personalized messages ("Dear [Name], your fee is due") | Merge fields at scale (5,000+ contacts) introduce send-time complexity, template validation, and failure modes when contact data is incomplete. Low literacy users struggle with template syntax. | Offer a simple "add to message from contact field" picker — name insertion only, validated at compose time |
| Opt-out / unsubscribe link management | GDPR-style compliance | Tanzania does not yet have an equivalent to GDPR/PECR for SMS marketing. Adding automated opt-out management adds complexity without a regulatory driver. | Manual contact deletion is sufficient for v1; add suppression list in v2 as the market matures |
| Mobile app (native) | Users want a home screen icon | PWA progressive web app behavior (add to home screen, offline contact list) gives 80% of the value with zero additional codebase. Native app is a v2 commitment. | Ensure the Next.js web app is PWA-ready (manifest + service worker) — this is a v1 task, not a v2 task |
| OAuth / social login | "Sign in with Google" | NIDA IS the identity layer. Adding Google/Facebook login creates an unverified account path that undermines the KYC differentiator. | Email + password with NIDA verification remains the only path. |
| Real-time delivery status (push) | Users want to watch the dashboard update live | Server-sent events or WebSockets for live delivery tracking add complexity for marginal benefit. Campaign sends complete in seconds to minutes — a page refresh or polling is sufficient. | Polling every 30s on the campaign detail screen is sufficient for v1; add SSE in v1.x if users complain |

---

## Feature Dependencies

```
NIDA Verification
    └──enables──> 50 Free SMS Credit Grant
    └──enables──> Account Activation (can't send without it)
    └──enables──> Custom Sender ID Application (TCRA requires verified identity)

Wallet / Credit Balance
    └──requires──> Payment (Azampay top-up) OR NIDA Grant
    └──enables──> Send Campaign (credits must be reserved before dispatch)
    └──enables──> Low-Credit Alert (threshold check on balance)

Contact Management
    └──requires──> Account (contacts belong to a user)
    └──enables──> Campaign Send (must have recipients)
    └──subenables──> CSV Import (batch population of contacts)
    └──subenables──> Duplicate Removal (happens at import time)
    └──subenables──> Groups (organizes contacts for targeted sends)

Campaign / Bulk Send
    └──requires──> Wallet (credits must be available)
    └──requires──> Contacts (or at least one phone number)
    └──requires──> Sender ID (default numeric if custom not approved)
    └──enables──> Delivery Tracking (per-campaign status)
    └──enables──> Analytics Dashboard (campaign history feeds charts)

Scheduling
    └──requires──> Campaign (schedules an existing campaign object)
    └──requires──> Reliable background job (Quartz/Spring Scheduler in messaging service)

Custom Sender ID
    └──requires──> NIDA Verification (regulatory: unverified users cannot register sender IDs)
    └──requires──> Admin Approval Workflow (admin service)
    └──enables──> Branded outbound identity

Analytics Dashboard
    └──requires──> Campaign History (data must exist first)
    └──requires──> Delivery Tracking (delivery rates need delivery events)
    └──requires──> Wallet Events (credit burn rate)

Admin Panel
    └──requires──> All services operational (cross-service read views)
    └──enables──> Sender ID Approval
    └──enables──> User management / suspension
    └──enables──> Bundle management
    └──enables──> Audit log review
```

### Dependency Notes

- **NIDA Verification gates everything:** Account activation, the free credit grant, and custom sender ID application all depend on NIDA completing. If NIDA API access is delayed, the entire acquisition model changes. This is the highest-risk dependency in the project.
- **Wallet reservation must precede campaign dispatch:** Credits must be reserved (not just checked) before a campaign is handed to the upstream SMS provider. The reservation pattern in the wallet service prevents over-spend. Campaign send depends on this being correct.
- **Custom Sender ID requires both NIDA and Admin approval:** Two dependencies. If either is missing (unverified user, or admin hasn't reviewed), the user falls back to the numeric shortcode. This fallback must be gracefully displayed in the UI.
- **Analytics requires live data:** There is nothing to show on the analytics screen until campaigns have been sent. The dashboard must degrade gracefully (empty state with "send your first campaign to see analytics" prompt) rather than showing broken charts.
- **Scheduling requires background job reliability:** A scheduled campaign that silently fails is worse than no scheduling feature. The messaging service must have a robust Quartz or Spring Scheduler setup with retry logic and admin visibility into scheduled job failures.

---

## MVP Definition

### Launch With (v1)

The minimum set that validates the core loop: Register → Top Up → Import Contacts → Send → See Results.

- [x] NIDA-verified registration with 50 free SMS credit grant — acquisition hook and trust differentiator; without this the product is just a worse NextSMS
- [x] Email + password login with OTP-based password reset — baseline auth; NIDA is onboarding, not daily login
- [x] Credit balance display on every authenticated screen — users must always know their balance
- [x] Azampay mobile money payment (all 5 operators) with polling recovery — this is the only viable payment method for the target market
- [x] Prepaid bundle selection (Taster/Starter/Growth/Pro/Scale) — pricing is already designed; implement it as-is
- [x] Contact management (add/edit/delete individual contacts) — baseline address book
- [x] Contact groups (create, name, assign contacts to group) — essential for Tanzania use case (congregation X, class 4B, branch Y)
- [x] CSV contact import with duplicate removal — users have existing rosters; this is how they populate contacts
- [x] Compose and send bulk SMS to a group or ad-hoc list — the core product action
- [x] Campaign scheduling (future date/time) — needed for Sunday morning church sends, school term reminders
- [x] Delivery tracking per campaign (sent / delivered / failed counts) — "did it work?" is the first question
- [x] Campaign history list — enables repeat sends and audit trail
- [x] Low-credit alert (SMS + in-app) — prevents mid-campaign failures
- [x] Default numeric sender ID — users can send before custom ID is approved
- [x] Custom sender ID application with admin approval workflow — regulatory requirement and differentiator; must be in v1 even if approvals are manual
- [x] Message character counter + SMS unit count preview at compose time — cost transparency prevents distrust
- [x] Post-send credit deduction confirmation screen — shows credits used, remaining balance, delivery summary
- [x] Credit expiry display on wallet screen — shows 12-month / 30-day expiry dates
- [x] Admin panel: user list, sender ID approval queue, bundle management, audit log — internal operations cannot be deferred
- [x] Basic analytics dashboard: delivery rate, campaign history chart, credit burn rate — stated as explicit differentiator vs NextSMS; must be in v1 not deferred

### Add After Validation (v1.x)

Features to add once the core loop is validated with real users.

- [ ] Swahili UI localization (key screens: compose, contacts, top-up) — add when first 50 customers show non-English preference patterns
- [ ] PWA manifest + service worker (add-to-home-screen, offline contact list cache) — add when mobile usage patterns confirm users want native-like experience
- [ ] Credit expiry warning notifications (7-day and 1-day before expiry) — add once credit expiry has caused at least one churn event
- [ ] Template/draft message save — add when users request the ability to reuse messages
- [ ] Delivery report CSV export — add when NGOs or schools request it for donor/compliance reporting
- [ ] Advanced analytics (per-group performance, time-of-day heatmap, cost per campaign) — add once basic dashboard is validated and users ask for more depth
- [ ] Bulk contact edit (reassign group, delete batch) — add when users complain about managing large contact lists
- [ ] SSE/polling for live campaign status updates — add if users report frustration with manual refresh during campaign dispatch

### Future Consideration (v2+)

Features to defer until product-market fit is established.

- [ ] Public REST API — defer until developer-adjacent customers (NGOs with technical staff, schools with IT departments) make up a material segment
- [ ] Two-way SMS / inbound reply routing — requires inbound number, conversation threading UI, TCRA licensing — different product
- [ ] Native mobile app (React Native) — already in v2 plan; PWA covers the gap
- [ ] Credit sharing / org accounts with multiple admin users — defer until multi-admin use case is validated
- [ ] WhatsApp channel — Tanzania WhatsApp penetration is high but integration requires Meta Business API approval; valid v3 move
- [ ] White-label reseller accounts — defer; undermines NIDA trust story unless reseller KYC pipeline is designed carefully
- [ ] Opt-out / suppression list management — defer until Tanzania regulatory environment matures

---

## Feature Prioritization Matrix

| Feature | User Value | Implementation Cost | Priority |
|---------|------------|---------------------|----------|
| NIDA verification + 50 SMS grant | HIGH | HIGH | P1 |
| Azampay mobile money payment | HIGH | HIGH | P1 |
| Send bulk SMS to group | HIGH | MEDIUM | P1 |
| Contact groups + CSV import | HIGH | MEDIUM | P1 |
| Delivery tracking per campaign | HIGH | MEDIUM | P1 |
| Credit balance display | HIGH | LOW | P1 |
| Low-credit alert | HIGH | LOW | P1 |
| Campaign scheduling | HIGH | MEDIUM | P1 |
| Analytics dashboard | HIGH | HIGH | P1 |
| Custom sender ID + approval workflow | MEDIUM | MEDIUM | P1 |
| Message character counter | HIGH | LOW | P1 |
| Campaign history list | MEDIUM | LOW | P1 |
| Post-send confirmation + credit deduction | HIGH | LOW | P1 |
| Admin panel (user mgmt, sender ID approval, audit) | HIGH | HIGH | P1 |
| Credit expiry display | MEDIUM | LOW | P1 |
| Duplicate contact removal | MEDIUM | MEDIUM | P1 |
| Swahili UI localization | MEDIUM | MEDIUM | P2 |
| PWA manifest + offline cache | MEDIUM | LOW | P2 |
| Template/draft save | MEDIUM | LOW | P2 |
| Delivery report CSV export | LOW | LOW | P2 |
| Credit expiry warning notifications | MEDIUM | LOW | P2 |
| Advanced analytics | MEDIUM | HIGH | P2 |
| Bulk contact operations | LOW | MEDIUM | P2 |
| Public REST API | LOW | HIGH | P3 |
| Two-way SMS | LOW | HIGH | P3 |
| Native mobile app | MEDIUM | HIGH | P3 |
| WhatsApp channel | MEDIUM | HIGH | P3 |

**Priority key:**
- P1: Must have for launch
- P2: Should have, add when possible
- P3: Nice to have, future consideration

---

## Competitor Feature Analysis

| Feature | NextSMS (TZ) | Africa's Talking (regional) | Our Approach |
|---------|--------------|------------------------------|--------------|
| Registration | Email only, no KYC | Email only, no KYC | NIDA KYC — only verified platform in TZ |
| Free tier | None | Test credits (small, dev-focused) | 50 SMS on NIDA verify — non-technical user friendly |
| Pricing model | Prepaid credit, API-focused | Prepaid, developer-focused | Prepaid bundles with volume tiers, mobile-first |
| Payment methods | Card / bank transfer (TZ users report friction) | Card / bank transfer | Azampay: all 5 TZ mobile money operators |
| Contact management | Basic | Moderate (API-driven) | Full UI: groups, CSV import, deduplication |
| Bulk campaigns | Yes (API primarily) | Yes (API primarily) | Full no-code campaign builder |
| Campaign scheduling | Limited / API-only | API-only | UI scheduling with cancel queue |
| Delivery reports | Basic counts | Detailed (developer-facing) | Visual dashboard with delivery rate trends |
| Analytics | Minimal | Developer-oriented reports | Rich visual dashboard — explicit differentiator |
| Custom sender ID | Yes (approval required) | Yes (country-dependent) | Yes, with in-app approval workflow |
| Two-way SMS | Yes (additional cost) | Yes | Deferred to v2 |
| Public API | Yes — core product | Yes — core product | Deferred to post-MVP |
| Mobile-first UX | No — desktop portal | No — developer console | Yes — thumb-first, 5" screen optimized |
| Swahili support | No | No | Partial (v1.x) then full (v2) |
| Multi-operator mobile money | No | Varies by country | Yes via Azampay |

**Confidence note:** NextSMS and Africa's Talking analysis is based on training-data knowledge as of mid-2025. Validate against live competitor UIs before finalizing competitive positioning. Africa's Talking is included for regional context; they are developer-focused and not a direct competitor for non-technical Tanzania users.

---

## Tanzania / East Africa Specific Considerations

These are features or design decisions that would be obvious to a global bulk SMS platform but require specific adaptation for this market:

1. **Phone number format handling.** Tanzania numbers begin with +255 or 07xx/06xx locally. The contact import and manual entry must normalize both formats to international format (+255XXXXXXXXX) before storage. Silent normalization failure = messages to wrong numbers.

2. **Network operator detection.** Azampay routes to different operators. When a user enters their M-Pesa number for payment, the UI should ideally confirm the operator (or at minimum accept and let Azampay handle routing). Display operator name for confirmation.

3. **SMS character set — Latin vs non-Latin.** Swahili uses standard Latin characters, so 160-char single SMS limit applies. However, if users copy-paste from WhatsApp or Word documents they may inadvertently include smart quotes or accented characters (e.g., ä, ö from German-influenced mission church names) that trigger UCS-2 encoding, reducing limit to 70 chars. The character counter must detect encoding and warn accordingly.

4. **Low-bandwidth resilience.** Target users are in areas with variable data connectivity (2G/3G still common outside Dar es Salaam). Pages should load fast on slow connections. The Next.js app should prioritize critical path rendering, lazy-load analytics charts, and handle API timeouts gracefully rather than showing spinners indefinitely.

5. **TCRA compliance for sender IDs.** Tanzania Communications Regulatory Authority (TCRA) mandates that all bulk SMS sender IDs be registered. The approval workflow is not a UX nicety — it is a regulatory requirement. Unregistered sender IDs can be blocked by operators without notice. The admin panel must maintain a clear audit trail of sender ID approval decisions.

6. **NIDA API rate limits and downtime.** NIDA API is a government system with variable availability. Registration flow must handle NIDA timeouts gracefully — queue the verification, allow the user to continue with a "verification pending" state, and auto-complete when NIDA responds. Do not make new user creation fail hard if NIDA is slow.

7. **Mobile money transaction UX.** When the user initiates an Azampay payment, they receive a push notification on their phone to confirm the payment. The UI must clearly explain this ("Check your phone for a payment confirmation") and poll for completion without timing out aggressively. Show a waiting state, not a spinner with no explanation.

8. **Seasonal send patterns.** Tanzania organizations have clear seasonal spikes: school term starts/ends (January, May, September), Eid Al-Fitr, Christmas, Easter, Ramadan. Analytics should eventually surface these patterns. At v1, ensure the platform doesn't fall over during synchronized bulk sends across multiple users around these events (HPA is already in the architecture).

---

## Missing Features from Locked Design (Gap Analysis)

The locked design in PROJECT.md is well-considered. The following are gaps or implicit features that are not explicitly called out but should be confirmed as in-scope for v1:

| Missing / Ambiguous Feature | Assessment | Recommendation |
|-----------------------------|------------|----------------|
| Message character counter at compose time | Not explicitly in PROJECT.md requirements | Add to v1. Zero-effort frontend feature with high trust impact. |
| Post-send confirmation screen (credits used + balance) | Implied but not stated | Confirm as P1. Critical for trust — users need to know the deduction happened correctly. |
| NIDA timeout / graceful degradation | Not mentioned | Must design "verification pending" state. NIDA is a government API. |
| Credit expiry countdown on wallet screen | Mentioned in pricing model but not in UX requirements | Add to wallet screen. 7-day warning notification should be a v1 feature, not v1.x. |
| Sender ID fallback (numeric shortcode before custom approved) | Not explicitly stated | Confirm the default shortcode is allocated before launch. This is a pre-implementation blocker already in PROJECT.md open items. |
| Empty state UX for analytics dashboard | Not mentioned | Analytics is a differentiator; its empty state must be designed carefully or it looks broken |
| Phone number normalization (07xx → +255) | Not mentioned | Critical for correctness; should be in contact service's import and manual-add flows |
| Payment operator confirmation in top-up flow | Not mentioned | Reduces user error; Azampay routing depends on correct number format |
| Campaign cancel / abort (for scheduled campaigns) | Not mentioned | Scheduled campaigns must be cancellable before dispatch window |
| Audit log for user actions (not just admin) | Admin audit log is in scope; user-facing action history is not mentioned | User-facing "sent messages history" is covered by campaign history; confirm admin audit log covers sender ID applications and payment events |

---

## Sources

- PROJECT.md — primary source of truth for locked decisions, architecture, pricing, competitive position
- Training-data knowledge of: NextSMS Tanzania (API-first competitor), Africa's Talking (regional bulk SMS), Twilio, Vonage, Infobip (global SMS SaaS patterns), East Africa mobile money ecosystem (Azampay, M-Pesa TZ, Airtel Money, Tigo Pesa), TCRA regulatory context
- Confidence: MEDIUM — web research tools were not available during this session. Competitor feature lists (especially NextSMS current feature set) should be validated against live competitor UIs before implementing competitive positioning

---

*Feature research for: Bulk SMS reseller platform (Tanzania, non-technical admins, mobile-first)*
*Researched: 2026-06-18*

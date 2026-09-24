# Changelog

All notable changes to the BNS Warehouse System, in plain English. Newest first.

This is an internal tool with no formal release process, so version numbers here
are just a scanning aid, not a promise of semver-style compatibility.

## [0.23.32] - 2026-09-24 (v0.097)

### Fixed
- `print-agent/build_exe.bat` failed with `'pyinstaller' is not recognized as
  an internal or external command` even when PyInstaller had installed
  correctly - `pip` puts its `.exe` in Python's `Scripts` folder, which
  isn't always on PATH. Now calls it as `python -m PyInstaller` instead,
  which bypasses PATH entirely. It also now checks for `python` and for
  PyInstaller up front, installs `requirements.txt` itself if needed, and
  stops with a clear message (instead of a misleading "Done") if any step
  actually fails.

## [0.23.31] - 2026-09-24 (v0.096)

### Added
- Print Agent now runs as a **system tray app** with its own icon (a printer
  with a little person behind it) instead of a bare console window.
  Right-click it for **Settings** (set the SumatraPDF.exe location from a
  proper window with a Browse... button - no more editing files or
  environment variables) and **Exit**. The setting is saved to
  `%APPDATA%\BNSPrintAgent\config.json` and takes effect on the next print
  with no restart needed.
- Added `print-agent/build_exe.bat` + `requirements.txt` to build a
  standalone `BNSPrintAgent.exe` (via PyInstaller) - a single file with the
  tray icon and Settings window built in, no Python install needed on the
  PC that runs it. Running `python agent.py` directly still works exactly
  as before too, tray icon included, as long as `pystray`/`Pillow` are
  installed; without them it falls back to the original plain console mode.
- Added the **Setup Guide PDF** (`docs/BNS_Warehouse_Setup_Guide.pdf`) - a
  go-live runbook covering prerequisites, first-run steps, every Settings
  field, and full Shopify/DPD/email/Print Agent setup plus both bulk-import
  file formats. Previously delivered standalone; now folded into `docs/`
  alongside the other three PDFs as planned.

## [0.23.30] - 2026-09-23 (v0.095)

### Added
- Companies page now has a **search box** (name, account number, EORI/VAT),
  alongside the existing On Hold / Do Not Use filters.
- A ticket's **Caller name** field is now search-as-you-type against
  existing Contacts - pick a match and it auto-fills phone, email and
  company from that contact's record (whichever of those it actually has).
  No contact link is required at all - typing a plain name works exactly as
  before, and every auto-filled field stays independently editable
  afterwards. Editing the name away from a linked contact's own name quietly
  unlinks it (the field is always free text; the link is just a shortcut for
  filling it in). This replaces the previous separate "Link a contact" box.

## [0.23.29] - 2026-09-23 (v0.094)

### Added
- New **Contacts** feature under CRM - people at a company, kept permanently
  (not just an import artifact) so they're searchable and linkable when
  opening a support ticket. Each Contact belongs to a Company, and links back
  to it just like Support Tickets already do.
- Companies now have a **Contacts** button per row, taking you to the
  Contacts list pre-filtered to that company. Contacts have the same link
  back to their company, and a **Tickets** toggle of their own.
- Support Tickets can now optionally link to a specific **Contact** as well
  as/instead of a bare Company - selecting a contact auto-fills caller
  name/phone/email and the linked company (all still independently
  editable), the same way linking an order already auto-fills the company.
- Companies now carry four new fields from the OrderWise export: **On
  Hold** (a credit hold - stop processing new orders until cleared, distinct
  from the existing over-credit-limit block), **Do Not Use**, and the
  **GAPS**/**GDMS** tick-boxes (kept for a report to be built later). The
  Companies page has new On Hold / Do Not Use filters - these accounts are
  never excluded from anything, just filterable.
- New **Company & Contact Import** (Settings > Company & Contact Import) -
  bulk create/update of Companies and their Contacts from an OrderWise
  customer export (two spreadsheets: customer list + customer contact
  details), matched by Account number / CustomerCode. Same preview-then-
  confirm pattern as Bulk Stock Import. A contact whose CustomerCode doesn't
  match any company is skipped and reported, never guessed at.
- Contact records carry two fields (Shopify customer ID, invited-to-Shopify
  date) that aren't used yet - added now so a future Shopify B2B "Company
  Contacts" sync and invitation-email feature won't need another schema
  change.

## [0.23.28] - 2026-09-23 (v0.093)

### Added
- Support Tickets and RMAs are now grouped under a new **CRM** section in the
  sidebar, separate from Sales.
- Companies can now be **deleted** from the Companies page - blocked with a
  clear message if the company still has orders or support tickets linked,
  so a real account can't be removed by accident while duplicate/test
  companies (e.g. an early manually-added "BNS Distribution" alongside one
  Shopify created) delete cleanly.
- `docs/BNS_Warehouse_Support_Tickets_API.pdf` - a standalone integration
  guide for the Support Tickets public API, matching the style of the
  existing Stock API document.
- `docs/BNS_Warehouse_User_Guide.pdf` - a first-draft, in-depth end-to-end
  user guide covering every screen in the system, with placeholder boxes
  marking where real screenshots will go once there's browser access to
  capture them.

## [0.23.27] - 2026-09-23 (v0.092)

### Added
- **Support Tickets** - a new section for logging phone/email enquiries,
  separate from Sales Activity. Each ticket has a sequential ticket number
  (`TKT-10001` onward, allocated from a real Postgres sequence so two tickets
  opened in the same second by two different people never collide), caller
  name, phone, email, status (In Progress / On Hold / Closed / Complete),
  talk time (editable as hours + minutes), a title, and a dated,
  user-attributed timeline of notes rather than one free-text box.
- A ticket can be linked to a **Company** and/or a specific **Order** (e.g.
  "SO-10019 hasn't turned up") via a type-to-search picker; linking an order
  auto-fills its company unless one's already set. The order screen now
  shows a link to any ticket raised about it (or an "Open a support ticket
  for this order" link when there isn't one yet), and each company's row on
  the Companies page has a "Tickets" toggle listing every ticket linked to
  that account.
- A public API (`/api/public/tickets`, same `X-API-Key` auth as the existing
  stock API) so a third-party call-transcription/CRM-import service can open
  a ticket - or add a timeline entry to one it already opened - straight
  from a finished call, without anyone typing it in by hand.

## [0.23.26] - 2026-09-23 (v0.091)

### Fixed
- **DPD rejecting shipments with "Delivery notification email or mobile is
  mandatory".** This is a separate field from the delivery contact phone
  number already sent (`outboundConsignment.deliveryDetails.notificationDetails`,
  used to text/email the recipient about their delivery - distinct from
  `contactDetails.telephone`, the courier-facing contact number). Since the
  system only ever captures one phone number per order, that same number is
  now sent as the notification mobile too, and the order's email (from
  Shopify) is sent as the notification email when present.

### Added
- **Extended liability** toggle in Settings > DPD ("Request DPD's extended
  liability cover on every shipment"), off by default. DPD's extended
  liability is a chargeable insurance option, so this is opt-in rather than
  ever turned on automatically - worth checking with DPD whether your
  account terms already cover this before switching it on. When enabled,
  the insured value sent is the order's own goods value (the same figure
  used for `customsValue` on customs shipments), capped at DPD's £5,000
  maximum per shipment.

## [0.23.25] - 2026-09-23 (v0.090)

### Fixed
- **DPD rejecting Irish shipments with "Delivery Description is mandatory".**
  DPD want a description of the consignment's contents as a whole
  (`outboundConsignment.deliveryDescription`), which is a different thing
  from the per-product descriptions already sent inside each parcel - adding
  a description to a product doesn't satisfy it. Now sent on every shipment
  that needs a customs declaration.

### Added
- **Goods description** in Settings > DPD (max 45 characters, starts as
  "Telecoms and networking equipment"). It's an editable setting rather than
  something hardcoded because DPD explicitly warn that vague contents
  descriptions get parcels delayed or returned at customs - their own
  guidance is to write "women's cotton dresses" rather than "clothing", so
  something like "IP telephones and network switches" is safer than a
  catch-all. Booking stops with a clear message if it's ever left blank.
- **Customs value** is now sent (`customsValue`), worked out from the order
  lines - goods only, excluding VAT and shipping, which is the "intrinsic
  value" DPD ask for. DPD return parcels to the sender when this is zero, so
  an order whose lines have no prices is now stopped before booking with a
  message saying so, rather than being sent and bounced back.
- **Currency** in Settings > DPD (GBP/EUR/USD, defaults to GBP). DPD fall
  back to the myDPD account default when it isn't declared but recommend
  sending it explicitly so declarations can't end up in the wrong currency.

### Not changed (see reasoning)
- DPD's `liability`/`liabilityValue` (extended liability) are documented as
  needed for international destinations, but enabling extended liability is
  a chargeable insurance option on the DPD account - not something to switch
  on silently. Left off; if a liability-related rejection appears, it can be
  added as an explicit Settings toggle.

## [0.23.24] - 2026-09-23 (v0.089)

### Fixed
- **DPD rejecting Irish shipments with "Exporter address is mandatory" even
  with every Settings > DPD field filled in.** This was our bug, not a
  missing setting. DPD's schema nests the address and contact inside their
  own objects (`exporterDetails.address.street`, `.contactDetails.telephone`
  and so on) and we were sending those fields flat on `exporterDetails`
  itself - so DPD read an exporter block with no address in it at all.
  Rebuilt against DPD's own published schema.
- The same flat-vs-nested mistake was in `importerDetails` (so yesterday's
  Company EORI work would not have landed correctly either) and in
  `collectionDetails`. The collection one had been silently ignored since
  the integration was first written - DPD was quietly falling back to the
  collection address configured on the DPD account, which is why UK
  shipments carried on working and nothing ever looked wrong.
- Phone numbers are now stripped to digits (keeping a leading `+`) before
  being sent to DPD, and trimmed to DPD's 15-character limit. DPD validates
  telephone fields against a digits-only pattern, so a number stored the way
  people actually write them - `0121 500 2500`, `+353 (0)1 234 5678` - would
  have had the whole shipment rejected over the spaces and brackets.

### Added
- Settings > DPD now has the full set of address fields DPD accepts:
  **address line 1 (street)**, **line 2 (locality)**, **line 3 (town)**,
  **line 4 (county)**, postcode and country code. Only lines 1 and 3 and the
  country code are mandatory; blank optional ones are left out of the
  request entirely rather than sent as empty strings.
- A **sender VAT number** field in Settings > DPD, sent as the exporter's VAT
  number on customs declarations when filled in.

### Changed
- The Settings page is now **collapsible by section** - it opens as a short
  scannable list of headings, and you open just the area you came to change
  instead of scrolling through everything.
- The Settings page now uses the full page width instead of a narrow
  left-hand column, so the field grids have room (the DPD address fields go
  three-across on a wide screen).
- **Save Settings now floats** in the bottom-right corner, the same as the
  order screen - with sections collapsible you can finish editing anywhere
  on the page, so having to scroll to the bottom to find Save made no sense.
  The "My Email" and "Customisation" sections keep their own Save buttons,
  since those are per-user rather than shared settings.

### Removed
- The **sender contact email** field in Settings > DPD. DPD's shipment API
  has no field for it, so it was only ever being stored and never sent
  anywhere - keeping it implied it did something.

## [0.23.23] - 2026-09-22 (v0.088)

### Added
- DPD Irish (customs) shipments now send the two additional fields DPD's
  own commercial invoice output confirmed are required, checked directly
  against DPD's own API schema (their developer portal's "Create domestic
  shipment" reference):
  - `invoice.termsOfDelivery` is now sent as `"DAP"` on every customs
    shipment - the receiver settles any duty/tax directly with DPD, rather
    than BNS being invoiced for it (that's the separate `DT1` arrangement,
    which needs its own account setup DPD hasn't done for us).
  - Companies can now have an **EORI Number** and **VAT Number** set
    (Companies page - Edit). When an order linked to a company ships to a
    customs country, its company's EORI/VAT are now sent as
    `invoice.importerDetails.eoriNumber`/`vatNumber` - previously these
    were never sent at all, which is not what a real DPD invoice for one
    of our Irish orders showed. This is deliberately the **Company's**
    EORI, not BNS's own (BNS's own GB EORI, used for `exporterDetails`,
    stays under Settings > DPD as before) - BNS is the exporter/sender,
    but the receiving company is the importer of record for customs
    purposes.
  - Booking a DPD shipment to a customs country for an order linked to a
    Company with no EORI number set now stops with a clear message
    pointing at the Companies page, instead of DPD silently not getting an
    EORI it needed.
  - Orders with no linked Company (e.g. a direct consumer sale) are
    unaffected - EORI/VAT are simply left out, which is correct for a B2C
    customs declaration.

## [0.23.22] - 2026-09-22 (v0.087)

### Added
- Clearer pre-flight validation before a DPD shipment is booked, instead of
  only finding out from DPD's own raw rejection text after the fact:
  - Every shipment now checks the order has a delivery phone number set -
    DPD rejects any domestic shipment without one ("Delivery contact
    telephone number (outbound) is mandatory"), not just customs ones.
  - Shipping to a customs country (Ireland) now checks BNS's own sender
    address is filled in under Settings > DPD (organisation, street, town,
    postcode, contact name, contact phone) before attempting to book,
    naming exactly which field(s) are missing rather than DPD's generic
    "Exporter address is mandatory".

### Not changed (see reasoning)
- Confirmed the customs "exporterDetails" block is correctly BNS's own
  address/contact/EORI from Settings > DPD, not the order's Company -
  DPD's EORI rule for this field is GB-only, tied to whoever holds the DPD
  account and physically hands the parcel to DPD (BNS), regardless of
  which customer the order is for. Company-specific EORI/VAT numbers would
  belong on "importerDetails" (the receiving business) instead, which DPD
  supports as optional fields - not currently wired up, since nothing's
  asked for it yet, but a natural next step if BNS wants faster customs
  clearance for regular Irish B2B customers.

## [0.23.21] - 2026-09-22 (v0.086)

### Added
- The order edit page's Save button is now fixed to the bottom-right of the
  screen, so it's reachable from anywhere on what can be a genuinely long
  form (order details, lines, payments, DPD section) without scrolling
  all the way down.

### Fixed
- Found the actual cause of "I changed the delivery address to Ireland and
  saved, but the courier options didn't update until I left and came back
  in": saving an order only ever told the Sales Activity list to refresh -
  nothing told the order edit page itself, or its DPD available-services
  lookup, that anything had changed. The page's own "current order" data
  and its list of DPD services are now both refreshed as part of a
  successful save, so a change like the delivery country is reflected
  immediately rather than needing to navigate away and back. This was also
  a latent risk for spurious "someone else already edited this" conflict
  errors on a second save in the same visit, since the save-conflict check
  was comparing against that same stale cached order.

## [0.23.20] - 2026-09-22 (v0.085)

### Added
- Sales Activity's "Order ID" and "Order Date" column headers are now
  clickable to sort - first click sorts ascending (oldest/lowest first),
  clicking the same header again reverses it to descending, with a small
  ▲/▼ arrow showing which column and direction is active. Default view is
  unchanged (newest order date first) until someone actually clicks a
  header. This also directly fixes the "Order IDs look out of order"
  report - the list was always sorted by order date only, so the ID column
  could look jumbled purely because a given order's date doesn't always
  line up with the order it was created in; sorting by ID directly is now
  one click away.
- A filter dropdown under "Status" (all the same statuses used elsewhere in
  the app) and another under "Order Type" - pick one to narrow the list to
  just that status/type, or leave on "All" to show everything. Both filters
  and the search box all combine together.

## [0.23.19] - 2026-09-22 (v0.084)

### Changed
- The sidebar branding text now reads "System - Version: v0.084" instead of
  "System v0.084".

## [0.23.18] - 2026-09-22 (v0.083)

### Fixed
- Found the real shape of DPD's label response, from an actual raw response
  now visible thanks to v0.079's error message improvement: it's
  `{"data": {"printString": ["<label 1>", "<label 2>", ...]}}` - the label
  array is nested one level deeper, inside a "printString" field, not
  directly under "data" as originally assumed from the docs alone. That
  wrong assumption is exactly why despatching this kind of shipment hit the
  "unexpected label response shape" safety-net error and produced no label
  at all. Both shapes are now handled - the confirmed real one, and the
  originally-assumed one kept as a fallback.
- Timestamps shown across the app (Bug Reports' "when this happened" being
  the one reported, but this affects any LocalDateTime shown anywhere) were
  reading a full hour behind the real time during British Summer Time. The
  backend records these in UTC but was serializing them with no timezone
  marker at all, which browsers then read as already being in the viewer's
  own local time - silently skipping the UTC-to-BST conversion rather than
  applying it. Timestamps are now sent with an explicit UTC marker so the
  browser converts them correctly, in BST or GMT.

## [0.23.17] - 2026-09-22 (v0.082)

### Fixed
- v0.081's fix for the Excel-chart build failure didn't actually work -
  `mvn dependency:go-offline` itself failed to resolve the `poi-ooxml-full`
  dependency that fix added, before compilation even started. Rather than
  keep guessing at POI's dependency setup blind (this project's build tools
  aren't reachable to verify a fix compiles before sending it), the
  higher-risk part of the Invoice Report's Excel chart has been removed
  instead: the chart itself (title, legend, both series, markers) still
  builds natively in the exported .xlsx exactly as before, but it no longer
  tries to add an on-chart value label to each point, since that was the
  one part needing the schema classes that wouldn't resolve. The exact
  figure for every point is still right there in the small data table next
  to the chart on the same sheet. (The Dashboard's own line chart isn't
  affected by any of this - it's hand-drawn, not an Excel-native chart, so
  it already shows its value labels as before.)
- `backend/pom.xml` reverted to the plain `poi-ooxml` dependency it had
  before v0.081 - no more schema-jar swap needed now the code that needed
  it is gone.

## [0.23.16] - 2026-09-22 (v0.081)

### Fixed
- The v0.080 build failed to compile (`docker compose build` failing on the
  `api` target with `mvn clean package` exiting non-zero). Root cause: the
  native Excel line chart added to the Invoice Report export back in v0.078
  (the data-label code in `addMonthlyChartSheet`) reaches directly into raw
  OOXML schema classes (`CTLineChart`, `CTDLbls`) that only exist in POI's
  full schemas jar - `poi-ooxml` alone pulls in `poi-ooxml-lite`, a reduced
  jar that doesn't include them. This had been sitting uncompiled/unverified
  since v0.078 (flagged as such at the time) and only surfaced now on a
  clean rebuild. Fixed by swapping `poi-ooxml-lite` for `poi-ooxml-full` in
  `backend/pom.xml`.

### Changed
- The DPD service/courier picked on the Sales Activity/order screen is now
  always what gets booked, full stop. Previously, at despatch time that
  choice was re-checked against DPD's live "what's available now" list and
  silently swapped out (for the Settings default, or DPD's first available
  option) if it no longer matched - which could happen simply because the
  weight this re-check saw differed slightly from what was true when the
  choice was made on the order. That meant the courier shown on the picking
  note wasn't always the one actually booked. The order's chosen service is
  now booked exactly as picked, with no re-validation or silent override;
  the live-lookup/Settings-default/first-available fallback chain only
  still applies when nothing was picked on the order at all.

## [0.23.15] - 2026-09-22 (v0.080)

### Added
- The weight-cap override that was deliberately held off on in v0.079 has
  now been added after all, for a different reason than originally asked:
  Settings > DPD now has a **"Never offer Freight - cap the weight sent
  for the service check at (kg)"** field (`dpd_max_lookup_weight_kg`,
  blank by default = no change in behaviour). This isn't for the despatch
  booking itself - the real weight is always sent to DPD when a shipment
  is actually booked, unchanged. It's specifically for the Service dropdown
  on the Sales Activity/order screen, which is queried *before* an order
  is packed - at that point no cartons exist yet, so it has no choice but
  to treat the whole order as one parcel carrying its full weight, and a
  genuinely heavy order (e.g. 35kg) will always tip that lookup into
  Freight-only, however many cartons it eventually gets packed into. That
  matters beyond just the dropdown, because whatever service gets picked
  there is what shows on the picking note to tell the picker which courier
  to use. Setting a cap here only ever lowers what's sent to that one
  lookup call, never raises it, and never touches the real booked shipment
  weight.

### Fixed
- When DPD's label response doesn't match the shape the app expects (the
  "unexpected label response shape" error introduced in v0.079 to stop
  garbled labels reaching the printer), the error now includes the actual
  raw response text from DPD (truncated to 1000 characters) directly in
  the error message, so it's visible straight from the Bug Reports screen
  without needing server log access. Needed to actually diagnose what a
  Freight-tier shipment's label response looks like, since it's evidently
  not shaped the same way as an ordinary parcel shipment's.

## [0.23.14] - 2026-09-22 (v0.079)

### Fixed
- Found the actual cause of the "still 1 label, still freight" reports:
  **Reverse to Despatch** (and Cancel & Return to Stock) never cleared an
  order's DPD shipment reference. Confirming despatch already refuses to
  book a second DPD shipment for an order that has one - a sensible guard
  on its own - but because reversing an order back for correction (fix the
  quantity, address, etc.) left the *old* shipment's ID sitting on the
  order, re-confirming despatch after a correction silently skipped booking
  a new shipment altogether and kept pointing at the stale one - wrong
  weight, wrong parcel count, wrong service, whatever it was booked as the
  first time. This is what made the previous fixes look like they hadn't
  worked: reducing the quantity, repacking into 2 cartons and picking
  "Parcel Next Day" all did nothing, because despatch never rebooked
  against any of it. Both reversal actions now clear the old DPD shipment
  fields, so the next despatch books a genuinely fresh shipment reflecting
  whatever's actually true at that point. (DPD's documented API has no
  cancel/void-shipment endpoint, so the old shipment isn't actually
  cancelled on DPD's side by this - if it was already scanned in for
  collection, that still needs cancelling from DPD's own portal)
- The garbled "&n"-scattered label from the 35kg test was very likely the
  same stale-shipment bug compounding with the new label-fetch code: when a
  label response didn't come back in the shape that code expected, it
  silently fell back to printing the raw response text - including,
  potentially, a raw JSON error body straight to the printer. It no longer
  does that: an unrecognised response is now a clear error instead of
  whatever garbage would otherwise have gone to the label stock.

### Not changed (see reasoning)
- Didn't add a "cap the weight sent to DPD so freight never gets offered"
  override, despite being asked for one - once a despatch actually books a
  fresh shipment (the real bug above), the multi-carton weight-splitting
  fix from v0.078 should already keep an ordinary multi-carton order out of
  freight without needing to understate its weight to DPD. Deliberately
  holding off on quietly sending DPD a lower weight than the parcel
  actually is (a real compliance/liability question, not just a code
  change) until this is confirmed still needed on a **freshly created**
  order rather than one that had been reversed - please retest on a new
  order and let me know if freight still turns up.

## [0.23.13] - 2026-09-22 (v0.078)

### Fixed
- The real reason a 2-carton order still only produced one DPD label after
  the previous fix: two separate gaps, both now fixed together -
  - The label-fetch call was only ever getting one label back from DPD
    regardless of how many parcels the shipment actually had - DPD's docs
    say the plain response format returns a single response body, and only
    asking for the JSON array form (`Accept: application/json`) gets one
    raw label string back per parcel. Now always requests that form and
    joins every label together, so a shipment with 2+ parcels prints all of
    them, not just the first
  - The DPD "which services are available" lookup - used both to fill the
    Service dropdown and to pick the service actually booked - was still
    always asking DPD for a single parcel carrying the order's *entire*
    weight, regardless of how many cartons it was really packed into. For a
    heavier multi-carton order, that can tip DPD into only offering its
    freight/pallet-network service (which doesn't split into multiple
    everyday parcels) instead of an ordinary multi-parcel Parcel service -
    so even once multiple parcels were being requested in the booking
    itself, they were being booked under a service that doesn't support
    more than one. This lookup now reflects the order's real parcel split
    (from its packed cartons) the same way the shipment booking itself
    already did, so a normal multi-carton order gets offered - and booked
    against - an ordinary Parcel service rather than being pushed to freight

### Changed
- Dashboard's "Invoiced Values by Month" chart is now a line chart (was
  bars), with each point's value shown directly on the chart in a small
  label box, matching the original report this figure was modelled on
- The exportable Invoice Report (Reports > Invoice Reports) now includes a
  second sheet with the same monthly invoiced/credited figures as a data
  table and a real, native Excel line chart built from it (not a picture of
  one) - opens and can be edited/resized like any other Excel chart, with
  each point labelled with its value. It covers the calendar year of the
  report's "from" date (or the current year, if no date filter is set),
  since a by-month view only makes sense for a single year

## [0.23.12] - 2026-09-22 (v0.077)

### Fixed
- A multi-carton order only ever produced one DPD label. The shipment
  booking request had `numberOfParcels` hardcoded to 1 with a single parcel
  entry, regardless of how many cartons the order was actually packed
  into - so DPD only ever generated one label no matter how many boxes were
  going out. It now sends one parcel per carton the order was packed into
  (from the cartons created during packing), each with its own weight, so a
  2-carton order gets 2 labels, a 3-carton order gets 3, and so on
  - Each carton's own weight is used when it's been entered on the packing
    screen; if it hasn't, that carton's weight is worked out from what's
    actually in it (same basis as the previous single-parcel fallback) so a
    parcel is never sent to DPD with a weight of 0
  - Orders shipping to the Republic of Ireland (the one destination needing
    a full customs declaration) now get each parcel's own product/value
    breakdown from what's actually packed in that specific carton, instead
    of the whole order's contents being declared against a single parcel
  - If DPD is booked manually from the order screen before the order has
    been packed into cartons yet, this falls back to exactly the previous
    behaviour (one parcel covering the whole order) - there's nothing to
    go by yet at that point

### Investigated
- Asked whether the sender/return address shown on a DPD label can be
  blanked out or "white labelled". Checked DPD's full shipping API schema
  again specifically for this - there is no request field for it. DPD's own
  docs point to label sign-off being handled by their Customer Integration
  Team and a shipping-defaults template configured at the account level, so
  this needs to be requested from DPD directly (via the account manager),
  not something togglable from here - see `dpd-api-findings.md` in the
  project for the full note

## [0.23.11] - 2026-09-22 (v0.076)

### Changed
- Order screen's DPD "Service" dropdown no longer forces free text the
  moment the live DPD lookup fails - it now falls back to the last list of
  services DPD returned successfully (from any order), so there's always a
  real set of options to pick from rather than staff needing to remember and
  type an exact service code. A small badge on the right of the box shows
  "Live" (checked against DPD just now for this exact address/weight) or
  "Cached" (last known list, not re-verified for this address) - hover it
  for why. The dropdown only drops back to a free-text field when there's
  truly no fallback yet (nothing has ever been fetched successfully) or the
  order has no delivery postcode/country set yet
- The error shown under the Service field, when there is one, is now the
  actual message from DPD/the backend (e.g. the specific auth or validation
  failure) instead of a generic "check the postcode and Settings" line -
  the real reason was already being captured automatically in Bug Reports,
  this just also surfaces it right where staff are looking

## [0.23.10] - 2026-09-22 (v0.075)

### Added
- Orders that originated from Shopify now sync amendments back to the
  customer's Shopify order automatically on every save here, best-effort and
  non-blocking (it never holds up or reverses the save in this system if
  Shopify is unreachable or rejects the edit):
  - Address changes (name, address lines, town, postcode, country, phone)
    push via Shopify's `orderUpdate`
  - Removing a line, adding a line, or changing a line's quantity pushes via
    Shopify's order-edit flow (`orderEditBegin` → `orderEditSetQuantity` /
    `orderEditAddVariant` → `orderEditCommit`), matched by SKU. A line that's
    already fully fulfilled on Shopify's side can't be edited further there -
    that's reported back, not treated as an error
  - A newly-added line needs the product to already have its Shopify variant
    recorded (from the normal Shopify product sync) - if it doesn't, that
    one line is reported as not pushed rather than failing the whole sync
  - Price changes are detected and reported either way, but **not pushed
    automatically yet** - a price rise genuinely can't be (Shopify's
    order-edit API can only ever lower a line, never raise one above its
    original variant price), and a price fall, while technically possible
    via a discount, isn't implemented in this release since it touches
    customer-facing order totals directly and this whole feature hasn't
    been tried against a live Shopify store yet. Both cases show a message
    on save saying to adjust the price on Shopify directly for now
  - The result of the sync shows as a toast after saving the order (e.g.
    "Pushed to Shopify: added SKU123 x2. Address updated on Shopify.")

### Note
- This is a new feature that writes to live, customer-facing Shopify orders
  and has not been tested against a real Shopify store from this environment
  (no outbound network access here to verify it end-to-end). Try it on a
  low-stakes order first and check the result actually looks right on
  Shopify before relying on it for real amendments

## [0.23.9] - 2026-09-22 (v0.074)

### Fixed
- Docker build was failing at `mvn clean package` - `ReportService.java`'s
  new invoice-report methods (v0.073) used `BigDecimal` without the file
  ever importing `java.math.BigDecimal` (it only had `java.util.*`, which
  doesn't cover it). No other file touched this week had the same gap -
  checked explicitly across all of it.

## [0.23.8] - 2026-09-22 (v0.073)

### Fixed
- The print agent's CORS preflight response was never updated when raw ZPL
  printing was added in v0.072 - it only allowed the `Content-Type` and
  `X-Printer-Name` headers, so the browser silently blocked every raw print
  request over the new `X-Print-Format` header before it ever reached the
  agent. This showed up as "Print agent not reachable" everywhere a DPD
  label tried to print (despatch and the order screen's Print Label button)
  even with the agent running and working fine for picking notes
- The Shopify fulfillment push's "No matching Shopify fulfillment line
  items found (already fulfilled there, or SKUs don't match)" message
  didn't distinguish two very different situations. Now, when the SKU is
  genuinely on the Shopify order but nothing is left there to fulfil - the
  common case after an order's quantity is increased here beyond what
  Shopify's own order (and an earlier despatch) already used up - the
  message says so directly and explains the underlying Shopify order needs
  editing to match. A true SKU mismatch still gets its own distinct message

### Added
- Dashboard: an "Invoiced Values by Month" chart for the current year -
  invoiced (green) vs credited (red) net value per month, drawn with no new
  charting dependency (same approach as the existing status pie chart)
- Reports > Invoice Reports: an exportable Invoiced Values report (Excel),
  filterable by invoice date from/to, tick boxes for invoices and/or
  credits, and a searchable company dropdown
- A generic searchable dropdown component (`SearchableSelect`), used for
  the company filter above and reusable anywhere else a long list needs
  searching instead of scrolling

## [0.23.7] - 2026-09-22 (v0.072)

### Changed
- DPD shipping labels now print as raw ZPL (Zebra's own label command
  language) sent straight to the configured label printer via the print
  agent, instead of opening as HTML in a browser tab. This is the actual
  fix for labels not fitting the label stock properly - HTML/PDF printing
  has no idea what size label is physically loaded and scales to a normal
  page, where raw ZPL always comes out at the label's real size. Applies
  everywhere a DPD label is printed: Split Packing, Serial Packing, and the
  order screen's Print Label button
- Added a "Label printer DPI" setting under Settings > DPD (203/300 -
  matches the label printer's actual resolution)

### Added
- The print agent (`print-agent/agent.py`) now handles raw print jobs as
  well as PDFs - needs the `pywin32` package installed alongside it
  (`pip install pywin32`) to talk to the printer directly. See
  `print-agent/README.md` for the updated setup steps

## [0.23.6] - 2026-09-22 (v0.071)

### Fixed
- A DPD label opening in a new tab left the operator to remember to hit
  Ctrl+P themselves - now the browser's print dialog opens automatically as
  soon as the label tab has loaded, for all three places a DPD label can be
  printed (Split Packing, Serial Packing, and the order screen's "View/Print
  Label" button). If a pop-up blocker stops the tab from opening at all,
  that's now reported clearly ("pop-up was blocked") instead of silently
  doing nothing. DPD only ever returns labels as HTML, not a PDF, so this
  still goes through the browser's own print dialog rather than the fully
  silent print-agent path used for picking notes and the old placeholder
  labels

## [0.23.5] - 2026-09-22 (v0.070)

### Added
- The old free-text "Courier Method" box on the order screen (at Release for
  Despatch) is now two dropdowns: Courier (DPD today, built so another
  courier can be added later) and Service, which is populated live from
  DPD's own lookup for this exact order's delivery postcode and weight - the
  same live lookup despatch itself now uses, so what's shown here is exactly
  what's actually available, not a guessed or stale code. The chosen service
  is used first when the shipment is actually booked at despatch (re-checked
  against DPD's live list at that point, in case availability changed)
- If the live lookup can't be reached (e.g. the order has no delivery
  postcode yet, or DPD is unreachable), the Service field falls back to a
  plain text box so an order can still be released

### Fixed
- DPD was rejecting every shipment with "Shipment Date is mandatory" -
  `shipmentDate` is a required field DPD expects on every shipment (the
  date/approximate collection time) that wasn't being sent at all. Now sent
  automatically as the current date/time at the point of booking

## [0.23.4] - 2026-09-22 (v0.069)

### Fixed
- DPD shipments were being rejected with "Failed to query network". DPD
  requires a `networkCode` (their delivery service code) on every shipment,
  but it was only ever sent if a value happened to be typed into Settings -
  so if that field was left blank, the required field was silently missing
  altogether. On top of that, DPD's own documentation says network codes
  "may change at any time and should not be hardcoded", so a fixed code
  typed into Settings was never going to be reliable long-term anyway.
  Shipments now look up the real, currently-available delivery service for
  each shipment's actual collection and delivery postcodes and weight,
  every time, via DPD's own "validate outbound services" endpoint. The
  Settings > DPD network/service code field is now just an optional
  preference - if it matches one of the services DPD actually offers for
  that shipment it's used, otherwise DPD's first available service is used
  automatically and the shipment isn't blocked

## [0.23.3] - 2026-09-21 (v0.068)

### Added
- A "Print a placeholder sample label at despatch when no DPD shipment could
  be booked" toggle under Settings > DPD (on by default, matching the old
  behaviour). Turn it off once DPD is fully working so a despatch never
  silently prints an old test label - it'll simply report no label was
  available instead

### Fixed
- A DPD auth failure only ever showed a bare "DPD returned HTTP 401" with no
  way to tell whether the key, the secret, or the sandbox/live environment
  choice was wrong. Now surfaces DPD's own error message (e.g. "Failed to
  validate client-id")
- Errors from the labels endpoint (used by Confirm Despatch, which fetches
  the label as a file download) were showing a generic "Request failed with
  status code 400" instead of the real reason - axios returns error bodies
  as unparsed Blob/text for file-download requests, so the friendly server
  message was being silently dropped. Fixed at the API client level so this
  can't recur for any future file-download endpoint either
- Confirming despatch when no label could be produced (DPD not booked and
  sample labels turned off) no longer shows a top-level error on the
  despatch confirmation - the despatch itself succeeded, so this is now
  reported alongside the DPD status instead

## [0.23.2] - 2026-09-21 (v0.067)

### Fixed
- Confirming despatch could fail with "Transaction silently rolled back
  because it has been marked as rollback-only" - the DPD auto-booking added
  in v0.066 was itself `@Transactional`, so when it threw (e.g. missing
  address data on a test order), Spring marked the *shared* despatch
  transaction rollback-only the instant the exception crossed that method
  boundary - before DespatchService's own try/catch ever got a chance to
  handle it as the best-effort failure it was meant to be. Removed the
  annotation - DPD booking failures no longer touch the despatch transaction
  at all, exactly as intended

### Changed
- Shopify order sync now pulls the delivery address line 1/2 and phone
  number from the order's shipping address (the GraphQL query wasn't
  requesting them before) - needed so a DPD shipment can be booked straight
  off a Shopify-synced order without someone manually typing the street
  address in first. Applies to orders synced from now on, not a backfill of
  existing ones

## [0.23.1] - 2026-09-21 (v0.066, later same day)

### Fixed
- "Book DPD Shipment" on the order screen looked like it did nothing when it
  failed - the error was set correctly but only shown in the page's main
  error banner, far below a long line-items table. Now shown immediately
  next to the button itself, plus a toast on success

### Changed
- DPD shipments are now booked automatically at the point of despatch
  confirmation (both Split and Serial packing "Confirm Despatch & Print
  Labels") - exactly where the old placeholder test labels used to print.
  The real DPD label (HTML) opens in a new tab to print in place of the
  dummy PDF once a shipment is booked; the DPD consignment number is also
  used as the tracking number pushed to Shopify instead of a manually-typed
  carton tracking number. Booking is best-effort and never blocks the
  despatch itself - failure (or DPD not being configured) is shown on the
  despatch confirmation screen, and the "Book DPD Shipment" button on the
  order screen remains as a manual fallback/retry

## [0.23.0] - 2026-09-21 (v0.066)

### Added
- DPD shipping integration - book a real DPD shipment straight from an order
  ("Book DPD Shipment" button) and view/print the label, using DPD's actual
  REST API (auth, shipment creation, label retrieval) rather than the
  placeholder credential fields that were there before. Shipments to the
  Republic of Ireland automatically include the full commercial-invoice
  customs declaration DPD requires, built from the new per-product commodity
  code/country of origin fields
- Commodity Code and Country of Origin fields on products (Product Detail
  page) - needed for the Ireland customs declaration; Country of Origin
  defaults to GB
- Delivery Address Line 1/2 and Delivery Phone fields on orders - DPD
  requires a street address per shipment, which wasn't captured before
- DPD settings reworked: real API key/secret (Basic-auth), a sandbox/live
  environment toggle, a default network/service code, and a full sender
  address block (used as both the collection address and the customs
  exporter details) including an EORI number field, replacing the old
  placeholder username/password/account-number fields
- A small version number next to "System" in the top-left branding
  (currently v0.066) - an easy visual check that a deployed update has
  actually gone through; bump `frontend/src/version.ts` with each release

## [0.22.1] - 2026-09-07 (night)

### Fixed
- The optimistic locking added in the last version didn't actually trigger -
  manually setting an entity's @Version field once it's already loaded
  within the current request is explicitly unsupported by JPA; in practice
  Hibernate just ignores it rather than using it for the update's WHERE
  clause. Replaced with an explicit, deterministic comparison instead
  (compare the version this edit started from against the order's actual
  current version, reject if they don't match) that doesn't depend on any
  Hibernate internals - confirmed this is the actual fix, not another guess

### Added
- A small, auto-dismissing "Saved." notification (bottom-right, nothing to
  click) after a successful save - wired into every genuine Save/Create
  button across the app: order edit, all three Settings save actions
  (main settings, colours, my email), products, product edit, companies
  (both inline edit and new company), purchase orders, goods-in, and bug
  reports. Deliberately left out approve/reject/receive/release/despatch
  and similar action buttons - a different category from "save" the
  request was about, most of which already have their own clear feedback

## [0.22.0] - 2026-09-07 (later)

### Added
- Optimistic locking on order edits, for production with multiple concurrent
  users - deliberately not the old system's "lock the order until the
  current user exits" approach, which has a real failure mode: a crashed
  session or a closed laptop lid leaves the order stuck locked for everyone
  until someone with admin rights forces it open. Instead, every order
  carries a version number; saving states which version was actually
  loaded, and a save is rejected with a clear "this was changed by someone
  else - reload and try again" (plus a one-click reload button) if someone
  else has saved in between, rather than either blocking upfront or
  silently overwriting their change
- Sales Activity now quietly refreshes in the background every 15 seconds,
  same interval and reasoning as the handheld's picking list already used -
  a status change made by someone else (despatched, released, picked) shows
  up without a manual refresh. Deliberately not added to the order edit
  form itself - auto-refreshing fields while someone's mid-edit risks
  overwriting their unsaved typing, a genuinely different problem the
  optimistic lock above already handles correctly at save time instead

### Fixed
- The global error handler was auto-logging every non-401 error to Bug
  Reports, which would have meant a normal, expected edit conflict (the
  new 409 above) got logged as if it were a bug every single time it
  happened. Excluded 409 from that logging, same reasoning as the existing
  401 exclusion - expected, handled behaviour, not a bug

## [0.21.0] - 2026-09-07

### Fixed
- Who-did-this attribution across picking, goods-in, despatch, and manual
  stock movement was genuinely unreliable - found via Stock Trace showing
  a pick as "Dan" when Neil Brown was actually logged in and picking, and
  despatch showing no name at all. Root causes, all different flavours of
  the same underlying problem:
  - Picking's "picker name" was a plain text field cached in the browser's
    own localStorage on the handheld device - it could silently go stale
    the moment a different person logged into the same physical device
    without manually retyping their name, which is exactly what happened
  - Goods-in was hardcoded to the literal string "warehouse" (desktop) or
    "handheld" (handheld), never the actual user, on both starting and
    saving a session
  - Despatch never recorded who performed it at all
  - Manual stock moves already worked correctly by reading the logged-in
    user, which is what confirmed the fix direction
  Every one of these now derives the name from the real authenticated
  session server-side, not from anything the frontend sends - the
  handheld's "who's picking?" name prompt is removed entirely, since
  there's nothing left for it to do. This can't silently drift out of
  sync with who's actually logged in again, on any of these flows

## [0.20.2] - 2026-09-06 (evening)

### Fixed
- Saving an order after "Reverse to Despatch" failed with a foreign key
  error - order editing worked by blindly deleting and recreating every
  line on every save, which is fine for a never-picked order but breaks
  outright the moment a StockItem references a specific line's id. Order
  editing now reconciles against what's actually changed instead: an
  unchanged or increased quantity leaves picked stock completely alone (no
  re-picking needed), a decreased quantity returns the excess specific
  units to stock first, and a removed line does the same for everything on
  it before the line itself goes. A brand-new order (nothing picked yet)
  still uses the simple create-fresh path, since there's nothing to
  reconcile against

### Added
- A quantity increase after reversal now correctly makes the order
  reappear on the handheld to pick just the extra amount needed, and drop
  off the packing-ready list until that's done - reusing the existing
  picking/packing pipeline exactly as it already worked, not a new
  concept. No separate "resume picking" flow needed

## [0.20.1] - 2026-09-06 (later)

### Fixed
- The build itself failed to compile - a redundant `status !== "ON_HOLD"`
  check on the new Cancel & Return to Stock button, which TypeScript
  correctly flagged as impossible: it's already nested inside the "order
  is not On Hold" branch of an earlier check, so the type checker had
  already narrowed status to exclude On Hold entirely by that point.
  Removed the redundant check - the button was always going to render
  correctly regardless, this was purely a compile-time issue

## [0.20.0] - 2026-09-06

### Fixed
- "Reset for Testing" on an order failed with a foreign key error deleting
  cartons - carton_lines reference cartons and needed deleting first. Fixed
  as part of a bigger rework below, which this now delegates to
- A handful of literal "&amp;" strings showing up on screen instead of a
  real "&" (Settings > Despatch & Packing, Settings' reset section, RMA
  detail's return/receipt headings) - JSX text doesn't HTML-decode, so
  writing the HTML entity just displays it literally

### Added
- Real order reversal (Order page, once released) - matching how the
  previous system worked, for genuine post-despatch changes (a customer
  calling to change quantity, cancel, or change address), not just testing:
  - **Reverse to Despatch**: undoes despatch only. Picking and packing are
    left completely untouched - the same specific units (MACs etc.) stay
    allocated and packed into their cartons, so a correction doesn't mean
    re-picking. Re-confirm despatch once the change is made
  - **Cancel & Return to Stock**: undoes everything back to On Hold. Every
    allocated/despatched item genuinely returns to the bin it actually came
    from (reconstructed from its own movement history, not a guess or a
    default), cartons are removed, and the order ends up exactly as if it
    had just synced in fresh
  - "Reset for Testing" now delegates to the same real reversal, fixing the
    original bug and removing a second, slightly different implementation
    of the same thing

## [0.19.5] - 2026-09-05 (night)

### Changed
- Revised the batch-scan fix from the previous entry - blocking batch codes
  outright for tracked products was too broad and lost a genuinely useful
  feature: picking a whole unopened carton in one scan instead of scanning
  32 units individually. The actual rule now: a batch/carton scan is only
  accepted when it would consume *everything* remaining in it - never a
  partial take. A carton with 32 of a product left won't satisfy a line
  that only needs 1 (the original bug), but a carton with exactly the
  amount still needed left in it - however many - is accepted in one scan,
  tracked products included, since each unit taken still carries its own
  real MAC/serial from goods-in regardless of how it was scanned

## [0.19.4] - 2026-09-05 (evening)

### Fixed
- Picking would silently accept a batch code scan for a MAC/serial-tracked
  product, grabbing whichever matching unit happened to come first rather
  than requiring the unit's own identifier - defeating the entire point of
  individual tracking (the wrong unit's MAC/password could end up on the
  despatch email for that customer). Batch code scanning is now only
  accepted for genuinely untracked (quantity-only) products; scanning a
  batch code against a tracked product now gives a clear rejection message
  on the handheld instead of silently succeeding

## [0.19.3] - 2026-09-05 (later)

### Fixed
- Confirmed (via a live diagnostic with a matching-colour test) the sidebar
  white background was a real rendering issue, not a build/deploy problem -
  removed the background entirely for the open state. The active-group
  heading colour and the active-row dot now carry that signal on their own

### Added
- "Reset for Testing" on an order (Order page, once released - only visible
  when test data reset is enabled on this environment) - puts an order
  back to exactly where it was before release: any allocated/despatched
  stock returns to available, cartons are removed, picked/despatched
  quantities reset to zero, and picking status goes back to Not Started.
  Almost certainly also the real fix for a released order silently not
  showing up on the handheld to pick - repeatedly testing against the same
  order without a proper way to reset picking status back to Not Started
  would leave it stuck at Complete/Partial from a previous test, which
  quietly excludes it from the "ready to pick" list even though the order's
  own status looks perfectly fine. Built specifically to remove the need
  for a fresh Shopify test order every time

## [0.19.2] - 2026-09-05 (later)

### Fixed
- The open-group background was still reading as white even after
  switching to blue-50 - it's just too pale a tint to register against the
  dark sidebar. Reverted to a dark, blue-tinted overlay instead (blue-950
  at low opacity) that can't read as white regardless of screen/monitor,
  keeping the active-group green heading text and the active-row dot
  exactly as they were, per explicit feedback that those two were enough
  on their own
- Picking notes were falling back to opening in a browser tab instead of
  printing silently. Root cause: the print-agent's actual print call can
  legitimately take a few seconds (launching the PDF viewer, the OS
  spooler picking up the job) plus a deliberate 2-second pause afterwards,
  but the frontend was only waiting 1.5 seconds before deciding the agent
  was unreachable and falling back - a real, working print was liable to
  lose that race even with nothing actually wrong. Bumped the timeout to
  8 seconds (an agent that's genuinely not running still fails in
  milliseconds, so this doesn't slow that case down at all). Also found
  and fixed a related structural issue while in there: the print-agent
  was single-threaded and fully blocking per request, meaning two prints
  triggered close together (increasingly likely now that acknowledgement,
  picking note, and label can all auto-fire from one action) would queue
  behind each other rather than being handled concurrently - switched to
  Python's ThreadingHTTPServer, and moved the temp filename from a
  millisecond timestamp to a genuine UUID now that concurrent requests are
  possible

## [0.19.1] - 2026-09-05

### Changed
- Sidebar's open-group background switched from a near-white light grey to
  a light blue - the white read as too stark against the dark sidebar

### Added
- Picking notes can now auto-print on release for despatch, same as
  acknowledgement emails already did - one click covers acknowledgement,
  picking note printing, and (once packed) label printing, with a matching
  toggle in Settings > Despatch & Packing to turn it off if ever needed

## [0.19.0] - 2026-09-04 (night)

### Added
- Per-user email (Settings > My Email) - acknowledgement and despatch
  confirmation emails now go out through the sending user's own configured
  account rather than always the one shared mailbox, plus an optional CC
  address per user (e.g. cc'ing a shared "orders@" inbox on everything sent).
  Reuses the same per-user settings store already built for status colour
  customisation, so no new tables needed. The shared Settings > Email
  account remains as the fallback for anyone who hasn't set up their own
- Sidebar navigation reworked: Reports is now a normal collapsible group
  like every other one (previously it was a separately-implemented special
  case with its own "Order & Stock Reports" sub-heading) - both report pages
  now sit directly under "Reports". An open group gets a genuinely standout
  light background rather than blending into the dark sidebar, the active
  group's heading turns a distinct colour so it's obvious at a glance which
  section you're in, and the current page gets a small dot marker - its
  space is always reserved so the label never shifts depending on which row
  is active. Dashboard pulled out of the old single-item "Overview" group
  entirely and is now its own permanent, non-collapsible link at the top

## [0.18.0] - 2026-09-04 (evening)

### Added
- Shipping label printer, separate from the picking note printer (Settings >
  Printing) - most warehouses have a label printer right at the despatch
  bench, separate from wherever picking notes come out. The print-agent
  itself needed no changes at all - it was already fully generic (any named
  printer via a header), the gap was purely that labels never used it in
  the first place, just opening in a browser tab for manual printing. Now
  goes through the same silent print-agent mechanism picking notes already
  used, with the same graceful fallback if the agent isn't running
- Email (SMTP) is now configurable from Settings, not just .env - and
  changes take effect on the very next email sent, no restart needed. The
  previous setup built a single JavaMailSender bean once at application
  startup from static .env-only properties; replaced with a new
  EmailService that builds the sender fresh from current Settings on every
  send, falling back to whatever's in .env as defaults so nothing breaks
  for anyone already relying on it
- DPD credential fields in Settings (username, password, account number) -
  storage only for now, not yet wired to any actual DPD functionality. DPD's
  API documentation isn't public even with a real account - it's only
  handed to approved partners - so the real label/tracking/commercial-invoice
  integration is still pending that reference material

## [0.17.0] - 2026-09-04

### Fixed
- Found a more serious version of a risk that was flagged as a question:
  Shopify decrements its own "available" inventory the moment an order is
  *placed*, not when it's fulfilled - by the time an order gets despatched
  here, Shopify had already reduced its own count independently. The real
  risk was the periodic stock push potentially pushing our raw on-shelf
  count back to Shopify while an order sat received-but-unpicked in our
  system - accidentally handing back stock Shopify had correctly committed
  to that order, risking the same unit being sold to a second customer.
  Fixed by subtracting stock still owed against open, unpicked orders
  (Shopify-sourced or otherwise) before every push
- Default passwords were stored per product, not per unit - meaning the
  despatch confirmation email, and Stock Overview, were showing the exact
  same password for every unit of a product regardless of which one
  actually shipped. Moved to StockItem (and the intermediate
  ExpectedStockItem it's received against) where a genuine per-unit value
  belongs; the goods-in shipment spreadsheet now accepts an optional
  PASSWORD column to capture it going forward. Removed the now-meaningless
  field from the product edit form entirely, per an explicit decision -
  passwords are unit-specific, not a product attribute

### Added
- Sidebar navigation groups are now collapsible, collapsed by default - the
  list of features was only going to keep growing. A group auto-opens if
  you're currently on one of its pages, matching how the Reports section
  already behaved, so you're never landed on a page with no visible
  indication of where you are in the nav

## [0.16.2] - 2026-09-04 (even later)

### Fixed
- The bin picker's search/scan field overflowed slightly off the right edge
  of the screen - a classic Flexbox gotcha: a flex child's default min-width
  is auto, not 0, so it won't shrink below its content's natural width
  unless told to. ScanInput's inner `<input>` had that fix already, but its
  own outer wrapper never did. Fixed inside ScanInput itself, so every place
  it's used benefits, not just the bin picker
- Typing the first character of a bin code (e.g. "3" while aiming for "3B")
  was selecting immediately if that single character happened to exactly
  match a different, separate bin's code - selecting the wrong bin before
  you'd finished typing. Matching now only happens on Enter, not on every
  keystroke - the list still filters live as you type either way. This is
  also the technically correct behaviour for an actual barcode scan, not
  just typed search: a scanner sends Enter right after the scanned value,
  so nothing extra was needed to support that case once this was fixed.
  Bin code filtering also switched from substring to starts-with (typing
  "3" no longer pulls in "13", "23" etc. - descriptions still match by
  substring, matching how search works elsewhere in the system)

## [0.16.1] - 2026-09-04 (later)

### Fixed
- The handheld bin picker's floating dropdown panel genuinely didn't have
  room to render properly on a 720px-wide handheld screen - especially once
  split in half alongside the scan box, it was getting visibly cut off.
  Replaced with a full-screen picker instead: tapping "Select a bin" opens
  a dedicated screen with one combined search/scan field at the top and a
  naturally-sorted, tappable list below - no dropdown, no cramped space to
  run out of. The search field doubles as the scan target: a barcode scan
  "types" the full code almost instantly, so an exact match auto-selects
  immediately rather than also needing a tap on the one matching result.
  Removes the half-search/half-scan split box from the previous version -
  this single unified field does both jobs better

## [0.16.0] - 2026-09-04

### Added
- Split bin picker on the handheld side (HandheldBinPicker) - half the
  existing searchable list, half a scan-a-bin-code field. Works today by
  typing/scanning the bin's plain code even without printed labels yet;
  once bins have real barcodes, scanning one does exactly the same thing
  with no further changes needed, since it's just matching against the
  bin's existing code either way
- Android kiosk app now has a custom status bar (clock, battery %, Wi-Fi
  signal) - a pinned app hides Android's own status bar entirely, which
  otherwise means losing that information for the whole shift. Built with
  the actual target device in mind (Grandstream WP856 - confirmed 720x1440
  screen, Android 13, built-in hardware barcode scanner that behaves as a
  keyboard, matching how ScanInput was already designed)

## [0.15.1] - 2026-09-04

### Fixed
- The recurring "pull before you can push" issue - my sandbox has no
  internet access, so it could never fetch what was actually on GitHub
  after one direct web upload created a permanent fork between the two
  histories. Fixed properly this time: adopted your actual current project
  folder (uploaded directly) as the new source of truth, confirmed it's
  genuinely in sync with GitHub first (`up to date with 'origin/main'`)
  before switching to it. Should go back to a plain `git push` working
  cleanly from here
- While adopting it, found and cleaned up several stale files that had been
  silently surviving zip extraction after extraction for a long time -
  `frontend/src/pick/` and `pick-manifest.json` (superseded by the
  `handheld/` reorganisation), `Reports.tsx` (superseded by the later split
  into `ReportsOrders.tsx`/`ReportsStock.tsx`), and `AssignCartonRequest.java`
  (superseded by `AssignCartonItemRequest`/`AssignCartonLineRequest`).
  Cross-checked each one against current code before removing anything -
  none were referenced anywhere

### Added
- A real Gradle wrapper for the Android project (pinned to Gradle 8.5,
  generated by Android Studio itself) - was found sitting untracked in the
  uploaded folder. Committing it properly removes the earlier "no wrapper,
  relies on whatever Gradle your Android Studio has bundled" workaround;
  8.5 is comfortably compatible with the existing AGP 8.2.2 pin

## [0.15.0] - 2026-09-03 (later)

### Added
- A proper searchable bin picker (BinSelect / HandheldBinSelect), replacing
  every plain `<select>` across the app that listed locations - Goods In,
  Product/Products default bin, Stock Movement (desktop and handheld), and
  handheld Goods In. A native dropdown with 200+ bins, alphabetically
  sorted so "10" sat before "2", was genuinely painful to use. Now sorts
  naturally (1, 1A, 1B, 2, 2A ... 10, 10A) and has a search box

### Investigated, no change needed
- Checked whether picking/moving genuinely fails when the wrong-but-valid
  identifier gets scanned - it doesn't: every scan lookup (Picking, both
  Stock Movement pages) already tries MAC and serial number fields
  regardless of the product's declared tracking type. Confirmed the actual
  MAC vs Serial distinction should stay as-is rather than being collapsed
  into one concept

## [0.14.1] - 2026-09-03 (later still)

### Fixed
- The stock import genuinely completed successfully on its first real run
  (confirmed live - a bin from the file showed real imported stock), but the
  browser gave up with a 504 well before the response came back. Root cause:
  the commit step was doing one individual database write per physical unit
  for both the stock item and its movement record - roughly 80,000+ separate
  round trips for a 42,000-row file, comfortably exceeding nginx's default
  60-second timeout even though the backend itself has no timeout and kept
  working regardless. Rewrote to batch stock item writes and record one
  movement per product rather than per unit (a one-time bulk seed doesn't
  need per-unit movement granularity - the stock item's own batch code
  already carries that trace value), with periodic flush/clear so the
  persistence context doesn't grow across the whole operation. Also bumped
  nginx's timeout to 300s as a safety margin regardless of these
  improvements, and added Hibernate batch-insert configuration

## [0.14.0] - 2026-09-03 (night)

### Added
- Bulk Stock Import (Settings > Bulk Stock Import) - one-off replacement of
  current on-hand stock from an OrderWise export, with a real preview step
  first: bins to be created, products matched vs skipped, tracking type
  changes, edge cases worth a look, and exactly how many items will be
  removed and created - nothing touches the database until that's reviewed
  and "REPLACE" is typed to confirm. Deliberately narrow in scope, per
  explicit decisions made before building it: only ever replaces
  AVAILABLE/QUARANTINED stock (despatch history and anything allocated to
  an open order are untouched, system-wide, regardless of whether their
  product appears in the file), a SKU that doesn't match an existing
  product is skipped and reported rather than auto-creating a bare product,
  and the file's tracking type is authoritative and overrides whatever's
  currently set

### Fixed
- Found while building the above: the stock_movements foreign key had no
  ON DELETE clause, so Postgres would refuse to delete any stock item that
  still had movement history against it - which is almost every real stock
  item. Fixed to ON DELETE SET NULL, matching that the column was already
  nullable by design - the movement record survives, just detached from the
  now-gone item

## [0.13.2] - 2026-09-03 (even later still)

### Fixed
- Stock push failed again on the next attempt: "InventoryQuantityInput must
  include the following argument: changeFromQuantity." This time fetched
  Shopify's actual live schema reference directly rather than trusting
  another search snippet, after getting this mutation's shape wrong twice
  in a row. Turns out this is a genuinely confusing, actively-changing part
  of Shopify's own API right now (other developers have hit and reported
  this exact same transition) - compareQuantity/ignoreCompareQuantity are
  being replaced by a per-item changeFromQuantity field, and the field key
  has to be present even when opting out, which is done by passing an
  explicit null rather than omitting it. Also had to switch from Map.of()
  (which can't hold a null value at all) to a plain mutable map for that
  one item structure

## [0.13.1] - 2026-09-03 (even later)

### Fixed
- Stock push to Shopify was failing outright on every batch:
  "ignoreCompareQuantity Field is not defined on InventorySetQuantitiesInput".
  That field was inaccurate information from an earlier search result, not
  something the real schema actually has - Shopify's own validation error
  is a far more reliable source than that was, so trusting it directly this
  time rather than searching again. Removed the field entirely; without a
  compareQuantity supplied at all, there's nothing to compare against, so
  the mutation just sets the value directly - exactly the intended
  "warehouse is authoritative" behaviour, no flag needed

## [0.13.0] - 2026-09-03 (later still)

### Fixed
- Despatched (and allocated) stock items could still be found, "moved" to a
  new bin, and shown throughout Stock Movement and Stock Overview - nothing
  filtered on status at all. A despatched item has physically left the
  building; scanning it in Stock Movement now correctly says so instead of
  letting it be relocated, and Stock Overview (both by-bin and by-product)
  no longer lists anything that isn't genuinely on a shelf right now
  (AVAILABLE or QUARANTINED). RMA lookup is unaffected - it's a completely
  separate service that deliberately needs to find despatched items (that's
  the whole point of an RMA), confirmed while making this change
- Found and fixed a second, related latent bug while investigating the
  above: RmaService had its own local copy of the naive
  count-based order number generator - the exact same collision bug fixed
  in OrderService a while back, just never applied here since it's a
  separate copy in a different file. Now delegates to the one fixed version
  instead of maintaining two
- Shopify stock push was reporting "No Shopify locations found" - the real
  cause was a missing read_locations scope (separate from read_inventory,
  same pattern as the fulfillment orders read/write scope split earlier).
  Also fixed the error handling itself: a GraphQL-level permission error was
  being silently treated the same as "zero locations exist", which hid the
  real problem behind a misleading message

## [0.12.0] - 2026-09-03 (later)

### Added
- Stock Movement on the handheld app - same scan/lookup/move mechanics as
  the desktop version (scan MAC/serial/batch, pick a destination bin,
  confirm), just the handheld dark full-screen treatment. New third tile on
  the handheld home screen alongside Picking and Goods In
- README rewritten from scratch - it was written very early in the project
  and had drifted a long way from reality (still listed RMAs, user accounts,
  and the entire Shopify integration as "not yet built" despite all being
  fully implemented). Now an accurate, complete "what's implemented" /
  "what's not yet built" list, including the production-hardening items
  that were deliberately left loose while this was LAN-only

### Fixed
- Desktop Stock Movement's `movedBy` was a hardcoded "warehouse" placeholder
  - now uses the actual logged-in user's name, same as the new handheld
  version, now that real accounts exist to attribute it to

## [0.11.0] - 2026-09-03

### Added
- Stock levels now push from the warehouse system to Shopify - one-way,
  same principle as fulfillment status. Runs on a timer, pushing each linked
  product's "available" count (the exact same figure the public stock
  lookup API already uses, so there's only ever one definition of
  "available" across the whole system, not two that could quietly drift)
- Weight now also pushes one-way, warehouse -> Shopify, but event-driven
  rather than timed - fires the moment a weight is actually changed and
  saved, since it's a rare, deliberate edit rather than something that
  needs periodic batching. Regular product sync stops pulling weight in
  from Shopify for anything we already have a value for - only ever used
  as a starting point for a genuinely new product now, never allowed to
  silently overwrite an existing value (the warehouse system is
  authoritative, by explicit decision - a Shopify-side weight edit gets
  overwritten on the next push rather than the two systems fighting over
  which one's "more recent")
- New scope: write_inventory (covers both weight and stock - both target
  Shopify's InventoryItem object family, not Product/ProductVariant, so
  no write_products scope needed despite weight conceptually feeling like
  "a product field")

### Fixed
- Found while researching this: the Shopify API version pinned throughout
  this whole integration (2025-01) had already been sunset for months.
  Shopify doesn't error on a sunset version - it silently "falls forward"
  to whatever's oldest-currently-supported, so every Shopify call in this
  system has likely been running against a schema slightly different to
  the one actually being coded against, with zero warning. Bumped to
  2026-04 and flagging this as something worth revisiting every couple of
  quarters going forward, not just fixing once

## [0.10.0] - 2026-09-02 (night)

### Added
- "Download the app for this device" link on the handheld login page - once
  you've built the Android APK in Android Studio, drop it in
  `android-app/releases/app-debug.apk` and it's immediately downloadable
  from the handheld login screen, no rebuild or restart needed. Useful for
  setting up a new or replacement scanner: open the browser once, download,
  install, never need the browser again after that

## [0.9.2] - 2026-09-02 (evening, later still)

### Fixed
- Android build failed with "android.useAndroidX property is not enabled" -
  a completely standard `gradle.properties` file (turning AndroidX support
  on, required as soon as any androidx.* dependency is used, which core-ktx
  and appcompat both are) was simply missing from the project entirely. Added

## [0.9.1] - 2026-09-02 (evening, later)

### Fixed
- Android build failed immediately on sync: "Minimum supported Gradle
  version is 8.7. Current version is 8.5" - AGP 8.5.2 (what was originally
  set) genuinely needs Gradle 8.7+, but the project never shipped its own
  Gradle wrapper (deliberately - the wrapper needs a binary jar file that's
  awkward to hand-author correctly without being able to test it), so
  Android Studio fell back to whatever Gradle it already had bundled (8.5).
  Fixed by pinning AGP to 8.2.2 instead, confirmed against Google's own
  compatibility table to need only Gradle 8.2 minimum - builds against
  whatever Gradle Studio already has, no wrapper or download needed at all

## [0.9.0] - 2026-09-02 (evening)

### Added
- A complete Android Studio project (`android-app/`) for a dedicated
  warehouse-scanner kiosk build - a thin full-screen WebView wrapper around
  the existing handheld app, not a rewrite of anything. Set as the device's
  Home app and it auto-pins itself (Android's built-in screen pinning) so a
  swipe-up-to-home genuinely doesn't leave the app, with no special device
  provisioning required. This is source code, not a compiled `.apk` -
  building one needs Gradle to download the Android SDK, which needs
  internet access this environment doesn't have; see `android-app/README.md`
  for the (genuinely few) steps to build it in Android Studio
- The on-screen keyboard no longer pops up on the two barcode-scan fields
  (picking's MAC/serial/batch scan, goods-in's carton scan) - both were
  losing half the screen to a keyboard on every focus, for a field that's
  filled by a scanner ~99% of the time. New shared `ScanInput` component
  defaults to `inputMode="none"` with a small keyboard icon to bring up the
  keyboard manually for the rare case that needs typing

## [0.8.3] - 2026-09-02 (later still yet again)

### Fixed
- The 0.8.2 backend fix was actually correct - confirmed live by watching a
  fresh login: this time both the login call and the follow-up "am I logged
  in?" check came back 200, meaning the session genuinely persisted. But the
  page still didn't move off the login screen, which turned out to be a much
  simpler, separate bug: Login.tsx never called navigate() after a
  successful login. Updating the auth state doesn't, on its own, move the
  browser off /login - that route can't be wrapped in the usual auth gate
  (a logged-out visitor could never reach it if it were), so nothing was
  ever telling it to leave. HandheldLogin.tsx already had this right; only
  the desktop login was missing it

## [0.8.2] - 2026-09-02 (later still again)

### Fixed
- The actual root cause of login not sticking, found by watching it happen
  live in the browser: our custom login filter (JsonLoginFilter, extending
  Spring's UsernamePasswordAuthenticationFilter) holds its own
  securityContextRepository, entirely independent of whatever the rest of
  the security chain is configured with. It was silently defaulting to
  request-attribute-only storage - valid for the one request login happened
  in, never actually saved to the session. So authentication genuinely
  succeeded (visible in the backend log), then evaporated the instant that
  request ended, with nothing persisted for the very next request to find.
  The previous 0.8.1 fix (missing withCredentials on axios) was real and
  necessary but not sufficient on its own - this was the second half of the
  same underlying problem

## [0.8.1] - 2026-09-02 (later still)

### Fixed
- A real bug in the login system just shipped: the shared axios client never
  set `withCredentials: true`, so it silently never sent the session cookie
  on any request - unlike the browser's native `fetch()`, which does this by
  default. The practical effect: login would genuinely succeed on the
  backend (visible in the logs), but the very next thing the frontend did -
  checking "am I logged in?" - went through axios, got no cookie, came back
  looking unauthenticated, and silently undid the login a fraction of a
  second after it worked. Affected every screen in the app, not just login,
  since they all share that client. Found and fixed two more of the same gap
  while sweeping for it: the Bug Reports page's separate axios instance, and
  the auto-bug-report logger's own raw axios call

## [0.8.0] - 2026-09-02 (later)

### Added
- Real user accounts, at last - session-based login, a proper login page with
  a "remembered accounts" quick-switcher for shared warehouse-floor devices
  (names only ever stored locally, never passwords), and long-lived sessions
  by default since these are trusted devices used all shift, not public
  kiosks
- A dedicated handheld login page too, matching the handheld app's own dark
  full-screen look rather than reusing the desktop one - a locked-down
  handheld device should never land on a desktop-styled screen. Same
  accounts, same backend, same remembered-accounts list (shared localStorage
  key with the desktop login) - plus a log out control on the handheld home
  screen, which didn't have one
- New logins can be created (and passwords changed) from Settings > Users -
  no admin/staff distinction yet, matching how the rest of the app already
  works (everyone sees everything)
- The very first login ("Dan Cheetham") is seeded automatically on first
  startup with a temporary password, generated by the real password encoder
  at actual runtime rather than baked into a migration - meant to be changed
  immediately from Settings > Users
- Per-user settings now genuinely exist and are wired up for the first time:
  the order status colour customisation from the previous version moved from
  global to following your own login specifically

### Fixed
- Two gaps caught and fixed while wiring in the session-auth gate: the
  existing API-key-authenticated public stock endpoints would otherwise have
  been silently blocked by the new login requirement (their own separate
  auth mechanism is unrelated to logins and needed to keep working
  unaffected), and the automatic bug-report logger would have fired on every
  routine "am I logged in?" check, which isn't a bug

### Not yet built
- Linking a login to its own Outlook/Microsoft 365 account so system emails
  originate from that person - scoped as its own separate piece of work
  (a genuine OAuth integration against Microsoft Graph, comparable in size to
  the whole Shopify integration below), not bolted on alongside login itself

## [0.7.0] - 2026-09-02

### Added
- Sales Activity table remapped for the B2B/Shopify world: "Company" (was
  Customer Name, now shows the linked Shopify company), "PO Number" (was
  Order Ref., now sourced from Shopify's real `poNumber` field), "Ordered By"
  now properly populated from Shopify's own `purchasingEntity`/company contact
  data rather than left blank
- Row background colour now spans the whole row for order status, not just
  the small badge - easier to scan a full screen of orders at a glance
- New "Customisation" section in Settings - a colour picker per order status,
  with a reset-to-defaults option (later moved to per-user in 0.8.0)

### Fixed
- Orders synced before `shopifyOrderId` capture existed (a gap left by
  updating the sync logic mid-testing) are now automatically backfilled at
  the start of every sync, so the Shopify fulfillment push stops silently
  reporting "not a Shopify order" on orders that genuinely are

## [0.6.0] - 2026-09-01

### Added
- Two-way Shopify order sync - pulls orders in from Shopify (always landing
  as On Hold, so credit control and a staff review always happen before
  anything ships), matched to a Company via Shopify's own `purchasingEntity`
  data rather than guessing from a customer email address
- Shopify company sync - pulls Shopify's native B2B Company records in,
  matched by Shopify's own company ID so renames don't create duplicates
- B2B credit control: Companies with an optional credit limit, a Payments
  ledger recorded against specific orders (mirrors the existing OrderWise
  process rather than inventing something new), and a live "credit used"
  figure computed from outstanding order balances
- Release-for-despatch now blocks when a company is over its credit limit,
  with a required-reason override that gets logged - matches "block at
  release" rather than at Shopify checkout, since that's the existing process
- Despatch confirmation now pushes real fulfillment status back to Shopify
  (`notifyCustomer: false` always - Shopify never emails the customer itself)
- BNS's own despatch confirmation email, sent instead of relying on Shopify's -
  lists the MAC address, serial number and default password for every device
  shipped, since Shopify's own email template has no way to carry that

### Fixed
- `generateOrderNumber()` used a naive "row count + 1" scheme that could
  collide with an already-existing order number - this made the scheduled
  Shopify order sync fail silently and repeatedly on the exact same
  collision, forever, every 2 minutes. Now actually checks a number is free
  before using it
- Order sync used to run as one large database transaction covering every
  order in a batch - a single bad order could silently roll back every other
  order already processed in that same run, not just the failing one. Each
  order import now runs in its own transaction
- Reading Shopify's `fulfillmentOrders` needs its own read scope
  (`read_merchant_managed_fulfillment_orders`), entirely separate from the
  write scope used to actually create the fulfillment - was missing, so the
  fulfillment push was failing at the lookup step even with a valid write scope

## [0.5.0] - 2026-08-28

### Added
- Shopify product sync - pulls the live catalogue in, matches existing
  products by SKU, flags anything new as "Needs Review" since Shopify has no
  concept of MAC/serial tracking, default bin, or default password
- A proper "Connect to Shopify" OAuth flow - replaces an earlier mistaken
  attempt to use the app's OAuth Client Secret directly as an API access
  token (a very easy mistake given how Shopify's Dev Dashboard labels it)
- Dedicated product detail page (`/products/:id`) with room for the extra
  settings a product needs, replacing the old inline-expand edit on the list
- "Clear Demo Products" cleanup action in Settings, so real Shopify data can
  start from a clean product catalogue rather than alongside the seed examples
- Sidebar made properly sticky - previously scrolled away with long lists

### Fixed
- Receiving a returned RMA unit was trying to insert a duplicate stock record
  for an already-existing MAC address (despatch never actually deletes the
  original row, just clears its location) - now revives the existing row
  instead of colliding with it

## [0.4.0] - 2026-08-27

### Added
- Full RMA pipeline: public customer-facing return request form with live
  MAC/serial lookup against sales history, a staff review/approval queue,
  receipt processing, and automatic replacement-order or credit-note
  creation depending on whether the item is faulty or not
- Configurable return windows in Settings (28-day non-faulty, 365-day faulty
  RTB warranty) - previously hardcoded to a single 1-year figure regardless
  of fault status
- RMA cover sheet PDF, replacing the manual Excel/email process

### Changed
- Settings reorganised into clearly labelled sections (Printing, Despatch,
  Returns, Danger Zone) instead of one long undifferentiated list
- Dashboard now shows a pie chart of orders by status

## [0.3.0] - 2026-08-27 (earlier)

### Added
- Handheld picking and goods-in app (PWA), designed for a Zebra-style
  barcode scanner - scan-driven, works offline-tolerant on the LAN
- Split and Serial packing modes for despatch (quantity-split cartons vs
  per-unit serial assignment), dummy sample shipping labels
- Picking note redesigned: two-column header (order details / delivery
  details), special instructions pinned to a fixed position on every page,
  proper page footer with timestamp and page numbers

## [0.1.0] - [0.2.0] - 2026-08-26

### Added
- Core warehouse system built from scratch to replace OrderWise: products,
  suppliers, purchase orders, goods-in (carton scan-to-book), stock
  movement, sales orders, and despatch - the foundation everything above
  is built on

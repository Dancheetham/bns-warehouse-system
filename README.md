# BNS Warehouse System

A purpose-built warehouse management system for BNS Distribution, built to
replace OrderWise: serialised inventory (MAC/serial) tracking, carton-level
goods-in, full traceability from receipt to despatch, B2B credit control, and
a two-way Shopify integration.

See [`CHANGELOG.md`](CHANGELOG.md) for the detailed build history.

## Stack

- **Backend:** Java 21, Spring Boot 3, Spring Security, Spring Data JPA,
  PostgreSQL, Flyway
- **Frontend:** React + TypeScript, Tailwind CSS, TanStack Query
- **Handheld:** same React app, a separate dark full-screen PWA for
  scan-driven picking/goods-in/stock movement - also wrappable as a locked-down
  Android kiosk app (source in `android-app/`)
- **Deployment:** Docker Compose (Postgres + API + Nginx-served frontend)

## Running it

You need Docker + Docker Compose installed.

1. Copy `.env.example` to `.env` and fill in real values (or leave the
   optional ones - SMTP, Shopify - blank to run without them).
2. From the project root: `docker compose up --build`

First run will take a few minutes (Maven downloads dependencies, npm installs
packages). Once it's up:

- **Frontend:** http://localhost:8081
- **API:** http://localhost:8080/api

Flyway runs automatically on API startup and creates the full schema. The very
first startup also seeds one login - **admin**, password
`ChangeMe123!` - meant to be changed immediately from Settings > Users. **A
login is required for everything except the public RMA form.**

Each login can optionally have an email address (Settings > Users) so it can
use **Forgot password?** on the sign-in page - a one-time reset link, valid
for an hour, sent through the shared Settings > Email account. If this
deployment sits behind a reverse proxy or tunnel, also set **Public URL**
under Settings > Email so those reset links come out as `https://` rather
than a plain `http://` guess.

## Trying out the core workflow

1. Log in as admin (see above).
2. Go to **Purchase Orders** → New Purchase Order, add a line for a product.
3. Open the PO and **import a shipment spreadsheet** - header row with
   columns `SKU`, `MAC`, `SERIAL`, `BATCH` (optionally `WIFI_MAC`), `.xlsx`/
   `.xls`/`.csv`. The row count must exactly match the PO quantity - the whole
   import is rejected otherwise, mirroring the old system's strict rule.
4. Go to **Goods In**, select the PO and a bin, scan the batch codes in.
5. **Save & Complete** - creates the real stock records, updates inventory,
   writes movement history.
6. Go to **Stock Trace** and search one of the MACs you imported to see its
   full timeline.

## Backup & Restore

The database lives in a Docker-managed volume (`bns_postgres_data`), not in this
project folder - copying the folder to another machine does **not** bring the
data with it. Settings → **Backup & Restore** (in the app) downloads a single
`.zip` with the full database *and* the connection secrets that only ever live
in `.env` (Postgres/SMTP/Shopify credentials - never in the database). To stand
up a new machine from one:

```
# on the new machine, with a fresh copy of this project and no .env yet:
./restore.sh bns-warehouse-backup-2026-09-24.zip
```

That writes `.env` from the backup, imports the database, and brings the whole
stack up - same data, same settings, same secrets, ready to go. `backup.sh` is
the command-line equivalent of the in-app button (handy for cron). See
`docs/BNS_Warehouse_Setup_Guide.pdf` (Section 10) for the full walkthrough,
including restoring into an already-running system instead of a fresh one.

## Project structure

```
backend/       Spring Boot API (uk.co.bns.warehouse_api)
frontend/      React + TypeScript + Tailwind - desktop app and the handheld PWA
android-app/   Android Studio project - kiosk wrapper for the handheld PWA (source only, see android-app/README.md)
print-agent/   Small local program for genuinely silent PDF printing (not part of the web app - browsers can't do this alone)
docs/          BNS_Warehouse_Public_API.pdf - customer-facing Stock API integration guide
               BNS_Warehouse_Support_Tickets_API.pdf - Support Tickets API integration guide
               BNS_Warehouse_User_Guide.pdf - internal end-to-end user guide
               BNS_Warehouse_Setup_Guide.pdf - go-live/setup runbook: install, Settings walkthrough,
               Shopify/DPD/email/printing setup, bulk import file formats, Backup & Restore
backup.sh      Command-line backup (see Backup & Restore below) - same output as the in-app button
restore.sh     Stands up a new machine from a backup.sh/in-app backup - data AND secrets
docker-compose.yml
```

## What's implemented

**Core warehouse** - Products (NONE/SERIAL/MAC tracking), Locations,
Suppliers, Purchase Orders, strict-quantity spreadsheet import, Goods-In
sessions, Stock Items/Inventory/an immutable Movement audit trail, Stock
Trace, Stock Overview, Stock Movement (desktop **and** handheld), a
public read-only Stock API secured by API keys (`docs/BNS_Warehouse_Public_API.pdf`),
live Excel reports, and an automatic Bug Reports log.

**Sales & despatch** - Sales Activity (Company / PO Number / Ordered By
columns, full-row status colour), order creation and editing, Release for
Despatch (credit-checked, see below), on-demand Picking Note PDFs, genuinely
silent printing via `print-agent/`, Split and Serial packing modes, sample
shipping labels, and a despatch confirmation email (own system, not
Shopify's - see Shopify section) listing MAC/serial/default password per
device shipped.

**RMAs** - public customer-facing return request form with live MAC/serial
lookup, staff review/approval queue, receipt processing, automatic
replacement-order or credit-note creation depending on fault status,
configurable return windows, and a cover sheet PDF.

**B2B credit control** - Companies with an optional credit limit, a live
credit-used calculation (orders in progress, despatched-but-not-yet-invoiced
lines, and unpaid invoices, added together), a credit balance "wallet" for
overpayments and unapplied credit notes, and release-for-despatch blocking
when a company is over its limit (with a required-reason override that's
logged). Companies also carry an On Hold (credit hold) flag, a Do Not Use
flag, and GAPS/GDMS tick-boxes imported from OrderWise, all filterable on
the Companies page.

**Generate Invoices** - a fully despatched order on a company account moves
to Invoice Pending rather than straight to Complete, and sits on the
Invoicing > Generate Invoices page until picked up there. Selection is
per order line (not per whole order), so one order can be split across
multiple invoices when a customer wants that; generating produces a PDF
matching the OrderWise layout, emails it to the company's invoice contact
with a saved local copy, and marks the order Complete once every line is
invoiced. The order's delivery/shipping cost is billed as its own line on
the invoice PDF, once per order, on whichever invoice first touches it -
never on a credit note, since those credit returned goods, not the
original delivery charge. Invoice numbering continues from OrderWise's own
sequence and is editable from Settings > Invoicing, which also holds the
default VAT rate (overridable per company), payment terms, and optional
bank details for the PDF. Credit notes (from RMAs) go through the same
page under the Credit radio button, and are auto-applied to the RMA's
replacement order invoice when one already exists. Each company also has its own Invoice Grouping
setting - one invoice per order, or all of a day's selected lines
consolidated onto one. Invoicing > Invoice History lists every invoice and
credit note ever generated, searchable the same way as the rest of the app.

**Payment Tracking** - Invoicing > Payment Tracking lists every invoiced
order with an outstanding balance, searchable and filterable (close to
payment terms / over payment terms / part payment), shaded orange as an
invoice nears its payment terms and red once it's over. Recording a payment
or applying a company's credit balance against an invoice attempts to push
full payment to Shopify (`orderMarkAsPaid`, GraphQL only - this integration
deliberately avoids Shopify's legacy REST API); partial payments and
multi-order consolidated invoices are recorded here but reported back as
not pushed, with the reason, rather than guessed at. Each company has a
Payment Terms (days) override and an Auto-hold-when-overdue toggle
(Companies) - with it on, a company is put on hold the first time an
invoice goes over terms and taken back off once nothing's outstanding,
without re-holding a company staff have deliberately taken off hold by hand
for the same invoice(s). A daily job also sends once-only warning and
overdue chaser emails per invoice, with editable templates under Settings >
Payment Tracking, always to the company's Invoice Email.

**CRM** - Support Tickets (sequential ticket numbers, company/order/contact
linking, a dated timeline, and a public API for a call-transcription/CRM
import integration - `docs/BNS_Warehouse_Support_Tickets_API.pdf`), RMAs, and
a permanent **Contacts** list (people at a company, searchable and linkable
when opening a ticket), plus a Company & Contact bulk importer from an
OrderWise customer export.

**User accounts** - session-based login, a dedicated login page with a
"remembered accounts" quick-switcher for shared devices (names only, never
passwords, stored locally), a separate handheld login matching its own dark
UI, new logins manageable from Settings > Users, per-user settings
(currently just status colour customisation) alongside the app-wide ones,
and increasing-delay login throttling per IP/username against repeated
failed attempts.

**Handheld app** - a separate PWA (not the desktop app shrunk down) for
Picking, Goods In, and Stock Movement, scan-first throughout (barcode fields
default to no on-screen keyboard, with a manual toggle for the rare typed
entry), with its own login and logout. Also wrappable as a full-screen
Android kiosk app - source in `android-app/`, downloadable from the handheld
login page once built (see `android-app/README.md`).

**Shopify integration** (two-way):
- **Connect to Shopify** - a proper OAuth flow from Settings' Shopify Sync page
- **Pulls in:** the product catalogue (SKU-matched, new products flagged
  "Needs Review" since Shopify has no concept of MAC/serial tracking), native
  B2B companies, and orders (always landing On Hold so credit control and a
  human review always happen first; matched to a company via Shopify's own
  `purchasingEntity` data, not guesswork)
- **Pushes out:** despatch confirmation (marks the order fulfilled on
  Shopify without triggering Shopify's own customer email - this system's
  own despatch email is the one that actually goes to the customer, since it
  needs to carry device credentials Shopify's template can't), available
  stock quantity (periodic), and weight (the moment it's edited - the
  warehouse system is authoritative for weight, by explicit decision; a
  Shopify-side edit gets overwritten on the next push rather than the two
  systems trying to reconcile which was "more recent")

## What's not yet built

- **Real courier integration** - shipping labels are currently dummy/sample
  PDFs, not real DPD (or similar) labels
- **Accounting sync** (Xero/QuickBooks) - discussed at length (credit limit
  mechanisms, PO number field mapping) but not built
- **Per-user Outlook/Microsoft 365 linking** - so despatch/RMA emails could
  originate from the specific staff member who triggered them, not a shared
  address. Deliberately scoped as its own separate piece of work (a genuine
  OAuth integration against Microsoft Graph, comparable in size to the whole
  Shopify integration above), not bolted on alongside login
- **Webhooks** - Shopify sync is poll-based throughout; webhooks need a
  public HTTPS endpoint, which becomes viable once this is properly hosted
  rather than LAN-only
- **Multi-location Shopify stock push** - if a Shopify store has more than
  one location, the stock push currently reports an error rather than
  guessing which one to use; needs a location picker in Settings
- **Role-based permissions** - every logged-in user currently has equal
  access to everything, including creating other logins; no admin/staff
  distinction exists
- **Bulk stock import from OrderWise** - in progress: the plan is to export
  the current system's stock data and design an importer around whatever
  format that export turns out to be, minimising manual re-entry
- **Production hardening for internet-facing hosting** - this system has
  been built and tested as a LAN-only tool, and a few things were
  deliberately left loose specifically because of that, flagged in code
  comments at the time: CSRF protection is disabled, CORS currently accepts
  any origin, session cookies aren't marked `Secure`, and the public RMA
  form has no rate limiting. None of this is hard to fix, but it's a
  focused piece of work that needs doing together with picking a real host -
  not something to forget once a domain is pointed at it
- **Backups** - Settings → Backup & Restore (plus `backup.sh`/`restore.sh`)
  covers taking and restoring one manually; there's no automatic schedule
  yet, so someone still needs to actually run it regularly and keep the
  file somewhere safe
- **Android release build** - only a debug APK has been built and installed
  so far; a signed release build, and optionally Device Owner kiosk mode for
  a fully locked-down dedicated scanner, are still manual one-time steps
  described in `android-app/README.md`

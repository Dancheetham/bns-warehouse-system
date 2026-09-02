# BNS Warehouse System

A purpose-built warehouse management system for BNS Distribution: serialised
inventory (MAC/serial) tracking, carton-level goods-in, and full traceability
from receipt to despatch.

## Stack

- **Backend:** Java 21, Spring Boot 3, Spring Data JPA, PostgreSQL, Flyway
- **Frontend:** React + TypeScript, Tailwind CSS, TanStack Query
- **Deployment:** Docker Compose (Postgres + API + Nginx-served frontend)

## Running it

You need Docker + Docker Compose installed. From the project root:

```bash
docker compose up --build
```

First run will take a few minutes (Maven downloads dependencies, npm installs
packages). Once it's up:

- **Frontend:** http://localhost:8081
- **API:** http://localhost:8080/api (try http://localhost:8080/api/health)
- **Postgres:** localhost:5432 (db `bnswarehouse`, user/pass `bnsadmin`/`bnsadmin`)

Flyway runs automatically on API startup and creates the full schema, plus
some seed data (4 sample products, a supplier, and 4 locations) so there's
something to test against immediately.

## Trying out the core workflow

1. Go to **Purchase Orders** → New Purchase Order. Pick the seeded supplier
   (Grandstream) and add a line for e.g. `GWN7802P`, quantity 3.
2. Open the PO you just created and **import a shipment spreadsheet**. It
   needs a header row with columns `SKU`, `MAC`, `SERIAL`, `BATCH` (and
   optionally `WIFI_MAC`) - `.xlsx`, `.xls` or `.csv`. You need exactly 3 rows
   for `GWN7802P` to match the PO quantity (this mirrors the strict
   quantity-matching rule from the existing system - the whole import is
   rejected if it doesn't match exactly).
3. Go to **Goods In**, select the PO and a destination bin, and start a
   session. Scan (or type) the batch codes from your spreadsheet - each
   scan is registered instantly. Scanning the same carton twice in one
   session is silently ignored, just like the current system.
4. Click **Save & Complete**. This creates the real `StockItem` records,
   updates inventory, and writes the movement history.
5. Go to **Stock Trace** and search one of the MAC addresses you imported -
   you'll see its full timeline (received, moved, etc).

## Project structure

```
backend/    Spring Boot API (uk.co.bns.warehouse_api)
frontend/   React + TypeScript + Tailwind
docs/       BNS_Warehouse_Public_API.pdf - customer-facing API integration guide
docker-compose.yml
```

## What's implemented vs. what's next

**Implemented:**
- Products (with tracking type: NONE / SERIAL / MAC), Locations, Suppliers
- Purchase Orders with lines
- Supplier spreadsheet import with strict PO-quantity validation, uppercase
  normalisation of SKU/MAC/serial/batch (not passwords)
- Expected Cartons / Expected Stock Items (pre-receipt staging)
- Goods-In sessions with scanner-first carton booking, part-book/save-later,
  and the exact duplicate-scan behaviour from the existing system
- Stock Items, Inventory totals, and an immutable Stock Movement audit trail
- Stock Trace - search by MAC, serial, or whole batch/carton (case-insensitive),
  batch search shows a collapsible per-device timeline
- Stock Overview - browse by Bin or by Product, drilling down to individual
  units (MAC/serial/batch/password) at each level, with a filter box on each
  unit table
- Stock Movement - scan MACs, serials, or whole batches to build a move list,
  pick one destination bin, save; every move writes to that unit's trace
- Public read-only Stock API (`/api/public/stock`) secured by API keys,
  managed from the "API Access" screen - see `docs/BNS_Warehouse_Public_API.pdf`
- Reports - download live Excel exports: stock levels by location, a full
  stock item listing, and stock movement history (with an optional date range)
- Bug Reports - failed requests anywhere in the app are logged automatically
  with a timestamp, status code, and what was being attempted; entries can
  also be added by hand
- Sales Activity - order list matching the current portal's columns, click a
  row to preview its lines, double-click to edit. Orders are entered manually
  for now; the data model (`ecommerceOrderNumber` field, etc.) is ready for
  Shopify orders to land in the same table once that integration is built
- Despatch workflow - trims the old multi-screen "add shipping cost, check
  credit, untick On Hold, change output method, print, change output method
  again, email" flow down to a couple of clicks:
  - **Release for Despatch**: one panel (shipping cost + courier), one button,
    moves the order straight from On Hold to Awaiting Despatch
  - **Picking Note**: generates a PDF on demand (`GET /api/orders/{id}/picking-note`)
    showing what's required, total stock available, the product's default bin
    with its quantity, and a breakdown of alternative bins
  - **Genuinely silent printing**: see `print-agent/` - a small local program
    (not part of the web app, since browsers can't do this on their own) that
    receives the PDF and sends it straight to a named printer with zero
    dialogs. Falls back to opening the PDF in a new tab if the agent isn't
    running, so printing is never a dead end
  - **Acknowledgement email**: composes and (if SMTP is configured) sends
    automatically the moment an order is released, per the "Settings" screen
    toggle - or on demand with its own button. Honest about not actually
    sending if no SMTP server is configured, rather than pretending to
  - **Settings** screen for the printer name, print agent URL, and the
    auto-acknowledge toggle

**Not yet built (natural next steps):**
- Shopify integration - the Order/OrderLine model is ready for it (see
  `ecommerceOrderNumber`); needs a Shopify Admin API token, store domain, and
  a decision on webhooks vs polling once you're ready to connect it
- Picking / allocation / despatch workflow - despatching an order should
  presumably consume StockItems and update quantityDespatched on each line,
  the same way Goods-In consumes ExpectedStockItems in reverse
- Shopify integration (order webhooks, fulfilment sync)
- Courier integration (DPD label generation/printing)
- QuickBooks sync
- RMAs
- User accounts / authentication for the internal UI (currently open - fine
  for local testing, not for anything internet-facing). Note this is separate
  from the API key system, which only protects the external `/api/public/**`
  endpoints.

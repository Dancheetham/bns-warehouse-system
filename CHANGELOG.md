# Changelog

All notable changes to the BNS Warehouse System, in plain English. Newest first.

This is an internal tool with no formal release process, so version numbers here
are just a scanning aid, not a promise of semver-style compatibility.

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
  with a reset-to-defaults option. Deliberately global for now: there's no
  login system yet for a "per-user" preference to actually follow a specific
  person between devices, so this is shared by everyone until real user
  accounts exist as their own piece of work

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

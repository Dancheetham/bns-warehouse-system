export type TrackingType = "NONE" | "SERIAL" | "MAC";
export type POStatus = "DRAFT" | "AWAITING_STOCK" | "PART_RECEIVED" | "RECEIVED" | "CANCELLED";

export interface Product {
  id: number;
  sku: string;
  name: string;
  description?: string;
  trackingType: TrackingType;
  defaultLocation?: Location;
  weightKg?: number;
  commodityCode?: string;
  countryOfOrigin?: string;
  active: boolean;
  needsReview?: boolean;
  shopifyProductId?: string;
  shopifyVariantId?: string;
  lastSyncedAt?: string;
}

export interface ShopifyStatus {
  appConfigured: boolean;
  connected: boolean;
  shopDomain?: string;
  lastSyncedAt?: string;
  needsReviewCount: number;
  totalSyncedProducts: number;
  lastCompanySyncedAt?: string;
  lastOrderSyncedAt?: string;
  lastStockPushedAt?: string;
}

export interface ShopifyStockPushResult {
  configured: boolean;
  pushedAt?: string;
  pushed: number;
  skipped: string[];
  errors: string[];
}

export interface ShopifyCompanySyncResult {
  configured: boolean;
  syncedAt?: string;
  created: number;
  updated: number;
  errors: string[];
}

export interface ShopifyOrderSyncResult {
  configured: boolean;
  syncedAt?: string;
  imported: number;
  alreadyImported: number;
  skipped: string[];
  errors: string[];
}

export interface ShopifySyncResult {
  configured: boolean;
  syncedAt?: string;
  created: number;
  updated: number;
  skippedNoSku: number;
  errors: string[];
}

export type PickingStatus = "NOT_STARTED" | "IN_PROGRESS" | "COMPLETE" | "PARTIAL";

export interface OrderPickSummary {
  orderId: number;
  orderNumber: string;
  customerName: string;
  orderDate: string;
  lineCount: number;
  pickingStatus: PickingStatus;
  pickedBy?: string;
}

export interface PickLineView {
  orderLineId: number;
  productId: number;
  sku: string;
  productName: string;
  defaultBinCode?: string;
  defaultBinAvailable: number;
  totalAvailable: number;
  quantityOrdered: number;
  quantityPicked: number;
  requiresScan: boolean;
  complete: boolean;
  shortPicked: boolean;
}

export interface PickOrderView {
  orderId: number;
  orderNumber: string;
  customerName: string;
  pickingStatus: PickingStatus;
  pickedBy?: string;
  lines: PickLineView[];
}

export interface PickScanResult {
  view: PickOrderView;
  allocatedStockItemIds: number[];
}

export interface PackLineView {
  cartonLineId: number;
  orderLineId: number;
  sku: string;
  productName: string;
  quantity: number;
  cartonId?: number;
}

export interface CartonView {
  cartonId: number;
  cartonNumber: number;
  weightKg?: number;
  computedWeightKg?: number;
  trackingNumber?: string;
  lines: PackLineView[];
}

export interface PackingView {
  orderId: number;
  orderNumber: string;
  customerName: string;
  unassignedLines: PackLineView[];
  cartons: CartonView[];
  allAssigned: boolean;
}

export interface PackedItemView {
  stockItemId: number;
  sku: string;
  productName: string;
  identifier: string;
  cartonId?: number;
}

export interface SerialCartonView {
  cartonId: number;
  cartonNumber: number;
  weightKg?: number;
  computedWeightKg?: number;
  trackingNumber?: string;
  items: PackedItemView[];
}

export interface SerialPackingView {
  orderId: number;
  orderNumber: string;
  customerName: string;
  unassignedItems: PackedItemView[];
  cartons: SerialCartonView[];
  allAssigned: boolean;
}

export type RmaStatus = "SUBMITTED" | "APPROVED" | "REJECTED" | "RECEIVED";

export interface RmaLookupResult {
  identifier: string;
  itemFound: boolean;
  orderMatched: boolean;
  productId?: number;
  sku?: string;
  productName?: string;
  orderId?: number;
  orderNumber?: string;
  orderDate?: string;
  unitPrice?: number;
  returnWindowExpiresAt?: string;
  returnWindowValid?: boolean;
  returnWindowDays?: number;
}

export interface RmaItemSubmission {
  productId: number;
  identifier?: string;
  quantity: number;
  faulty: boolean;
  grandstreamTicketNumber?: string;
  reasonForReturn?: string;
}

export interface RmaSubmissionRequest {
  customerName: string;
  customerCompany?: string;
  customerAddress?: string;
  contactName?: string;
  contactPhone?: string;
  contactEmail?: string;
  deliveryName?: string;
  deliveryTown?: string;
  deliveryCountry?: string;
  deliveryPostcode?: string;
  deliveryCountryCode?: string;
  items: RmaItemSubmission[];
}

export interface RmaSummaryView {
  id: number;
  publicReference: string;
  rmaNumber?: string;
  status: RmaStatus;
  customerName: string;
  submittedAt: string;
  itemCount: number;
  anyUnmatched: boolean;
  anyFaulty: boolean;
}

export interface RmaItemView {
  id: number;
  productId: number;
  sku: string;
  productName: string;
  identifier?: string;
  quantity: number;
  faulty: boolean;
  grandstreamTicketNumber?: string;
  reasonForReturn?: string;
  matchedOrderId?: number;
  matchedOrderNumber?: string;
  matchedUnitPrice?: number;
  returnWindowExpiresAt?: string;
  returnWindowValid: boolean;
  grandstreamWarrantyChecked: boolean;
  received: boolean;
  rsfApplied: boolean;
  credited: boolean;
}

export interface RmaDetailView {
  id: number;
  publicReference: string;
  rmaNumber?: string;
  status: RmaStatus;
  customerName: string;
  customerCompany?: string;
  customerAddress?: string;
  contactName?: string;
  contactPhone?: string;
  contactEmail?: string;
  deliveryName?: string;
  deliveryTown?: string;
  deliveryCountry?: string;
  deliveryPostcode?: string;
  deliveryCountryCode?: string;
  originalOrderId?: number;
  originalOrderNumber?: string;
  replacementOrderId?: number;
  replacementOrderNumber?: string;
  creditOrderId?: number;
  creditOrderNumber?: string;
  notes?: string;
  submittedAt: string;
  approvedAt?: string;
  approvedBy?: string;
  rejectedAt?: string;
  rejectedBy?: string;
  rejectionReason?: string;
  receivedAt?: string;
  receivedBy?: string;
  items: RmaItemView[];
}

export interface Location {
  id: number;
  code: string;
  description?: string;
  active: boolean;
}

export interface Supplier {
  id: number;
  name: string;
  accountNumber?: string;
  contactName?: string;
  contactEmail?: string;
  contactPhone?: string;
  active: boolean;
}

export interface PurchaseOrderLine {
  id: number;
  product: Product;
  quantityOrdered: number;
  quantityReceived: number;
  unitCost?: number;
  notes?: string;
}

export interface PurchaseOrder {
  id: number;
  poNumber: string;
  supplier: Supplier;
  expectedDate?: string;
  status: POStatus;
  createdAt: string;
  lines: PurchaseOrderLine[];
}

export interface ImportRowResult {
  sku: string;
  poQuantity: number;
  spreadsheetQuantity: number;
  matches: boolean;
}

export interface ImportResult {
  success: boolean;
  cartonsCreated: number;
  itemsCreated: number;
  lineValidation: ImportRowResult[];
  errors: string[];
}

export interface GoodsInSession {
  id: number;
  purchaseOrder: PurchaseOrder;
  location: Location;
  status: "OPEN" | "SAVED";
  startedBy?: string;
  startedAt: string;
}

export interface ScanCartonResult {
  status: "ADDED" | "ALREADY_IN_SESSION" | "ALREADY_RECEIVED" | "NOT_FOUND";
  message?: string;
  productSku?: string;
  productName?: string;
  itemCount?: number;
}

export interface StockTraceEvent {
  timestamp: string;
  eventType: string;
  fromLocation?: string;
  toLocation?: string;
  reference?: string;
  notes?: string;
  performedBy?: string;
}

export interface StockTraceResult {
  identifierType: string;
  identifier: string;
  productSku: string;
  productName: string;
  currentStatus: string;
  currentLocation?: string;
  timeline: StockTraceEvent[];
}

export interface LocationStockSummary {
  locationId: number;
  locationCode: string;
  locationDescription?: string;
  available: number;
  quarantined: number;
  allocated: number;
  despatched: number;
  returned: number;
  total: number;
}

export interface MoveStockRequest {
  fromLocationId: number;
  toLocationId: number;
  quantity: number;
  movedBy?: string;
  notes?: string;
}

export interface StockItemSummary {
  id: number;
  macAddress?: string;
  serialNumber?: string;
  wifiMacAddress?: string;
  batchCode?: string;
  defaultPassword?: string;
  productSku: string;
  productName: string;
  locationId?: number;
  locationCode?: string;
  status: string;
}

export interface MoveItemsResult {
  movedCount: number;
  skippedCount: number;
  skippedReasons: string[];
}

export interface StockItemDetail {
  id: number;
  macAddress?: string;
  serialNumber?: string;
  wifiMacAddress?: string;
  batchCode?: string;
  defaultPassword?: string;
  status: string;
  locationCode?: string;
}

export interface BinProductGroup {
  productId: number;
  productSku: string;
  productName: string;
  items: StockItemDetail[];
}

export interface ApiKeySummary {
  id: number;
  label: string;
  active: boolean;
  createdAt: string;
  lastUsedAt?: string;
}

export interface ApiKeyCreatedResponse {
  id: number;
  label: string;
  apiKey: string;
  createdAt: string;
}

export interface BugReport {
  id: number;
  occurredAt: string;
  source: "AUTO" | "MANUAL";
  errorCode?: string;
  description: string;
  context?: string;
}

export type OrderStatus =
  | "ON_HOLD"
  | "AWAITING_DESPATCH"
  | "CANCELLED"
  | "COMPLETED"
  | "PARTIALLY_DESPATCHED"
  | "AWAITING_CONVERSION"
  // Fully despatched (or, for a CREDIT_REFUND order, fully processed) on a
  // company/credit account, but not yet invoiced - sits on the Generate
  // Invoices page until picked up there, then moves to COMPLETED. An order
  // with no linked Company (e.g. a Shopify order paid at checkout) skips
  // this and goes straight to COMPLETED, same as before.
  | "INVOICE_PENDING";
export type OrderType = "ORDER" | "PAUSED" | "QUOTE" | "CREDIT_REFUND" | "SCHEDULED";

export interface OrderLine {
  id: number;
  product: Product;
  quantityOrdered: number;
  quantityDespatched: number;
  unitPrice?: number;
  notes?: string;
}

export interface CompanyRef {
  id: number;
  name: string;
  creditLimit?: number;
  shopifyCompanyId?: string;
  notes?: string;
}

export type InvoiceGrouping = "PER_ORDER" | "CONSOLIDATED";

export interface CompanyView {
  id: number;
  name: string;
  creditLimit?: number;
  shopifyCompanyId?: string;
  notes?: string;
  // Sent as the DPD customs importer's EORI/VAT number for this company's
  // orders when shipping to a customs country (e.g. Ireland).
  eoriNumber?: string;
  vatNumber?: string;
  // The OrderWise "Account number" code - used as the bulk import match key.
  accountNumber?: string;
  // Credit hold - stops processing any more of this company's orders until
  // cleared. Distinct from the computed overLimit below.
  onHold: boolean;
  doNotUse: boolean;
  gaps: boolean;
  gdms: boolean;
  creditUsed?: number;
  creditAvailable?: number;
  overLimit: boolean;
  // Generate Invoices - see below.
  invoiceEmail?: string;
  vatRate?: number;
  invoiceGrouping: InvoiceGrouping;
}

export interface CompanyRequest {
  name: string;
  creditLimit?: number;
  shopifyCompanyId?: string;
  notes?: string;
  eoriNumber?: string;
  vatNumber?: string;
  accountNumber?: string;
  onHold?: boolean;
  doNotUse?: boolean;
  gaps?: boolean;
  gdms?: boolean;
  // Where Generate Invoices emails the PDF - separate from any Contact.
  invoiceEmail?: string;
  // Overrides the default VAT rate (Settings > Invoicing) for this company
  // only - e.g. 0 for a zero-rated/Irish account. Leave unset to use the
  // default.
  vatRate?: number;
  // Whether a Generate Invoices run bundles this company's ticked lines
  // into one invoice per order (default) or one invoice per company.
  invoiceGrouping?: InvoiceGrouping;
}

export interface InvoicedMonthValue {
  month: number; // 1-12
  invoiceTotal: number;
  creditTotal: number;
}

// Generate Invoices - see InvoiceController/InvoiceService on the backend.
export type InvoiceType = "INVOICE" | "CREDIT_NOTE";

export interface PendingInvoiceLineView {
  orderLineId: number;
  orderId: number;
  orderNumber: string;
  orderDate: string;
  companyId: number;
  companyName: string;
  sku: string;
  description?: string;
  quantityPending: number;
  unitPrice: number;
  netAmount: number;
}

export interface GenerateInvoicesRequest {
  invoiceType: InvoiceType;
  generationDate: string;
  orderLineIds: number[];
}

export interface GeneratedInvoiceSummary {
  invoiceId: number;
  invoiceNumber: number;
  companyName: string;
  grandTotal: number;
  emailed: boolean;
  emailError?: string;
}

export interface GenerateInvoicesResult {
  invoices: GeneratedInvoiceSummary[];
  warnings: string[];
}

export interface OrderCreditStatus {
  companyId: number;
  companyName: string;
  creditLimit?: number;
  creditUsed?: number;
  creditAvailable?: number;
  overLimit: boolean;
  orderTotal: number;
  orderOutstanding: number;
}

export interface PaymentView {
  id: number;
  orderId: number;
  orderNumber: string;
  amount: number;
  receivedAt: string;
  reference?: string;
  notes?: string;
  recordedBy?: string;
}

export interface PaymentRequest {
  amount: number;
  receivedAt?: string;
  reference?: string;
  notes?: string;
  recordedBy?: string;
}

export interface Order {
  id: number;
  orderNumber: string;
  version: number;
  orderDate: string;
  customerName: string;
  customerEmail?: string;
  company?: CompanyRef;
  orderReference?: string;
  ecommerceOrderNumber?: string;
  orderedBy?: string;
  deliveryName?: string;
  deliveryAddressLine1?: string;
  deliveryAddressLine2?: string;
  deliveryPhone?: string;
  deliveryTown?: string;
  deliveryCountry?: string;
  deliveryPostcode?: string;
  deliveryCountryCode?: string;
  status: OrderStatus;
  orderType: OrderType;
  shippingCost?: number;
  courierMethod?: string;
  dpdNetworkKey?: string;
  specialInstructions?: string;
  acknowledgementSentAt?: string;
  dpdShipmentId?: string;
  dpdConsignmentNumber?: string;
  dpdParcelNumbers?: string;
  dpdShippedAt?: string;
  lines: OrderLine[];
  // Set only on the response to a save - whether (and how) that save's
  // changes were pushed back to the underlying Shopify order. Never present
  // otherwise (e.g. on a plain GET), so always optional here.
  shopifyAmendStatus?: string;
}

// One DPD service actually available right now for an order's delivery
// address and weight, per DPD's live "validate outbound services" lookup -
// networkKey is what's sent back to DPD as the shipment's networkCode.
export interface DpdServiceOption {
  networkKey: string;
  networkDesc: string;
  serviceDesc: string;
}

// Response for the Service dropdown. `live` is true only when `services`
// came straight from DPD's own live lookup just now; when that fails,
// `services` falls back to the last list DPD returned successfully (from
// any order) so the dropdown still has real options, `live` is false, and
// `liveError` carries why the fresh check failed.
export interface DpdServiceLookupResult {
  services: DpdServiceOption[];
  live: boolean;
  liveError?: string;
}

export interface AcknowledgementResult {
  emailSent: boolean;
  reason: string;
  toAddress?: string;
  subject: string;
  body: string;
}

export interface DespatchConfirmationResult {
  order: Order;
  despatchEmail: AcknowledgementResult;
  shopifyFulfillmentStatus: string;
  dpdStatus?: string;
}

export interface UnmatchedSkuSummary {
  sku: string;
  rowCount: number;
  totalQty: number;
}

export interface TrackingTypeChange {
  sku: string;
  from: string;
  to: string;
}

export interface StockImportPreview {
  totalRows: number;
  binsToCreate: string[];
  matchedProductCount: number;
  unmatchedSkus: UnmatchedSkuSummary[];
  trackingTypeChanges: TrackingTypeChange[];
  itemsToCreate: number;
  currentOnHandItemsToRemove: number;
  edgeCaseNotes: string[];
  errors: string[];
}

export interface StockImportResult {
  success: boolean;
  binsCreated: number;
  itemsRemoved: number;
  itemsCreated: number;
  productsSkipped: number;
  errors: string[];
}

export type TicketStatus = "IN_PROGRESS" | "ON_HOLD" | "CLOSED" | "COMPLETE";

export interface TicketEntryView {
  id: number;
  author: string;
  note: string;
  createdAt: string;
}

export interface TicketEntryRequest {
  note: string;
  author?: string;
}

// Used for ticket lists (all tickets, a company's tickets, an order's linked
// ticket) - leaves out the timeline, which only TicketView (a single ticket)
// carries.
export interface TicketSummaryView {
  id: number;
  ticketNumber: string;
  title: string;
  callerName?: string;
  phone?: string;
  email?: string;
  companyId?: number;
  companyName?: string;
  orderId?: number;
  orderNumber?: string;
  contactId?: number;
  contactName?: string;
  status: TicketStatus;
  talkTimeMinutes: number;
  createdAt: string;
  updatedAt?: string;
}

export interface TicketView extends TicketSummaryView {
  entries: TicketEntryView[];
}

export interface TicketRequest {
  title: string;
  callerName?: string;
  phone?: string;
  email?: string;
  companyId?: number;
  orderId?: number;
  contactId?: number;
  status?: TicketStatus;
  talkTimeMinutes?: number;
}

export interface ContactView {
  id: number;
  companyId: number;
  companyName: string;
  name: string;
  email?: string;
  phone?: string;
  position?: string;
  mainContact: boolean;
  active: boolean;
}

export interface ContactRequest {
  companyId: number;
  name: string;
  email?: string;
  phone?: string;
  position?: string;
  mainContact?: boolean;
  active?: boolean;
}

export interface CompanyImportPreview {
  totalCompanyRows: number;
  totalContactRows: number;
  companiesToCreate: number;
  companiesToUpdate: number;
  contactsToCreate: number;
  contactsToUpdate: number;
  unmatchedContactCodes: string[];
  edgeCaseNotes: string[];
  errors: string[];
}

export interface CompanyImportResult {
  success: boolean;
  companiesCreated: number;
  companiesUpdated: number;
  contactsCreated: number;
  contactsUpdated: number;
  contactsSkipped: number;
  errors: string[];
}

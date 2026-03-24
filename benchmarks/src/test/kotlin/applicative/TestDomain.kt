package applicative

// ════════════════════════════════════════════════════════════════════════
// Shared domain types for tests.
//
// Every type is distinct, so kap+with chains enforce correct parameter
// ordering at compile time — swap two .with lines and it won't compile.
// ════════════════════════════════════════════════════════════════════════

// ── Checkout domain ─────────────────────────────────────────────────────

data class UserProfile(val name: String, val id: Long = 42)
data class ShoppingCart(val items: Int, val total: Double)
data class PromotionBundle(val code: String, val discountPct: Int)
data class InventorySnapshot(val allInStock: Boolean)
data class StockConfirmation(val confirmed: Boolean)
data class ShippingQuote(val amount: Double, val method: String)
data class TaxBreakdown(val amount: Double, val rate: Double)
data class DiscountSummary(val amount: Double, val promoApplied: String)
data class PaymentAuth(val cardLast4: String, val authorized: Boolean)
data class OrderConfirmation(val orderId: String)
data class EmailReceipt(val sentTo: String, val orderId: String)

data class CheckoutResult(
    val user: UserProfile,
    val cart: ShoppingCart,
    val promos: PromotionBundle,
    val inventory: InventorySnapshot,
    val stock: StockConfirmation,
    val shipping: ShippingQuote,
    val tax: TaxBreakdown,
    val discounts: DiscountSummary,
    val payment: PaymentAuth,
    val confirmation: OrderConfirmation,
    val email: EmailReceipt,
)

// ── Dashboard domain ────────────────────────────────────────────────────

data class Preferences(val theme: String, val layout: String)
data class FeatureFlags(val flags: Map<String, Boolean>)
data class AuthToken(val token: String, val scope: String)
data class FeedContent(val posts: List<String>)
data class NotificationList(val unread: Int)
data class MessageSummary(val conversations: Int)
data class Recommendations(val suggested: List<String>)
data class AnalyticsEnrichment(val segment: String)
data class TrendingTopics(val tags: List<String>)
data class PeopleSuggestions(val count: Int)
data class AdSlots(val content: String)
data class SocialProof(val likesToday: Int)
data class AppVersion(val version: String)

data class DashboardView(
    val user: UserProfile,
    val prefs: Preferences,
    val flags: FeatureFlags,
    val auth: AuthToken,
    val feed: FeedContent,
    val notifications: NotificationList,
    val messages: MessageSummary,
    val recommendations: Recommendations,
    val analytics: AnalyticsEnrichment,
    val trending: TrendingTopics,
    val suggestions: PeopleSuggestions,
    val ads: AdSlots,
    val social: SocialProof,
    val appVersion: AppVersion,
)

// ── Flight booking domain ───────────────────────────────────────────────

data class PassportId(val number: String)
data class SeatAssignment(val seat: String)
data class PaymentConfirmation(val method: String)
data class FlightAvailability(val status: String)
data class LoungeAccess(val lounge: String)

data class BookingResult(
    val passport: PassportId,
    val seat: SeatAssignment,
    val payment: PaymentConfirmation,
    val availability: FlightAvailability,
    val extras: LoungeAccess,
)

// ── Nullable / optional domain ──────────────────────────────────────────

data class CartSummary(val items: Int)
data class Discount(val code: String, val pct: Int)
data class InsurancePlan(val provider: String)
data class OrderTotal(val amount: Double)

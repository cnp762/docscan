package cam.docscan.billing

import cam.docscan.BuildConfig

/**
 * Play Billing wiring stub. Wire BillingClient in a later slice.
 */
object BillingStub {
    val productIds = listOf(
        BuildConfig.PRODUCT_PRO_MONTHLY,
        BuildConfig.PRODUCT_PRO_QUARTERLY,
        BuildConfig.PRODUCT_PRO_YEARLY,
    )
}

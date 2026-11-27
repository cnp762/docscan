package cam.docscan.billing

import android.app.Activity
import android.content.Context
import cam.docscan.BuildConfig
import cam.docscan.data.remote.DocScanApiClient
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Play Billing 7.1.1 integration for DocScan Pro subscriptions (`pro_monthly`, `pro_quarterly`, `pro_yearly`).
 */
class PlayBillingManager(
    context: Context,
    private val apiClient: DocScanApiClient,
    private val onEntitlementChanged: (Boolean, String?) -> Unit,
) : PurchasesUpdatedListener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val prefs = context.applicationContext.getSharedPreferences("docscan_billing", Context.MODE_PRIVATE)

    private val _productDetails = MutableStateFlow<Map<String, ProductDetails>>(emptyMap())
    val productDetails: StateFlow<Map<String, ProductDetails>> = _productDetails.asStateFlow()

    private var billingClient: BillingClient = BillingClient.newBuilder(context.applicationContext)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().enablePrepaidPlans().build(),
        )
        .build()

    fun isCachedPro(): Boolean = prefs.getBoolean(KEY_IS_PRO, false)

    fun startConnection() {
        if (billingClient.isReady) {
            scope.launch {
                queryProducts()
                restorePurchases()
            }
            return
        }
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    scope.launch {
                        queryProducts()
                        restorePurchases()
                    }
                }
            }

            override fun onBillingServiceDisconnected() {
                // Reconnect lazily on next user action
            }
        })
    }

    suspend fun queryProducts() = withContext(Dispatchers.IO) {
        if (!billingClient.isReady) return@withContext
        val products = listOf(
            BuildConfig.PRODUCT_PRO_MONTHLY,
            BuildConfig.PRODUCT_PRO_QUARTERLY,
            BuildConfig.PRODUCT_PRO_YEARLY,
        ).map { id ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(id)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        }
        val params = QueryProductDetailsParams.newBuilder().setProductList(products).build()
        val result = billingClient.queryProductDetails(params)
        if (result.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            _productDetails.value = result.productDetailsList.orEmpty().associateBy { it.productId }
        }
    }

    fun launchPurchase(activity: Activity, productId: String): Boolean {
        val details = _productDetails.value[productId] ?: return false
        val offerToken = details.subscriptionOfferDetails?.firstOrNull()?.offerToken ?: return false
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .setOfferToken(offerToken)
            .build()
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))
            .build()
        val res = billingClient.launchBillingFlow(activity, flowParams)
        return res.responseCode == BillingClient.BillingResponseCode.OK
    }

    suspend fun restorePurchases(): Boolean = withContext(Dispatchers.IO) {
        if (!billingClient.isReady) return@withContext false
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        val res = billingClient.queryPurchasesAsync(params)
        if (res.billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
            return@withContext false
        }
        var activePro = false
        for (purchase in res.purchasesList) {
            if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                if (handlePurchased(purchase)) {
                    activePro = true
                }
            }
        }
        if (!activePro) {
            val serverEnt = apiClient.getEntitlement().getOrNull()
            if (serverEnt?.isPro == true) {
                cachePro(true)
                withContext(Dispatchers.Main) { onEntitlementChanged(true, serverEnt.productId) }
                return@withContext true
            }
        }
        activePro
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        if (result.responseCode != BillingClient.BillingResponseCode.OK || purchases == null) return
        scope.launch {
            for (purchase in purchases) {
                if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                    handlePurchased(purchase)
                }
            }
        }
    }

    private suspend fun handlePurchased(purchase: Purchase): Boolean = withContext(Dispatchers.IO) {
        if (!purchase.isAcknowledged) {
            val ackParams = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            billingClient.acknowledgePurchase(ackParams)
        }
        val productId = purchase.products.firstOrNull() ?: BuildConfig.PRODUCT_PRO_MONTHLY
        val verified = apiClient.verifyBilling(productId, purchase.purchaseToken).getOrNull()
        val isPro = verified?.isPro ?: true
        cachePro(isPro)
        withContext(Dispatchers.Main) {
            onEntitlementChanged(isPro, verified?.productId ?: productId)
        }
        isPro
    }

    fun clearLocalEntitlement() {
        cachePro(false)
    }

    private fun cachePro(isPro: Boolean) {
        prefs.edit().putBoolean(KEY_IS_PRO, isPro).apply()
    }

    companion object {
        private const val KEY_IS_PRO = "is_pro"
    }
}

package nir.com.videopoker;

import android.app.Activity;
import android.content.SharedPreferences;
import com.android.billingclient.api.*;
import java.util.Collections;
import java.util.List;

/** Remove-ads one-time IAP ("remove_ads"). Persists entitlement in SharedPreferences. */
public class RemoveAdsManager implements PurchasesUpdatedListener {
    public interface Listener { void onAdsRemoved(); void onPrice(String price); }

    private static final String PRODUCT = "remove_ads";
    private final Activity act;
    private final SharedPreferences sp;
    private final Listener listener;
    private BillingClient bc;
    private ProductDetails details;

    public RemoveAdsManager(Activity a, SharedPreferences sp, Listener l) {
        act = a; this.sp = sp; listener = l;
    }

    public boolean isAdsRemoved() { return sp.getBoolean(PRODUCT, false); }

    public void start() {
        bc = BillingClient.newBuilder(act)
            .setListener(this)
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .build();
        bc.startConnection(new BillingClientStateListener() {
            @Override public void onBillingSetupFinished(BillingResult r) {
                if (r.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    queryProduct();
                    restore();
                }
            }
            @Override public void onBillingServiceDisconnected() { /* retry on next launch */ }
        });
    }

    private void queryProduct() {
        QueryProductDetailsParams.Product prod = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(PRODUCT)
            .setProductType(BillingClient.ProductType.INAPP)
            .build();
        bc.queryProductDetailsAsync(
            QueryProductDetailsParams.newBuilder().setProductList(Collections.singletonList(prod)).build(),
            (r, result) -> {
                if (r.getResponseCode() == BillingClient.BillingResponseCode.OK && !result.getProductDetailsList().isEmpty()) {
                    details = result.getProductDetailsList().get(0);
                    String price = details.getOneTimePurchaseOfferDetails() != null
                        ? details.getOneTimePurchaseOfferDetails().getFormattedPrice() : null;
                    if (price != null) act.runOnUiThread(() -> listener.onPrice(price));
                }
            });
    }

    public String getPrice() {
        return details != null && details.getOneTimePurchaseOfferDetails() != null
            ? details.getOneTimePurchaseOfferDetails().getFormattedPrice() : null;
    }

    public void buy() {
        if (details == null || bc == null || !bc.isReady()) { queryProduct(); return; }
        BillingFlowParams.ProductDetailsParams p = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details).build();
        bc.launchBillingFlow(act, BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(Collections.singletonList(p)).build());
    }

    private void restore() {
        bc.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build(),
            (r, purchases) -> {
                if (r.getResponseCode() == BillingClient.BillingResponseCode.OK) handle(purchases);
            });
    }

    @Override public void onPurchasesUpdated(BillingResult r, List<Purchase> purchases) {
        if (r.getResponseCode() == BillingClient.BillingResponseCode.OK && purchases != null) handle(purchases);
    }

    private void handle(List<Purchase> purchases) {
        for (Purchase p : purchases) {
            if (p.getPurchaseState() != Purchase.PurchaseState.PURCHASED) continue;
            if (!p.getProducts().contains(PRODUCT)) continue;
            if (!p.isAcknowledged()) {
                bc.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(p.getPurchaseToken()).build(), r -> {});
            }
            if (!isAdsRemoved()) {
                sp.edit().putBoolean(PRODUCT, true).apply();
                act.runOnUiThread(() -> listener.onAdsRemoved());
            }
        }
    }
}

package nir.com.videopoker;

import android.app.Activity;
import android.content.Context;
import com.android.billingclient.api.*;
import java.util.*;

/** Google Play Billing: coin-pack consumables + one-time remove-ads. */
public class BillingManager implements PurchasesUpdatedListener {
    public static final String REMOVE_ADS = "remove_ads";
    public static final String[] PACK_IDS = {"coins_small", "coins_medium", "coins_large", "coins_mega"};
    public static final long[] PACK_COINS = {1000, 5500, 12000, 30000};

    public interface Listener {
        void onAdsRemovedOwned();
        void onCoinsPurchased(long coins);
        void onProductsReady();
    }

    private final Context ctx;
    private final Listener listener;
    private BillingClient bc;
    private final Map<String, ProductDetails> details = new HashMap<>();
    private boolean ready = false;

    public BillingManager(Context c, Listener l) {
        ctx = c.getApplicationContext(); listener = l;
        bc = BillingClient.newBuilder(ctx)
            .setListener(this)
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .build();
        connect();
    }

    private void connect() {
        bc.startConnection(new BillingClientStateListener() {
            @Override public void onBillingSetupFinished(BillingResult r) {
                if (r.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    ready = true;
                    restorePurchases();
                    queryProducts();
                }
            }
            @Override public void onBillingServiceDisconnected() { ready = false; }
        });
    }

    private void restorePurchases() {
        bc.queryPurchasesAsync(QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP).build(), (r, list) -> {
            if (r.getResponseCode() != BillingClient.BillingResponseCode.OK) return;
            for (Purchase p : list) handlePurchase(p);
        });
    }

    private void queryProducts() {
        List<QueryProductDetailsParams.Product> prods = new ArrayList<>();
        prods.add(QueryProductDetailsParams.Product.newBuilder()
            .setProductId(REMOVE_ADS).setProductType(BillingClient.ProductType.INAPP).build());
        for (String id : PACK_IDS) prods.add(QueryProductDetailsParams.Product.newBuilder()
            .setProductId(id).setProductType(BillingClient.ProductType.INAPP).build());
        bc.queryProductDetailsAsync(QueryProductDetailsParams.newBuilder().setProductList(prods).build(),
            (r, result) -> {
                if (r.getResponseCode() != BillingClient.BillingResponseCode.OK) return;
                details.clear();
                for (ProductDetails pd : result.getProductDetailsList()) details.put(pd.getProductId(), pd);
                listener.onProductsReady();
            });
    }

    public boolean hasProducts() { return !details.isEmpty(); }
    public ProductDetails getDetails(String id) { return details.get(id); }

    public String priceOf(String id) {
        ProductDetails pd = details.get(id);
        if (pd == null) return null;
        List<ProductDetails.OneTimePurchaseOfferDetails> offers = pd.getOneTimePurchaseOfferDetailsList();
        if (offers != null && !offers.isEmpty()) return offers.get(0).getFormattedPrice();
        ProductDetails.OneTimePurchaseOfferDetails legacy = pd.getOneTimePurchaseOfferDetails();
        return legacy != null ? legacy.getFormattedPrice() : null;
    }

    public void buy(Activity act, String productId) {
        if (!ready) { connect(); }
        ProductDetails pd = details.get(productId);
        if (pd == null) return;
        BillingFlowParams.ProductDetailsParams.Builder b =
            BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(pd);
        List<ProductDetails.OneTimePurchaseOfferDetails> offers = pd.getOneTimePurchaseOfferDetailsList();
        if (offers != null && !offers.isEmpty()) {
            String token = offers.get(0).getOfferToken();
            if (token != null && !token.isEmpty()) b.setOfferToken(token);
        }
        bc.launchBillingFlow(act, BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(Collections.singletonList(b.build())).build());
    }

    @Override public void onPurchasesUpdated(BillingResult r, List<Purchase> purchases) {
        if (r.getResponseCode() == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (Purchase p : purchases) handlePurchase(p);
        }
    }

    private void handlePurchase(Purchase p) {
        if (p.getPurchaseState() != Purchase.PurchaseState.PURCHASED) return;
        for (String id : p.getProducts()) {
            if (REMOVE_ADS.equals(id)) {
                if (!p.isAcknowledged()) {
                    bc.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(p.getPurchaseToken()).build(), rr -> {});
                }
                listener.onAdsRemovedOwned();
            } else {
                int idx = Arrays.asList(PACK_IDS).indexOf(id);
                if (idx >= 0) {
                    long coins = PACK_COINS[idx];
                    bc.consumeAsync(ConsumeParams.newBuilder()
                        .setPurchaseToken(p.getPurchaseToken()).build(), (rr, token) -> {
                        if (rr.getResponseCode() == BillingClient.BillingResponseCode.OK)
                            listener.onCoinsPurchased(coins);
                    });
                }
            }
        }
    }
}

package com.crab.friendlycc;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.AcknowledgePurchaseResponseListener;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.ConsumeParams;
import com.android.billingclient.api.ConsumeResponseListener;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchaseHistoryRecord;
import com.android.billingclient.api.PurchaseHistoryResponseListener;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.SkuDetails;
import com.android.billingclient.api.SkuDetailsParams;
import com.android.billingclient.api.SkuDetailsResponseListener;

import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private String TAG =  "MainActivity";
    private BillingClient billingClient;
    private Handler handler = new Handler();
    private final int consumeImmediately = 0;
    private final int consumeDelay = 1;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        billingClient = BillingClient.newBuilder(this).enablePendingPurchases().setListener(new PurchasesUpdatedListener() {
            @Override
            public void onPurchasesUpdated(BillingResult billingResult, @Nullable List<Purchase> purchases) {
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && purchases != null) {
                    for (final Purchase purchase : purchases) {
                        if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
                            // Acknowledge purchase and grant the item to the user
                            Log.i(TAG, "Purchase success");
                            //确认购买交易，不然三天后会退款给用户
                            if (!purchase.isAcknowledged()) {
                                acknowledgePurchase(purchase);
                            }
                            //消耗品 开始消耗
                            handler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    consumePuchase(purchase, consumeDelay);
                                }
                            }, 2000);
                            //TODO:发放商品
                        } else if (purchase.getPurchaseState() == Purchase.PurchaseState.PENDING) {
                            //需要用户确认
                            Log.i(TAG, "Purchase pending,need to check");

                        }
                    }
                } else if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.USER_CANCELED) {
                    //用户取消
                    Log.i(TAG, "Purchase cancel");
                } else {
                    //支付错误
                    Log.i(TAG, "Pay result error,code=" + billingResult.getResponseCode() + "\nerrorMsg=" + billingResult.getDebugMessage());
                }
            }
        }).build();
        //连接google服务器
        billingClient.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(BillingResult billingResult) {
                Log.i(TAG, "billingResult Code=" + billingResult.getResponseCode());
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    // The BillingClient is ready. You can query purchases here.
                    Log.i(TAG, "Init success,The BillingClient is ready");
                    //每次进行重连的时候都应该消耗之前缓存的商品，不然可能会导致用户支付不了
                    queryAndConsumePurchase();

                } else {
                    Log.i(TAG, "Init failed,The BillingClient is not ready,code=" + billingResult.getResponseCode() + "\nMsg=" + billingResult.getDebugMessage());
                }
            }

            @Override
            public void onBillingServiceDisconnected() {
                // Try to restart the connection on the next request to
                // Google Play by calling the startConnection() method.
                Log.i(TAG, "Init failed,Billing Service Disconnected,The BillingClient is not ready");
            }
        });
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()){
            case R.id.google_pay:
                //传入你在google上申请的对应商品的支付码
                pay("friendlycc_1000");
        }
    }

    private void pay(String payCode){
        List<String> skuList = new ArrayList<>();
        skuList.add(payCode);
        SkuDetailsParams.Builder params = SkuDetailsParams.newBuilder();
        params.setSkusList(skuList).setType(BillingClient.SkuType.INAPP);
        billingClient.querySkuDetailsAsync(params.build(),
                new SkuDetailsResponseListener() {
                    @Override
                    public void onSkuDetailsResponse(BillingResult billingResult, List<SkuDetails> skuDetailsList) {
                        Log.i(TAG, "querySkuDetailsAsync=getResponseCode==" + billingResult.getResponseCode() + ",skuDetailsList.size=" + skuDetailsList.size());
                        // Process the result.
                        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                            if (skuDetailsList.size() > 0) {
                                for (SkuDetails skuDetails : skuDetailsList) {
                                    String sku = skuDetails.getSku();
                                    String price = skuDetails.getPrice();
                                    Log.i(TAG, "Sku=" + sku + ",price=" + price);
                                    BillingFlowParams flowParams = BillingFlowParams.newBuilder()
                                            .setSkuDetails(skuDetails)
                                            .build();
                                    int responseCode = billingClient.launchBillingFlow(MainActivity.this, flowParams).getResponseCode();
                                    if (responseCode == BillingClient.BillingResponseCode.OK) {
                                        Log.i(TAG, "成功启动google支付");
                                    } else {
                                        //BILLING_RESPONSE_RESULT_OK	0	成功
                                        //BILLING_RESPONSE_RESULT_USER_CANCELED	1	用户按上一步或取消对话框
                                        //BILLING_RESPONSE_RESULT_SERVICE_UNAVAILABLE	2	网络连接断开
                                        //BILLING_RESPONSE_RESULT_BILLING_UNAVAILABLE	3	所请求的类型不支持 Google Play 结算服务 AIDL 版本
                                        //BILLING_RESPONSE_RESULT_ITEM_UNAVAILABLE	4	请求的商品已不再出售
                                        //BILLING_RESPONSE_RESULT_DEVELOPER_ERROR	5	提供给 API 的参数无效。此错误也可能说明应用未针对 Google Play 结算服务正确签名或设置，或者在其清单中缺少必要的权限。
                                        //BILLING_RESPONSE_RESULT_ERROR	6	API 操作期间出现严重错误
                                        //BILLING_RESPONSE_RESULT_ITEM_ALREADY_OWNED	7	未能购买，因为已经拥有此商品
                                        //BILLING_RESPONSE_RESULT_ITEM_NOT_OWNED	8	未能消费，因为尚未拥有此商品
                                        Log.i(TAG, "LaunchBillingFlow Fail,code=" + responseCode);
                                    }
                                }
                            } else {
                                Log.i(TAG, "skuDetailsList is empty.");
                            }
                        } else {
                            Log.i(TAG, "Get SkuDetails Failed,Msg=" + billingResult.getDebugMessage());
                        }
                    }
                });
    }

    //查询最近的购买交易，并消耗商品
    private void queryAndConsumePurchase() {
        //queryPurchases() 方法会使用 Google Play 商店应用的缓存，而不会发起网络请求
        billingClient.queryPurchaseHistoryAsync(BillingClient.SkuType.INAPP,
                new PurchaseHistoryResponseListener() {
                    @Override
                    public void onPurchaseHistoryResponse(BillingResult billingResult,
                                                          List<PurchaseHistoryRecord> purchaseHistoryRecordList) {
                        {
                            if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && purchaseHistoryRecordList != null) {
                                for (PurchaseHistoryRecord purchaseHistoryRecord : purchaseHistoryRecordList) {
                                    // Process the result.
                                    //确认购买交易，不然三天后会退款给用户
                                    try {
                                        Purchase purchase = new Purchase(purchaseHistoryRecord.getOriginalJson(), purchaseHistoryRecord.getSignature());
                                        if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
                                            //消耗品 开始消耗
                                            consumePuchase(purchase, consumeImmediately);
                                            //确认购买交易
                                            if (!purchase.isAcknowledged()) {
                                                acknowledgePurchase(purchase);
                                            }
                                            //TODO：这里可以添加订单找回功能，防止变态用户付完钱就杀死App的这种
                                        }
                                    } catch (JSONException e) {
                                        e.printStackTrace();
                                    }
                                }
                            }
                        }
                    }
                });
    }

    //消耗商品
    private void consumePuchase(final Purchase purchase, final int state) {
        ConsumeParams.Builder consumeParams = ConsumeParams.newBuilder();
        consumeParams.setPurchaseToken(purchase.getPurchaseToken());
        billingClient.consumeAsync(consumeParams.build(), new ConsumeResponseListener() {
            @Override
            public void onConsumeResponse(BillingResult billingResult, String purchaseToken) {
                Log.i(TAG, "onConsumeResponse, code=" + billingResult.getResponseCode());
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    Log.i(TAG, "onConsumeResponse,code=BillingResponseCode.OK");
                    if (state == consumeImmediately) {

                    }
                } else {
                    //如果消耗不成功，那就再消耗一次
                    Log.i(TAG, "onConsumeResponse=getDebugMessage==" + billingResult.getDebugMessage());
                    if (state == consumeDelay && billingResult.getDebugMessage().contains("Server error, please try again")) {
                        handler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                queryAndConsumePurchase();
                            }
                        }, 5 * 1000);
                    }
                }
            }
        });
    }

    //确认订单
    private void acknowledgePurchase(Purchase purchase) {
        AcknowledgePurchaseParams acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.getPurchaseToken())
                .build();
        AcknowledgePurchaseResponseListener acknowledgePurchaseResponseListener = new AcknowledgePurchaseResponseListener() {
            @Override
            public void onAcknowledgePurchaseResponse(BillingResult billingResult) {
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    Log.i(TAG, "Acknowledge purchase success");
                } else {
                    Log.i(TAG, "Acknowledge purchase failed,code=" + billingResult.getResponseCode() + ",\nerrorMsg=" + billingResult.getDebugMessage());
                }
            }
        };
        billingClient.acknowledgePurchase(acknowledgePurchaseParams, acknowledgePurchaseResponseListener);
    }



}
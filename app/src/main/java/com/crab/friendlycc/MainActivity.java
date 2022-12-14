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
                            //??????????????????????????????????????????????????????
                            if (!purchase.isAcknowledged()) {
                                acknowledgePurchase(purchase);
                            }
                            //????????? ????????????
                            handler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    consumePuchase(purchase, consumeDelay);
                                }
                            }, 2000);
                            //TODO:????????????
                        } else if (purchase.getPurchaseState() == Purchase.PurchaseState.PENDING) {
                            //??????????????????
                            Log.i(TAG, "Purchase pending,need to check");

                        }
                    }
                } else if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.USER_CANCELED) {
                    //????????????
                    Log.i(TAG, "Purchase cancel");
                } else {
                    //????????????
                    Log.i(TAG, "Pay result error,code=" + billingResult.getResponseCode() + "\nerrorMsg=" + billingResult.getDebugMessage());
                }
            }
        }).build();
        //??????google?????????
        billingClient.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(BillingResult billingResult) {
                Log.i(TAG, "billingResult Code=" + billingResult.getResponseCode());
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    // The BillingClient is ready. You can query purchases here.
                    Log.i(TAG, "Init success,The BillingClient is ready");
                    //?????????????????????????????????????????????????????????????????????????????????????????????????????????
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
                //????????????google????????????????????????????????????
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
                                        Log.i(TAG, "????????????google??????");
                                    } else {
                                        //BILLING_RESPONSE_RESULT_OK	0	??????
                                        //BILLING_RESPONSE_RESULT_USER_CANCELED	1	????????????????????????????????????
                                        //BILLING_RESPONSE_RESULT_SERVICE_UNAVAILABLE	2	??????????????????
                                        //BILLING_RESPONSE_RESULT_BILLING_UNAVAILABLE	3	??????????????????????????? Google Play ???????????? AIDL ??????
                                        //BILLING_RESPONSE_RESULT_ITEM_UNAVAILABLE	4	??????????????????????????????
                                        //BILLING_RESPONSE_RESULT_DEVELOPER_ERROR	5	????????? API ????????????????????????????????????????????????????????? Google Play ?????????????????????????????????????????????????????????????????????????????????
                                        //BILLING_RESPONSE_RESULT_ERROR	6	API ??????????????????????????????
                                        //BILLING_RESPONSE_RESULT_ITEM_ALREADY_OWNED	7	??????????????????????????????????????????
                                        //BILLING_RESPONSE_RESULT_ITEM_NOT_OWNED	8	??????????????????????????????????????????
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

    //?????????????????????????????????????????????
    private void queryAndConsumePurchase() {
        //queryPurchases() ??????????????? Google Play ???????????????????????????????????????????????????
        billingClient.queryPurchaseHistoryAsync(BillingClient.SkuType.INAPP,
                new PurchaseHistoryResponseListener() {
                    @Override
                    public void onPurchaseHistoryResponse(BillingResult billingResult,
                                                          List<PurchaseHistoryRecord> purchaseHistoryRecordList) {
                        {
                            if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && purchaseHistoryRecordList != null) {
                                for (PurchaseHistoryRecord purchaseHistoryRecord : purchaseHistoryRecordList) {
                                    // Process the result.
                                    //??????????????????????????????????????????????????????
                                    try {
                                        Purchase purchase = new Purchase(purchaseHistoryRecord.getOriginalJson(), purchaseHistoryRecord.getSignature());
                                        if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
                                            //????????? ????????????
                                            consumePuchase(purchase, consumeImmediately);
                                            //??????????????????
                                            if (!purchase.isAcknowledged()) {
                                                acknowledgePurchase(purchase);
                                            }
                                            //TODO??????????????????????????????????????????????????????????????????????????????App?????????
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

    //????????????
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
                    //?????????????????????????????????????????????
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

    //????????????
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
package com.nuvopoint.cordova;

import android.content.Intent;
import android.os.Bundle;

import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.PluginResult;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaWebView;

import com.google.gson.Gson;
import com.sumup.merchant.api.SumUpState;
import com.sumup.merchant.api.SumUpAPI;
import com.sumup.merchant.api.SumUpLogin;
import com.sumup.merchant.api.SumUpPayment;
import com.sumup.merchant.Models.TransactionInfo;

import java.lang.reflect.Array;
import java.math.BigDecimal;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class SumUp extends CordovaPlugin {

  private static final int REQUEST_CODE_LOGIN = 1;
  private static final int REQUEST_CODE_PAYMENT = 2;
  private static final int REQUEST_CODE_PAYMENT_SETTINGS = 3;
  private static final int REQUEST_CODE_LOGIN_SETTING = 4;

  private CallbackContext callback = null;

  @Override
  public void initialize(CordovaInterface cordova, CordovaWebView webView) {
    super.initialize(cordova, webView);

    cordova.getActivity().runOnUiThread(() -> SumUpState.init(cordova.getActivity()));
  }

  @Override
  public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
    String affiliateKey = this.cordova.getActivity().getString(cordova.getActivity().getResources()
      .getIdentifier("SUMUP_API_KEY", "string", cordova.getActivity().getPackageName()));

    if (action.equals("login")) {
      Runnable runnable = () -> {
        Object accessToken = null;
        try {
          accessToken = args.get(0);
        } catch (Exception e) {
          System.out.println(e.getMessage());
        }
        SumUpLogin sumUpLogin;
        if (accessToken != null) {
          sumUpLogin = SumUpLogin.builder(affiliateKey).accessToken(accessToken.toString()).build();
        } else {
          sumUpLogin = SumUpLogin.builder(affiliateKey).build();
        }
        SumUpAPI.openLoginActivity(cordova.getActivity(), sumUpLogin, REQUEST_CODE_LOGIN);
      };

      callback = callbackContext;
      cordova.setActivityResultCallback(this);
      cordova.getActivity().runOnUiThread(runnable);
      return true;
    }

    if (action.equals("settings")) {
      callback = callbackContext;
      cordova.setActivityResultCallback(this);
      cordova.getActivity().runOnUiThread(() -> SumUpAPI.openPaymentSettingsActivity(cordova.getActivity(), REQUEST_CODE_LOGIN_SETTING));
      return true;
    }

    if (action.equals("logout")) {
      callback = callbackContext;
      cordova.setActivityResultCallback(this);
      cordova.getActivity().runOnUiThread(SumUpAPI::logout);
      return true;
    }

    if (action.equals("prepare")) {
      cordova.getActivity().runOnUiThread(SumUpAPI::prepareForCheckout);
      return true;
    }

    if (action.equals("pay")) {

      BigDecimal amount;
      try {
        amount = new BigDecimal(args.get(0).toString());
      } catch (Exception e) {
        callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, "Can't parse amount"));
        return false;
      }

      SumUpPayment.Currency currency;
      try {
        currency = SumUpPayment.Currency.valueOf(args.get(1).toString());
      } catch (Exception e) {
        callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, "Can't parse currency"));
        return false;
      }

      String title;
      try {
        title = args.get(2).toString();
      } catch (Exception e) {
        callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, "Can't parse title"));
        return false;
      }

      Runnable runnable = () -> {
        SumUpPayment payment = SumUpPayment.builder()
          .total(amount)
          .currency(currency)
          .title(title)
          .skipSuccessScreen()
          .build();

        SumUpAPI.checkout(cordova.getActivity(), payment, REQUEST_CODE_PAYMENT);
      };

      callback = callbackContext;
      cordova.setActivityResultCallback(this);
      cordova.getActivity().runOnUiThread(runnable);
      return true;
    }

    return false;
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent data) {

    if (requestCode == REQUEST_CODE_LOGIN) {
      try {
        Bundle extras = data.getExtras();
        Integer code = extras.getInt(SumUpAPI.Response.RESULT_CODE);
        String message = extras.getString(SumUpAPI.Response.MESSAGE);

        JSONObject obj = new JSONObject();
        obj.put("code", code);
        obj.put("message", message);

        if (code == 1) {
          PluginResult result = new PluginResult(PluginResult.Status.OK, obj);
          result.setKeepCallback(true);
          callback.sendPluginResult(result);
        } else {
          PluginResult result = new PluginResult(PluginResult.Status.ERROR, obj);
          result.setKeepCallback(true);
          callback.sendPluginResult(result);
        }

      } catch (Exception e) {
        callback.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, e.getMessage()));
      }
    }

    if (requestCode == REQUEST_CODE_PAYMENT) {

      try {
        Bundle extras = data.getExtras();
        Integer code = extras.getInt(SumUpAPI.Response.RESULT_CODE);
        String message = extras.getString(SumUpAPI.Response.MESSAGE);


        JSONObject obj = new JSONObject();
        obj.put("code", code);
        obj.put("message", message);

        if (!extras.isEmpty()) {
          TransactionInfo txinfo = extras.getParcelable(SumUpAPI.Response.TX_INFO);

          obj.put("transaction_code", txinfo.getTransactionCode());
          obj.put("merchant_code", txinfo.getMerchantCode());
          obj.put("amount", txinfo.getAmount());
          obj.put("tip_amount", txinfo.getTipAmount());
          obj.put("vat_amount", txinfo.getVatAmount());
          obj.put("currency", txinfo.getCurrency());
          obj.put("status", txinfo.getStatus());
          obj.put("payment_type", txinfo.getPaymentType());
          obj.put("entry_mode", txinfo.getEntryMode());
          obj.put("installments", txinfo.getInstallments());
          obj.put("card_type", txinfo.getCard().getType());
        }

        if (code == 1) {
          PluginResult result = new PluginResult(PluginResult.Status.OK, obj);
          result.setKeepCallback(true);
          callback.sendPluginResult(result);
        } else {
          PluginResult result = new PluginResult(PluginResult.Status.ERROR, obj);
          result.setKeepCallback(true);
          callback.sendPluginResult(result);
        }

      } catch (Exception e) {
        callback.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, e.getMessage()));
      }
    }

    if (requestCode == REQUEST_CODE_LOGIN_SETTING) {
      SumUpAPI.openPaymentSettingsActivity(this.cordova.getActivity(), REQUEST_CODE_PAYMENT_SETTINGS);
    }

    if (requestCode == REQUEST_CODE_PAYMENT_SETTINGS) {
      try {

        Bundle extras = data.getExtras();
        Integer code = extras.getInt(SumUpAPI.Response.RESULT_CODE);
        String message = extras.getString(SumUpAPI.Response.MESSAGE);

        JSONObject obj = new JSONObject();
        obj.put("code", code);
        obj.put("message", message);

        if (code == 1) {
          PluginResult result = new PluginResult(PluginResult.Status.OK, obj);
          result.setKeepCallback(true);
          callback.sendPluginResult(result);
        } else {
          PluginResult result = new PluginResult(PluginResult.Status.ERROR, obj);
          result.setKeepCallback(true);
          callback.sendPluginResult(result);
        }

      } catch (Exception e) {
        callback.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, e.getMessage()));
      }
    }
  }
}

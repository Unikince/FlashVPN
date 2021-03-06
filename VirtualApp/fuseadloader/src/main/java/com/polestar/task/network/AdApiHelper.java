package com.polestar.task.network;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import com.polestar.ad.AdLog;
import com.polestar.task.ADErrorCode;
import com.polestar.task.IGeneralErrorListener;
import com.polestar.task.IProductStatusListener;
import com.polestar.task.ITaskStatusListener;
import com.polestar.task.IUserStatusListener;
import com.polestar.task.network.datamodels.User;
import com.polestar.task.network.responses.ProductsResponse;
import com.polestar.task.network.responses.TasksResponse;
import com.polestar.task.network.responses.UserProductResponse;
import com.polestar.task.network.responses.UserTaskResponse;
import com.polestar.task.network.services.AuthApi;
import com.polestar.task.network.services.ProductsApi;
import com.polestar.task.network.services.TasksApi;
import com.witter.msg.Sender;

import java.io.IOException;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AdApiHelper {
    protected static <T> ADErrorCode createADErrorFromResponse(Response<T> response) {
        try {
            String rawErrorBody = response.errorBody().string();
            return new ADErrorCode(response.code(), rawErrorBody);
        } catch (IOException e) {
            e.printStackTrace();
            return new ADErrorCode(response.code(), response.message());
        }
    }

    public static final int REQUEST_SUCCEED = 0;
    public static final int ERR_REQUEST_TOO_FREQUENT = 1;
    public static final int SUBSCRIBE_STATUTS_NONE = 0;
    public static final int SUBSCRIBE_STATUTS_VALID = 1;

    private static String KEY_REGISTER = "register";
    private static String KEY_GET_PRODUCTS = "getProducts";
    private static String KEY_GET_TASKS = "getTasks";
    private static String KEY_CONSUME_PRODUCT = "consumeProduct";
    private static String KEY_FINISH_TASK = "finishTask";

    private static final HashMap<String, Date> sTimeMapping = new HashMap<>();

    private static final HashMap<String, HashSet<Date>> sRangeMapping = new HashMap<>();

    protected static String getSecret(String deviceId) {
        return deviceId + "#" + Calendar.getInstance().getTimeInMillis();
    }

    protected static boolean canDoSingleRequest(String requestKey, long thresholder) {
        Date lastTime = sTimeMapping.get(requestKey);
        if (lastTime == null) {
            sTimeMapping.put(requestKey, Calendar.getInstance().getTime());
            return true;
        }

        if (MiscUtils.tooCloseWithNow(lastTime, thresholder)) {
            Log.w(Configuration.HTTP_TAG, "Too close with last http request time " + lastTime.toString() + " for " + requestKey);
            return false;
        } else {
            sTimeMapping.put(requestKey, Calendar.getInstance().getTime());
            return true;
        }
    }

    protected static boolean canDoRangeRequest(String requestKey, long timeRange, int maxCount) {
        Date now = Calendar.getInstance().getTime();
        HashSet<Date> alreadyDone = sRangeMapping.get(requestKey);
        if (alreadyDone == null) {
            alreadyDone = new HashSet<Date>();
            sRangeMapping.put(requestKey, alreadyDone);
        }

        HashSet<Date> toRemove = new HashSet<>();
        for (Date date : alreadyDone) {
            if (!MiscUtils.tooClose(date, now, timeRange)) {
                Log.i(Configuration.HTTP_TAG, " Will remove " + date.toString() + " for " + requestKey);
                toRemove.add(date);
            }
        }
        for (Date date : toRemove) {
            alreadyDone.remove(date);
        }
        Log.i(Configuration.HTTP_TAG, " After remove, " + requestKey + " has " + alreadyDone.size() + " in range " + timeRange);
        if (alreadyDone.size() >= maxCount) {
            return false;
        }

        alreadyDone.add(now);
        return true;
    }

    protected static int checkRequestTooFrequent(String key, final IGeneralErrorListener listener, boolean force) {
        if (!force &&
                (!canDoSingleRequest(key, Configuration.API_COMMON_INTERVAL)
                        || !canDoRangeRequest(key, Configuration.API_RANGE_INTERVAL, Configuration.API_RANGE_MAX_COUNT))) {
            if (listener != null) {
                listener.onGeneralError(ADErrorCode.createTooManyRequests());
            }
            return ERR_REQUEST_TOO_FREQUENT;
        }
        return REQUEST_SUCCEED;
    }

    public static int register(Context context, String deviceId, final IUserStatusListener listener, boolean force) {
        return register(context, deviceId, listener, force, SUBSCRIBE_STATUTS_NONE);
    }

    public static int register(Context context, String deviceId, final IUserStatusListener listener, boolean force, int subscribe) {
        if (checkRequestTooFrequent(KEY_REGISTER, listener, force) == ERR_REQUEST_TOO_FREQUENT) {
            return ERR_REQUEST_TOO_FREQUENT;
        }

        AuthApi service = RetrofitServiceFactory.createSimpleRetroFitService(AuthApi.class);
        android.content.res.Configuration configuration = context.getResources().getConfiguration();
        Locale locale;
        if (Build.VERSION.SDK_INT >= 24) {
            locale = configuration.getLocales().get(0);
        } else {
            locale = configuration.locale;
        }
        Call<User> call = service.registerAnonymous(Configuration.APP_VERSION_CODE,
                Configuration.PKG_NAME, Sender.Send(getSecret(deviceId)),
                configuration.mcc, configuration.mnc, locale.toString(), subscribe);
        call.enqueue(new Callback<User>(){
            @Override
            public void onResponse(Call<User> call, Response<User> response){
                AdLog.i(Configuration.HTTP_TAG, "onResponse: "+ response.toString());

                switch(response.code()){
                    case 200:
                        User ur = response.body();
                        AdLog.i(Configuration.HTTP_TAG, "onResponse: "+ ur.mReferralCode);
                        if (listener != null) {
                            listener.onRegisterSuccess(ur);
                        }
                        break;
                    default:
                        if (listener != null) {
                            listener.onGeneralError(createADErrorFromResponse(response));
                        }
                        break;
                }
            }

            @Override
            public void onFailure(Call<User> call, Throwable t){
                AdLog.e(Configuration.HTTP_TAG, "onFailure: " + t.getMessage());
                if (listener != null) {
                    if (ErrorCodeInterceptor.isAdErrorMsg(t.getMessage())) {
                        listener.onRegisterFailed(ADErrorCode.createFromAdErrMsg(t.getMessage()));
                    } else {
                        listener.onRegisterFailed(ADErrorCode.createServerDown());
                    }
                }
            }
        });

        return REQUEST_SUCCEED;
    }

    public static int getAvailableProducts(String deviceId, final IProductStatusListener listener) {
        return getAvailableProducts(deviceId, listener, false);
    }

    public static int getAvailableProducts(String deviceId, final IProductStatusListener listener, boolean force) {
        if (checkRequestTooFrequent(KEY_GET_PRODUCTS, listener, force) == ERR_REQUEST_TOO_FREQUENT) {
            return ERR_REQUEST_TOO_FREQUENT;
        }

        ProductsApi service = RetrofitServiceFactory.createSimpleRetroFitService(ProductsApi.class);
        Call<ProductsResponse> call = service.getAvailableProducts(Configuration.APP_VERSION_CODE,
                Configuration.PKG_NAME, Sender.Send(getSecret(deviceId)));
        call.enqueue(new Callback<ProductsResponse>() {
            @Override
            public void onResponse(Call<ProductsResponse> call, Response<ProductsResponse> response) {
                AdLog.i(Configuration.HTTP_TAG, "onResponse: "+ response.toString());

                switch(response.code()){
                    case 200:
                        ProductsResponse ur = response.body();
                        AdLog.i(Configuration.HTTP_TAG, "onResponse product count: "+ ur.mProducts.size());
                        if (listener != null) {
                            listener.onGetAllAvailableProducts(ur.mProducts);
                        }
                        break;
                    default:
                        if (listener != null) {
                            listener.onGeneralError(createADErrorFromResponse(response));
                        }
                        break;
                }
            }

            @Override
            public void onFailure(Call<ProductsResponse> call, Throwable t) {
                AdLog.e(Configuration.HTTP_TAG, "onFailure: "+ t.getMessage());
                // getProducts has no predefined err, so this must be server error
                if (listener != null) {
                    if (ErrorCodeInterceptor.isAdErrorMsg(t.getMessage())) {
                        listener.onGeneralError(ADErrorCode.createFromAdErrMsg(t.getMessage()));
                    } else {
                        listener.onGeneralError(ADErrorCode.createServerDown());
                    }
                }
            }
        });

        return REQUEST_SUCCEED;
    }

    public static int consumeProduct(String deviceId, final long id, final int amount,
                                     final String email, final String info,
                                     final IProductStatusListener listener) {
        return consumeProduct(deviceId, id, amount, email, info, listener, false);
    }

    public static int consumeProduct(String deviceId, final long id, final int amount,
                                     final String email, final String info,
                                     final IProductStatusListener listener,
                                        boolean force) {
        if (checkRequestTooFrequent(KEY_CONSUME_PRODUCT, listener, force) == ERR_REQUEST_TOO_FREQUENT) {
            return ERR_REQUEST_TOO_FREQUENT;
        }


        ProductsApi service = RetrofitServiceFactory.createSimpleRetroFitService(ProductsApi.class);
        Call<UserProductResponse> call = service.consumeProduct(Configuration.APP_VERSION_CODE,
                Configuration.PKG_NAME, Sender.Send(getSecret(deviceId)),
                id, amount, email, info);
        call.enqueue(new Callback<UserProductResponse>() {
            @Override
            public void onResponse(Call<UserProductResponse> call, Response<UserProductResponse> response) {
                AdLog.i(Configuration.HTTP_TAG, "onResponse: "+ response.toString());

                switch(response.code()){
                    case 200:
                        UserProductResponse ur = response.body();
                        AdLog.i(Configuration.HTTP_TAG, "onResponse cost: "+ ur.mUserProduct.mCost);
                        if (listener != null) {
                            listener.onConsumeSuccess(id, amount, ur.mUserProduct.mCost, ur.mUser.mBalance);
                        }
                        break;
                    default:
                        if (listener != null) {
                            listener.onGeneralError(createADErrorFromResponse(response));
                        }
                        break;
                }
            }

            @Override
            public void onFailure(Call<UserProductResponse> call, Throwable t) {
                AdLog.e(Configuration.HTTP_TAG, "onFailure: " + t.getMessage());
                if (listener != null) {
                    if (ErrorCodeInterceptor.isAdErrorMsg(t.getMessage())) {
                        listener.onConsumeFail(ADErrorCode.createFromAdErrMsg(t.getMessage()));
                    } else {
                        listener.onGeneralError(ADErrorCode.createServerDown());
                    }
                }
            }
        });

        return REQUEST_SUCCEED;
    }

    public static int getAvailableTasks(String deviceId, final ITaskStatusListener listener) {
        return getAvailableTasks(deviceId, listener, false);
    }

    public static int getAvailableTasks(String deviceId, final ITaskStatusListener listener, boolean force) {
        if (checkRequestTooFrequent(KEY_GET_TASKS, listener, force) == ERR_REQUEST_TOO_FREQUENT) {
            return ERR_REQUEST_TOO_FREQUENT;
        }

        TasksApi service = RetrofitServiceFactory.createSimpleRetroFitService(TasksApi.class);
        Call<TasksResponse> call = service.getAvailableTasks(Configuration.APP_VERSION_CODE,
                Configuration.PKG_NAME, Sender.Send(getSecret(deviceId)));
        call.enqueue(new Callback<TasksResponse>() {
            @Override
            public void onResponse(Call<TasksResponse> call, Response<TasksResponse> response) {
                AdLog.i(Configuration.HTTP_TAG, "onResponse: "+ response.toString());

                switch(response.code()){
                    case 200:
                        TasksResponse ur = response.body();
                        AdLog.i(Configuration.HTTP_TAG, "onResponse task count "+ ur.mTasks.size());
                        if (listener != null) {
                            listener.onGetAllAvailableTasks(ur.mTasks);
                        }
                        break;
                    default:
                        if (listener != null) {
                            listener.onGeneralError(createADErrorFromResponse(response));
                        }
                        break;
                }
            }

            @Override
            public void onFailure(Call<TasksResponse> call, Throwable t) {
                AdLog.e(Configuration.HTTP_TAG, "onFailure: " + t.getMessage());
                if (listener != null) {
                    if (ErrorCodeInterceptor.isAdErrorMsg(t.getMessage())) {
                        listener.onGeneralError(ADErrorCode.createFromAdErrMsg(t.getMessage()));
                    } else {
                        listener.onGeneralError(ADErrorCode.createServerDown());
                    }
                }
            }
        });

        return REQUEST_SUCCEED;
    }

    public static int finishTask(String deviceId, final long id, String referralCode, final ITaskStatusListener listener) {
        return finishTask(deviceId, id, referralCode, listener, false);
    }

    public static int finishTask(String deviceId, final long id, String referralCode, final ITaskStatusListener listener, boolean force) {
        if (checkRequestTooFrequent(KEY_FINISH_TASK, listener, force) == ERR_REQUEST_TOO_FREQUENT) {
            return ERR_REQUEST_TOO_FREQUENT;
        }

        TasksApi service = RetrofitServiceFactory.createSimpleRetroFitService(TasksApi.class);
        Call<UserTaskResponse> call = service.finishTask(Configuration.APP_VERSION_CODE,
                Configuration.PKG_NAME, Sender.Send(getSecret(deviceId)),
                id, referralCode);
        call.enqueue(new Callback<UserTaskResponse>() {
            @Override
            public void onResponse(Call<UserTaskResponse> call, Response<UserTaskResponse> response) {
                AdLog.i(Configuration.HTTP_TAG, "onResponse: "+ response.toString());

                switch(response.code()){
                    case 200:
                        UserTaskResponse ur = response.body();
                        AdLog.i(Configuration.HTTP_TAG, "onResponse paid: "+ ur.mUserTask.mPayout);
                        if (listener != null) {
                            listener.onTaskSuccess(id, ur.mUserTask.getPayout(), ur.mUser.mBalance);
                        }
                        break;
                    default:
                        if (listener != null) {
                            listener.onGeneralError(createADErrorFromResponse(response));
                        }
                        break;
                }
            }

            @Override
            public void onFailure(Call<UserTaskResponse> call, Throwable t) {
                AdLog.e(Configuration.HTTP_TAG, "onFailure: " + t.getMessage());
                if (listener != null) {
                    if (ErrorCodeInterceptor.isAdErrorMsg(t.getMessage())) {
                        listener.onTaskFail(id, ADErrorCode.createFromAdErrMsg(t.getMessage()));
                    } else {
                        listener.onGeneralError(ADErrorCode.createServerDown());
                    }
                }
            }
        });

        return REQUEST_SUCCEED;
    }
}

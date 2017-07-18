package com.polestar.ad.adapters;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import com.google.android.gms.ads.AdSize;
import com.polestar.ad.AdConfig;
import com.polestar.ad.AdConstants;
import com.polestar.ad.AdLog;
import com.polestar.domultiple.utils.PreferencesUtils;
import com.polestar.domultiple.utils.RemoteConfig;
import com.polestar.imageloader.ImageLoader;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;


/**
 * Created by guojia on 2016/11/15.
 */

public class FuseAdLoader {
    private Context mContext;
    private List<AdConfig> mNativeAdConfigList = new ArrayList();
    private HashMap<String, IAdAdapter> mNativeAdCache = new HashMap<>();
    private IAdLoadListener mListener;
    private int lastIdx =0;
    private String mSlot;
    private AdSize mBannerAdSize;
    private long mProtectOverTime = 0;
    private Handler mHandler = new Handler(Looper.getMainLooper());
    private int mLoadingBits;

    private static HashMap<String, FuseAdLoader> sAdLoaderMap = new HashMap<>();
    public synchronized static FuseAdLoader get(String slot, Context appContext) {
        FuseAdLoader adLoader = sAdLoaderMap.get(slot);
        if (adLoader == null) {
            adLoader = new FuseAdLoader(slot, appContext);
            sAdLoaderMap.put(slot, adLoader);
        }
        return adLoader;
    }

    private FuseAdLoader(String slot, Context context) {
        this.mContext = context;
        mSlot = slot;
        List<AdConfig> adSources = RemoteConfig.getAdConfigList(mSlot);
        addAdConfigList(adSources);
    }

    public static final HashSet<String> SUPPORTED_TYPES = new HashSet<>();

    public void preloadAd() {
        loadAd(1, null);
    }

    public void setBannerAdSize(AdSize adSize) {
        mBannerAdSize = adSize;
    }

    public void loadAd(int burstNum, long protectTime, IAdLoadListener listener) {
        AdLog.d("FuseAdLoader :" + mSlot + " load ad: " + burstNum + " listener: " + listener);
        if (Looper.getMainLooper() != Looper.myLooper()) {
            throw new IllegalStateException("Load ad not from main thread");
        }
        if (PreferencesUtils.isAdFree()) {
            AdLog.d("FuseAdLoader : AD free version");
            if (listener != null) {
                listener.onError("AD free version");
            }
            return;
        }
        if ( burstNum  <= 0 || mNativeAdConfigList.size() == 0) {
            AdLog.d("FuseAdLoader :" + mSlot + " load num wrong: " + burstNum);
            if (listener != null) {
                listener.onError("Wrong config");
            }
            return;
        }
        if (burstNum == 1) { protectTime = 0;}
        mProtectOverTime = System.currentTimeMillis() + protectTime;
        mListener = listener;
        lastIdx = 0;
        if (protectTime > 0) {
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (mListener != null) {
                        IAdAdapter cache = getValidCache();
                        if (cache != null) {
                            mListener.onAdLoaded(cache);
                            mListener = null;
                        }
                    }
                }
            }, protectTime);
        }
        for (int i = 0; i < burstNum; i ++ ) {
            if (loadNextNativeAd()) {
                AdLog.d("Stop burst as already find cache at: " + i);
                break;
            }
        }
    }

    private boolean isLoading (int idx) {
        return (mLoadingBits & (0x1 << idx)) != 0;
    }

    private void markLoading(int idx) {
        mLoadingBits |= (0x1 << idx);
    }

    private void finishLoading(int idx) {
        mLoadingBits &= (~(0x1 << idx));
        long now = System.currentTimeMillis();
        IAdAdapter ad = getValidCache();
        if (ad == null) {
            //need load next or no fill;
            if (idx == mNativeAdConfigList.size() - 1) {
                if (mListener != null) {
                    mListener.onError("No Fill");
                    mListener = null;
                }
            } else {
                loadNextNativeAd();
            }
        } else {
            // no need load next, fill or just wait timeout;
            int i ;
            for (i = idx -1; i >= 0; i --) {
                if (isLoading(i)) {
                    break;
                }
            }
            if (now >= mProtectOverTime || i < 0) {
                if (mListener != null) {
                    mListener.onAdLoaded(ad);
                    mListener = null;
                }
            } else {
                AdLog.d("Wait for protect time over");
            }
        }
    }

    private int nextLoadingIdx() {
        return  lastIdx ++;
    }

    private IAdAdapter getValidCache() {
        for (AdConfig config: mNativeAdConfigList) {
            IAdAdapter cache = mNativeAdCache.get(config.key);
            if (cache != null) {
                if (cache.isShowed() || ((System.currentTimeMillis() - cache.getLoadedTime())/1000) > config.cacheTime) {
                    AdLog.d("AdAdapter cache time out : " + cache.getTitle() + " type: " + cache.getAdType());
                    mNativeAdCache.remove(config.key);
                } else {
                    return cache;
                }
            }
        }
        return null;
    }

    private boolean hasValidCache(AdConfig config) {
        IAdAdapter cache = mNativeAdCache.get(config.key);
        if (cache != null) {
            if (cache.isShowed() || ((System.currentTimeMillis() - cache.getLoadedTime())/1000) > config.cacheTime) {
                AdLog.d("AdAdapter cache time out : " + cache.getTitle() + " type: " + cache.getAdType());
                mNativeAdCache.remove(config.key);
            } else {
                return true;
            }
        }
        return false;
    }

    public void loadAd(int burstNum, IAdLoadListener listener) {
        loadAd(burstNum, 0 , listener);
    }

    public void addAdConfig(AdConfig adConfig) {
        if (adConfig != null && !TextUtils.isEmpty(adConfig.source) && !TextUtils.isEmpty(adConfig.key)) {
            if (SUPPORTED_TYPES.contains(adConfig.source)) {
                mNativeAdConfigList.add(adConfig);
                AdLog.d("add adConfig : " + adConfig.toString());
            }
        }
    }

    public void addAdConfigList(List<AdConfig> adConfigList) {
        if (adConfigList != null) {
            for(AdConfig adConfig: adConfigList) {
                addAdConfig(adConfig);
            }
        }
    }

    public boolean hasValidAdSource() {
        return mNativeAdConfigList!=null && mNativeAdConfigList.size() > 0;
    }

    abstract class IndexAdListener implements IAdLoadListener {
        int index;
        public IndexAdListener(int index) {
            this.index = index;
        }
    }
    private boolean loadNextNativeAd() {
        int idx = nextLoadingIdx();
        if (idx < 0 || idx >= mNativeAdConfigList.size()) {
            AdLog.d(mSlot + " tried to load all source . Index : " + idx);
            return false;
        }
        if (isLoading(idx)) {
            AdLog.d(mSlot + " already loading . Index : " + idx);
            return false;
        }
        markLoading(idx);
        AdConfig config = mNativeAdConfigList.get(idx);
        if (hasValidCache(config)) {
            AdLog.d(mSlot + " already have cache for : " + config.key);
            finishLoading(idx);
            return true;
        }
        //Do load
        IAdAdapter loader = getNativeAdAdapter(config);
        if (loader == null) {
            finishLoading(idx);
            return false;
        }
        AdLog.d(mSlot + " start load for : " + config.source + " index : " + idx );
        loader.loadAd(1, new IndexAdListener(idx) {
            @Override
            public void onAdLoaded(IAdAdapter ad) {
                mNativeAdCache.put(mNativeAdConfigList.get(index).key, ad);
                AdLog.d(mSlot + " ad loaded " + ad.getAdType());
                if (ad.getCoverImageUrl() != null) {
                    AdLog.d("preload " + ad.getCoverImageUrl());
                    ImageLoader.getInstance().doPreLoad(mContext, ad.getCoverImageUrl());
                }
                if (ad.getIconImageUrl() != null) {
                    AdLog.d("preload " + ad.getIconImageUrl());
                    ImageLoader.getInstance().doPreLoad(mContext, ad.getIconImageUrl());
                }
                finishLoading(index);
            }

            @Override
            public void onAdListLoaded(List<IAdAdapter> ads) {
                //not support list yet
            }

            @Override
            public void onError(String error) {
                finishLoading(index);
                AdLog.e("Load current source " + mNativeAdConfigList.get(index).source + " error : " + error);
            }
        });
        return false;
    }

    private IAdAdapter getNativeAdAdapter(AdConfig config){
        if (config == null || config.source == null) {
            return null;
        }
        switch (config.source) {
            case AdConstants.NativeAdType.AD_SOURCE_ADMOB:
                return new AdmobNativeAdapter(mContext, config.key);
            case AdConstants.NativeAdType.AD_SOURCE_MOPUB:
                return new MopubNativeAdapter(mContext, config.key);
            case AdConstants.NativeAdType.AD_SOURCE_ADMOB_NAVTIVE_BANNER:
                return mBannerAdSize == null? null : new AdmobExpressAdapter(mContext, config.key, mBannerAdSize);
            case AdConstants.NativeAdType.AD_SOURCE_ADMOB_CONTENT:
                AdmobNativeAdapter adapter = new AdmobNativeAdapter(mContext, config.key);
                adapter.setFilter(AdConstants.AdMob.FILTER_ONLY_CONTENT);
                return adapter;
            case AdConstants.NativeAdType.AD_SOURCE_ADMOB_INSTALL:
                AdmobNativeAdapter adapterInstall = new AdmobNativeAdapter(mContext, config.key);
                adapterInstall.setFilter(AdConstants.AdMob.FILTER_ONLY_INSTALL);
                return adapterInstall;
            case AdConstants.NativeAdType.AD_SOURCE_FACEBOOK:
                return new FBNativeAdapter(mContext, config.key);
            case AdConstants.NativeAdType.AD_SOURCE_FACEBOOK_INTERSTITIAL:
                return new FBInterstitialAdapter(mContext, config.key);
            case AdConstants.NativeAdType.AD_SOURCE_ADMOB_INTERSTITIAL:
                return new AdmobInterstitialAdapter(mContext, config.key);
            case AdConstants.NativeAdType.AD_SOURCE_MOPUB_INTERSTITIAL:
                return new MopubInterstitialAdapter(mContext, config.key);
            case AdConstants.NativeAdType.AD_SOURCE_VK:
            default:
                AdLog.e("not suppported source " + config.source);
                return null;
        }
    }

    static {
        SUPPORTED_TYPES.add(AdConstants.NativeAdType.AD_SOURCE_ADMOB);
        SUPPORTED_TYPES.add(AdConstants.NativeAdType.AD_SOURCE_ADMOB_CONTENT);
        SUPPORTED_TYPES.add(AdConstants.NativeAdType.AD_SOURCE_ADMOB_INSTALL);
        SUPPORTED_TYPES.add(AdConstants.NativeAdType.AD_SOURCE_ADMOB_INTERSTITIAL);
        SUPPORTED_TYPES.add(AdConstants.NativeAdType.AD_SOURCE_FACEBOOK);
        SUPPORTED_TYPES.add(AdConstants.NativeAdType.AD_SOURCE_FACEBOOK_INTERSTITIAL);
        SUPPORTED_TYPES.add(AdConstants.NativeAdType.AD_SOURCE_MOPUB);
        SUPPORTED_TYPES.add(AdConstants.NativeAdType.AD_SOURCE_MOPUB_INTERSTITIAL);
        SUPPORTED_TYPES.add(AdConstants.NativeAdType.AD_SOURCE_ADMOB_NAVTIVE_BANNER);
    }
}
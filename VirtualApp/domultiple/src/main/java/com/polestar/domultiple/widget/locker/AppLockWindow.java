package com.polestar.domultiple.widget.locker;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.support.v7.view.menu.MenuBuilder;
import android.support.v7.view.menu.MenuPopupHelper;
import android.support.v7.widget.PopupMenu;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.NativeExpressAdView;
import com.lody.virtual.client.core.VirtualCore;
import com.polestar.ad.AdViewBinder;
import com.polestar.ad.adapters.FuseAdLoader;
import com.polestar.ad.adapters.IAdAdapter;
import com.polestar.ad.adapters.IAdLoadListener;
import com.polestar.domultiple.PolestarApp;
import com.polestar.domultiple.R;
import com.polestar.domultiple.components.ui.LockSecureQuestionActivity;
import com.polestar.domultiple.utils.CommonUtils;
import com.polestar.domultiple.utils.DisplayUtils;
import com.polestar.domultiple.utils.MLogs;
import com.polestar.domultiple.utils.PreferencesUtils;
import com.polestar.domultiple.utils.RemoteConfig;
import com.polestar.domultiple.utils.ResourcesUtil;
import com.polestar.domultiple.widget.FloatWindow;

import java.util.List;

import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;

/**
 * Created by guojia on 2017/1/3.
 */

public class AppLockWindow implements PopupMenu.OnMenuItemClickListener {

    private PopupMenu mPopupMenu;
    private BlurBackground mBlurBackground;

    private String mPkgName;
    private Handler mHandler;
    private FloatWindow mWindow;
    private View mContentView;
    private TextView mForgotPasswordTv;
    private NativeExpressAdView mAdmobExpressView;
    private LockIconImageView mCenterIcon;
    private LinearLayout mAdInfoContainer;
    private ImageView mToolbarIcon;
    private TextView mToolbarText;
    
    private TextView mCenterAppText;

    private boolean mIsShowing;

    private AppLockPasswordLogic mAppLockPasswordLogic = null;

    public final static String CONFIG_SLOT_APP_LOCK = "slot_app_lock";
    public final static String CONFIG_SLOT_APP_LOCK_PROTECT_TIME = "slot_app_lock_protect_time";

    public AppLockWindow(String pkgName, Handler handler) {
        mPkgName = pkgName;
        mHandler = handler;

        mWindow = new FloatWindow(PolestarApp.getApp());

        mContentView = LayoutInflater.from(PolestarApp.getApp()).inflate(R.layout.applock_window_layout, null);

        mWindow.setContentView(mContentView);
        mBlurBackground = (BlurBackground)mContentView.findViewById(R.id.applock_window);
        mWindow.setOnBackPressedListener(new FloatWindow.OnBackPressedListener() {
            @Override
            public void onBackPressed() {
                MLogs.d("AppLockWindow onBackPressed");
                mBlurBackground.onIncorrectPassword(mAdInfoContainer);
            }
        });

        mAppLockPasswordLogic = new AppLockPasswordLogic(mContentView, new AppLockPasswordLogic.EventListener() {
            @Override
            public void onCorrectPassword() {
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        AppLockWindow window = AppLockWindowManager.getInstance().get(mPkgName);
                        if (window != null && window.isShowing()) {
                            window.dismiss();
                        }
                    }
                }, 500);
                mHandler.sendMessage(mHandler.obtainMessage(AppLockMonitor.MSG_PACKAGE_UNLOCKED, mPkgName));
            }

            @Override
            public void onIncorrectPassword() {
                mBlurBackground.onIncorrectPassword(mAdInfoContainer);
            }

            @Override
            public void onCancel() {
                mBlurBackground.onIncorrectPassword(mAdInfoContainer);
            }
        });
        mAppLockPasswordLogic.onFinishInflate();

        mToolbarIcon = (ImageView) mContentView.findViewById(R.id.lock_bar_icon);
        mToolbarText = (TextView) mContentView.findViewById(R.id.lock_bar_text);

        initToolbar();
        MLogs.d("AppLockWindow initialized 0");
        mAdInfoContainer = (LinearLayout)mContentView.findViewById(R.id.layout_appinfo_container);

        mCenterIcon = (LockIconImageView) mContentView.findViewById(R.id.window_applock_icon);
        mCenterAppText = (TextView) mContentView.findViewById(R.id.window_applock_name);
        PackageManager pm = PolestarApp.getApp().getPackageManager();
        ApplicationInfo ai = null;
        try {
            ai = pm.getApplicationInfo(mPkgName, 0);
        }catch (Exception e) {
            MLogs.logBug(MLogs.getStackTraceString(e));
        }
        if ( ai != null) {
            Drawable drawable = pm.getApplicationIcon(ai);
            if (drawable != null) {
                mCenterIcon.setImageBitmap( CommonUtils.createCustomIcon(PolestarApp.getApp(), drawable));
            }
            CharSequence title = pm.getApplicationLabel(ai);
            if (title != null) {
                mCenterAppText.setText(String.format(ResourcesUtil.getString(R.string.clone_label_tag),title));
            }
        }
        MLogs.d("AppLockWindow initialized 1");
        mForgotPasswordTv = (TextView)mContentView.findViewById(R.id.forgot_password_tv);
        mForgotPasswordTv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                forgotPassword();
            }
        });
        MLogs.d("AppLockWindow initialized");
    }

    public static AdSize getBannerSize() {
        int dpWidth = DisplayUtils.px2dip(VirtualCore.get().getContext(), DisplayUtils.getScreenWidth(VirtualCore.get().getContext()));
        dpWidth = Math.max(280, dpWidth*9/10);
        return  new AdSize(dpWidth, 280);
    }

    private void updateTitleBar() {
        mToolbarIcon.setImageDrawable(mCenterIcon.getDrawable());
        mToolbarIcon.setBackground(null);
        mToolbarText.setText(mCenterAppText.getText());
    }

    private void inflatNativeAd(IAdAdapter ad) {
//        final AdViewBinder viewBinder =  new AdViewBinder.Builder(R.layout.lock_window_native_ad)
//                .titleId(R.id.ad_title)
//                .textId(R.id.ad_subtitle_text)
//                .mainImageId(R.id.ad_cover_image)
//                .iconImageId(R.id.ad_icon_image)
//                .callToActionId(R.id.ad_cta_text)
//                .privacyInformationIconImageId(R.id.ad_choices_image)
//                .build();
//        View adView = ad.getAdView(viewBinder);
//        if (adView != null) {
//            adView.setBackgroundColor(0);
//            mAdInfoContainer.removeAllViews();
//            mAdInfoContainer.addView(adView);
//            updateTitleBar();
//        }
    }
    private void loadNative(){
        final FuseAdLoader adLoader = AppLockMonitor.getInstance().getAdLoader();
        adLoader.setBannerAdSize(getBannerSize());
//        adLoader.addAdConfig(new AdConfig(AdConstants.NativeAdType.AD_SOURCE_FACEBOOK, "1713507248906238_1787756514814644", -1));
//        adLoader.addAdConfig(new AdConfig(AdConstants.NativeAdType.AD_SOURCE_MOPUB, "ea31e844abf44e3690e934daad125451", -1));
        if (adLoader != null && adLoader.hasValidAdSource()) {
            adLoader.loadAd(2, RemoteConfig.getLong(CONFIG_SLOT_APP_LOCK_PROTECT_TIME), new IAdLoadListener() {
                @Override
                public void onAdLoaded(IAdAdapter ad) {
                    MLogs.d("Applock native ad loaded. showing: " + isShowing());
                    if (isShowing()) {
                        inflatNativeAd(ad);
                        //loadAdmobNativeExpress();
                        adLoader.loadAd(1, null);
                    }
                }

                @Override
                public void onAdListLoaded(List<IAdAdapter> ads) {

                }

                @Override
                public void onError(String error) {
                    MLogs.d("Lock window load ad error: " + error);
                }
            });
        }
    }

    private void initToolbar() {
        if (mContentView == null) return;

        View menuLayout = LayoutInflater.from(PolestarApp.getApp()).inflate(R.layout.menu_applock_toolbar, null);
        menuLayout.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        mPopupMenu = new PopupMenu(PolestarApp.getApp(), (ViewGroup)menuLayout);
        mPopupMenu.setOnMenuItemClickListener(this);

        final View menu = mContentView.findViewById(R.id.toolbar_applock_menu);
        menu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    MenuPopupHelper menuHelper = new MenuPopupHelper(PolestarApp.getApp(), (MenuBuilder) mPopupMenu.getMenu(), menuLayout);
                    menuHelper.show();
//            Field field = homeMenuPopup.getClass().getDeclaredField("mPopup");
//            field.setAccessible(true);
//            MenuPopupHelper mHelper = (MenuPopupHelper) field.get(homeMenuPopup);
//            mHelper.setForceShowIcon(true);
                } catch (Exception e) {
                    MLogs.logBug(MLogs.getStackTraceString(e));
                }
            }
        });
    }

    public void show(boolean showAd) {
        if (!mIsShowing) {
            mIsShowing = true;
            mBlurBackground.init();
            mBlurBackground.reloadWithTheme(mPkgName);
            MLogs.d("LockWindow show ad" + showAd);
            mAppLockPasswordLogic.onShow();
            mWindow.show();
            if (showAd) {
                loadNative();
            }
            //loadAdmobNativeExpress();
        }
    }

    public void dismiss() {
        if (mIsShowing && mPopupMenu != null && mWindow != null) {
            mBlurBackground.resetLayout();
            mAppLockPasswordLogic.onBeforeHide();
            mPopupMenu.dismiss();
            mWindow.hide();
            mIsShowing = false;
        }
    }

    private void forgotPassword() {
        if (PreferencesUtils.isSafeQuestionSet(VirtualCore.get().getContext())) {
            Intent intent = new Intent(VirtualCore.get().getContext(), LockSecureQuestionActivity.class);
            intent.putExtra(LockSecureQuestionActivity.EXTRA_IS_SETTING, false);
            intent.setFlags(FLAG_ACTIVITY_NEW_TASK);
            PolestarApp.getApp().getApplicationContext().startActivity(intent);
        }
    }

    public boolean isShowing() {
        return mIsShowing;
    }


    @Override
    public boolean onMenuItemClick(MenuItem item) {
        return false;
    }
}
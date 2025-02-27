/*
 * Copyright (C) 2018 CypherOS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.launcher3.qsb;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.support.v4.graphics.ColorUtils;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.OnScrollListener;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup.MarginLayoutParams;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.launcher3.BaseRecyclerView;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.allapps.AllAppsContainerView;
import com.android.launcher3.allapps.SearchUiManager;
import com.android.launcher3.allapps.search.AllAppsSearchBarController;
import com.android.launcher3.uioverrides.WallpaperColorInfo;
import com.android.launcher3.uioverrides.WallpaperColorInfo.OnChangeListener;
import com.android.launcher3.util.Themes;

import com.android.launcher3.LauncherCallbacks;
import com.android.launcher3.qsb.configs.ConfigurationBuilder;
import com.android.launcher3.qsb.configs.QsbConfiguration;
import com.android.launcher3.qsb.search.DefaultSearchView;
import com.android.launcher3.search.SearchThread;
import com.android.launcher3.util.PackageStatusReceiver;

import com.android.internal.util.aosip.aosipUtils;

public class AllAppsQsbView extends BaseQsbView implements SearchUiManager, OnChangeListener {

    public Context mContext;
    public QsbConfiguration mQsbConfig;
    public int mMarginAdjusting;
    public Bitmap mQsbScroll;
    public float mTranslationY;
    public AllAppsContainerView mAppsView;
    public boolean mKeepDefaultView;
    public DefaultSearchView mDefaultSearchView;
    public TextView mHint;
    private PackageStatusReceiver mGsaStatus;
    public int mShadowAlpha;
    public boolean mUseDefaultSearch;

    public AllAppsQsbView(Context context, AttributeSet attributeSet) {
        this(context, attributeSet, 0);
    }

    public AllAppsQsbView(Context context, AttributeSet attributeSet, int res) {
        super(context, attributeSet, res);
        mContext = context;
        mShadowAlpha = 0;
        setOnClickListener(this);
        mQsbConfig = QsbConfiguration.getInstance(context);
        mMarginAdjusting = mContext.getResources().getDimensionPixelSize(R.dimen.qsb_margin_top_adjusting);
        mTranslationY = getTranslationY();
        setClipToPadding(false);
        mGsaStatus = new PackageStatusReceiver(context) {
            @Override
            public void onReceive(Context context, Intent intent) {
                loadMicViews();
                setSearchType();
            }
        };
    }

    @Override
    public void onFinishInflate() {
        super.onFinishInflate();
        mHint = (TextView) findViewById(R.id.qsb_hint);
    }

    @Override
    public void setInsets(Rect rect) {
        setSearchType();
        MarginLayoutParams marginLayoutParams = (MarginLayoutParams) getLayoutParams();
        marginLayoutParams.topMargin = Math.max((int) (-mTranslationY), rect.top - mMarginAdjusting);
        requestLayout();
        if (mLauncher.getDeviceProfile().isVerticalBarLayout()) {
            mLauncher.mAllAppsController.setScrollRangeDelta(0.0f);
            return;
        }
        mLauncher.mAllAppsController.setScrollRangeDelta((float) Math.round(((float) HotseatQsbView.getHotseatPadding(mLauncher)) + (((float) (marginLayoutParams.height + marginLayoutParams.topMargin)) + mTranslationY)));
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        mGsaStatus.register(LauncherCallbacks.SEARCH_PACKAGE);
        onExtractedColorsChanged(mWallpaperColorInfo);
        updateConfiguration();
    }

    @Override
    public void onDetachedFromWindow() {
        mGsaStatus.unregister();
        WallpaperColorInfo.getInstance(getContext()).removeOnChangeListener(this);
        super.onDetachedFromWindow();
    }

    @Override
    public void onExtractedColorsChanged(WallpaperColorInfo wallpaperColorInfo) {
        final Configuration config = mContext.getResources().getConfiguration();
        final boolean nightModeWantsDarkTheme = (config.uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        setColor(ColorUtils.compositeColors(
                ColorUtils.compositeColors(nightModeWantsDarkTheme ? 0xD9282828 : 0xCCFFFFFF, Themes.getAttrColor(mLauncher, R.attr.allAppsScrimColor)),
                wallpaperColorInfo.getMainColor()));
    }

    @Override
    public int getWidth(int width) {
        if (mLauncher.getDeviceProfile().isVerticalBarLayout()) {
            return (width - mAppsView.getActiveRecyclerView().getPaddingLeft()) - mAppsView.getActiveRecyclerView().getPaddingRight();
        }
        View view = mLauncher.getHotseat().getLayout();
        return (width - view.getPaddingLeft()) - view.getPaddingRight();
    }

    @Override
    public void initialize(AllAppsContainerView allAppsContainerView) {
        mAppsView = allAppsContainerView;
        mAppsView.addElevationController(new OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                setShadowAlpha(((BaseRecyclerView) recyclerView).getCurrentScrollY());
            }
        });
        mAppsView.setRecyclerViewVerticalFadingEdgeEnabled(true);
    }

    public void updateConfiguration() {
        setColorAlpha(mColor);
        setMicPaint(0.0f);
        mUseTwoBubbles = false;
        setHintText(mQsbConfig.hintTextValue(), mHint);
        //setMicRipple();
    }

    public void onClick(View view) {
        super.onClick(view);
        if (view == this) {
            if (!Launcher.getLauncher(getContext()).isFeedIntegrationEnabled()) {
                Launcher.getLauncher(getContext()).startSearch("", false, null, true);
                return;
            }
            startSearch("", mResult);
        }
    }

    @Override
    public void startSearch(String initialQuery, int result) {
        ConfigurationBuilder config = new ConfigurationBuilder(this, true);
        if (mLauncher.getClient().startSearch(config.build(), config.getExtras())) {
            mLauncher.getQsbController().playQsbAnimation();
        } else {
            searchFallback(initialQuery);
        }
        mResult = 0;
    }

    public void searchFallback(String query) {
        if (mDefaultSearchView == null) {
            ensureFallbackView();
        }
        mDefaultSearchView.setText(query);
        mDefaultSearchView.showKeyboard();
    }

    public void resetSearch() {
        setShadowAlpha(0);
        if (mUseDefaultSearch) {
            resetFallbackView();
        } else if (!mKeepDefaultView) {
            removeDefaultView();
        }
    }

    public void ensureFallbackView() {
        setOnClickListener(null);
        mDefaultSearchView = (DefaultSearchView) mLauncher.getLayoutInflater().inflate(R.layout.all_apps_google_search_fallback, this, false);
        mDefaultSearchView.mAllAppsQsbView = this;
        mDefaultSearchView.mApps = mAppsView.getApps();
        mDefaultSearchView.mAppsView = mAppsView;
        SearchThread searchThread = new SearchThread(mDefaultSearchView.getContext());
        mDefaultSearchView.mController.initialize(searchThread, mDefaultSearchView, Launcher.getLauncher(mDefaultSearchView.getContext()), mDefaultSearchView);
        addView(mDefaultSearchView);
    }

    public void removeDefaultView() {
        if (mDefaultSearchView != null) {
            mDefaultSearchView.clearSearchResult();
            setOnClickListener(this);
            removeView(mDefaultSearchView);
            mDefaultSearchView = null;
        }
    }

    public void resetFallbackView() {
        if (mDefaultSearchView != null) {
            mDefaultSearchView.reset();
            mDefaultSearchView.clearSearchResult();
        }
    }

    @Override
    public void onLayout(boolean z, int i, int i2, int i3, int i4) {
        super.onLayout(z, i, i2, i3, i4);
        View view = (View) getParent();
        setTranslationX((float) ((view.getPaddingLeft() + ((((view.getWidth() - view.getPaddingLeft()) - view.getPaddingRight()) - (i3 - i)) / 2)) - i));
    }

    @Override
    public void draw(Canvas canvas) {
        if (mShadowAlpha > 0) {
            if (mQsbScroll == null) {
                mQsbScroll = createBitmap(mContext.getResources().getDimension(
                      R.dimen.hotseat_qsb_scroll_shadow_blur_radius), mContext.getResources().getDimension(R.dimen.hotseat_qsb_scroll_key_shadow_offset), 0);
            }
            mShadowHelper.paint.setAlpha(mShadowAlpha);
            drawShadow(mQsbScroll, canvas);
            mShadowHelper.paint.setAlpha(255);
        }
        super.draw(canvas);
    }

    public void setShadowAlpha(int alpha) {
        alpha = Utilities.boundToRange(alpha, 0, 255);
        if (mShadowAlpha != alpha) {
            mShadowAlpha = alpha;
            invalidate();
        }
    }

    @Override
    public boolean isClipboard() {
        if (mDefaultSearchView != null) {
            return false;
        }
        return super.isClipboard();
    }

    protected void setSearchType() {
        boolean useDefaultSearch = !aosipUtils.isPackageInstalled(Launcher.getLauncher(mContext), LauncherCallbacks.SEARCH_PACKAGE);
        if (mUseDefaultSearch != useDefaultSearch) {
            removeDefaultView();
            mUseDefaultSearch = useDefaultSearch;
            ((ImageView) findViewById(R.id.g_icon)).setImageResource(mUseDefaultSearch ? R.drawable.ic_allapps_search : R.drawable.ic_super_g_color);
            if (mMicIconView != null) {
                mMicIconView.setAlpha(mUseDefaultSearch ? 0.0f : 1.0f);
            }
            if (mUseDefaultSearch) {
                ensureFallbackView();
                mDefaultSearchView.setHint(R.string.all_apps_search_bar_hint);
            }
        }
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    @Override
    public void preDispatchKeyEvent(KeyEvent keyEvent) {
    }

    public void setKeepDefaultView(boolean canKeep) {
        mKeepDefaultView = canKeep;
    }
}

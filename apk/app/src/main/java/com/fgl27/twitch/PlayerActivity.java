//adapt part of the player from https://github.com/yuliskov/SmartYouTubeTV

package com.fgl27.twitch;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory;
import com.google.android.exoplayer2.util.Util;

public class PlayerActivity extends Activity {
    public static final String TAG = PlayerActivity.class.getName();
    public static final int[] positions = {
            Gravity.END | Gravity.BOTTOM,
            Gravity.END | Gravity.CENTER,
            Gravity.END | Gravity.TOP,
            Gravity.CENTER | Gravity.TOP,
            Gravity.START | Gravity.TOP,
            Gravity.START | Gravity.CENTER,
            Gravity.START | Gravity.BOTTOM,
            Gravity.CENTER | Gravity.BOTTOM};

    public int[] BUFFER_SIZE = {4000, 4000, 4000, 4000};//Default, live, vod, clips

    public int DefaultPositions = 0;

    public PlayerView[] simpleExoPlayerView = new PlayerView[2];
    public SimpleExoPlayer[] player = new SimpleExoPlayer[2];
    public DataSource.Factory dataSourceFactory;

    public DefaultTrackSelector[] trackSelector = new DefaultTrackSelector[2];

    public DefaultTrackSelector.Parameters trackSelectorParameters;
    public DefaultTrackSelector.Parameters trackSelectorParametersSmall;
    public int mainPlayerBandwidth = Integer.MAX_VALUE;
    public int smallPlayerBandwidth = 3000000;

    public long mResumePosition;
    public boolean seeking;
    public int mwhocall = 1;

    public Uri uri;
    public MediaSource mediaurireset;

    public MediaSource[] mediaSourceAuto =  new MediaSource[2];
    public MediaSource[] mediaSourcesAuto = new MediaSource[2];

    public boolean[] loadingcanshow = new boolean[2];
    public ImageView[] spinner = new ImageView[2];
    public ImageView spinnermain;
    //Not used as when we change video size the animation can became odd looking
    //public float density;
    //public FrameLayout.LayoutParams IconSizeSmall;
    //public FrameLayout.LayoutParams IconSizeBig;
    public FrameLayout.LayoutParams PlayerViewDefaultSize;
    public FrameLayout.LayoutParams PlayerViewDefaultSizeChat;

    public Animation rotation;

    public WebView mwebview;

    private boolean onCreateReady;
    public boolean PicturePicture;

    public int heightDefault = 0;
    public int mwidthDefault = 0;
    public int heightChat = 0;
    public int mwidthChat = 0;

    public int mainPlayer = 0;
    public int playerDivider = 3;

    public Handler myHandler;
    public Handler[] PlayerCheckHandler = new Handler[2];
    public int[] PlayerCheckCounter = new int[2];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //On create is called onResume so prevent it if already set
        if (!onCreateReady) {
            onCreateReady = true;
            setContentView(R.layout.activity_player);

            myHandler = new Handler(Looper.getMainLooper());
            PlayerCheckHandler[0] = new Handler(Looper.getMainLooper());
            PlayerCheckHandler[1] = new Handler(Looper.getMainLooper());

            dataSourceFactory =
                    new DefaultHttpDataSourceFactory(
                            Util.getUserAgent(this, this.getString(R.string.app_name)),
                            DefaultHttpDataSource.DEFAULT_CONNECT_TIMEOUT_MILLIS,
                            DefaultHttpDataSource.DEFAULT_READ_TIMEOUT_MILLIS,
                            true);

            trackSelectorParameters = new DefaultTrackSelector.ParametersBuilder().build();

            // Prevent small window causing lag to the device
            // Bitrates bigger then 8Mbs on two simultaneous video playback side by side can slowdown some devices
            // even though that device can play a 2160p60 at 30+Mbs on a single playback without problem
            trackSelectorParametersSmall = trackSelectorParameters
                    .buildUpon()
                    .setMaxVideoBitrate(smallPlayerBandwidth)
                    .build();

            trackSelectorParameters = trackSelectorParameters
                    .buildUpon()
                    .setMaxVideoBitrate(mainPlayerBandwidth)
                    .build();

            mediaurireset = Tools.buildMediaSource(Uri.parse("file:///android_asset/temp.mp4"), dataSourceFactory, 3);

            spinnermain = findViewById(R.id.spinnermain);
            rotation = AnimationUtils.loadAnimation(this, R.anim.rotation);
            spinnermain.startAnimation(rotation);

            spinner[0] = findViewById(R.id.spinner1);
            spinner[1] = findViewById(R.id.spinner2);

            simpleExoPlayerView[0] = findViewById(R.id.player_view);
            simpleExoPlayerView[1] = findViewById(R.id.player_view2);

            simpleExoPlayerView[1].setVisibility(View.GONE);

            initializeWebview();
        }
    }

    // The main player initialization function
    private void initializePlayer(int position) {
        //Toast.makeText(this, "position " + position + " mainPlayer " + mainPlayer, Toast.LENGTH_SHORT).show();
        // always release before starting for performance check ClearPlayer
        if (player[position] != null) {
            player[position].setPlayWhenReady(false);
            releasePlayer(position);
        }

        showLoading(position);

        PlayerCheckHandler[position].removeCallbacksAndMessages(null);

        if (simpleExoPlayerView[position].getVisibility() != View.VISIBLE)
            simpleExoPlayerView[position].setVisibility(View.VISIBLE);

        boolean isSmall = (mainPlayer != position);

        trackSelector[position] = new DefaultTrackSelector();
        trackSelector[position].setParameters(isSmall ? trackSelectorParametersSmall : trackSelectorParameters);

        player[position] = ExoPlayerFactory.newSimpleInstance(
                this,
                new DefaultRenderersFactory(this),
                trackSelector[position],
                Tools.getLoadControl(BUFFER_SIZE[mwhocall]));

        player[position].addListener(new PlayerEventListener(position));

        player[position].setPlayWhenReady(true);

        simpleExoPlayerView[position].setPlayer(player[position]);

        seeking = (mResumePosition > 0) && (mwhocall > 1);
        if (seeking) player[position].seekTo(mResumePosition);

        player[position].prepare(
                mediaSourceAuto[position] != null ? mediaSourceAuto[position] : Tools.buildMediaSource(uri, dataSourceFactory, mwhocall),
                !seeking,
                true);

        if (!isSmall) {
            //Reset small player view so it shows after big one has started
            int tempPos = position == 0 ? 1 : 0;
            if (player[tempPos] != null) simpleExoPlayerView[tempPos].setVisibility(View.GONE);
            if (player[tempPos] != null) simpleExoPlayerView[tempPos].setVisibility(View.VISIBLE);
        } else player[position].setVolume(0f);//small is default no volume
    }

    // For some reason the player can lag a device when stated without releasing it first
    // It seems that the app keeps working on the player somehow in the background on the already released player
    // So here we do more then what seems necessary by releasing, starting and releasing again
    // But on longer test this gives the best performance
    private void ClearPlayer(int position) {
        if (player[position] != null) {
            player[position].setPlayWhenReady(false);
            releasePlayer(position);
        }

        PlayerCheckCounter[position] = 0;

        //Reset player background to a empty black screen and reset all states
        player[position] = ExoPlayerFactory.newSimpleInstance(this);
        simpleExoPlayerView[position].setPlayer(player[position]);

        player[position].setPlayWhenReady(false);
        player[position].prepare(mediaurireset, true, true);

        releasePlayer(position);
        simpleExoPlayerView[position].setVisibility(View.GONE);
        hideLoading(position);
    }

    //The main PreinitializePlayer used for when we first start the player or to play clips/vods
    //Also used to change the main player that is the big screen
    private void PreinitializePlayer(MediaSource mediaSource, String videoAddress, int whocall, long position) {

        mediaSourceAuto[mainPlayer] = mediaSource;
        uri = Uri.parse(videoAddress);
        mwhocall = whocall;
        mResumePosition = position > 0 ? position : 0;

        initializePlayer(mainPlayer);
    }

    //The way to start the Picture in Picture small window
    private void PreinitializePlayer2(MediaSource mediaSource, String videoAddress) {

        mediaSourceAuto[mainPlayer ^ 1] = mediaSource;
        uri = Uri.parse(videoAddress);
        mwhocall = 1;
        mResumePosition = 0;

        initializePlayer(mainPlayer ^ 1);
        PicturePicture = true;
    }

    //Stop the player called from js, clear it all
    private void PreResetPlayer(int whocall, int position) {

        if (mainPlayer == 1) SwitchPlayer(false);
        PicturePicture = false;

        PlayerCheckHandler[0].removeCallbacksAndMessages(null);
        PlayerCheckHandler[1].removeCallbacksAndMessages(null);
        mediaSourceAuto[0] = null;
        mediaSourceAuto[1] = null;
        mediaSourcesAuto[0] = null;
        mediaSourcesAuto[1] = null;
        mwhocall = whocall;
        mResumePosition = 0;

        ClearPlayer(position);
        clearResumePosition();
    }

    //Main release function
    private void releasePlayer(int position) {
        if (player[position] != null) {
            player[position].release();
            player[position] = null;
            trackSelector[position] = null;
        }
        hideLoading(position);
    }

    //Basic player position setting, for resume playback 
    private void clearResumePosition() {
        mResumePosition = C.TIME_UNSET;
    }

    private void updateResumePosition(int position) {
        if (player[position] == null) return;

        mResumePosition = player[position].isCurrentWindowSeekable() ?
                Math.max(0, player[position].getCurrentPosition()) : C.TIME_UNSET;
    }

    //Basic animation for loading functions
    private void hideLoadingMain() {
        spinnermain.setVisibility(View.GONE);
        spinnermain.clearAnimation();
    }

    private void showLoadingMain() {
        if (spinnermain.getVisibility() != View.VISIBLE) {
            // The duration of the spin is 1s, reset every show to use the spin as a performance counter
            // to know how much time something takes to load
            spinnermain.startAnimation(rotation);
            spinnermain.setVisibility(View.VISIBLE);
        }
    }

    //Basic animation for loading playback functions
    private void hideLoading(int position) {
        loadingcanshow[position] = false;
        spinner[position].setVisibility(View.GONE);
        spinner[position].clearAnimation();
    }

    private void showLoadingDelay(int position) {
        //Add a delay to prevent "short blink" ladings, can happen sporadic or right before STATE_ENDED because of STATE_BUFFERING
        myHandler.postDelayed(() -> {
            if (loadingcanshow[position]) showLoading(position);
        }, 500);
    }

    private void showLoading(int position) {
        if (spinner[position].getVisibility() != View.VISIBLE) {
            // The duration of the spin is 1s, reset every show to use the spin as a performance counter
            // to know how much time takes to load
            spinner[position].startAnimation(rotation);
            spinner[position].setVisibility(View.VISIBLE);
        }
    }

    private void SetheightDefault() {
        //Make it visible for calculation
        boolean isvisible = simpleExoPlayerView[0].getVisibility() != View.VISIBLE;
        if (isvisible) simpleExoPlayerView[0].setVisibility(View.VISIBLE);

        heightDefault = simpleExoPlayerView[0].getHeight();
        mwidthDefault = simpleExoPlayerView[0].getWidth();

        heightChat = (int) (heightDefault * 0.75);
        mwidthChat = (int) (mwidthDefault * 0.75);

        if (isvisible) simpleExoPlayerView[0].setVisibility(View.GONE);
        //density = this.getResources().getDisplayMetrics().density;
        //IconSizeSmall = new FrameLayout.LayoutParams(Math.round(35 * density), Math.round(35 * density), Gravity.CENTER);
        //IconSizeBig = new FrameLayout.LayoutParams(Math.round(50 * density), Math.round(50 * density), Gravity.CENTER);
        PlayerViewDefaultSize = new FrameLayout.LayoutParams(mwidthDefault, heightDefault, Gravity.TOP);
        PlayerViewDefaultSizeChat = new FrameLayout.LayoutParams(mwidthChat, heightChat, Gravity.CENTER_VERTICAL);

        UpdadeSizePosSmall(1);
    }

    //Used in side-by-side mode chat plus video
    private void updateVidesizeChat(boolean sizechat) {
        if (sizechat)simpleExoPlayerView[0].setLayoutParams(PlayerViewDefaultSizeChat);
        else simpleExoPlayerView[0].setLayoutParams(PlayerViewDefaultSize);
    }

    //SwitchPlayer with is the big and small player used by picture in picture mode
    private void SwitchPlayer(boolean show) {
        int WillBeMain = mainPlayer ^ 1;//shift 0 to 1 and vice versa

        //change trackSelector to limit video bandwidth
        if (trackSelector[WillBeMain] != null) trackSelector[WillBeMain].setParameters(trackSelectorParameters);
        if (trackSelector[mainPlayer] != null) trackSelector[mainPlayer].setParameters(trackSelectorParametersSmall);

        //Set proper video size
        simpleExoPlayerView[WillBeMain].setLayoutParams(PlayerViewDefaultSize);
        UpdadeSizePosSmall(mainPlayer);

        //Set proper video loading icon size
        //spinner[mainPlayer].setLayoutParams(IconSizeSmall);
        //spinner[WillBeMain].setLayoutParams(IconSizeBig);

        //Set proper video volume, muted to small
        if (player[WillBeMain] != null) player[WillBeMain].setVolume(1f);
        if (player[mainPlayer] != null) player[mainPlayer].setVolume(0f);

        //Reset the view so it show on top
        ResetViews(mainPlayer, WillBeMain);

        simpleExoPlayerView[mainPlayer].setVisibility(View.GONE);
        if (show) simpleExoPlayerView[mainPlayer].setVisibility(View.VISIBLE);

        mainPlayer = WillBeMain;
    }

    // Is necessary to bring the small player to the front of the big using bringToFront
    // and reset it view by Visibility(GONE then VISIBLE) to have the player properly display the same all the time
    public void ResetViews(int front, int back) {
        //TODO make this a for
        simpleExoPlayerView[back].bringToFront();
        simpleExoPlayerView[back].invalidate();
        simpleExoPlayerView[front].invalidate();
        mwebview.invalidate();
        spinnermain.invalidate();

        simpleExoPlayerView[front].bringToFront();
        simpleExoPlayerView[front].invalidate();
        simpleExoPlayerView[back].invalidate();
        mwebview.invalidate();
        spinnermain.invalidate();

        mwebview.bringToFront();
        mwebview.invalidate();
        simpleExoPlayerView[front].invalidate();
        simpleExoPlayerView[back].invalidate();
        spinnermain.invalidate();

        spinnermain.bringToFront();
        spinnermain.invalidate();
        simpleExoPlayerView[front].invalidate();
        simpleExoPlayerView[back].invalidate();
        mwebview.invalidate();
    }

    public void UpdadeSizePosSmall(int pos) {
        simpleExoPlayerView[pos].setLayoutParams(new FrameLayout.LayoutParams((mwidthDefault / playerDivider), (heightDefault / playerDivider), positions[DefaultPositions]));
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    //This function is called when overview key is pressed
    @Override
    public void onPause() {
        super.onPause();
    }

    //This function is called when home key is pressed
    @Override
    public void onStop() {
        super.onStop();
        PlayerCheckHandler[0].removeCallbacksAndMessages(null);
        PlayerCheckHandler[1].removeCallbacksAndMessages(null);
        updateResumePosition(0);
        updateResumePosition(1);
        ClearPlayer(0);
        ClearPlayer(1);
    }

    //This function is called when TV wakes up
    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Toast.makeText(this, "onDestroy", Toast.LENGTH_SHORT).show();
        ClearPlayer(0);
        ClearPlayer(1);
    }

    //Close the app
    private void closeThis() {
        finishAndRemoveTask();
    }

    //Minimize the app
    private void minimizeThis() {
        this.moveTaskToBack(true);
    }

    //https://android-developers.googleblog.com/2009/12/back-and-other-hard-keys-three-stories.html
    //Use back key to kill the app
    @Override
    public boolean onKeyLongPress(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            closeThis();
            return true;
        }
        return super.onKeyLongPress(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.isTracking()
                && !event.isCanceled()) {
            // if the call key is being released, AND we are tracking
            // it from an initial key down, AND it is not canceled,
            // then handle it.
            mwebview.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_1));
            mwebview.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_1));
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            // this tells the framework to start tracking for
            // a long press and eventual key up.  it will only
            // do so if this is the first down (not a repeat).
            event.startTracking();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    // A web app that loads all thumbnails content and interact with the player
    private void initializeWebview() {
        mwebview = findViewById(R.id.WebView);
        mwebview.setBackgroundColor(Color.TRANSPARENT);
        WebSettings websettings = mwebview.getSettings();
        websettings.setJavaScriptEnabled(true);
        websettings.setDomStorageEnabled(true);

        websettings.setAllowFileAccess(true);
        websettings.setAllowContentAccess(true);
        websettings.setAllowFileAccessFromFileURLs(true);
        websettings.setAllowUniversalAccessFromFileURLs(true);
        websettings.setCacheMode(WebSettings.LOAD_NO_CACHE);

        mwebview.clearCache(true);
        mwebview.clearHistory();

        //To load page from assets
        mwebview.loadUrl("file:///android_asset/index.html");
        //To load page from githubio
        //mwebview.loadUrl("https://fgl27.github.io/SmartTwitchTV/release/index.min.html");

        mwebview.addJavascriptInterface(new WebAppInterface(this), "Android");

        mwebview.setWebViewClient(new WebViewClient() {
            @SuppressWarnings("unused")//called by JS
            public void onConsoleMessage(String message, int lineNumber, String sourceID) {
                Log.d(TAG, message + " -- From line " +
                        lineNumber + " of " +
                        sourceID);
            }
        });
        mwebview.requestFocus();
    }

    public class WebAppInterface {
        final Context mwebContext;

        /**
         * Instantiate the interface and set the context
         */
        WebAppInterface(Context context) {
            mwebContext = context;
        }

        @SuppressWarnings("unused")//called by JS
        @JavascriptInterface
        public void mshowLoading(boolean show) {
            myHandler.post(() -> {
                if (show) showLoadingMain();
                else hideLoadingMain();
            });
        }

        @SuppressWarnings("unused")//called by JS
        @JavascriptInterface
        public void mclose(boolean close) {
            myHandler.post(() -> {
                if (close) closeThis();
                else minimizeThis();
            });
        }

        /**
         * Show a toast from the web page
         */
        @SuppressWarnings("unused")//called by JS
        @JavascriptInterface
        public void showToast(String toast) {
            myHandler.post(() -> Toast.makeText(mwebContext, toast, Toast.LENGTH_SHORT).show());
        }

        @SuppressWarnings("unused")//called by JS
        @JavascriptInterface
        public void mupdatesize(boolean sizechat) {
            myHandler.post(() -> updateVidesizeChat(sizechat));
        }

        @SuppressWarnings("unused")//called by JS
        @JavascriptInterface
        public void startVideo(String videoAddress, int whocall) {
            myHandler.post(() -> PreinitializePlayer(null, videoAddress, whocall, -1));
        }

        @SuppressWarnings("unused")//called by JS
        @JavascriptInterface
        public void startVideoOffset(String videoAddress, int whocall, long position) {
            myHandler.post(() -> PreinitializePlayer(null, videoAddress, whocall, position));
        }

        @SuppressWarnings("unused")//called by JS
        @JavascriptInterface
        public void initializePlayer2(String url) {
            myHandler.post(() -> PreinitializePlayer2(null, url));
        }

        @SuppressWarnings("unused")//called by JS
        @JavascriptInterface
        public void initializePlayer2Auto() {
            myHandler.post(() -> PreinitializePlayer2(mainPlayer == 0 ? mediaSourcesAuto[1] : mediaSourcesAuto[0], ""));
        }

        @SuppressWarnings("unused")//called by JS
        @JavascriptInterface
        public void SetAuto(String url) {
            //The token expires in 15 min so we need to set the mediaSource in case we use it in the future
            myHandler.post(() -> mediaSourcesAuto[mainPlayer] = Tools.buildMediaSource(Uri.parse(url), dataSourceFactory, 1));
        }

        @SuppressWarnings("unused")//called by JS
        @JavascriptInterface
        public void SetAuto2(String url) {
            //The token expires in 15 min so we need to set the mediaSource in case we use it in the future
            myHandler.post(() -> mediaSourcesAuto[mainPlayer ^ 1] = Tools.buildMediaSource(Uri.parse(url), dataSourceFactory, 1));
        }

        @SuppressWarnings("unused")//called by JS
        @JavascriptInterface
        public void StartAuto(int whocall, long position) {
            myHandler.post(() -> PreinitializePlayer(mediaSourcesAuto[mainPlayer], "", whocall, position));
        }

        @SuppressWarnings("unused")//called by JS
        @JavascriptInterface
        public void stopVideo(int whocall) {
            myHandler.post(() -> PreResetPlayer(whocall, mainPlayer));
        }

        @SuppressWarnings("unused")//called by JS
        @JavascriptInterface
        public void mClearBigPlayer() {
            myHandler.post(() -> ClearPlayer(mainPlayer));
        }

        @SuppressWarnings("unused")//called by JS
        @JavascriptInterface
        public void mClearSmallPlayer() {
            //The token expires in 15 min so we need to set the mediaSource in case we use it in the future
            myHandler.post(() -> ClearPlayer(mainPlayer ^ 1));
        }

        @SuppressWarnings("unused")//called by JS
        @JavascriptInterface
        public void mSwitchPlayer() {
            myHandler.post(() -> {
                if (player[0] != null) player[0].setPlayWhenReady(false);
                if (player[1] != null) player[1].setPlayWhenReady(false);

                SwitchPlayer(true);

                if (player[0] != null) player[0].setPlayWhenReady(true);
                if (player[1] != null) player[1].setPlayWhenReady(true);
            });
        }

        @SuppressWarnings("unused")//called by JS
        @JavascriptInterface
        public void mSwitchPlayerPosition(int position) {
            DefaultPositions = position;
            myHandler.post(() -> UpdadeSizePosSmall(mainPlayer ^ 1));
        }

        @SuppressWarnings("unused")//called by JS
        @JavascriptInterface
        public void mSetPlayerPosition(int position) {
            DefaultPositions = position;
        }

        @SuppressWarnings("unused")//called by JS
        @JavascriptInterface
        public void mSwitchPlayerSize(int position) {
            playerDivider = position;
            myHandler.post(() -> UpdadeSizePosSmall(mainPlayer ^ 1));
        }

        @SuppressWarnings("unused")//called by JS
        @JavascriptInterface
        public void mSetPlayerSize(int position) {
            playerDivider = position;
            myHandler.post(PlayerActivity.this::SetheightDefault);
        }

        @SuppressWarnings("unused")//called by JS
        @JavascriptInterface
        public void SetMainPlayerBandwidth(int band) {
            mainPlayerBandwidth = band == 0 ? Integer.MAX_VALUE : band;
            myHandler.post(() -> trackSelectorParameters = trackSelectorParameters
                    .buildUpon()
                    .setMaxVideoBitrate(mainPlayerBandwidth)
                    .build());
        }

        @SuppressWarnings("unused")//called by JS
        @JavascriptInterface
        public void SetSmallPlayerBandwidth(int band) {
            smallPlayerBandwidth = band == 0 ? Integer.MAX_VALUE : band;
            myHandler.post(() -> trackSelectorParametersSmall = trackSelectorParameters
                    .buildUpon()
                    .setMaxVideoBitrate(smallPlayerBandwidth)
                    .build());
        }

        @SuppressWarnings("unused")//called by JS
        @JavascriptInterface
        public long getsavedtime() {
            return mResumePosition;
        }

        @SuppressWarnings("unused")//called by JS
        @JavascriptInterface
        public long gettime() {
            HVTHandler.RunnableResult<Long> result = HVTHandler.post(myHandler, new HVTHandler.RunnableValue<Long>() {
                @Override
                public void run() {
                    if (player[mainPlayer] != null) value = player[mainPlayer].getCurrentPosition();
                    else value = 0L;
                }
            });
            return result.get();
        }

        @SuppressWarnings("unused")//called by JS
        @JavascriptInterface
        public int getAndroid() {
            return 1;
        }

        @SuppressWarnings("unused")//called by JS
        @JavascriptInterface
        public String mreadUrl(String urlString, int timeout, int HeaderQuantity, String access_token) {
            return Tools.readUrl(urlString, timeout, HeaderQuantity, access_token, false);
        }

        @SuppressWarnings("unused")//called by JS
        @JavascriptInterface
        public String mreadUrl(String urlString, int timeout, int HeaderQuantity, String access_token, boolean post) {
            return Tools.readUrl(urlString, timeout, HeaderQuantity, access_token, post);
        }

        @SuppressWarnings("unused")//called by JS
        @JavascriptInterface
        public void play(boolean play) {
            myHandler.post(() -> {
                if (player[0] != null) player[0].setPlayWhenReady(play);
                if (player[1] != null) player[1].setPlayWhenReady(play);
            });
        }

        @SuppressWarnings("unused")//called by JS
        @JavascriptInterface
        public boolean getPlaybackState() {
            HVTHandler.RunnableResult<Boolean> result = HVTHandler.post(myHandler, new HVTHandler.RunnableValue<Boolean>() {
                @Override
                public void run() {
                    if (player[mainPlayer] != null) value = player[mainPlayer].getPlayWhenReady();
                    else value = false;
                }
            });
            return result.get();
        }

        @SuppressWarnings("unused")//called by JS
        @JavascriptInterface
        public void setPlaybackSpeed(float value) {
            myHandler.post(() -> {
                if (player[mainPlayer] != null) player[mainPlayer].setPlaybackParameters(new PlaybackParameters(value, 1.0f));
            });
        }

        @SuppressWarnings("unused")//called by JS
        @JavascriptInterface
        public void SetBuffer(int whocall, int value) {
            BUFFER_SIZE[whocall] = Math.min(value, 15000);
        }

        @SuppressWarnings("unused")//called by JS
        @JavascriptInterface
        public String getversion() {
            return BuildConfig.VERSION_NAME;
        }

        @SuppressWarnings("unused")//called by JS
        @JavascriptInterface
        public void clearCookie() {
            CookieManager.getInstance().removeAllCookies(null);
            CookieManager.getInstance().flush();
        }

        @SuppressWarnings("unused")//called by JS
        @JavascriptInterface
        public boolean misCodecSupported() {
            return Tools.isCodecSupported("vp9");
        }

        @SuppressWarnings("unused")//called by JS
        @JavascriptInterface
        public String getVideoQuality() {
            HVTHandler.RunnableResult<String> result = HVTHandler.post(myHandler, new HVTHandler.RunnableValue<String>() {
                @Override
                public void run() {
                    if (player[mainPlayer] != null) value = Tools.mgetVideoQuality(player[mainPlayer]);
                    else value = null;
                }
            });
            return result.get();
        }
    }

    // Basic EventListener for exoplayer
    private class PlayerEventListener implements Player.EventListener {

        private int position;
        private int delayms;

        private PlayerEventListener(int mposition) {
            position = mposition;
            delayms = (BUFFER_SIZE[mwhocall] * 2) + (mwhocall == 2 ? 3000 : 1000);
        }

        @Override
        public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
            myHandler.post(() -> {
                if (playWhenReady) {
                    if (playbackState == Player.STATE_ENDED) {
                        PlayerCheckHandler[position].removeCallbacksAndMessages(null);
                        player[position].setPlayWhenReady(false);

                        hideLoadingMain();
                        hideLoading(position);

                        if (PicturePicture) {
                            PicturePicture = false;
                            ClearPlayer(position);
                            if (mainPlayer == position) SwitchPlayer(false);
                        } else mwebview.loadUrl("javascript:Play_PannelEndStart(" + mwhocall + ")");

                    } else if (playbackState == Player.STATE_BUFFERING) {
                        hideLoadingMain();
                        loadingcanshow[position] = true;
                        showLoadingDelay(position);

                        //Use the player buffer as a player check state to prevent be buffering for ever
                        //If buffer for as long as BUFFER_SIZE * 2 do something because player is frozen
                        PlayerCheckHandler[position].removeCallbacksAndMessages(null);
                        PlayerCheckHandler[position].postDelayed(() -> {
                            //Player was released or is on pause
                            if (player[position] == null || !player[position].getPlayWhenReady()) return;

                            //First try only restart the player second ask js to check if there is a lower resolution
                            PlayerCheckCounter[position]++;
                            Toast.makeText(PlayerActivity.this, "PlayerCheckCounter " + PlayerCheckCounter[position], Toast.LENGTH_SHORT).show();
                            //Pause to things run smother and prevent odd behavior during the checks
                            player[position].setPlayWhenReady(false);
                            if (PlayerCheckCounter[position] < 4 && (mainPlayer != position || mediaSourceAuto[position] != null)) {
                                //this is small screen  or is in auto mode just restart it
                                updateResumePosition(position);
                                initializePlayer(position);
                            } else if (PlayerCheckCounter[position] > 3){

                                // treys == 3 Give up internet is probably down or something related

                                hideLoadingMain();
                                hideLoading(position);

                                if (PicturePicture) {
                                    PicturePicture = false;
                                    ClearPlayer(position);
                                    if (mainPlayer == position) SwitchPlayer(false);
                                } else mwebview.loadUrl("javascript:Play_PannelEndStart(" + mwhocall + ")");

                            } else if (PlayerCheckCounter[position] > 1) //Use js to check if is possible to drop quality
                                mwebview.loadUrl("javascript:Play_PlayerCheck(" + mwhocall + ")");
                            else {
                                updateResumePosition(position);
                                initializePlayer(position);
                            }

                        }, delayms);
                    } else if (playbackState == Player.STATE_READY) {
                        PlayerCheckHandler[position].removeCallbacksAndMessages(null);
                        PlayerCheckCounter[position] = 0;

                        //If other not playing just play it so they stay close to sync
                        int otherplayer = position == 0 ? 1 : 0;
                        if (player[otherplayer] != null) {
                            if (!player[otherplayer].getPlayWhenReady()) player[otherplayer].setPlayWhenReady(true);
                        }

                        hideLoading(position);
                        if (player[position] != null && mwhocall > 1) {
                            myHandler.post(() -> mwebview.loadUrl("javascript:Play_UpdateDuration(" +
                                    mwhocall + "," + player[position].getDuration() + ")"));
                        }
                    }
                } else  {
                    hideLoadingMain();
                    hideLoading(position);
                }
            });
        }

        @Override
        public void onPlayerError(ExoPlaybackException e) {
            if (Tools.isBehindLiveWindow(e)) clearResumePosition();
            else updateResumePosition(position);

            initializePlayer(position);
        }

    }
}

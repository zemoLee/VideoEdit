package com.videoedit;

import android.Manifest;
import android.content.Intent;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.videoedit.bean.VideoBean;
import com.videoedit.util.PermissionsActivity;
import com.videoedit.util.PermissionsChecker;
import com.videoedit.util.VideoCutHelper;
import com.videoedit.view.CutView;
import com.videoedit.view.DurView;
import com.videoedit.view.MyVideoView;

/**
 * Created by frank on 18/3/21.
 * <p>
 * 视频尺寸裁剪、时长裁剪
 */

public class VideoCutActivity extends BaseActivity implements View.OnClickListener, DurView.IOnRangeChangeListener {

    private static final String TAG = "VideoCutActivity";
    private static final int CODE_REQUEST_VIDEO = 1000;

    private PermissionsChecker mPermissionsChecker; // 权限检测器
    private static final int REQUEST_CODE = 0; // 请求码

    // 所需的全部权限
    static final String[] PERMISSIONS = new String[]{
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    private MyVideoView vv_play;
    private String path;
    private CutView mSizeCutView;
    private int windowWidth;
    private int windowHeight;
    private int dp50;
    private int videoWidth;
    private int videoHeight;
    private VideoBean videoBean;
    private DurView mDurationView;
    private int startT, endT;

    private RelativeLayout rel_open_gallery;
    private TextView rl_finish;
    private TextView open_gallery;

    /**
     * 获取本地视频信息
     */
    public static VideoBean getLocalVideoInfo(String path) {
        VideoBean info = new VideoBean();
        info.src_path = path;
        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
        try {
            mmr.setDataSource(path);
            info.src_path = path;
            info.duration = Integer.valueOf(mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
            info.rate = Integer.valueOf(mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE));
            info.width = Integer.valueOf(mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
            info.height = Integer.valueOf(mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            mmr.release();
        }
        return info;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_cut_size);

        VideoCutHelper.getInstance().init(this);

        windowWidth = getWindowManager().getDefaultDisplay().getWidth();
        windowHeight = getWindowManager().getDefaultDisplay().getHeight();
        dp50 = (int) getResources().getDimension(R.dimen.dp50);

        initUI();
        initPermission();
    }

    private void initUI() {

        vv_play = (MyVideoView) findViewById(R.id.vv_play);
        mSizeCutView = (CutView) findViewById(R.id.cv_video);
        open_gallery = (TextView) findViewById(R.id.open_gallery);
        rl_finish = (TextView) findViewById(R.id.rl_finish);
        mDurationView = (DurView) findViewById(R.id.cut_view);
        mDurationView.setRangeChangeListener(this);
        open_gallery.setOnClickListener(this);
        rl_finish.setOnClickListener(this);

        rel_open_gallery = (RelativeLayout) findViewById(R.id.rel_open_gallery);
        rel_open_gallery.setOnClickListener(this);
    }

    private void initPermission() {
        mPermissionsChecker = new PermissionsChecker(this);

        // 缺少权限时, 进入权限配置页面
        if (mPermissionsChecker.lacksPermissions(PERMISSIONS)) {
            startPermissionsActivity();
        }
    }

    /**
     * 裁剪视频大小
     */
    private void cutVideo(String path, int cropWidth, int cropHeight, int x, int y) {
        showProgressDialog();
        String outPut = getDestPath(videoBean.src_path);
        int duration = (endT - startT) / 1000;
        int startTime = startT / 1000;
        Log.d(TAG, "startTime:" + startTime + " duration " + duration);
        VideoCutHelper.getInstance().cropVideo(this,
                path, outPut,
                startTime, duration,
                cropWidth, cropHeight,
                x, y, new VideoCutHelper.FFListener() {

                    public void onProgress(Integer progress) {
                        setProgressDialog(progress);
                        Log.d(TAG, "progress " + progress);
                    }

                    public void onFinish() {
                        Log.d(TAG, "onFinish ");
                        closeProgressDialog();
                    }

                    public void onFail(String msg) {
                        Log.d(TAG, "onFail " + msg);
                        closeProgressDialog();

                    }
                });
    }

    private String getDestPath(String srcVideo) {
        int start = srcVideo.lastIndexOf(".");
        if (start == -1) {
            start = srcVideo.length();
        }
        String destPath = srcVideo.substring(0, start) + "_p.mp4";
        return destPath;
    }

    private void editVideo() {

        //得到裁剪后的margin值
        float[] cutArr = mSizeCutView.getCutArr();
        float left = cutArr[0];
        float top = cutArr[1];
        float right = cutArr[2];
        float bottom = cutArr[3];
        int cutWidth = mSizeCutView.getRectWidth();
        int cutHeight = mSizeCutView.getRectHeight();

        //计算宽高缩放比
        float leftPro = left / cutWidth;
        float topPro = top / cutHeight;
        float rightPro = right / cutWidth;
        float bottomPro = bottom / cutHeight;

        //得到裁剪位置
        int cropWidth = (int) (videoWidth * (rightPro - leftPro));
        int cropHeight = (int) (videoHeight * (bottomPro - topPro));
        int x = (int) (leftPro * videoWidth);
        int y = (int) (topPro * videoHeight);

        cutVideo(path, cropWidth, cropHeight, x, y);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d(TAG, "requestCode:" + requestCode);

        if (requestCode == CODE_REQUEST_VIDEO && resultCode == RESULT_OK) {
            path = getPathFromURI(data.getData());
            Log.d(TAG, path);

            videoBean = getLocalVideoInfo(path);
            startT = 0;
            endT = (int) videoBean.duration;
            vv_play.setVideoPath(data.getData());
            vv_play.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    vv_play.setLooping(true);
                    vv_play.start();

                    videoWidth = vv_play.getVideoWidth();
                    videoHeight = vv_play.getVideoHeight();
                    float widthF = 0.7f;
                    float ra = videoWidth * 1f / videoHeight;
                    if (ra >= 1) {
                        widthF = 0.9f;
                    } else {
                        widthF = 0.7f;
                    }
                    Log.d(TAG, "ghc videoWidth:" + videoWidth);
//                    float widthF = videoWidth * 1f / videoBean.width;

                    float heightF = videoHeight * 1f / videoBean.height;
                    ViewGroup.LayoutParams layoutParams = vv_play.getLayoutParams();
                    Log.d(TAG, "ghc widthF:" + widthF);
                    layoutParams.width = (int) (windowWidth * widthF);
                    layoutParams.height = (int) (layoutParams.width / ra);
                    vv_play.setLayoutParams(layoutParams);
                }
            });

            vv_play.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                int isFirstMeasure = 0;

                @Override
                public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
                    // 此处添加标志位，避免onLayoutChange 多次回调，致使手动调节裁剪框不生效
                    // 同时，使用int自增，是因为第一次onLayoutChange 回调时，返回的值是0，非需要的视频尺寸大小
                    if (isFirstMeasure <= 1) {
                        mSizeCutView.setMargin(vv_play.getLeft(), vv_play.getTop(),
                                windowWidth - vv_play.getRight(), windowHeight - vv_play.getBottom() - dp50);
                    }
                    isFirstMeasure++;
                }
            });

            mDurationView.setMediaFileInfo(videoBean);

            rel_open_gallery.setVisibility(View.GONE);
            rl_finish.setVisibility(View.VISIBLE);
        }

        if (requestCode == REQUEST_CODE && resultCode == PermissionsActivity.PERMISSIONS_DENIED) {
            finish();
        }

    }

    /*
    * 调用系统文件管理器选择视频文件
    * */
    public void openVideo() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("video/*");
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivityForResult(intent, CODE_REQUEST_VIDEO);
    }

    /*
    * 系统返回的视频文件信息，路径是URI，需要转换成String
    * content://com.miui.gallery.open/raw/%2Fstorage%2Femulated%2F0%2FDCIM%2FCamera%2FVID_20180528_094829.mp4
    * /storage/emulated/0/DCIM/Camera/VID_20180528_094829.mp4
    * */
    public String getPathFromURI(Uri contentUri) {
        Cursor cursor = null;
        try {
            String[] proj = {MediaStore.Images.Media.DATA};
            cursor = getApplicationContext().getContentResolver().query(contentUri, proj, null, null, null);
            int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
            cursor.moveToFirst();
            return cursor.getString(column_index);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.open_gallery:
                openVideo();
                break;

            case R.id.rl_finish:
                editVideo();
                break;

            case R.id.rel_open_gallery:
                openVideo();
                break;

            default:
                break;
        }
    }

    @Override
    public void onCutViewDown() {

    }

    @Override
    public void onCutViewUp(int startTime, int endTime) {
        // 设置时长裁剪
        startT = startTime;
        endT = endTime;
    }

    private void startPermissionsActivity() {
        PermissionsActivity.startActivityForResult(this, REQUEST_CODE, PERMISSIONS);
    }

}

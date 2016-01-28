package com.yangchao.namecardscanner.activity;

import android.animation.ObjectAnimator;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.yangchao.namecardscanner.R;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import cn.sharp.android.ncr.ocr.OCRItems;
import cn.sharp.android.ncr.ocr.OCRManager;

/**
 * 扫描名片界面
 * <p/>
 * Created by yc on 2016-1-20
 */
public class NameCardScannerActivity extends AppCompatActivity implements SurfaceHolder.Callback {

    private static final String TAG = NameCardScannerActivity.class.getSimpleName();

    /**
     * 扫描模式的Key
     */
    public static final String KEY_SCAN_MODE = "KEY_SCAN_MODE";

    /**
     * 拍图模式
     * 先拍照，后扫描
     */
    public static final int SCAN_MODE_PICTURE = 0;

    /**
     * 预览模式
     * 边预览边扫描
     */
    public static final int SCAN_MODE_PREVIEW = 1;
    /**
     * 扫描模式
     * 0 拍图模式
     * 1 预览模式
     */
    private int mScanMode;

    /**
     * 选择图片的requestCode
     */
    private static final int GET_JPEG_REQUEST_CODE = 0;

    /**
     * 扫描沉睡间隔
     */
    private static final long SCANNER_SLEEP_TIME = 4000;

    /**
     * 屏幕宽度
     */
    private int mScreenWidth;
    /**
     * 屏幕高度
     */
    private int mScreenHeight;

    /**
     * 是否正在预览
     */
    private AtomicBoolean isPreviewing = new AtomicBoolean(false);
    /**
     * 是否正在扫描
     */
    private AtomicBoolean isScanning = new AtomicBoolean(false);
    /**
     * 是否正在解码
     */
    private AtomicBoolean isDecoding = new AtomicBoolean(false);
    /**
     * 是否已经释放
     */
    private AtomicBoolean isReleased = new AtomicBoolean(true);
    /**
     * 是否已经正在拍照
     */
    private AtomicBoolean isTaking = new AtomicBoolean(false);
    /**
     * 是否打开闪关灯
     */
    private AtomicBoolean isOpenFlash = new AtomicBoolean(false);


    /**
     * 自动对焦回调
     */
    private Camera.AutoFocusCallback mAutoFocusCallback;

    /**
     * 预览帧数据
     */
    private byte[] mPreviewData;

    private TextView mTake;
    private SurfaceView mSurfaceView;

    private Handler handler = new Handler();

    private Camera mCamera;
    private ImageView mImageView;
    private TextView mFlash, mFile;
    private View line1, line2;

    /**
     * 跳转到本界面
     */
    public static void startActivity(Context context, int scanMode) {
        Intent intent = new Intent(context, NameCardScannerActivity.class);
        scanMode = scanMode == SCAN_MODE_PREVIEW ? SCAN_MODE_PREVIEW : SCAN_MODE_PICTURE;
        intent.putExtra(KEY_SCAN_MODE, scanMode);
        context.startActivity(intent);
    }

    /**
     * 将图片按照某个角度进行旋转
     *
     * @param bm     需要旋转的图片
     * @param degree 旋转角度
     * @return 旋转后的图片
     */
    public static Bitmap rotateBitmapByDegree(Bitmap bm, int degree) {
        Bitmap returnBm = null;

        // 根据旋转角度，生成旋转矩阵
        Matrix matrix = new Matrix();
        matrix.postRotate(degree);
        try {
            // 将原始图片按照旋转矩阵进行旋转，并得到新的图片
            returnBm = Bitmap.createBitmap(bm, 0, 0, bm.getWidth(), bm.getHeight(), matrix, true);
        } catch (OutOfMemoryError e) {
        }
        if (returnBm == null) {
            returnBm = bm;
        }
        if (bm != returnBm) {
            bm.recycle();
        }
        return returnBm;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            mScanMode = savedInstanceState.getInt(KEY_SCAN_MODE);
        } else {
            mScanMode = getIntent().getIntExtra(KEY_SCAN_MODE, SCAN_MODE_PICTURE);
        }

        // 保持界面常亮
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_name_card_scanner);

        //获取屏幕宽高
        DisplayMetrics m = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(m);
        mScreenWidth = m.widthPixels;
        mScreenHeight = m.heightPixels;

        initView();

    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(KEY_SCAN_MODE, mScanMode);
    }

    private void initView() {

        mTake = (TextView) findViewById(R.id.take);
        mImageView = (ImageView) findViewById(R.id.imageView);
        if (mScanMode == SCAN_MODE_PICTURE) {
            mTake.setVisibility(View.VISIBLE);
            mImageView.setVisibility(View.VISIBLE);
            mTake.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    doTakePicture();
                }
            });
        } else {
            mTake.setVisibility(View.GONE);
            mImageView.setVisibility(View.GONE);
        }

        mFlash = (TextView) findViewById(R.id.flash);
        mFlash.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isPreviewing.get()) {
                    if (isOpenFlash.compareAndSet(false, true)) {
                        Camera.Parameters p = mCamera.getParameters();
                        p.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
                        mCamera.setParameters(p);
                        mFlash.setText("关灯");
                    } else {
                        Camera.Parameters p = mCamera.getParameters();
                        p.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
                        mCamera.setParameters(p);
                        mFlash.setText("开灯");
                        isOpenFlash.set(false);
                    }
                }
            }
        });
        mFile = (TextView) findViewById(R.id.file);
        mFile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("image/*");
                startActivityForResult(intent, GET_JPEG_REQUEST_CODE);
            }
        });

        /**
         * 1、使用SurfaceView预览手机拍到的图像
         */
        mSurfaceView = (SurfaceView) findViewById(R.id.surfaceView);
//        1.1、设置回调
        mSurfaceView.getHolder().addCallback(this);
//        1.2、设置从缓冲区读取数据，配合Camera
        mSurfaceView.getHolder().setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        line1 = findViewById(R.id.line_1);
        line1.setVisibility(View.GONE);
        line2 = findViewById(R.id.line_2);
        line2.setVisibility(View.GONE);
        if (mScanMode == SCAN_MODE_PREVIEW) {
            line1.setVisibility(View.VISIBLE);
            line2.setVisibility(View.VISIBLE);
        }


    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == GET_JPEG_REQUEST_CODE && resultCode == RESULT_OK) {
            if (data != null) {
                Bitmap bm = null;
                ByteArrayOutputStream o = null;
                try {
                    ContentResolver resolver = getContentResolver();
                    Uri originalUri = data.getData();   //获得图片的uri
                    //得到bitmap图片，注意这里有可能用户选择的不是图片
                    bm = MediaStore.Images.Media.getBitmap(resolver, originalUri);
                    if (bm != null) {
                        o = new ByteArrayOutputStream();
                        bm.compress(Bitmap.CompressFormat.JPEG, 100, o);
                        if (o != null) {
                            doTakePictureDecode(o.toByteArray(), 0);
                            return;
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    if (o != null){
                        try {
                            o.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    if (bm != null) bm.recycle();
                }
            }
            showToast("不识别的图片格式");
        }
    }

    /**
     * 检查并获取 Camera 实例
     *
     * @return 是否获取了 Camera 实例
     */
    private boolean checkCameraDevice() {
        if (mCamera == null) {
            mCamera = Camera.open();
        }
        return mCamera != null;
    }

    /**
     * 显示Toast
     *
     * @param message 消息内容
     */
    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    /**
     * 2、当 SurfaceView 准备好之后，配置 Camera
     * 这里使用过期的 Camera 类，为了向下兼容
     */
    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        Log.e(TAG, "surfaceCreated");
        //        2.1、检查SurfaceView是否创建了 holder ，获取 Camera 实例
        if (holder.isCreating() && checkCameraDevice()) {
//            2.2、配置 Camera
            Camera.Parameters parameters = mCamera.getParameters();
            //获得支持的图片分辨率
            List<Camera.Size> vSizeList = parameters.getSupportedPictureSizes();
            for (int num = 0; num < vSizeList.size(); num++) {
                Camera.Size vSize = vSizeList.get(num);
                Log.e(TAG, vSize.width + " - " + vSize.height);
                if (vSize.width <= 1280) {
                    parameters.setPictureSize(vSize.width, vSize.height);
                    break;
                }
            }
            //获得支持的预览分辨率
            List<Camera.Size> sizeList = parameters.getSupportedPreviewSizes();
            for (int num = 0; num < sizeList.size(); num++) {
                Camera.Size vSize = sizeList.get(num);
                Log.e(TAG, vSize.width + " - " + vSize.height);
                if (vSize.width <= 1280) {
                    parameters.setPreviewSize(vSize.width, vSize.height);
                    break;
                }
            }
            //调整相机预览的横竖屏方向
            if (this.getResources().getConfiguration().orientation != Configuration.ORIENTATION_LANDSCAPE) {
                mCamera.setDisplayOrientation(90);
            } else {
                mCamera.setDisplayOrientation(0);
            }
            //设置聚焦区域
//            List<Camera.Area> areas = new ArrayList<>(1);
//            areas.add(new Camera.Area(new Rect(0, 0, width, height), 1));
//            parameters.setFocusAreas(areas);
            //设置图片尺寸
//            parameters.setPictureSize(640, 480);
            //设置预览尺寸
//            parameters.setPreviewSize(640, 480);

            //设置图片编码
//            parameters.setPictureFormat(PixelFormat.JPEG);
            //设置预览编码
//            parameters.setPreviewFormat(PixelFormat.YCbCr_420_SP);

            //设置闪关灯
            parameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);

            //设置参数
            mCamera.setParameters(parameters);

            //如果是预览模式,添加预览回调
            if (mScanMode == SCAN_MODE_PREVIEW) {
                mCamera.setPreviewCallback(new MyPreviewCallback());
            }
            //设置预览，将帧数据给 SurfaceView 显示
            try {
                mCamera.setPreviewDisplay(holder);
                isReleased.set(false);
            } catch (IOException exception) {
                mCamera.release();
                mCamera = null;
            }
            /**
             * 3.开始预览
             */
            startPreview();
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

    }

    /**
     * 开始预览
     */
    private void startPreview() {
        if (checkCameraDevice()) {
            //如果没有预览，则开始预览
            if (isPreviewing.compareAndSet(false, true)) {
                mCamera.startPreview();
                if (mScanMode == SCAN_MODE_PREVIEW) {
                    final ObjectAnimator animator = ObjectAnimator.ofFloat(line1, "translationY", 0, mSurfaceView.getMeasuredHeight());
                    animator.setDuration(4000);
                    animator.setInterpolator(new LinearInterpolator());
                    animator.setRepeatCount(ObjectAnimator.INFINITE);
                    animator.start();
                    final ObjectAnimator animator2 = ObjectAnimator.ofFloat(line2, "translationY", 0, -mSurfaceView.getMeasuredHeight());
                    animator2.setDuration(4000);
                    animator2.setInterpolator(new LinearInterpolator());
                    animator2.setRepeatCount(ObjectAnimator.INFINITE);
                    animator2.start();
                }
                Log.e(TAG, "开始进行预览");
            } else {
                Log.e(TAG, "警告：预览在之前已经被开启");
            }
            //开始定时聚焦
            startAutoFocus();
        }
    }

    /**
     * 开始定时自动聚焦
     */
    private void startAutoFocus() {
        //初始化自动对焦回调
        if (mAutoFocusCallback == null) {
            mAutoFocusCallback = new Camera.AutoFocusCallback() {
                @Override
                public void onAutoFocus(boolean success, Camera camera) {
                    Log.e(TAG, "自动聚焦：" + success);
                    if (success) {
                        if (mScanMode == SCAN_MODE_PREVIEW) {
                            doDecode();
                        }
                    }
                }
            };
        }
        //开启自动对焦任务
        if (isPreviewing.get()) {
            if (isScanning.compareAndSet(false, true)) {
                Log.e(TAG, "开始进行自动对焦任务");
                new Thread(mAutoFocusRunnable) {
                }.start();
            } else {
                Log.e(TAG, "警告：自动对焦任务在之前已经被开启");
            }
        }
    }

    /**
     * 拍照
     */
    private void doTakePicture() {

        if (mScanMode != SCAN_MODE_PICTURE) return;
        if (isReleased.get()) return;
        if (!isPreviewing.get()) return;
        if (!isScanning.get()) return;

        //如果没有在拍照
        if (isTaking.compareAndSet(false, true)) {
            Log.e(TAG, "正在拍照");
            //初始化自动对焦回调
            Camera.AutoFocusCallback autoFocusCallback = new Camera.AutoFocusCallback() {
                @Override
                public void onAutoFocus(boolean success, Camera camera) {
                    if (success) {  //对焦成功
                        //进行拍照
                        mCamera.takePicture(new Camera.ShutterCallback() {
                            @Override
                            public void onShutter() {

                            }
                        }, null, new Camera.PictureCallback() {
                            @Override
                            public void onPictureTaken(byte[] data, Camera camera) {
                                doTakePictureDecode(data, 90);
                                try {
                                    mCamera.startPreview();
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        });
                    } else {
                        //失败
                        isTaking.set(false);
                    }
                }
            };
            doAutoFocus(autoFocusCallback);
        }
    }

    private void doTakePictureDecode(final byte[] data, final int degree) {
        AsyncTask<Void, Void, OCRItems> task = new AsyncTask<Void, Void, OCRItems>() {
            @Override
            protected OCRItems doInBackground(Void... params) {
                if (mScanMode == SCAN_MODE_PICTURE) {
                    Bitmap bmp = BitmapFactory.decodeByteArray(data, 0, data.length);
                    final Bitmap bitmap = rotateBitmapByDegree(bmp, degree);
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            mImageView.setImageBitmap(bitmap);
                        }
                    });
                }
                return new OCRManager().rec(data);
            }

            @Override
            protected void onPostExecute(OCRItems ocrItems) {
                super.onPostExecute(ocrItems);
                if (isReleased.get()) return;
                if (ocrItems == null) {
                    showToast("解码失败，请重试");
                } else {
                    showToast(ocrItems.toString());
                }
                isTaking.set(false);
            }
        };
        task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    /**
     * 预览模式解码任务
     */
    private void doDecode() {
        AsyncTask<byte[], Void, OCRItems> task = new AsyncTask<byte[], Void, OCRItems>() {
            @Override
            protected OCRItems doInBackground(byte[]... params) {
                int width = mCamera.getParameters().getPreviewSize().width;
                int height = mCamera.getParameters().getPreviewSize().height;
                YuvImage image = new YuvImage(mPreviewData, ImageFormat.NV21, width, height, null);
                ByteArrayOutputStream os = new ByteArrayOutputStream(NameCardScannerActivity.this.mPreviewData.length);
                if (!image.compressToJpeg(new Rect(0, 0, width, height), 100, os)) {
                    return null;
                }
                byte[] tmp = os.toByteArray();
//                final Bitmap bmp = BitmapFactory.decodeByteArray(tmp, 0, tmp.length);
//                handler.post(new Runnable() {
//                    @Override
//                    public void run() {
//                        mImageView.setImageBitmap(bmp);
//                    }
//                });
                return new OCRManager().rec(tmp);
            }

            @Override
            protected void onPostExecute(OCRItems ocrItems) {
                super.onPostExecute(ocrItems);
                isDecoding.set(false);
                if (isReleased.get()) return;
                if (ocrItems != null) {
                    showToast(ocrItems.toString());
                }
            }
        };
        task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if (mCamera != null) {
            mCamera.setPreviewCallback(null);
            mCamera.stopPreview();
            isScanning.set(false);
            isDecoding.set(false);
            isPreviewing.set(false);
            isReleased.set(true);
            handler.post(new Runnable() {
                @Override
                public void run() {
                    mCamera.release();
                    mCamera = null;
                }
            });
        }
        isOpenFlash.set(false);
        if (mFlash != null) {
            mFlash.setText("开灯");
        }
    }

    /**
     * 定时聚焦任务
     */
    private Runnable mAutoFocusRunnable = new Runnable() {
        @Override
        public void run() {
            while (isScanning.get()) {
                if (mCamera != null && isScanning.get() && !isTaking.get()) {
                    if (!isReleased.get() && isPreviewing.get() && !isTaking.get()) {  //如果没有释放,没有拍照,并且正在预览
                        doAutoFocus(mAutoFocusCallback);
                    }
                }
                try {
                    Thread.sleep(SCANNER_SLEEP_TIME);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    };

    private void doAutoFocus(final Camera.AutoFocusCallback autoFocusCallback) {
        //释放自动对焦
        mCamera.cancelAutoFocus();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                //进行自动对焦，
                try {
                    mCamera.autoFocus(autoFocusCallback);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }, 1000);
    }

    /**
     * 获得预览帧的回调
     */
    private class MyPreviewCallback implements Camera.PreviewCallback {

        @Override
        public void onPreviewFrame(byte[] data, Camera camera) {
            if (mCamera != null && isPreviewing.get() && !isReleased.get()) {
                mPreviewData = data;
            }
        }
    }

}

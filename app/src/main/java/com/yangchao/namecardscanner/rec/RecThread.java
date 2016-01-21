package com.yangchao.namecardscanner.rec;

import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;

import android.os.Handler;
import android.os.Message;
import android.util.Log;

import cn.sharp.android.ncr.ocr.OCRItems;
import cn.sharp.android.ncr.ocr.OCRManager;

/**
 * 解析图片线程
 */
public class RecThread extends Thread {

    private final static String TAG = "RecThread";

    //图片数据
    private byte[] jpeg;
    private Handler handler;

    /**
     * AtomicBoolean 用来保证多线程下取值和赋值操作的原子性，即不会被其他操作打断
     */
    private AtomicBoolean stopped;
    private OCRManager ocrManager;
    private AtomicBoolean running;

    /**
     * Constructor for RecThread class, with no jpeg data passed in, you should
     * invoke setJpeg() before the thread running
     *
     * @param handler
     * @param ocrManager
     */
    public RecThread(Handler handler, OCRManager ocrManager) {
        this.handler = handler;
        stopped = new AtomicBoolean();
        this.ocrManager = ocrManager;
        running = new AtomicBoolean();
    }

    public RecThread(byte[] jpeg, Handler handler, OCRManager ocrManager) {
        this.jpeg = jpeg;
        this.handler = handler;
        stopped = new AtomicBoolean();
        this.ocrManager = ocrManager;
        running = new AtomicBoolean();
    }

    @Override
    public void run() {
        running.set(true);
        stopped.set(false);
        Message msg = new Message();
        long start = System.currentTimeMillis();

        OCRItems ocrItems = ocrManager.rec(jpeg);

        long elapsed = System.currentTimeMillis() - start;
        Date elapsedTime = new Date(elapsed);
        Log.v(TAG, "time cost for rec namecard(s):" + elapsedTime.getSeconds());
        if (stopped.get()) {
            Log.e(TAG, "thead stopped after rec");
            ocrItems = null;
            return;
        }
        if (ocrItems == null) {
            msg.what = -1;
        } else {
            msg.what = 1;
            msg.obj = ocrItems;
        }
        if (handler != null) {
            handler.sendMessage(msg);
            Log.d(TAG, "decode image data success message sent");
        } else {
            Log.i(TAG, "handler==null in decode image data progress, msg not sent");
        }
        running.set(false);
    }

    public void setJpeg(byte[] jpeg) {
        if (!running.get()) {
            this.jpeg = jpeg;
        }
    }

    public void forceStop() {
        stopped.set(true);
    }
}

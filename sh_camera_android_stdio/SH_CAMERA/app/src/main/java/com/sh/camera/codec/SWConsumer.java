/*  car eye 车辆管理平台
 * car-eye管理平台   www.car-eye.cn
 * car-eye开源网址:  https://github.com/Car-eye-team
 * Copyright
 */

package com.sh.camera.codec;

import android.content.Context;
import android.graphics.ImageFormat;
import android.util.Log;

import org.push.sw.JNIUtil;
import org.push.sw.X264Encoder;
import org.push.push.Pusher;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * Created by apple on 2017/5/13.
 */

public class SWConsumer extends Thread implements VideoConsumer {
    private static final String TAG = "SWConsumer";
    private int mHeight;
    private int mWidth;
    private X264Encoder x264;
    private final Pusher mPusher;
    private int m_index;
    private volatile boolean mVideoStarted;
    public SWConsumer(Context context, Pusher pusher, int index){
        mPusher = pusher;
        m_index = index;
    }
    @Override
    public void onVideoStart(int width, int height) {
        this.mWidth = width;
        this.mHeight = height;

        x264 = new X264Encoder();
        int bitrate = (int) (mWidth*mHeight*20*2*0.07f);
        x264.create(width, height, 20, bitrate/500);
        mVideoStarted = true;
        start();
    }


    class TimedBuffer {
        byte[] buffer;
        long time;

        public TimedBuffer(byte[] data) {
            buffer = data;
            time = System.currentTimeMillis();
        }
    }

    private ArrayBlockingQueue<TimedBuffer> yuvs = new ArrayBlockingQueue<TimedBuffer>(2);
    private ArrayBlockingQueue<byte[]> yuv_caches = new ArrayBlockingQueue<byte[]>(10);

    @Override
    public void run(){

        byte[]h264 = new byte[mWidth*mHeight*3/2];
        byte[] keyFrm = new byte[1];
        int []outLen = new int[1];
        do {
            try {
                int r = 0;
                TimedBuffer tb = yuvs.take();
                byte[] data = tb.buffer;

                long begin = System.currentTimeMillis();
                r = x264.encode(data, 0, h264, 0, outLen, keyFrm);
                if (r > 0) {
                    Log.i(TAG, String.format("encode spend:%d ms. keyFrm:%d", System.currentTimeMillis() - begin, keyFrm[0]));
//                                newBuf = new byte[outLen[0]];
//                                System.arraycopy(h264, 0, newBuf, 0, newBuf.length);
                }
                yuv_caches.offer(data);
                //mPusher.push(h264, 0, outLen[0], tb.time, 1);
                
              //  mPusher.SendBuffer(h264,  outLen[0],0, 0);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }while (mVideoStarted);
    }


    final int millisPerframe = 1000/20;
    long lastPush = 0;
    @Override
    public int onVideo(byte[] data, int format) {
        try {
            if (lastPush == 0) {
                lastPush = System.currentTimeMillis();
            }
            long time = System.currentTimeMillis() - lastPush;
            if (time >= 0) {
                time = millisPerframe - time;
                if (time > 0) Thread.sleep(time / 2);
            }
            byte[] buffer = yuv_caches.poll();
            if (buffer == null || buffer.length != data.length) {
                buffer = new byte[data.length];
            }
            System.arraycopy(data, 0, buffer, 0, data.length);
            yuvs.offer(new TimedBuffer(buffer));
            if (time > 0) Thread.sleep(time / 2);
            lastPush = System.currentTimeMillis();
        }catch (InterruptedException ex){
            ex.printStackTrace();
        }
        return 0;
    }

    @Override
    public void onVideoStop() {
        do {
            mVideoStarted = false;
            try {
                interrupt();
                join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }while (isAlive());
        x264.close();
    }
}
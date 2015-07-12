/**
 * Copyright 2011, Felix Palmer
 * Copyright 2015, The TeamEos Project
 *
 * Licensed under the MIT license:
 * http://creativecommons.org/licenses/MIT/
 */

package com.pheelicks.visualizer;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff.Mode;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.media.audiofx.Visualizer;
import android.util.Log;

import com.pheelicks.visualizer.renderer.BarGraphRenderer;
import com.pheelicks.visualizer.renderer.CircleBarRenderer;
import com.pheelicks.visualizer.renderer.CircleRenderer;
import com.pheelicks.visualizer.renderer.LineRenderer;
import com.pheelicks.visualizer.renderer.Renderer;

import java.util.HashSet;
import java.util.Set;

/**
 * A class that draws visualizations of data received from a
 * {@link Visualizer.OnDataCaptureListener#onWaveFormDataCapture } and
 * {@link Visualizer.OnDataCaptureListener#onFftDataCapture }
 */
public abstract class BaseVisualizer {

    protected static String TAG = "BaseVisualizer";

    protected byte[] mBytes;
    protected byte[] mFFTBytes;
    protected Matrix mMatrix;
    protected AudioData mAudioData;
    protected FFTData mFftData;
    protected boolean mDrawingEnabled = true;
    protected Rect mRect = new Rect();
    protected Visualizer mVisualizer;
    protected int mAudioSessionId;

    protected Set<Renderer> mRenderers;

    protected Paint mFlashPaint = new Paint();
    protected Paint mFadePaint = new Paint();
    protected Bitmap mCanvasBitmap;
    protected Canvas mCanvas;
    protected boolean mFlash = false;

    public BaseVisualizer() {
        mBytes = null;
        mFFTBytes = null;

        mAudioData = new AudioData(null);
        mFftData = new FFTData(null);

        mMatrix = new Matrix();

        mFlashPaint.setColor(Color.argb(122, 255, 255, 255));
        mFadePaint.setColor(Color.argb(200, 255, 255, 255)); // Adjust alpha to
                                                             // change how
                                                             // quickly the
                                                             // image fades
        mFadePaint.setXfermode(new PorterDuffXfermode(Mode.MULTIPLY));

        mRenderers = new HashSet<Renderer>();
    }

    protected abstract void onInvalidate();
    protected abstract int onGetWidth();
    protected abstract int onGetHeight();

    /**
     * Links the visualizer to a player
     * 
     * @param player - MediaPlayer instance to link to
     */
    public final void link(int audioSessionId)
    {
        if (mVisualizer != null && audioSessionId != mAudioSessionId) {
            mVisualizer.setEnabled(false);
            mVisualizer.release();
            mVisualizer = null;
        }

        Log.i(TAG, "session=" + audioSessionId);
        mAudioSessionId = audioSessionId;

        if (mVisualizer == null) {

            // Create the Visualizer object and attach it to our media player.
            try {
                mVisualizer = new Visualizer(audioSessionId);
            } catch (Exception e) {
                Log.e(TAG, "Error enabling visualizer!", e);
                return;
            }
            mVisualizer.setEnabled(false);
            mVisualizer.setCaptureSize(Visualizer.getCaptureSizeRange()[1]);

            // Pass through Visualizer data to VisualizerView
            Visualizer.OnDataCaptureListener captureListener = new Visualizer.OnDataCaptureListener()
            {
                @Override
                public void onWaveFormDataCapture(Visualizer visualizer, byte[] bytes,
                        int samplingRate)
                {
                    updateVisualizer(bytes);
                }

                @Override
                public void onFftDataCapture(Visualizer visualizer, byte[] bytes,
                        int samplingRate)
                {
                    updateVisualizerFFT(bytes);
                }
            };

            mVisualizer.setDataCaptureListener(captureListener,
                    (int) (Visualizer.getMaxCaptureRate() * 0.75), true, true);

        }
        mVisualizer.setEnabled(true);
    }

    public final void unlink() {
        if (mVisualizer != null) {
            mVisualizer.setEnabled(false);
            mVisualizer.release();
            mVisualizer = null;
        }
    }

    public final void addRenderer(Renderer renderer)
    {
        if (renderer != null)
        {
            mRenderers.add(renderer);
        }
    }

    public final void clearRenderers()
    {
        mRenderers.clear();
    }

    public final void invalidate() {
        onInvalidate();
    }

    public final int getWidth() {
        return onGetWidth();
    }

    public final int getHeight() {
        return onGetHeight();
    }

    public void setDrawingEnabled(boolean draw) {
        mDrawingEnabled = draw;
    }

    /**
     * Call to release the resources used by VisualizerView. Like with the
     * MediaPlayer it is good practice to call this method
     */
    public final void release()
    {
        mVisualizer.release();
    }

    /**
     * Pass data to the visualizer. Typically this will be obtained from the
     * Android Visualizer.OnDataCaptureListener call back. See
     * {@link Visualizer.OnDataCaptureListener#onWaveFormDataCapture }
     * 
     * @param bytes
     */
    public void updateVisualizer(byte[] bytes) {
        mBytes = bytes;
        mAudioData.bytes = bytes;
        invalidate();
    }

    /**
     * Pass FFT data to the visualizer. Typically this will be obtained from the
     * Android Visualizer.OnDataCaptureListener call back. See
     * {@link Visualizer.OnDataCaptureListener#onFftDataCapture }
     * 
     * @param bytes
     */
    public void updateVisualizerFFT(byte[] bytes) {
        mFFTBytes = bytes;
        mFftData.bytes = bytes;
        invalidate();
    }

    /**
     * Call this to make the visualizer flash. Useful for flashing at the start
     * of a song/loop etc...
     */
    public void flash() {
        mFlash = true;
        invalidate();
    }

    public void onSizeChanged(int w, int h, int oldw, int oldh) {
        mRect.set(0, 0, getWidth(), getHeight());
    }

    public void onDraw(Canvas canvas) {

        // Create canvas once we're ready to draw
        mRect.set(0, 0, getWidth(), getHeight());

        if (mCanvasBitmap == null)
        {
            mCanvasBitmap = Bitmap.createBitmap(canvas.getWidth(), canvas.getHeight(),
                    Config.ARGB_8888);
        }
        if (mCanvas == null)
        {
            mCanvas = new Canvas(mCanvasBitmap);
        }

        if (mBytes != null) {
            // Render all audio renderers
            for (Renderer r : mRenderers)
            {
                r.render(mCanvas, mAudioData, mRect);
            }
        }

        if (mFFTBytes != null) {
            // Render all FFT renderers
            for (Renderer r : mRenderers)
            {
                r.render(mCanvas, mFftData, mRect);
            }
        }

        if (mDrawingEnabled) {
            // Fade out old contents
            mCanvas.drawPaint(mFadePaint);

            if (mFlash)
            {
                mFlash = false;
                mCanvas.drawPaint(mFlashPaint);
            }

            canvas.drawBitmap(mCanvasBitmap, mMatrix, null);
        }
    }

    // Methods for adding renderers to visualizer
    public void addBarGraphRendererBottom()
    {
        Paint paint = new Paint();
        paint.setStrokeWidth(50f);
        paint.setAntiAlias(true);
        paint.setColor(Color.argb(200, 56, 138, 252));
        BarGraphRenderer barGraphRendererBottom = new BarGraphRenderer(16, paint, false);
        addRenderer(barGraphRendererBottom);
    }

    public void addBarGraphRendererTop() {
        Paint paint2 = new Paint();
        paint2.setStrokeWidth(12f);
        paint2.setAntiAlias(true);
        paint2.setColor(Color.argb(200, 181, 111, 233));
        BarGraphRenderer barGraphRendererTop = new BarGraphRenderer(4, paint2, true);
        addRenderer(barGraphRendererTop);
    }

    public void addCircleBarRenderer()
    {
        Paint paint = new Paint();
        paint.setStrokeWidth(8f);
        paint.setAntiAlias(true);
        paint.setXfermode(new PorterDuffXfermode(Mode.LIGHTEN));
        paint.setColor(Color.argb(255, 222, 92, 143));
        CircleBarRenderer circleBarRenderer = new CircleBarRenderer(paint, 32, true);
        addRenderer(circleBarRenderer);
    }

    public void addCircleRenderer()
    {
        Paint paint = new Paint();
        paint.setStrokeWidth(3f);
        paint.setAntiAlias(true);
        paint.setColor(Color.argb(255, 222, 92, 143));
        CircleRenderer circleRenderer = new CircleRenderer(paint, true);
        addRenderer(circleRenderer);
    }

    public void addLineRenderer()
    {
        Paint linePaint = new Paint();
        linePaint.setStrokeWidth(1f);
        linePaint.setAntiAlias(true);
        linePaint.setColor(Color.argb(88, 0, 128, 255));

        Paint lineFlashPaint = new Paint();
        lineFlashPaint.setStrokeWidth(5f);
        lineFlashPaint.setAntiAlias(true);
        lineFlashPaint.setColor(Color.argb(188, 255, 255, 255));
        LineRenderer lineRenderer = new LineRenderer(linePaint, lineFlashPaint, true);
        addRenderer(lineRenderer);
    }
}

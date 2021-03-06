/*
 * Copyright 2017 The Android Open Source Project.
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
package com.example.androidthings.photobooth;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.media.Image;
import android.media.ImageReader;
import android.media.ImageReader.OnImageAvailableListener;
import android.os.Trace;
import android.util.Log;
import android.widget.ImageView;

import junit.framework.Assert;

import static android.content.ContentValues.TAG;

/**
 * Class that takes in preview frames and converts the image to Bitmaps to process with Tensorflow.
 */
public class PhotoboothImageAvailableListener implements OnImageAvailableListener {
    private static final int INPUT_SIZE = 480;

    private int sensorOrientation = 0;

    private Bitmap rgbFrameBitmap = null;
    private Bitmap croppedBitmap = null;

    private boolean computing = false;
    private Activity activity;

    private int previewWidth;
    private int previewHeight;

    private byte[][] cachedYuvBytes = new byte[3][];
    private int[] rgbBytes = null;

    // Keep reference to most recent bitmap in preview window, as this will be the one stylized.
    private Bitmap mLatestBitmap = null;

    private boolean mInPreviewMode = false;

    public void initialize(
            final Activity activity,
            final Integer sensorOrientation) {
        Assert.assertNotNull(sensorOrientation);
        this.activity = activity;
        this.sensorOrientation = sensorOrientation == null ? 0 : sensorOrientation;
    }

    @Override
    public void onImageAvailable(final ImageReader reader) {

        Image image = null;
        try {
            image = reader.acquireLatestImage();

            if (image == null) {
                computing = false;
                return;
            } else if (!mInPreviewMode) {
                image.close();
                computing = false;
                return;
            } else if (computing) {
                image.close();
                Log.d(TAG, "Mutexed.");

                return;
            }
            computing = true;

            Trace.beginSection("imageAvailable");

            // Initialize the storage bitmaps once when the resolution is known.
            if (previewWidth != image.getWidth() || previewHeight != image.getHeight()) {
                previewWidth = image.getWidth();
                previewHeight = image.getHeight();

                rgbBytes = new int[previewWidth * previewHeight];
                rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Config.ARGB_8888);
                croppedBitmap = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Config.ARGB_8888);
            }

            ImageUtils.convertImageToBitmap(image, previewWidth, previewHeight, rgbBytes, cachedYuvBytes);
            image.close();

            if (croppedBitmap != null && rgbFrameBitmap != null) {
                rgbFrameBitmap.setPixels(rgbBytes, 0, previewWidth, 0, 0, previewWidth, previewHeight);
                ImageUtils.cropAndRescaleBitmap(rgbFrameBitmap, croppedBitmap, sensorOrientation);
            }

            updateImageView(croppedBitmap, activity);
        } catch (final Exception e) {
            if (image != null) {
                image.close();
            }
            Log.e(TAG, "Exception!");
            Trace.endSection();
            computing = false;
            return;
        }

        computing = false;
        Trace.endSection();
    }

    public void setPreviewMode(boolean inPreviewMode) {
        mInPreviewMode = inPreviewMode;
    }

    public boolean getInPreviewMode() {
        return mInPreviewMode;
    }

    private void updateImageView(final Bitmap bmp, final Activity activity) {
        if (activity != null && mInPreviewMode) {
            activity.runOnUiThread(
                    () -> {
                        if (bmp != null) {
                            ImageView view = (ImageView) activity.findViewById(R.id.imageView);
                            if (view != null) {
                                view.setImageBitmap(bmp);
                                mLatestBitmap = bmp;
                            } else {
                                Log.d(TAG, "Not updating image view: View is null.");
                            }
                        } else {
                            Log.d(TAG, "bmp is null, not updating image view.");
                        }
                    });
        } else {
            Log.d(TAG, "Update did not occur.  Likely not in preview mode");
        }
    }

    public void clearLastImage() {
        if (mLatestBitmap != null && !mLatestBitmap.isRecycled()) {
            mLatestBitmap.recycle();
            mLatestBitmap = null;
        }
    }

    public Bitmap getLatestBitmap() {
        return mLatestBitmap;
    }
}

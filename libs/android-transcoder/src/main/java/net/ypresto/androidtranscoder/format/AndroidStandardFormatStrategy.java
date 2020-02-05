/*
 * Copyright (C) 2014 Yuya Tanaka
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.ypresto.androidtranscoder.format;

import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

class AndroidStandardFormatStrategy implements MediaFormatStrategy {
    public static final int AUDIO_BITRATE_AS_IS = -1;
    public static final int AUDIO_CHANNELS_AS_IS = -1;
    private static final String TAG = "StandardCompression";
    private static final int DEFAULT_VIDEO_BITRATE = 2000 * 1000;
    private static int LONGER_LENGTH = 1280;
    private static int SHORTER_LENGTH = 720;
    private final int mVideoBitrate;
    private final int mVideoresolution;
    private final int mAudioBitrate;
    private final int mAudioChannels;
    private float ASPECT_RATIO = LONGER_LENGTH / SHORTER_LENGTH;

    public AndroidStandardFormatStrategy() {
        this(DEFAULT_VIDEO_BITRATE, SHORTER_LENGTH);
    }

    public AndroidStandardFormatStrategy(int videoBitrate, int SHORTER_LENGTH) {
        this(videoBitrate, SHORTER_LENGTH, AUDIO_BITRATE_AS_IS, AUDIO_CHANNELS_AS_IS);
    }

    public AndroidStandardFormatStrategy(int videoBitrate, int SHORTER_LENGTH, int audioBitrate, int audioChannels) {
        mVideoBitrate = videoBitrate;
        mVideoresolution = SHORTER_LENGTH;
        mAudioBitrate = audioBitrate;
        mAudioChannels = audioChannels;
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    @Override
    public MediaFormat createVideoOutputFormat(MediaFormat inputFormat) {
        int width = inputFormat.getInteger(MediaFormat.KEY_WIDTH);
        int height = inputFormat.getInteger(MediaFormat.KEY_HEIGHT);
        ASPECT_RATIO = (float) width / height;
        Log.d(TAG, "Input video (" + width + "x" + height + " ratio: " + ASPECT_RATIO);
        int shorter, outWidth, outHeight;
        if (width >= height) {
            shorter = height;
            outWidth = Math.round(mVideoresolution * ASPECT_RATIO);
            outHeight = mVideoresolution;
        } else {
            shorter = width;
            outWidth = mVideoresolution;
            outHeight = Math.round(mVideoresolution * ASPECT_RATIO);
        }
        if (shorter < mVideoresolution) {
            Log.d(TAG, "This video is less to " + mVideoresolution + "p, pass-through. (" + width + "x" + height + ")");
            return null;
        }
        Log.d(TAG, "Converting video (" + outWidth + "x" + outHeight + " ratio: " + ASPECT_RATIO + ")");
        MediaFormat format = MediaFormat.createVideoFormat("video/avc", outWidth, outHeight);
        format.setInteger(MediaFormat.KEY_BIT_RATE, mVideoBitrate);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, 30);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 3);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            format.setInteger(MediaFormat.KEY_PROFILE ,MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline);
            format.setInteger(MediaFormat.KEY_LEVEL, MediaCodecInfo.CodecProfileLevel.AVCLevel13);
        }
        return format;
    }

    @Override
    public MediaFormat createAudioOutputFormat(MediaFormat inputFormat) {
        if (mAudioBitrate == AUDIO_BITRATE_AS_IS || mAudioChannels == AUDIO_CHANNELS_AS_IS)
            return null;

        // Use original sample rate, as resampling is not supported yet.
        final MediaFormat format = MediaFormat.createAudioFormat(MediaFormatExtraConstants.MIMETYPE_AUDIO_AAC,
                inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE), mAudioChannels);
        format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        format.setInteger(MediaFormat.KEY_BIT_RATE, mAudioBitrate);
        return format;
    }
}

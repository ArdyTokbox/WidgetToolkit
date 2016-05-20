package com.opentok.android.ui;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.util.Size;
import android.util.SparseArray;
import android.util.SparseIntArray;
import android.view.ViewGroup;
import android.widget.FrameLayout;


import com.opentok.android.BaseVideoCapturer;
import com.opentok.android.BaseVideoRenderer;
import com.opentok.android.OpentokError;
import com.opentok.android.Publisher;
import com.opentok.android.PublisherKit;
import com.opentok.android.Session;
import com.opentok.android.Stream;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;

import dynamiccamframe.tokbox.com.dynamiccamframe.R;

public class PublisherView extends FrameLayout {

    /* attributes from xml */
    private String                              _attrName;
    private boolean                             _attrPreview;
    private boolean                             _attrAudioFallbackEnabled;
    private boolean                             _attrAudioSupported;
    private boolean                             _attrVideoSupported;
    private String                              _attrCapturerClass;
    private String                              _attrRenderClass;
    private int                                 _attrCaptureResultion;
    private int                                 _attrCaptureFPS;
    /* internal members */
    private Context                             _ctx;
    private Publisher                           _publisher;
    private List<Publisher.PublisherListener>   _visitorLst         = new ArrayList<>();
    private List<Publisher.AudioLevelListener>  _audioVisitorLst    = new ArrayList<>();
    private Publisher.PublisherListener         _publisherListener  = new Publisher.PublisherListener() {
        @Override
        public void onStreamCreated(PublisherKit publisher, Stream stream) {
            for (Publisher.PublisherListener visitor: _visitorLst) {
                visitor.onStreamCreated(publisher, stream);
            }
        }

        @Override
        public void onStreamDestroyed(PublisherKit publisher, Stream stream) {
            for (Publisher.PublisherListener visitor: _visitorLst) {
                visitor.onStreamDestroyed(publisher, stream);
            }
        }

        @Override
        public void onError(PublisherKit publisher, OpentokError stream) {
            for (Publisher.PublisherListener visitor: _visitorLst) {
                visitor.onError(publisher, stream);
            }
        }
    };
    private Publisher.AudioLevelListener        _audioListener  = new PublisherKit.AudioLevelListener() {
        @Override
        public void onAudioLevelUpdated(PublisherKit publisherKit, float v) {

        }
    };
    /* helper tables */
    private static final SparseArray<Publisher.CameraCaptureResolution> mResolutionTbl = new SparseArray<Publisher.CameraCaptureResolution>() {
        {
            append(0, Publisher.CameraCaptureResolution.LOW);
            append(1, Publisher.CameraCaptureResolution.MEDIUM);
            append(2, Publisher.CameraCaptureResolution.HIGH);
        }
    };
    private static final SparseArray<Publisher.CameraCaptureFrameRate> mFrameRateTbl = new SparseArray<Publisher.CameraCaptureFrameRate>() {
        {
            append(3, Publisher.CameraCaptureFrameRate.FPS_1);
            append(2, Publisher.CameraCaptureFrameRate.FPS_7);
            append(1, Publisher.CameraCaptureFrameRate.FPS_15);
            append(0, Publisher.CameraCaptureFrameRate.FPS_30);
        }
    };


    /* Members */
    public PublisherView(Context ctx) {
        super(ctx);
        _ctx = ctx;
    }

    public PublisherView(Context ctx, AttributeSet attrs) {
        super(ctx, attrs);
        _ctx = ctx;
        _parseAttributes(attrs);
    }

    public PublisherView(Context ctx, AttributeSet attrs, int defStyleAttr) {
        super(ctx, attrs, defStyleAttr);
        _ctx = ctx;
        _parseAttributes(attrs);
    }

    public void addPublisherListener(Publisher.PublisherListener visitor) {
        if (!_visitorLst.contains(visitor)) {
            _visitorLst.add(visitor);
        }
    }

    public void removePublisherListener(PublisherKit.PublisherListener visitor) {
        if (_visitorLst.contains(visitor)) {
            _visitorLst.remove(visitor);
        }
    }

    public void addVolumeListener(PublisherKit.AudioLevelListener visitor) {
        if (!_audioVisitorLst.contains(visitor)) {
            _audioVisitorLst.add(visitor);
        }
    }

    public void removeVolumeListener(PublisherKit.AudioLevelListener visitor) {
        if (_audioVisitorLst.contains(visitor)) {
            _audioVisitorLst.remove(visitor);
        }
    }

    public void connect(Session session) {
        session.publish(_publisher);
    }

    public void disconnect(Session session) {
        session.unpublish(_publisher);
    }

    public void cycleCamera() {
        if (null != _publisher) {
            _publisher.cycleCamera();
        } else {
            // TODO: keep track of pending cycle camera calls
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        _constructPublisher();
        _publisher.setStyle(
                BaseVideoRenderer.STYLE_VIDEO_SCALE,
                BaseVideoRenderer.STYLE_VIDEO_FILL
        );
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        this.addView(_publisher.getView(), layoutParams);
        if (_attrPreview) {
            _publisher.startPreview();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        this.removeView(_publisher.getView());
        _destroyPublisher();
    }

    private void _constructPublisher() {
        /* create publisher depending on flags */
        if (null != _attrCapturerClass) {
            _publisher = new Publisher(_ctx, _attrName, _constructCapturer());
        } else if (1 != _attrCaptureResultion || 0 != _attrCaptureFPS) {
            _publisher = new Publisher(
                    _ctx,
                    _attrName,
                    mResolutionTbl.get(_attrCaptureResultion),
                    mFrameRateTbl.get(_attrCaptureFPS));
        } else {
            _publisher = new Publisher(_ctx, _attrName, _attrAudioSupported, _attrVideoSupported);
        }
        /* setup publisher */
        _publisher.setPublisherListener(_publisherListener);
        _publisher.setAudioFallbackEnabled(_attrAudioFallbackEnabled);
        if (null != _attrRenderClass) {
            _publisher.setRenderer(_constructRenderer());
        }
    }

    private void _destroyPublisher() {
        if (_attrPreview) {
            _publisher.destroy();
        }
        _publisher = null;
    }

    private BaseVideoCapturer _constructCapturer() {
        return (BaseVideoCapturer)_constructClass(_attrCapturerClass);
    }

    private BaseVideoRenderer _constructRenderer() {
        return (BaseVideoRenderer)_constructClass(_attrRenderClass);
    }

    private Object _constructClass(String className) {
        try {
            Class<?> clz = Class.forName(className);
            Constructor ctor = clz.getConstructor(new Class[]{Context.class});
            return ctor.newInstance(_ctx);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private void _parseAttributes(AttributeSet attrs) {
        TypedArray vals = _ctx.obtainStyledAttributes(attrs, R.styleable.PublisherView);
        try {
            _attrName               = vals.getString(R.styleable.PublisherView_name);
            _attrPreview            = vals.getBoolean(
                    R.styleable.PublisherView_previewEnabled, false
            );
            _attrAudioFallbackEnabled= vals.getBoolean(
                    R.styleable.PublisherView_audioFallbackEnabled, false
            );
            _attrAudioSupported      = vals.getBoolean(
                    R.styleable.PublisherView_supportAudio, true
            );
            _attrVideoSupported      = vals.getBoolean(
                    R.styleable.PublisherView_supportVideo, true
            );
            _attrCapturerClass       = vals.getString(R.styleable.PublisherView_capturerClass);
            _attrRenderClass         = vals.getString(R.styleable.PublisherView_rendererClass);
            _attrCaptureResultion    = vals.getInteger(
                    R.styleable.PublisherView_captureResolution, 1
            );
            _attrCaptureFPS          = vals.getInteger(
                    R.styleable.PublisherView_captureFrameRate, 0
            );
        } finally {
            vals.recycle();
        }
    }
}

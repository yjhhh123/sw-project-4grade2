package org.pytorch.demo.objectdetection;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.speech.tts.TextToSpeech;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.os.Handler;
import java.util.ArrayList;
import java.util.Locale;

public class ResultView extends View {
    private final Handler mHandler = new Handler();
    private final static int TEXT_X = 40;
    private final static int TEXT_Y = 35;
    private final static int TEXT_WIDTH = 450;
    private final static int TEXT_HEIGHT = 50;

    private Paint mPaintRectangle;
    private Paint mPaintText;
    private ArrayList<Result> mResults;
    private TextToSpeech tts;
    private boolean mIsSpeaking = false;
    private long mLastDetectionTime = 0;
    private static final long DETECTION_INTERVAL = 1500; // 1.5초
    private static final String CAR_CLASS = "Car";

    public ResultView(Context context) {
        super(context);
        initTTS(context);
    }

    public ResultView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initTTS(context);
        mPaintRectangle = new Paint();
        mPaintRectangle.setColor(Color.YELLOW);
        mPaintText = new Paint();
    }

    private void initTTS(Context context) {
        tts = new TextToSpeech(context, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status == TextToSpeech.SUCCESS) {
                    tts.setLanguage(Locale.KOREA);
                } else {
                    Log.e("TTS", "Initialization failed");
                }
            }
        });
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (mResults == null) return;
        for (Result result : mResults) {
            mPaintRectangle.setStrokeWidth(5);
            mPaintRectangle.setStyle(Paint.Style.STROKE);
            canvas.drawRect(result.rect, mPaintRectangle);

            Path mPath = new Path();
            RectF mRectF = new RectF(result.rect.left, result.rect.top, result.rect.left + TEXT_WIDTH, result.rect.top + TEXT_HEIGHT);
            mPath.addRect(mRectF, Path.Direction.CW);
            mPaintText.setColor(Color.MAGENTA);
            canvas.drawPath(mPath, mPaintText);

            if (PrePostProcessor.mClasses != null) {
                mPaintText.setColor(Color.WHITE);
                mPaintText.setStrokeWidth(0);
                mPaintText.setStyle(Paint.Style.FILL);
                mPaintText.setTextSize(32);

                String textToDraw = String.format("%s", PrePostProcessor.mClasses[result.classIndex]);
                canvas.drawText(
                        String.format("%s %.2f", PrePostProcessor.mClasses[result.classIndex], Math.min(result.score + 0.32, 0.98)),
                        result.rect.left + TEXT_X,
                        result.rect.top + TEXT_Y,
                        mPaintText
                );


                String speechOutput = generateSpeechOutput(textToDraw);
                if (!speechOutput.isEmpty() && !mIsSpeaking) {
                    if (textToDraw.equals(CAR_CLASS)) {
                        tts.speak(speechOutput, TextToSpeech.QUEUE_FLUSH, null, null);
                    } else if (System.currentTimeMillis() - mLastDetectionTime > DETECTION_INTERVAL) {
                        tts.speak(speechOutput, TextToSpeech.QUEUE_FLUSH, null, null);
                        mIsSpeaking = true;
                        mHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                mIsSpeaking = false;
                                mLastDetectionTime = System.currentTimeMillis();
                                invalidate();
                            }
                        }, 2000);
                    }
                }
            } else {
                Log.e("ResultView", "PrePostProcessor.mClasses is null!");
            }
        }
    }

    private String generateSpeechOutput(String textToDraw) {
        String speechOutput = "";
        if (textToDraw.equals("Car")) {
            speechOutput = "전방에 차량이 존재합니다 주의해서 건너주세요";
        } else if (textToDraw.equals("crosswalk")) {
            speechOutput = "전방에 횡단보도가 있습니다";
        } else if (textToDraw.equals("green_pedestrian_light")) {
            speechOutput = "초록불입니다 주의하시면서 건너세요";
        } else if (textToDraw.equals("red_pedestrian_light")) {
            speechOutput = "빨간불입니다 잠시 대기해주세요";
        }
        return speechOutput;
    }

    public void setResults(ArrayList<Result> results) {
        mResults = results;
    }
}


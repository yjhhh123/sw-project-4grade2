
package org.pytorch.demo.objectdetection;


import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Context;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;

import org.pytorch.IValue;

import org.pytorch.Module;
import org.pytorch.Tensor;


import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import android.app.NotificationChannel;
import android.app.NotificationManager;

import android.os.Build;



import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.minew.beaconset.BluetoothState;
import com.minew.beaconset.MinewBeacon;
import com.minew.beaconset.MinewBeaconManager;
import com.minew.beaconset.MinewBeaconManagerListener;


import java.util.Locale;

public class MainActivity extends AppCompatActivity implements Runnable, TextToSpeech.OnInitListener {
    private boolean isConnected;
    private long lastChangeTime = 0;
    private static final long DEBOUNCE_TIME = 5000; // 디바운스 시간을 10초로 설정
    private int mImageIndex = 0;
    private String[] mTestImages = {"signal1.png", "signal2.png", "signal3.png"};
    private ImageView mImageView;
    private ResultView mResultView;
    private Button mButtonDetect;
    private ProgressBar mProgressBar;


    private Bitmap mBitmap = null;
    private Module mModule = null;
    private float mImgScaleX, mImgScaleY, mIvScaleX, mIvScaleY, mStartX, mStartY;
    private TextToSpeech textToSpeech;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        textToSpeech = new TextToSpeech(this, this);

        // Check for permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 1);
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 1);
        }

        mImageView = findViewById(R.id.imageView);
        mResultView = findViewById(R.id.resultView);
        mResultView.setVisibility(View.INVISIBLE);

        final Button buttonTest = findViewById(R.id.testButton);
        buttonTest.setText(("Test Image 1/3"));
        buttonTest.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mResultView.setVisibility(View.INVISIBLE);
                mImageIndex = (mImageIndex + 1) % mTestImages.length;
                buttonTest.setText(String.format("Text Image %d/%d", mImageIndex + 1, mTestImages.length));

                try {
                    mBitmap = BitmapFactory.decodeStream(getAssets().open(mTestImages[mImageIndex]));
                    mImageView.setImageBitmap(mBitmap);
                } catch (IOException e) {
                    Log.e("Object Detection", "Error reading assets", e);
                    finish();
                }
            }
        });

        final Button buttonLive = findViewById(R.id.liveButton);
        buttonLive.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if(event.getAction() == MotionEvent.ACTION_DOWN) {
                    // 버튼이 눌렸을 때 축소 애니메이션 적용
                    Animation anim = new ScaleAnimation(1f, 0.9f, 1f, 0.9f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
                    anim.setFillAfter(true);
                    anim.setDuration(100);
                    v.startAnimation(anim);
                } else if(event.getAction() == MotionEvent.ACTION_UP) {
                    // 손을 때면 다시 확대 애니메이션 적용
                    Animation anim = new ScaleAnimation(0.9f, 1f, 0.9f, 1f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
                    anim.setFillAfter(true);
                    anim.setDuration(100);
                    v.startAnimation(anim);
                    // 클릭 이벤트 처리
                    Intent intent = new Intent(MainActivity.this, ObjectDetectionActivity.class);
                    startActivity(intent);
                }
                return false;
            }
        });

        mButtonDetect = findViewById(R.id.detectButton);
        mProgressBar = findViewById(R.id.progressBar);
        mButtonDetect.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mButtonDetect.setEnabled(false);
                mProgressBar.setVisibility(ProgressBar.VISIBLE);
                mButtonDetect.setText(getString(R.string.run_model));

                mImgScaleX = (float)mBitmap.getWidth() / PrePostProcessor.mInputWidth;
                mImgScaleY = (float)mBitmap.getHeight() / PrePostProcessor.mInputHeight;

                mIvScaleX = (mBitmap.getWidth() > mBitmap.getHeight() ? (float)mImageView.getWidth() / mBitmap.getWidth() : (float)mImageView.getHeight() / mBitmap.getHeight());
                mIvScaleY  = (mBitmap.getHeight() > mBitmap.getWidth() ? (float)mImageView.getHeight() / mBitmap.getHeight() : (float)mImageView.getWidth() / mBitmap.getWidth());

                mStartX = (mImageView.getWidth() - mIvScaleX * mBitmap.getWidth())/2;
                mStartY = (mImageView.getHeight() -  mIvScaleY * mBitmap.getHeight())/2;

                Thread thread = new Thread(MainActivity.this);
                thread.start();
            }
        });

        try {
            // Change here: Load the PyTorch module
            mModule = org.pytorch.LiteModuleLoader.load(MainActivity.assetFilePath(getApplicationContext(), "model.ptl"));
            BufferedReader br = new BufferedReader(new InputStreamReader(getAssets().open("signal_light.txt")));
            String line;
            List<String> classes = new ArrayList<>();
            while ((line = br.readLine()) != null) {
                classes.add(line);
            }
            PrePostProcessor.mClasses = new String[classes.size()];
            classes.toArray(PrePostProcessor.mClasses);
        } catch (IOException e) {
            Log.e("Object Detection", "Error reading assets", e);
            finish();
        }

        MinewBeaconManager mMinewBeaconManager = MinewBeaconManager.getInstance(this);
        mMinewBeaconManager.setMinewbeaconManagerListener(new MinewBeaconManagerListener() {
            private static final String TARGET_UUID = "74278BDA-B644-4520-8F0C-720EAF059935";
            private static final int TARGET_MAJOR = 40011;
            private static final int TARGET_MINOR = 45552;

            @Override
            public void onRangeBeacons(final List<MinewBeacon> minewBeacons) {
                boolean foundTargetBeacon = false;

                for (MinewBeacon minewBeacon : minewBeacons) {
                    if (isTargetBeacon(minewBeacon)) {
                        foundTargetBeacon = true;
                        break;
                    }
                }

                long currentTime = System.currentTimeMillis();
                if (foundTargetBeacon && !isConnected && (currentTime - lastChangeTime > DEBOUNCE_TIME)) {
                    isConnected = true;
                    lastChangeTime = currentTime;
                    showNotification("비콘이 연결되었습니다.");
                    if (textToSpeech != null) {
                        textToSpeech.speak("횡단보도에서 벗어났습니다.", TextToSpeech.QUEUE_FLUSH, null, null);
                    }
                    Log.d("Notification", "Beacon connected");
                } else if (!foundTargetBeacon && isConnected && (currentTime - lastChangeTime > DEBOUNCE_TIME)) {
                    isConnected = false;
                    lastChangeTime = currentTime;
                    showNotification("비콘이 연결 해제되었습니다.");
                    Log.d("Notification", "Beacon disconnected");
                }
            }

            private boolean isTargetBeacon(MinewBeacon minewBeacon) {
                String beaconUUID = minewBeacon.getUuid();
                int beaconMajor = Integer.parseInt(minewBeacon.getMajor());
                int beaconMinor = Integer.parseInt(minewBeacon.getMinor());
                return TARGET_UUID.equals(beaconUUID) && TARGET_MAJOR == beaconMajor && TARGET_MINOR == beaconMinor;
            }

            @Override
            public void onAppearBeacons(List<MinewBeacon> list) {
                // 생략 (이벤트 로직에 따라 필요한 경우 구현)
            }

            @Override
            public void onDisappearBeacons(List<MinewBeacon> list) {
                // 생략 (이벤트 로직에 따라 필요한 경우 구현)
            }

            @Override
            public void onUpdateBluetoothState(BluetoothState state) {
                // 생략 (상태 업데이트 로직에 따라 필요한 경우 구현)
            }
        });

        try {
            mMinewBeaconManager.startScan();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showNotification(String message) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = getString(R.string.channel_name);
            String description = getString(R.string.channel_description);
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel channel = new NotificationChannel("channel_id", name, importance);
            channel.setDescription(description);
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "channel_id")
                .setContentTitle(getString(R.string.app_name))
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setSmallIcon(R.drawable.ic_launcher_background);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        notificationManager.notify(1, builder.build());
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_CANCELED) {
            switch (requestCode) {
                case 0:
                    if (resultCode == RESULT_OK && data != null) {
                        mBitmap = (Bitmap) data.getExtras().get("data");
                        Matrix matrix = new Matrix();
                        matrix.postRotate(90.0f);
                        mBitmap = Bitmap.createBitmap(mBitmap, 0, 0, mBitmap.getWidth(), mBitmap.getHeight(), matrix, true);
                        mImageView.setImageBitmap(mBitmap);
                    }
                    break;
                case 1:
                    if (resultCode == RESULT_OK && data != null) {
                        Uri selectedImage = data.getData();
                        String[] filePathColumn = {MediaStore.Images.Media.DATA};
                        if (selectedImage != null) {
                            Cursor cursor = getContentResolver().query(selectedImage,
                                    filePathColumn, null, null, null);
                            if (cursor != null) {
                                cursor.moveToFirst();
                                int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
                                String picturePath = cursor.getString(columnIndex);
                                mBitmap = BitmapFactory.decodeFile(picturePath);
                                Matrix matrix = new Matrix();
                                matrix.postRotate(90.0f);
                                mBitmap = Bitmap.createBitmap(mBitmap, 0, 0, mBitmap.getWidth(), mBitmap.getHeight(), matrix, true);
                                mImageView.setImageBitmap(mBitmap);
                                cursor.close();
                            }
                        }
                    }
                    break;
            }
        }
    }

    @Override
    public void run() {
        Bitmap resizedBitmap = Bitmap.createScaledBitmap(mBitmap, PrePostProcessor.mInputWidth, PrePostProcessor.mInputHeight, true);
        final Tensor inputTensor = org.pytorch.torchvision.TensorImageUtils.bitmapToFloat32Tensor(resizedBitmap, PrePostProcessor.NO_MEAN_RGB, PrePostProcessor.NO_STD_RGB);
        IValue[] outputTuple = mModule.forward(IValue.from(inputTensor)).toTuple();
        final Tensor outputTensor = outputTuple[0].toTensor();
        final float[] outputs = outputTensor.getDataAsFloatArray();
        final ArrayList<Result> results =  PrePostProcessor.outputsToNMSPredictions(outputs, mImgScaleX, mImgScaleY, mIvScaleX, mIvScaleY, mStartX, mStartY);

        runOnUiThread(() -> {
            mButtonDetect.setEnabled(true);
            mButtonDetect.setText(getString(R.string.detect));
            mProgressBar.setVisibility(ProgressBar.INVISIBLE);
            mResultView.setResults(results);
            mResultView.invalidate();
            mResultView.setVisibility(View.VISIBLE);
        });
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            int result = textToSpeech.setLanguage(Locale.KOREA);

            if (result == TextToSpeech.LANG_MISSING_DATA ||
                    result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TTS", "Language not supported");
            } else {
                // 음성으로 안내할 메시지
                String text = "안녕하세요. 본 어플리케이션은 시각장애인분들의 안전한 횡단보도의 횡단을 위한 어플리케이션입니다. " +
                        "모든 상황에서 완벽하게 작동하지 않으니 주의하여 사용해주세요. 화면을 클릭하면 어플이 시작됩니다";

                textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
            }
        } else {
            Log.e("TTS", "Initialization failed");
        }
    }

    @Override
    protected void onDestroy() {
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        super.onDestroy();
    }

    public static String assetFilePath(Context context, String assetName) throws IOException {
        File file = new File(context.getFilesDir(), assetName);
        if (file.exists() && file.length() > 0) {
            return file.getAbsolutePath();
        }

        try (InputStream is = context.getAssets().open(assetName)) {
            try (OutputStream os = new FileOutputStream(file)) {
                byte[] buffer = new byte[4 * 1024];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    os.write(buffer, 0, read);
                }
                os.flush();
            }
            return file.getAbsolutePath();
        }
    }
}

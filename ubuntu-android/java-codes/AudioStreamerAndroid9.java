import android.app.Application;
import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Looper;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class AudioStreamerAndroid9 {
    private static final int SAMPLE_RATE = 44100;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    
    // 伪装包名
    private static final String OP_PACKAGE_NAME = "com.android.shell";

    public static void main(String[] args) {
        System.out.println("Starting AudioStreamer on Android " + Build.VERSION.RELEASE + " (SDK " + Build.VERSION.SDK_INT + ")...");

        try {
            // 1. 准备 Looper
            if (Looper.myLooper() == null) {
                Looper.prepare();
            }

            // =============================================================
            // 【核心修复】: 在创建 AudioRecord 之前，伪造 ActivityThread 环境
            // 解决 Android 9 JNI 层因获取不到包名导致的空指针崩溃 (SIGSEGV)
            // =============================================================
            spoofActivityThread();

            // 2. 计算缓冲区 (加倍以防溢出)
            int minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
            int bufferSize = Math.max(minBufferSize * 2, 4096);
            System.out.println("Buffer Size: " + bufferSize);

            // 3. 初始化 AudioRecord
            // 此时构造函数内部调用 ActivityThread.currentOpPackageName() 时，会拿到我们伪造的包名
            AudioRecord recorder = new AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    bufferSize
            );

            // 4. 检查初始化
            if (recorder.getState() != AudioRecord.STATE_INITIALIZED) {
                System.err.println("Error: AudioRecord init failed. State: " + recorder.getState());
                return;
            }

            // 5. 启动录制
            recorder.startRecording();
            
            // 针对老旧设备的硬件预热
            if (Build.VERSION.SDK_INT <= 28) {
                Thread.sleep(200);
            }

            if (recorder.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
                 System.err.println("Error: Failed to start recording.");
                 return;
            }

            System.out.println("Recording started! (Reading 500 frames...)");

            byte[] buffer = new byte[bufferSize];
            
            // 6. 循环读取
            for (int i = 0; i < 500; i++) {
                int readBytes = recorder.read(buffer, 0, bufferSize);
                if (readBytes > 0) {
                    long sum = 0;
                    for (int j = 0; j < Math.min(readBytes, 50); j++) sum += Math.abs(buffer[j]);
                    System.out.print("\rRead: " + readBytes + " bytes | Vol: " + (sum / 50) + "  ");
                } else if (readBytes < 0) {
                    System.err.println("\nRead Error: " + readBytes);
                    break;
                }
            }

            recorder.stop();
            recorder.release();
            System.out.println("\nFinished.");

        } catch (Throwable e) {
            System.err.println("\nCRASH CAUGHT: " + e.toString());
            e.printStackTrace();
        }
    }

    /**
     * 通过反射伪造 ActivityThread 和 Application，
     * 使得 ActivityThread.currentOpPackageName() 返回 "com.android.shell"。
     */
    private static void spoofActivityThread() {
        try {
            Class<?> atClass = Class.forName("android.app.ActivityThread");
            
            // 1. 获取或创建 ActivityThread 实例
            Method currentAtMethod = atClass.getDeclaredMethod("currentActivityThread");
            currentAtMethod.setAccessible(true);
            Object currentAt = currentAtMethod.invoke(null);
            
            if (currentAt == null) {
                // 如果当前没有 ActivityThread，手动 new 一个
                Constructor<?> ctor = atClass.getDeclaredConstructor();
                ctor.setAccessible(true);
                currentAt = ctor.newInstance();
                
                // 将其设置到静态字段 sCurrentActivityThread 中
                Field sCurrentField = atClass.getDeclaredField("sCurrentActivityThread");
                sCurrentField.setAccessible(true);
                sCurrentField.set(null, currentAt);
            }
            
            // 2. 创建一个伪造的 Application 对象
            // 重写 getOpPackageName 方法返回我们需要的值
            Application mockApp = new Application() {
                // @Override // 编译时请确保 SDK 版本兼容，或者直接去掉 @Override 注解
                public String getOpPackageName() {
                    return OP_PACKAGE_NAME;
                }
                // @Override
                public String getPackageName() {
                    return OP_PACKAGE_NAME;
                }
            };
            
            // 3. 将伪造的 Application 塞入 ActivityThread 的 mInitialApplication 字段
            // 这样 ActivityThread.currentOpPackageName() 就会调用 mockApp.getOpPackageName()
            Field mAppField = atClass.getDeclaredField("mInitialApplication");
            mAppField.setAccessible(true);
            mAppField.set(currentAt, mockApp);
            
            System.out.println("DEBUG: Spoofed ActivityThread package name to: " + OP_PACKAGE_NAME);

        } catch (Exception e) {
            System.err.println("WARNING: Failed to spoof ActivityThread: " + e);
            e.printStackTrace();
        }
    }
}
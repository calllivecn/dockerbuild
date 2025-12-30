import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Looper;
import android.content.Context;
import android.content.ContextWrapper;
import android.os.Build;
import java.lang.reflect.Field;

/**
 * 专为 Root 环境设计的简化版 AudioRecord 示例
 * 兼容 Android 9 (API 28) 到 Android 14 (API 34)+
 * android9 还是失败
 */
public class AudioStreamer {
    private static final int SAMPLE_RATE = 44100;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;

    // 强制伪装的包名，shell 通常有录音权限
    private static final String OP_PACKAGE_NAME = "com.android.shell";

    public static void main(String[] args) {
        // 1. 初始化 Looper (部分 Android 系统底层调用依赖它)
        if (Looper.myLooper() == null) {
            Looper.prepare();
        }

        // 2. 获取 Context 并解决 Android 9 的底层 String16(NULL) 崩溃
        // 即使是 Root 环境，底层 JNI 依然会通过 context.getOpPackageName() 获取调用者身份
        Context rawContext = InitializeAndroidEnvironment.getSystemContext();
        Context safeContext = new ContextWrapper(rawContext) {
            @Override
            public String getOpPackageName() {
                return "com.android.shell"; // 即使是 Root，伪装成 shell 是最稳妥的
            }
            @Override
            public String getPackageName() {
                return "com.android.shell";
            }
        };

        int minbufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
        // 修复：不要直接用 minBufferSize，建议扩大到 2倍 或 至少 4096 字节，防止底层溢出
        int bufferSize = Math.max(minbufferSize * 2, 4096);

        // 3. 直接使用构造函数 (在 API 28-34+ 均表现稳定)
        // 注意：由于是 Root，我们将 AudioSource 设置为 MIC 
        AudioRecord recorder = new AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
        );

        // =============================================================
        // 【关键修复 1】: 通过反射强行注入 OpPackageName
        // 解决 "String16(NULL)" 崩溃。因为 app_process 没有自动设置它。
        // =============================================================
        try {
            // AudioRecord 内部有一个 mOpPackageName 字段 (String)
            Field opPackageNameField = AudioRecord.class.getDeclaredField("mOpPackageName");
            opPackageNameField.setAccessible(true);
            // 强行设置为 shell，这样 native 层检查权限时就有值了
            opPackageNameField.set(recorder, OP_PACKAGE_NAME);
            System.out.println("Fixed: 已通过反射注入 OpPackageName = " + OP_PACKAGE_NAME);
        } catch (Exception e) {
            // 如果字段找不到（不同版本可能混淆），尝试忽略，但打印警告
            System.err.println("Warning: 反射注入包名失败: " + e.getMessage());
        }

        if (recorder.getState() != AudioRecord.STATE_INITIALIZED) {
            System.err.println("AudioRecord 初始化失败。请检查是否有其他进程占用麦克风。");
            return;
        }

        try {
            recorder.startRecording();
            // =============================================================
            // 【关键修复 2】: 避让华为/Android 9 的硬件启动延迟
            // =============================================================
            if (Build.VERSION.SDK_INT <= 28) {
                System.out.println("适配 Android 9: 等待硬件预热...");
                try { Thread.sleep(200); } catch (InterruptedException e) {}
            }
            
            // 再次检查状态，防止 start 过程中崩溃或被系统打断
            if (recorder.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
                 System.err.println("Error: startRecording 调用后未能进入录制状态。");
                 return;
            }

            System.out.println("录音启动成功 (Root)。按 Ctrl+C 停止。");

            byte[] buffer = new byte[bufferSize];
            // 简单演示录制 500 次数据读取
            for (int i = 0; i < 500; i++) {
                int read = recorder.read(buffer, 0, bufferSize);
                if (read > 0) {
                    // 简化的电平反馈 (取前 100 个字节的平均值)
                    long sum = 0;
                    for (int j = 0; j < Math.min(read, 100); j++) {
                        sum += Math.abs(buffer[j]);
                    }
                    System.out.printf("\r数据读取中... 缓冲区大小: %-5d | 信号特征: %-5d", read, (sum / 100));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            recorder.stop();
            recorder.release();
            System.out.println("\n资源已释放。");
            System.exit(0);
        }
    }
}
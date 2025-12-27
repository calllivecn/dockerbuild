import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Looper;
import android.content.Context;
import android.content.ContextWrapper;

/**
 * 专为 Root 环境设计的简化版 AudioRecord 示例
 * 兼容 Android 9 (API 28) 到 Android 14 (API 34)+
 */
public class AudioStreamer {
    private static final int SAMPLE_RATE = 44100;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;

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

        int bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);

        // 3. 直接使用构造函数 (在 API 28-34+ 均表现稳定)
        // 注意：由于是 Root，我们将 AudioSource 设置为 MIC 
        AudioRecord recorder = new AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
        );

        if (recorder.getState() != AudioRecord.STATE_INITIALIZED) {
            System.err.println("AudioRecord 初始化失败。请检查是否有其他进程占用麦克风。");
            return;
        }

        try {
            recorder.startRecording();
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
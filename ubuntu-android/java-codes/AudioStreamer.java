
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Looper;
import android.os.Build;
import android.content.Context;

/**
 * 对应你编译指令中的 AudioStreamer.java
 */
public class AudioStreamer {
    private static final String TAG = "AudioStreamer";
    private static final int SAMPLE_RATE = 44100;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;

    public static void main(String[] args) {
        System.out.println("--- 启动 AudioRecord 流处理示例 ---");

        // 初始化 Android 环境并获取 Context
        // 假设你的 InitializeAndroidEnvironment 返回 android.content.Context
        Context context = InitializeAndroidEnvironment.getSystemContext();

        // 处理 Looper 警告：在 API 30+ 建议直接使用 prepare() 或 handle 本地消息
        if (Looper.myLooper() == null) {
            Looper.prepare(); 
        }

        int bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
        
        AudioRecord recorder;

        // 根据 Android 版本选择构造函数
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // API 31+
            recorder = new AudioRecord.Builder()
                    .setAudioSource(MediaRecorder.AudioSource.MIC)
                    .setAudioFormat(new AudioFormat.Builder()
                            .setEncoding(AUDIO_FORMAT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(CHANNEL_CONFIG)
                            .build())
                    .setBufferSizeInBytes(bufferSize)
                    .setContext(context) // 使用你环境中的 Context
                    .build();
        } else {
            recorder = new AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    bufferSize
            );
        }

        if (recorder.getState() != AudioRecord.STATE_INITIALIZED) {
            System.err.println("错误: AudioRecord 无法初始化。请确认 UID 权限或 Context 是否有效。");
            return;
        }

        recorder.startRecording();
        System.out.println("开始读取音频流 (10秒)...");

        byte[] buffer = new byte[bufferSize];
        long startTime = System.currentTimeMillis();

        try {
            while (System.currentTimeMillis() - startTime < 10000) {
                int read = recorder.read(buffer, 0, bufferSize);
                if (read > 0) {
                    // 计算简单的平均振幅作为输出反馈
                    long sum = 0;
                    for (int i = 0; i < read / 2; i++) {
                        short sample = (short) ((buffer[i * 2] & 0xFF) | (buffer[i * 2 + 1] << 8));
                        sum += Math.abs(sample);
                    }
                    System.out.printf("\r读取: %d bytes | 平均振幅: %d    ", read, (sum / (read / 2)));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            recorder.stop();
            recorder.release();
            System.out.println("\n录音结束。");
        }
    }
}
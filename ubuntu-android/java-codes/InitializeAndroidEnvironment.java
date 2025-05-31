// 兼容 Android 9.0 (API 28) 及以上版本
// 编译建议：minSdkVersion 28, targetSdkVersion 28+
// 通用安卓环境初始化
import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Looper;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

@SuppressLint({"PrivateApi", "BlockedPrivateApi", "SoonBlockedPrivateApi", "DiscouragedPrivateApi", "InternalInsetResource", "DiscouragedApi"})
public final class InitializeAndroidEnvironment {

    private static Context sContext;
    private static final String TAG = "EnvInit工具";

    /**
     * 初始化一个模拟的 Android 环境，并获取系统 Context。
     * 此方法会尝试为当前线程准备 Looper，并利用反射创建 ActivityThread 实例。
     * @return 成功初始化的系统 Context。
     * @throws RuntimeException 如果环境初始化失败。
     */
    public static Context getSystemContext() throws RuntimeException {

        // 检查 Android 版本是否符合要求
        if (android.os.Build.VERSION.SDK_INT < 28) {
            System.err.println("错误: 仅支持 Android 9.0 (API 28) 及以上版本。");
            System.exit(1);
        }


        if (sContext != null) {
            System.out.println(TAG + ": 系统上下文已存在，直接返回。");
            return sContext;
        }

        try {
            // 在创建 ActivityThread 实例之前，检查并准备 Looper
            if (Looper.myLooper() == null) {
                System.out.println(TAG + ": 当前线程没有 Looper，正在调用 Looper.prepare()...");
                Looper.prepare(); // 为当前线程准备 Looper
            } else {
                System.out.println(TAG + ": 当前线程已有 Looper。");
            }

            Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
            Constructor<?> activityThreadConstructor = activityThreadClass.getDeclaredConstructor();
            activityThreadConstructor.setAccessible(true);
            Object activityThreadInstance = activityThreadConstructor.newInstance();

            Field sCurrentActivityThreadField = activityThreadClass.getDeclaredField("sCurrentActivityThread");
            sCurrentActivityThreadField.setAccessible(true);
            sCurrentActivityThreadField.set(null, activityThreadInstance);

            try {
                Field mSystemThreadField = activityThreadClass.getDeclaredField("mSystemThread");
                mSystemThreadField.setAccessible(true);
                mSystemThreadField.setBoolean(activityThreadInstance, true);
            } catch (NoSuchFieldException e) {
                System.err.println(TAG + ": 警告: 未找到 mSystemThread 字段，跳过设置。 " + e.getMessage());
            }

            Method getSystemContextMethod = activityThreadClass.getDeclaredMethod("getSystemContext");
            getSystemContextMethod.setAccessible(true);
            sContext = (Context) getSystemContextMethod.invoke(activityThreadInstance);

            if (sContext == null) {
                throw new IllegalStateException("设置后未能获取系统上下文。");
            }
            System.out.println(TAG + ": Android 环境模拟设置完成。已获取上下文。");
            return sContext;

        } catch (Exception e) {
            System.err.println(TAG + ": 致命错误: 初始化 Android 环境模拟失败: " + e.getMessage());
            e.printStackTrace(System.err);
            System.exit(1); // 退出程序
            throw new RuntimeException("初始化 Android 环境失败", e); // 抛出异常以满足方法签名
        }
    }
}

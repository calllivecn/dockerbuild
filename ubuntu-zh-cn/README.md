# ubuntu-zh-cn:latest 

- 安装中文字体

```bash
bash build.sh
```

---


## 在容器（尤其是 Docker）中处理中文显示问题，本质上是解决**系统环境（Locale）**与**资源支持（Fonts）**的缺失。由于容器镜像追求极简，它们通常剔除了所有非必要的语言包和字体。

## 要彻底解决“中文乱码”或“方框/豆腐块”问题，你需要根据应用场景（终端显示 vs. 图像/PDF 渲染）分两步走。


## 1. 解决终端/日志乱码：配置 Locale

如果你在 `docker logs` 或 `exec` 进入容器时看到文件名或输出信息是乱码，这是因为容器默认使用 `POSIX/C` 编码（仅支持 ASCII）。

### 解决方案（以 Debian/Ubuntu 为例）：

在 Dockerfile 中安装 `locales` 并生成 UTF-8 配置。

```dockerfile
# 安装 locales 软件
RUN apt-get update && apt-get install -y locales \
    # 取消 zh_CN.UTF-8 的注释并生成
    && sed -i -e 's/# zh_CN.UTF-8 UTF-8/zh_CN.UTF-8 UTF-8/' /etc/locale.gen \
    && locale-gen

# 设置环境变量
ENV LANG=zh_CN.UTF-8 \
    LANGUAGE=zh_CN:zh \
    LC_ALL=zh_CN.UTF-8

```

* **Python 用户注意**：设置 `PYTHONIOENCODING=utf-8` 环境变量可以强制 Python 在标准输出时使用 UTF-8，防止 `UnicodeEncodeError`。

---

## 2. 解决图片/PDF/浏览器乱码：安装 CJK 字体

如果你使用 Python 的 `matplotlib`、`Pillow`，或者使用 `headless chrome` 生成截图，发现中文变成了**方框（豆腐块）**，这是因为容器内缺失中文字体文件。

### 常用字体包选择：

* **文泉驿（开源经典）**：`fonts-wqy-microhei` (微米黑) 或 `fonts-wqy-zenhei` (正黑)。
* **Google Noto（兼容性强）**：`fonts-noto-cjk`。

### Dockerfile 示例：

```dockerfile
RUN apt-get update && apt-get install -y \
    fonts-wqy-microhei \
    # 刷新字体缓存
    && fc-cache -f -v

```

---

## 3. 常见场景的避坑指南

### A. Alpine 镜像的特殊处理

Alpine 使用的是 `musl libc` 而非 `glibc`，其 Locale 处理方式不同。

* **简单方法**：设置 `ENV LANG=C.UTF-8` 即可解决大部分基础显示问题。
* **复杂方法**：如果必须使用特定 Locale（如 `zh_CN`），需要安装 `musl-locales` 包，但这在 Alpine 上通常不是最佳实践。

### B. Matplotlib 中文显示

即使安装了系统字体，Matplotlib 可能依然找不到。你需要在 Python 代码中显式指定：

```python
import matplotlib.pyplot as plt
# 指定安装好的文泉驿微米黑
plt.rcParams['font.sans-serif'] = ['WenQuanYi Micro Hei']
plt.rcParams['axes.unicode_minus'] = False # 解决负号显示为方块的问题

```

### C. 极简方案：直接挂载宿主机字体

如果你不想在镜像中安装字体（减小体积），可以在启动容器时挂载宿主机的字体目录：

```bash
docker run -v /usr/share/fonts:/usr/share/fonts:ro my-image

```

---

## 总结与检查

你可以通过以下命令在运行中的容器内检查配置是否生效：

1. **检查编码**：输入 `locale`，确认 `LANG` 是否为 `zh_CN.UTF-8`。
2. **检查字体**：输入 `fc-list :lang=zh`，看是否有字体文件路径输出。

**您目前遇到的具体乱码场景是什么？是程序输出的 Log 乱码，还是生成的图片里中文不显示？**

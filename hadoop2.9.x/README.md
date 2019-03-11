# 安装Hadoop2.9.x (单节点)

## Linux所需的软件包括：
- 必须安装java[HadoopJavaVersions](http://wiki.apache.org/hadoop/HadoopJavaVersions "支持的java版本对应关系")
- 必须安装ssh，并且必须运行sshd。
- 还需要安装，rsync。

上述完成之后 [下载hadoop](http://www.apache.org/dyn/closer.cgi/hadoop/common/  "下载链接")


## 准备启动Hadoop集群:
解压缩下载的Hadoop发行版。在分发中，编辑文件etc/hadoop/hadoop-env.sh以定义一些参数，如下所示：
```shell
# 设置为java安装的根目录
export JAVA_HOME=${java_home}
```
尝试运行（这将显示hadoop脚本的使用文档）：
```shell
$ bin/hadoop
```

现在，您已准备好以三种支持模式之一启动Hadoop集群：
- [本地(独立)模式]()
- [伪分布式模式]()
- [全分布式模式]()

### 独立操作:
默认情况下，Hadoop配置为以非分布式模式运行，作为单个Java进程。这对调试很有用。
以下示例复制解压缩的conf目录以用作输入，然后查找并显示给定正则表达式的每个匹配项。输出将写入给定的输出目录。
```shell
$ mkdir input
$ cp etc/hadoop/*.xml input
$ bin/hadoop jar share/hadoop/mapreduce/hadoop-mapreduce-examples-2.9.2.jar grep input output 'dfs[a-z.]+'
$ cat output/*
```

### 伪分布式操作:
Hadoop也可以在伪分布式模式下在单节点上运行，其中每个Hadoop守护程序在单独的Java进程中运行。

Use the following:

etc/hadoop/core-site.xml:

```shell
<configuration>
    <property>
        <name>fs.defaultFS</name>
        <value>hdfs://localhost:9000</value>
    </property>
</configuration>
```

etc/hadoop/hdfs-site.xml:
```shell
<configuration>
    <property>
        <name>dfs.replication</name>
        <value>1</value>
    </property>
</configuration>
```

### 设置passphraseless ssh:

现在检查您是否可以在没有密码的情况下ssh到localhost，如果不能，请配置ssh无密码登陆：

```shell
 $ ssh localhost
```

### 执行:

以下说明是在本地运行MapReduce作业。如果要在YARN上执行作业，请参阅单节点上的YARN 。

（1） 格式化文件系统：
```shell
$ bin/hdfs namenode -format
```

（2）启动NameNode守护程序和DataNode守护程序：
```shell
$ sbin/start-dfs.sh
```
hadoop守护程序日志输出将写入$ HADOOP_LOG_DIR目录（默认为$ HADOOP_HOME / logs）。

（3）浏览NameNode的Web界面; 默认情况下，它可用于：

* NameNode - http://localhost:50070/

（4）创建执行MapReduce作业所需的HDFS目录：
```shell
$ bin/hdfs dfs -mkdir /user
$ bin/hdfs dfs -mkdir /user/<username>
```

（5）将输入文件复制到分布式文件系统中：
```shell
$ bin/hdfs dfs -put etc/hadoop input
```

（6）运行一些提供的示例：
```shell
$ bin/hadoop jar share/hadoop/mapreduce/hadoop-mapreduce-examples-2.9.2.jar grep input output 'dfs[a-z.]+'
```

（7）检查输出文件：将输出文件从分布式文件系统复制到本地文件系统并检查它们：

```shell
$ bin/hdfs dfs -get output output
$ cat output/*
```

要么，查看分布式文件系统上的输出文件：

```shell
$ bin/hdfs dfs -cat output/*
```

（8）完成后，停止守护进程：
```shell
$ sbin/stop-dfs.sh
```
---

## YARN on a Single Node:

您可以通过设置一些参数并运行ResourceManager守护程序和NodeManager守护程序，以伪分布式模式在YARN上运行MapReduce作业。

以下说明假设已执行上述指令的 1.~4步骤。

（1）配置参数如下：etc/hadoop/mapred-site.xml：
```shell
<configuration>
    <property>
        <name>mapreduce.framework.name</name>
        <value>yarn</value>
    </property>
</configuration>
```

etc/hadoop/yarn-site.xml：
```shell
<configuration>
    <property>
        <name>yarn.nodemanager.aux-services</name>
        <value>mapreduce_shuffle</value>
    </property>
</configuration>
```

（2）启动ResourceManager守护程序和NodeManager守护程序：
```shell
$ sbin/start-yarn.sh
```

（3）浏览ResourceManager的Web界面; 默认情况下，它可用于：

- ResourceManager - http://localhost:8088 /

（4）运行MapReduce作业。

（5）完成后，停止守护进程：
```shell
$ sbin/stop-yarn.sh
```


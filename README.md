# AndResGuard-Jenkins-Plugin
这是一个用于jenkins中，自动化构建Android项目，生成apk后，对apk进行资源混淆的插件。

需要注意：

1、不支持windows下的jenkins，没测试过，理论不支持。

2、linux或者mac请先安装7zip后使用，因为默认开启了7zip压缩，参考[AndResGuard](https://github.com/shwenzhang/AndResGuard "AndResGuard")。

3、各种配置文件的目录是基于工作空间的，所以填写文件路径时，要写相对于工作空间的路径。

4、需要为zipalign设置环境变量。

# 说明

该项目专为Xperia 1等机型添加了3840x1644分辨率的支持，以提供21:9的原生分辨率串流。

* 增加了4K (21:9) 和 2K (21:9) 分辨率



## 如何更改分辨率

在Nvidia控制面板中，增加自定义分辨率，并设置电脑分辨率。

注意：只有一部分游戏支持21:9的超宽屏。

![Screenshot_20200727-095255](/screenshot/Screenshot_20200727-095255.png)

![16556A5CDE990FAE78F443B1EB9941F7](/screenshot/16556A5CDE990FAE78F443B1EB9941F7.jpg)



## 如何搭建5G 4K 100Mbps流畅串流环境

1、尽可能清空5G通道，能走有线的设备走有线

2、低速率设备放到2.4G，专门为游戏手机提供一个独立的5G通道

3、使用一个高端路由器，例如华硕RT-AX92U

测了一下Xperia 1 II，100Mbps码率下120刷新平均每帧延迟12ms，60刷新延迟14ms。画质几乎没有差别。由于硬件限制60刷新，没有增加实际硬件负荷。
同时测了一下低分辨率的效果，换来的更快解码不足以弥补画质的损失，还是原生分辨率最好。当然是在原生分辨率解码够快的情况下。如果对实时要求更高，可以降低分辨率使用。

# Moonlight Android

[![Travis CI Status](https://travis-ci.org/moonlight-stream/moonlight-android.svg?branch=master)](https://travis-ci.org/moonlight-stream/moonlight-android)

[Moonlight for Android](https://moonlight-stream.org) is an open source implementation of NVIDIA's GameStream, as used by the NVIDIA Shield.

Moonlight for Android will allow you to stream your full collection of games from your Windows PC to your Android device,
whether in your own home or over the internet.

Moonlight also has a [PC client](https://github.com/moonlight-stream/moonlight-qt) and [iOS/tvOS client](https://github.com/moonlight-stream/moonlight-ios).

Check out [the Moonlight wiki](https://github.com/moonlight-stream/moonlight-docs/wiki) for more detailed project information, setup guide, or troubleshooting steps.

## Downloads
* [Google Play Store](https://play.google.com/store/apps/details?id=com.limelight)
* [Amazon App Store](https://www.amazon.com/gp/product/B00JK4MFN2)
* [F-Droid](https://f-droid.org/packages/com.limelight)
* [APK](https://github.com/moonlight-stream/moonlight-android/releases)

## Building
* Install Android Studio and the Android NDK
* Run ‘git submodule update --init --recursive’ from within moonlight-android/
* In moonlight-android/, create a file called ‘local.properties’. Add an ‘ndk.dir=’ property to the local.properties file and set it equal to your NDK directory.
* Build the APK using Android Studio or gradle

## Authors

* [Cameron Gutman](https://github.com/cgutman)  
* [Diego Waxemberg](https://github.com/dwaxemberg)  
* [Aaron Neyer](https://github.com/Aaronneyer)  
* [Andrew Hennessy](https://github.com/yetanothername)

Moonlight is the work of students at [Case Western](http://case.edu) and was
started as a project at [MHacks](http://mhacks.org).

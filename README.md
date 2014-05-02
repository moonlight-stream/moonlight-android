#Limelight

Limelight is an open source implementation of NVIDIA's GameStream, as used by the NVIDIA Shield.
We reverse engineered the Shield streaming software, and created a version that can be run on any Android device.

Limelight will allow you to stream your full collection of Steam games from your Windows PC to your Android device,
in your own home, or over the internet.

[Limelight-pc](https://github.com/limelight-stream/limelight-pc) is also currently in development for Windows, OS X and Linux. Versions for [iOS](https://github.com/limelight-stream/limelight-ios) and [Windows Phone](https://github.com/limelight-stream/limelight-wp) are also in development.

##Features

* Streams Steam and all of your games from your PC to your Android device
* Full gamepad support for MOGA, Xbox 360, PS3, OUYA, and Shield

##Features in development

* Use mDNS to scan for compatible GeForce Experience (GFE) machines on the network
* Choose from the list of available games instead of just launching Steam
* Keyboard input

##Installation

* Download and install Limelight for Android from
[XDA](http://forum.xda-developers.com/showthread.php?t=2505510)
* Download [GeForce Experience](http://www.geforce.com/geforce-experience) and install on your Windows PC

##Requirements

* [GFE compatible](http://shield.nvidia.com/play-pc-games/) computer with GTX 600/700 series GPU
* Android device running 4.1 (Jelly Bean) or higher
* High-end wireless router (802.11n dual-band recommended)
* Exynos/Snapdragon SoC __OR__ Quad-Core 1.4 GHz Cortex-A9 or higher (Tegra 3)

##Usage

* Turn on Shield Streaming in the GFE settings
* If you are connecting from outside the same network, turn on internet
  streaming
* In Limelight, enter your PC's IP or Hostname and click "Pair"
* Accept the pairing confirmation on your PC
* In Limelight, click "Start Streaming"
* Play games!

##Contribute

This project is being actively developed at [XDA Developers](http://forum.xda-developers.com/showthread.php?t=2505510)

1. Fork us
2. Write code
3. Send Pull Requests

Check out our [website](http://limelight-stream.com) for project links and information.

##Authors

* [Cameron Gutman](https://github.com/cgutman)  
* [Diego Waxemberg](https://github.com/dwaxemberg)  
* [Aaron Neyer](https://github.com/Aaronneyer)  
* [Andrew Hennessy](https://github.com/yetanothername)

Limelight is the work of students at [Case Western](http://case.edu) and was
started as a project at [MHacks](http://mhacks.org).

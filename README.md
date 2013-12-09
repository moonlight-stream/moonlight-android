#Limelight

Limelight is an open source implementation of NVIDIA's GameStream, as used by the NVIDIA Shield.
We reverse engineered the Shield streaming software, and created a version that can be run on any Android device.

In order to stream with Limelight, you need a desktop with a GTX 650 or higher GPU. The full requirements can be found [here](http://shield.nvidia.com/play-pc-games/).

Limelight requires your Android device to be on the same network as your Windows PC, but can be used remotely if you
are running [Shield Proxy](http://forum.xda-developers.com/showthread.php?t=2435481).

[Limelight-pc](https://github.com/limelight-stream/limelight-pc) is also currently in development for Windows, OSX and Linux.


##Installation

Download Limelight for Android and install the APK to your Android device. The latest APK can be found on the
[XDA Thread](http://forum.xda-developers.com/showthread.php?t=2505510).

Download [GeForce Experience](http://www.geforce.com/geforce-experience) and install on your Windows PC. This was
most likely already installed when you installed the NVIDIA drivers.

##Usage

Ensure your Android device with Limelight is on the same network as your Windows PC with GFE, or you have 
[Shield Proxy](http://forum.xda-developers.com/showthread.php?t=2435481) running, and you have turned on
Shield Streaming in the GFE settings. In Limelight, enter in the IP or Hostname of your Windows PC and click
"Pair Computer". On your Windows PC, GFE should display a dialog asking to confirm pairing. Accept this and Limelight
should give you a notification. Now click "Start Streaming" and Steam will open.

You can control Steam and play games by using a gamepad paired with your Android device. Limelight supports Xbox 360, MOGA, OUYA and PS3
controllers although may still work with others.

##Contribute

This project is being actively developed at [XDA](http://forum.xda-developers.com/showthread.php?t=2505510)

1. Fork us
2. Write code
3. Send Pull Requests

##Authors

* [Cameron Gutman](https://github.com/cgutman)  
* [Diego Waxemberg](https://github.com/dwaxemberg)  
* [Aaron Neyer](https://github.com/Aaronneyer)  
* [Andrew Hennessy](https://github.com/yetanothername)

Limelight is the work of students at [Case Western](http://case.edu).
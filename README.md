# Moonlight Android

[![Travis CI Status](https://travis-ci.org/moonlight-stream/moonlight-android.svg?branch=master)](https://travis-ci.org/moonlight-stream/moonlight-android)

[Moonlight](https://moonlight-stream.org) is an open source implementation of NVIDIA's GameStream, as used by the NVIDIA Shield.
We reverse engineered the Shield streaming software and created a version that can be run on any Android device.

Moonlight will allow you to stream your full collection of games from your Windows PC to your Android device,
whether in your own home or over the internet.

Check our [wiki](https://github.com/moonlight-stream/moonlight-docs/wiki) for more detailed information or a troubleshooting guide. 

## Features

* Streams any of your games from your PC to your Android device
* Full gamepad support for MOGA, Xbox 360, PS3, OUYA, and Shield
* Automatically finds GameStream-compatible PCs on your network

## Installation

* Download and install Moonlight for Android from
[Google Play](https://play.google.com/store/apps/details?id=com.limelight), [F-Droid](https://f-droid.org/packages/com.limelight/), [Amazon App Store](http://www.amazon.com/gp/product/B00JK4MFN2), or directly from the [releases page](https://github.com/moonlight-stream/moonlight-android/releases)
* Download [GeForce Experience](http://www.geforce.com/geforce-experience) and install on your Windows PC

## Requirements

* [GameStream compatible](http://shield.nvidia.com/play-pc-games/) computer with an NVIDIA GeForce GTX 600 series or higher desktop or mobile GPU (GT-series and AMD GPUs not supported)
* Android device running 4.1 (Jelly Bean) or higher
* High-end wireless router (802.11n dual-band recommended)

## Usage

* Turn on GameStream in the GFE settings
* If you are connecting from outside the same network, turn on internet
  streaming
* When on the same network as your PC, open Moonlight and tap on your PC in the list
* Accept the pairing confirmation on your PC and add the PIN if needed
* Tap your PC again to view the list of apps to stream
* Play games!

## Contribute

This project is being actively developed at [XDA Developers](http://forum.xda-developers.com/showthread.php?t=2505510)

1. Fork us
2. Write code
3. Send Pull Requests

## Building
* Install Android Studio and the Android NDK
* Run ‘git submodule update --init --recursive’ from within moonlight-android/
* In moonlight-android/, create a file called ‘local.properties’. Add an ‘ndk.dir=’ property to the local.properties file and set it equal to your NDK directory.
* Build the APK using Android Studio

## Authors

* [Cameron Gutman](https://github.com/cgutman)  
* [Diego Waxemberg](https://github.com/dwaxemberg)  
* [Aaron Neyer](https://github.com/Aaronneyer)  
* [Andrew Hennessy](https://github.com/yetanothername)

Moonlight is the work of students at [Case Western](http://case.edu) and was
started as a project at [MHacks](http://mhacks.org).

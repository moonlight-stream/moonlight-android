# Iris controller architecture

Iris and Prism use the controller extensions already present in the Moonlight
protocol. No protocol fork is required:

- Iris reports controller accelerometer and gyroscope capabilities and streams
  samples when Prism requests them.
- Prism sends rumble, trigger-rumble, and LED feedback through Moonlight's
  existing feedback messages.
- Iris advertises and sends `PADDLE1` through `PADDLE4` after a saved physical
  event is matched.
- Prism selects a DualSense Edge virtual device when any paddle is advertised.
  Inputtino writes the four bits into the Edge input report as Fn1, Fn2, left
  paddle, and right paddle.

The calibration profile stores Android device descriptors plus key and scan
codes. Calibration explicitly selects one to four controls and defaults to two.
The profile separately identifies a primary controller, allowing a handheld to
expose rear controls through an auxiliary Android input device. During streaming
Iris applies auxiliary button state directly to the primary controller context,
so it does not allocate a second network controller.

## Why InputPlumber is not a runtime dependency

InputPlumber is useful prior art for controller identity, capability routing,
and composite-device handling on Linux. It is not in the runtime path here:

- the Android client cannot depend on its Linux daemon, D-Bus service, or
  uinput stack;
- Moonlight already transports motion, feedback, and four extra buttons;
- making it mandatory on the host would add another privileged service without
  adding a required transport capability.

InputPlumber can still be used independently on a Linux handheld before Iris
receives events, or inform future profile-detection work. The Iris/Prism
contract remains usable without it.

## Hardware validation

Each target handheld should be checked for:

1. stable input descriptors across reboots;
2. distinct down/up events for all rear controls;
3. controller-first motion with handheld gyro fallback;
4. rumble reaching the intended physical controller;
5. every configured Edge control appearing independently in Steam Input.

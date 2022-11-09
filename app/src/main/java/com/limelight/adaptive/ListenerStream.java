package com.limelight.adaptive;

import com.limelight.LimeLog;

import java.io.IOException;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ExecutionException;

public class ListenerStream {
    // private ArrayList<Object> mListenerList = new ArrayList<Object>();
    private static Date lastTimePost = new Date();
    private static Queue<MyVideoMeasurementParams> queue = new LinkedList<MyVideoMeasurementParams>();


    public ListenerStream() {
        DequeueStream().start();
    }

    public void remove() {
        DequeueStream().stop();
    }

    public Thread DequeueStream() {
        return new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    Date currentTime = new Date();
                    /*
                     * @deviantSecond Represent for millisecond deviant
                     * between current time and last post time
                     */
                    long deviantSecond = currentTime.getTime() - lastTimePost.getTime();
                    if (deviantSecond >= 1000) {
                        long totalBytes = 0;
                        Queue<MyVideoMeasurementParams> queueNeedRemove = new LinkedList<MyVideoMeasurementParams>();
                        for (MyVideoMeasurementParams item : queue) {
                            totalBytes += item.decodeUnitLength;
                            queueNeedRemove.add(item);
                        }
                        if(queueNeedRemove != null)
                            queue.removeAll(queueNeedRemove);

                        LimeLog.info("Listener Stream: " + Long.toString(totalBytes) + "/ 1s");
                        lastTimePost = currentTime;
                    }
                }
            }
        });
    }

    private static class MyVideoMeasurementParams {
        int decodeUnitLength;
        long receiveTime;
        MyVideoMeasurementParams(int decodeUnitLength, long receiveTime) {
            this.decodeUnitLength = decodeUnitLength;
            this.receiveTime = receiveTime;
        }
    }

    public synchronized void add(int bytes, long timeInMs) {
        queue.add(new MyVideoMeasurementParams(bytes, timeInMs));
    }

}
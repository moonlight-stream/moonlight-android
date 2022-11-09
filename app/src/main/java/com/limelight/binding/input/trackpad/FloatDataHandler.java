package com.limelight.binding.input.trackpad;

import android.util.Log;

public class FloatDataHandler {

    private static final String TAG = "FloatDataHandler";

    private final float[] floatData = new float[10];

    public short floatToShort (int index, float floatData){
        if(this.floatData.length < index){
            Log.e(TAG,"Over the index length");
            return 0;
        }

        short returnData;

        // Current data processing
        short integer = (short) floatData;
        returnData = integer; // Return Only integer Data
        this.floatData[index] += floatData - integer; // Add old data and current data
        // Complete Current Data Precessing

        // Old data processing
        // Check if local floatData exceed 1
        if(this.floatData[index] >= 1){
            short localInteger = (short) this.floatData[index];
            this.floatData[index] -= localInteger; // Save the fractional Part
            returnData += localInteger;
        }
        return returnData;
    }
}

package com.limelight.observer_stream;

/**
 * Moving average calculation for ConnectionClass.
 */
class ExponentialGeometricAverage {

  private final double mDecayConstant;
  private final int mCutover;

  private double mValue = -1;
  private int mCount;

  public ExponentialGeometricAverage(double decayConstant) {
    mDecayConstant = decayConstant;
    mCutover = decayConstant == 0.0
        ? Integer.MAX_VALUE
        : (int) Math.ceil(1 / decayConstant);
  }

  /**
   * Adds a new measurement to the moving average.
   * @param measurement - Bandwidth measurement in bits/ms to add to the moving average.
   */
  public void addMeasurement(double measurement) {
    double keepConstant = 1 - mDecayConstant;
    if (mCount > mCutover) {
      mValue = Math.exp(keepConstant * Math.log(mValue) + mDecayConstant * Math.log(measurement));
    } else if (mCount > 0) {
      double retained = keepConstant * mCount / (mCount + 1.0);
      double newcomer = 1.0 - retained;
      mValue = Math.exp(retained * Math.log(mValue) + newcomer * Math.log(measurement));
    } else {
      mValue = measurement;
    }
    mCount++;
  }

  public double getAverage() {
    return mValue;
  }

  /**
   * Reset the moving average.
   */
  public void reset() {
    mValue = -1.0;
    mCount = 0;
  }
}

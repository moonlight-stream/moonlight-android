package com.limelight.computers;

import com.limelight.nvstream.http.ComputerDetails;

public interface ComputerManagerListener {
    public void notifyComputerUpdated(ComputerDetails details);
}

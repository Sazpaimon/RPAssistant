package com.limelight.binding.input.driver;

import android.util.SparseIntArray;

public interface UsbDriverListener {
    void reportControllerState(int controllerId, SparseIntArray buttonEvents,
                               float leftStickX, float leftStickY,
                               float rightStickX, float rightStickY,
                               float leftTrigger, float rightTrigger);

    void deviceRemoved(AbstractController controller);
    void deviceAdded(AbstractController controller);
}

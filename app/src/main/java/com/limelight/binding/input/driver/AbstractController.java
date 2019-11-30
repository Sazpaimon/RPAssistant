package com.limelight.binding.input.driver;

import android.util.SparseIntArray;
import android.view.KeyEvent;

import java.util.ArrayList;
import java.util.HashMap;

public abstract class AbstractController {

    private final int deviceId;

    private UsbDriverListener listener;

    protected SparseIntArray buttonEvents = new SparseIntArray();
    protected float leftTrigger, rightTrigger;
    protected float rightStickX, rightStickY;
    protected float leftStickX, leftStickY;

    public int getControllerId() {
        return deviceId;
    }

    protected void setButtonFlag(int buttonFlag, int data) {
        if (data != 0) {
            buttonEvents.put(buttonFlag, KeyEvent.ACTION_DOWN);
        }
        else {
            buttonEvents.put(buttonFlag, KeyEvent.ACTION_UP);
        }
    }

    protected void reportInput() {
        listener.reportControllerState(deviceId, buttonEvents, leftStickX, leftStickY,
                rightStickX, rightStickY, leftTrigger, rightTrigger);
    }

    public abstract boolean start();
    public abstract void stop();

    public AbstractController(int deviceId, UsbDriverListener listener) {
        this.deviceId = deviceId;
        this.listener = listener;
    }

    public abstract void rumble(short lowFreqMotor, short highFreqMotor);

    protected void notifyDeviceRemoved() {
        listener.deviceRemoved(this);
    }

    protected void notifyDeviceAdded() {
        listener.deviceAdded(this);
    }
}

package com.rAs.android.rpgamepad;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.SparseIntArray;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.widget.Toast;

import com.limelight.binding.input.driver.AbstractController;
import com.limelight.binding.input.driver.UsbDriverListener;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PSGamepadHandler implements UsbDriverListener {
    public static class Gamepad {

        static final int BUTTON_BACK = 0x1;

        static final int BUTTON_LEFT_THUMB = 0x2;
        static final int BUTTON_RIGHT_THUMB = 0x4;

        static final int BUTTON_START = 0x8;

        static final int DPAD_UP = 0x10;
        static final int DPAD_RIGHT = 0x20;
        static final int DPAD_DOWN = 0x40;
        static final int DPAD_LEFT = 0x80;

        static final int BUTTON_L2 = 0x100;
        static final int BUTTON_R2 = 0x200;

        static final int BUTTON_LEFT_SHOULDER = 0x400;
        static final int BUTTON_RIGHT_SHOULDER = 0x800;

        static final int BUTTON_Y = 0x1000;
        static final int BUTTON_B = 0x2000;
        static final int BUTTON_A = 0x4000;
        static final int BUTTON_X = 0x8000;

        static final int BUTTON_PS = 0x10000;

        static final int BUTTON_TOUCHPAD = 0x100000;

        static final int AXIS_LEFT_ANALOG_UP = -1;
        static final int AXIS_LEFT_ANALOG_RIGHT = -2;
        static final int AXIS_LEFT_ANALOG_DOWN = -3;
        static final int AXIS_LEFT_ANALOG_LEFT = -4;

        static final int AXIS_RIGHT_ANALOG_UP = -5;
        static final int AXIS_RIGHT_ANALOG_RIGHT = -6;
        static final int AXIS_RIGHT_ANALOG_DOWN = -7;
        static final int AXIS_RIGHT_ANALOG_LEFT = -8;

        static final int AXIS_L2 = -9;
        static final int AXIS_R2 = -10;
    }

    interface OnGamepadStateChangeListener {
        void onGamepadStateChange(boolean sensor);
    }

    public static int[] AXISES = new int[]{MotionEvent.AXIS_HAT_X, MotionEvent.AXIS_HAT_Y,
            MotionEvent.AXIS_X, MotionEvent.AXIS_Y,
            MotionEvent.AXIS_Z, MotionEvent.AXIS_RZ,
            MotionEvent.AXIS_BRAKE, MotionEvent.AXIS_GAS,
            MotionEvent.AXIS_RX, MotionEvent.AXIS_RY};

    static final int ANALOG_MAX_VALUE = 255;
    static final int ANALOG_STICK_NEUTRAL_VALUE = 128;


    private Context context;

    private OnGamepadStateChangeListener onGamepadStateChangeListener;

    private boolean isCombineButtonPressed;
    private int combineButtonKeyCode;
    private List<InputInfo> buttons, buttonCombines, axises, axiseCombines;
    private Set<Integer> buttonCombinePressed;
    private PSGamepadValues prevGamepadValues, gamepadValues;

    public float analogDeadZone = 0f;

    public AbstractController activeController;

    public PSGamepadHandler(Context context, String profileName, SharedPreferences prefs) {
        if(this.context == null && context != null)
            this.context = context;

        init(profileName, prefs);
    }

    private boolean init(String profileName, SharedPreferences prefs){
        if(prefs != null) {
            if(profileName == null || profileName.equals(prefs.getString("last_profile", null))) {
                // Use prefs
                init(prefs.getAll());
                return true;
            }
        }

        if(context == null || profileName == null) return false;

        File dataPath = context.getExternalFilesDir(null).getParentFile().getParentFile();
        File profileFilePath = new File(new File(dataPath, this.getClass().getPackage().getName()), "files");
        File file = new File(profileFilePath, profileName);

        if(!file.exists()) return false;

        Map<String, String> prefsValues = new HashMap<>();
        BufferedReader br = null;
        try{
            br = new BufferedReader(new InputStreamReader(new FileInputStream(file)));
            String line;
            while((line = br.readLine()) != null) {
                int idx = line.indexOf("=");
                if(idx > 0) {
                    String key = line.substring(0, idx);
                    String value = line.substring(idx + 1);
                    prefsValues.put(key, value);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                br.close();
            } catch (Exception e) {
            }
        }

        return init(prefsValues);
    }

    private boolean init(Map<String, ?> prefsValues) {

        isCombineButtonPressed = false;

        combineButtonKeyCode = 0;
        buttons = new ArrayList<InputInfo>();
        buttonCombines = new ArrayList<InputInfo>();
        buttonCombinePressed = new HashSet<Integer>();

        axises = new ArrayList<InputInfo>();
        axiseCombines = new ArrayList<InputInfo>();

        prevGamepadValues = new PSGamepadValues();
        gamepadValues = new PSGamepadValues();

        analogDeadZone = 0f;


        for(String key : prefsValues.keySet()) {
            String value = (String)prefsValues.get(key);

            if("deadzone".equals(key)){
                analogDeadZone = Integer.parseInt(value) / 100f;
                continue;
            }

            String[] values = value.split("\\|");
            if(values.length < 2) continue;

            String name = values[0];
            int code = Integer.parseInt(values[1]);

            if("button_combine".equals(key)){
                combineButtonKeyCode = code;
                continue;
            }

            InputInfo input = new InputInfo();
            input.setWithCombine(name.startsWith("@"));
            input.setNegative(name.endsWith("-"));
            input.setAxis(name.contains("AXIS_"));
            input.setKeyCode(code);

            if("button_option".equals(key)){
                input.setOutput(Gamepad.BUTTON_START);
            } else if("button_share".equals(key)){
                input.setOutput(Gamepad.BUTTON_BACK);
            } else if("dpad_up".equals(key)){
                input.setOutput(Gamepad.DPAD_UP);
            } else if("dpad_right".equals(key)){
                input.setOutput(Gamepad.DPAD_RIGHT);
            } else if("dpad_down".equals(key)){
                input.setOutput(Gamepad.DPAD_DOWN);
            } else if("dpad_left".equals(key)){
                input.setOutput(Gamepad.DPAD_LEFT);
            } else if("left_analog_up".equals(key)){
                input.setOutput(Gamepad.AXIS_LEFT_ANALOG_UP);
            } else if("left_analog_right".equals(key)){
                input.setOutput(Gamepad.AXIS_LEFT_ANALOG_RIGHT);
            } else if("left_analog_down".equals(key)){
                input.setOutput(Gamepad.AXIS_LEFT_ANALOG_DOWN);
            } else if("left_analog_left".equals(key)){
                input.setOutput(Gamepad.AXIS_LEFT_ANALOG_LEFT);
            } else if("right_analog_up".equals(key)){
                input.setOutput(Gamepad.AXIS_RIGHT_ANALOG_UP);
            } else if("right_analog_right".equals(key)){
                input.setOutput(Gamepad.AXIS_RIGHT_ANALOG_RIGHT);
            } else if("right_analog_down".equals(key)){
                input.setOutput(Gamepad.AXIS_RIGHT_ANALOG_DOWN);
            } else if("right_analog_left".equals(key)){
                input.setOutput(Gamepad.AXIS_RIGHT_ANALOG_LEFT);
            } else if("button_circle".equals(key)){
                input.setOutput(Gamepad.BUTTON_B);
            } else if("button_cross".equals(key)){
                input.setOutput(Gamepad.BUTTON_A);
            } else if("button_triangle".equals(key)){
                input.setOutput(Gamepad.BUTTON_Y);
            } else if("button_square".equals(key)){
                input.setOutput(Gamepad.BUTTON_X);
            } else if("button_ps".equals(key)){
                input.setOutput(Gamepad.BUTTON_PS);
            } else if("button_touchpad".equals(key)){
                input.setOutput(Gamepad.BUTTON_TOUCHPAD);
            } else if("button_l1".equals(key)){
                input.setOutput(Gamepad.BUTTON_LEFT_SHOULDER);
            } else if("button_r1".equals(key)){
                input.setOutput(Gamepad.BUTTON_RIGHT_SHOULDER);
            } else if("analog_l2".equals(key)){
                input.setOutput(Gamepad.AXIS_L2);
            } else if("analog_r2".equals(key)){
                input.setOutput(Gamepad.AXIS_R2);
            } else if("button_l3".equals(key)){
                input.setOutput(Gamepad.BUTTON_LEFT_THUMB);
            } else if("button_r3".equals(key)){
                input.setOutput(Gamepad.BUTTON_RIGHT_THUMB);
            } else if("switch_profile1".equals(key)){
                if(values.length >= 3)
                    input.setToProfile(values[2]);
            } else if("switch_profile2".equals(key)){
                if(values.length >= 3)
                    input.setToProfile(values[2]);
            }

            if(input.isAxis()) {
                if(input.isWithCombine())
                    axiseCombines.add(input);
                else
                    axises.add(input);
            } else {
                if(input.isWithCombine())
                    buttonCombines.add(input);
                else
                    buttons.add(input);
            }
        }

        return true;
    }

    public void setOnGamepadStateChangeListener(OnGamepadStateChangeListener onGamepadStateChangeListener) {
        this.onGamepadStateChangeListener = onGamepadStateChangeListener;
    }

    public void setGamepadKeyState(KeyEvent event) {
        if(onGamepadStateChangeListener == null) return;

        if(event.getKeyCode() == KeyEvent.KEYCODE_UNKNOWN) {
            return;
        }

        int action = event.getAction();

        if(event.getKeyCode() == combineButtonKeyCode) {
            isCombineButtonPressed = action == KeyEvent.ACTION_DOWN;
            return;
        }

        List<InputInfo> buttons = isCombineButtonPressed || buttonCombinePressed.contains(event.getKeyCode()) ? this.buttonCombines : this.buttons;

        for(InputInfo input : buttons) {
            if (input.getKeyCode() != event.getKeyCode()) continue;

            if(input.getToProfile() != null) {
                boolean result = init(input.getToProfile(), null);
                if(context != null && result) {
                    Toast.makeText(context, "[ " + input.getToProfile() + " ]", Toast.LENGTH_SHORT).show();
                }
                return;
            }

            int button = input.getOutput();

            if(button == 0) continue;

            setButtonState(action, button, event);
        }

        boolean sensorChanged = isSensorChanged();

        if(!isButtonChanged() && !sensorChanged) return;

        storePrevGamepadValues(sensorChanged);
        onGamepadStateChangeListener.onGamepadStateChange(sensorChanged);
    }

    private void setButtonState(int action, int button, KeyEvent event) {
        int buttonState = gamepadValues.getButtonState();

        // Digital input -> Analog output
        switch(button) {
            case Gamepad.AXIS_LEFT_ANALOG_UP:
                if(action == KeyEvent.ACTION_DOWN || gamepadValues.getLeftAxisY() != ANALOG_STICK_NEUTRAL_VALUE)
                    gamepadValues.setLeftAxisY(action == KeyEvent.ACTION_DOWN ? ANALOG_MAX_VALUE : ANALOG_STICK_NEUTRAL_VALUE);
                break;
            case Gamepad.AXIS_LEFT_ANALOG_RIGHT:
                if(action == KeyEvent.ACTION_DOWN || gamepadValues.getLeftAxisX() != ANALOG_STICK_NEUTRAL_VALUE)
                    gamepadValues.setLeftAxisX(action == KeyEvent.ACTION_DOWN ? ANALOG_MAX_VALUE : ANALOG_STICK_NEUTRAL_VALUE);
                break;
            case Gamepad.AXIS_LEFT_ANALOG_DOWN:
                if(action == KeyEvent.ACTION_DOWN || gamepadValues.getLeftAxisY() != ANALOG_MAX_VALUE)
                    gamepadValues.setLeftAxisY(action == KeyEvent.ACTION_DOWN ? 0 : ANALOG_STICK_NEUTRAL_VALUE);
                break;
            case Gamepad.AXIS_LEFT_ANALOG_LEFT:
                if(action == KeyEvent.ACTION_DOWN || gamepadValues.getLeftAxisX() != ANALOG_MAX_VALUE)
                    gamepadValues.setLeftAxisX(action == KeyEvent.ACTION_DOWN ? 0 : ANALOG_STICK_NEUTRAL_VALUE);
                break;
            case Gamepad.AXIS_RIGHT_ANALOG_UP:
                if(action == KeyEvent.ACTION_DOWN || gamepadValues.getRightAxisY() != ANALOG_STICK_NEUTRAL_VALUE)
                    gamepadValues.setRightAxisY(action == KeyEvent.ACTION_DOWN ? ANALOG_MAX_VALUE : ANALOG_STICK_NEUTRAL_VALUE);
                break;
            case Gamepad.AXIS_RIGHT_ANALOG_RIGHT:
                if(action == KeyEvent.ACTION_DOWN || gamepadValues.getRightAxisX() != ANALOG_STICK_NEUTRAL_VALUE)
                    gamepadValues.setRightAxisX(action == KeyEvent.ACTION_DOWN ? ANALOG_MAX_VALUE : ANALOG_STICK_NEUTRAL_VALUE);
                break;
            case Gamepad.AXIS_RIGHT_ANALOG_DOWN:
                if(action == KeyEvent.ACTION_DOWN || gamepadValues.getRightAxisY() != ANALOG_MAX_VALUE)
                    gamepadValues.setRightAxisY(action == KeyEvent.ACTION_DOWN ? 0 : ANALOG_STICK_NEUTRAL_VALUE);
                break;
            case Gamepad.AXIS_RIGHT_ANALOG_LEFT:
                if(action == KeyEvent.ACTION_DOWN || gamepadValues.getRightAxisX() != ANALOG_MAX_VALUE)
                    gamepadValues.setRightAxisX(action == KeyEvent.ACTION_DOWN ? 0 : ANALOG_STICK_NEUTRAL_VALUE);
                break;
            case Gamepad.AXIS_L2:
                gamepadValues.setLeftTrigger(action == KeyEvent.ACTION_DOWN ? ANALOG_MAX_VALUE : 0);
                if (action == KeyEvent.ACTION_DOWN) {
                    buttonState |= Gamepad.BUTTON_L2;
                } else if (action == KeyEvent.ACTION_UP) {
                    buttonState &= ~Gamepad.BUTTON_L2;
                }
                gamepadValues.setButtonState(buttonState);
                gamepadValues.setChangedButtons(Gamepad.BUTTON_L2);
                break;
            case Gamepad.AXIS_R2:
                gamepadValues.setRightTrigger(action == KeyEvent.ACTION_DOWN ? ANALOG_MAX_VALUE : 0);
                if (action == KeyEvent.ACTION_DOWN) {
                    buttonState |= Gamepad.BUTTON_R2;
                } else if (action == KeyEvent.ACTION_UP) {
                    buttonState &= ~Gamepad.BUTTON_R2;
                }
                gamepadValues.setButtonState(buttonState);
                gamepadValues.setChangedButtons(Gamepad.BUTTON_R2);
                break;

            // Digital input -> Digital output
            default:
                if (event.getSource() == InputDevice.SOURCE_JOYSTICK) {
                    break;
                }
                switch (action) {
                    case KeyEvent.ACTION_DOWN:
                        buttonState |= button;
                        if (buttons == this.buttonCombines)
                            buttonCombinePressed.add(event.getKeyCode());
                        break;
                    case KeyEvent.ACTION_UP:
                        buttonState &= ~button;
                        if (buttons == this.buttonCombines)
                            buttonCombinePressed.remove(event.getKeyCode());
                        break;
                }

                gamepadValues.setButtonState(buttonState);
                gamepadValues.setChangedButtons(button);
                break;

        }
    }

    @SuppressWarnings("deprecation")
    public void setGamepadAxisState(MotionEvent event) {
        if(onGamepadStateChangeListener == null) return;

        int action = event.getAction();

        if(action != MotionEvent.ACTION_MOVE) return;

        int pointerIndex = (action & MotionEvent.ACTION_POINTER_ID_MASK) >> MotionEvent.ACTION_POINTER_ID_SHIFT;

        gamepadValues.setLeftAxisY(ANALOG_STICK_NEUTRAL_VALUE);
        gamepadValues.setLeftAxisX(ANALOG_STICK_NEUTRAL_VALUE);
        gamepadValues.setRightAxisY(ANALOG_STICK_NEUTRAL_VALUE);
        gamepadValues.setRightAxisX(ANALOG_STICK_NEUTRAL_VALUE);
        gamepadValues.setLeftTrigger(0);
        gamepadValues.setRightTrigger(0);

        for(int axis : AXISES) {
            List<InputInfo> axises = isCombineButtonPressed ? this.axiseCombines : this.axises;

            for(InputInfo input : axises) {
                if(input.getKeyCode() != axis) continue;

                float value = event.getAxisValue(axis, pointerIndex);
                if(axis == MotionEvent.AXIS_RX || axis == MotionEvent.AXIS_RY) {
                    value = (value + 1) / 2f;
                } else if((axis == MotionEvent.AXIS_BRAKE || axis == MotionEvent.AXIS_GAS) && value < 0) {
                    value = 0;
                }
                setGamepadAxisState(input, value);
            }
        }

        boolean sensorChanged = isSensorChanged();

        if(!isButtonChanged() && !sensorChanged) return;

        storePrevGamepadValues(sensorChanged);
        onGamepadStateChangeListener.onGamepadStateChange(sensorChanged);
    }

    private void setGamepadAxisState(InputInfo input, float value) {
        int v = 0;
        int button = input.getOutput();
        int buttonState = gamepadValues.getButtonState();
        int changedButtons = gamepadValues.getChangedButtons();


        // Analog input -> Analog output
        switch(button) {
            case Gamepad.AXIS_LEFT_ANALOG_UP:
            case Gamepad.AXIS_LEFT_ANALOG_DOWN:
            case Gamepad.AXIS_LEFT_ANALOG_LEFT:
            case Gamepad.AXIS_LEFT_ANALOG_RIGHT:
            case Gamepad.AXIS_RIGHT_ANALOG_UP:
            case Gamepad.AXIS_RIGHT_ANALOG_DOWN:
            case Gamepad.AXIS_RIGHT_ANALOG_LEFT:
            case Gamepad.AXIS_RIGHT_ANALOG_RIGHT:
                if(Math.abs(value) > analogDeadZone) v = (short)(value * 127f) + ANALOG_STICK_NEUTRAL_VALUE;
                break;
            case Gamepad.AXIS_L2:
            case Gamepad.AXIS_R2:
                if(Math.abs(value) > analogDeadZone) v = (short)(value * ANALOG_MAX_VALUE);
                break;

            // Analog input -> Digital output
            default:
                if(Math.abs(value) <= analogDeadZone) value = 0;

                if(input.isNegative() && value > 0 || !input.isNegative() && value < 0) {
                    return;
                }

                boolean isDown = Math.abs(value) > 0.5f;

                if(isDown && input.getToProfile() != null) {
                    boolean result = init(input.getToProfile(), null);
                    if(context != null && result) {
                        Toast.makeText(context, "[ " + input.getToProfile() + " ]", Toast.LENGTH_SHORT).show();
                    }
                    return;
                }

                if(isDown) {
                    buttonState |= button;
                    changedButtons |= button;
                } else {
                    buttonState &= -(button + 1);
                    if(button == Gamepad.DPAD_UP) {
                        buttonState &= -(Gamepad.DPAD_DOWN + 1);
                    } else if(button == Gamepad.DPAD_DOWN) {
                        buttonState &= -(Gamepad.DPAD_UP + 1);
                    } else if(button == Gamepad.DPAD_LEFT) {
                        buttonState &= -(Gamepad.DPAD_RIGHT + 1);
                    } else if(button == Gamepad.DPAD_RIGHT) {
                        buttonState &= -(Gamepad.DPAD_LEFT + 1);
                    }

                    changedButtons |= (button + 1);
                }

                gamepadValues.setButtonState(buttonState);
                gamepadValues.setChangedButtons(changedButtons);

                return;
        }

        if (v == 0) {
            // Clear the trigger buttons if we haven't already
            if (button == Gamepad.AXIS_L2 || button == Gamepad.AXIS_R2) {
                buttonState &= ~(button == Gamepad.AXIS_L2 ? Gamepad.BUTTON_L2 : Gamepad.BUTTON_R2);
                gamepadValues.setButtonState(buttonState);
                gamepadValues.setChangedButtons(button);
            }
            return;
        }

        // Analog input -> Analog output
        if(v > 0 && (button == Gamepad.AXIS_L2 || button == Gamepad.AXIS_R2)) {
            switch (button) {
                case Gamepad.AXIS_L2:
                    gamepadValues.setLeftTrigger(v);
                    buttonState |= Gamepad.BUTTON_L2;
                    gamepadValues.setButtonState(buttonState);
                    gamepadValues.setChangedButtons(Gamepad.BUTTON_L2);
                    break;
                case Gamepad.AXIS_R2:
                    gamepadValues.setRightTrigger(v);
                    buttonState |= Gamepad.BUTTON_R2;
                    gamepadValues.setButtonState(buttonState);
                    gamepadValues.setChangedButtons(Gamepad.BUTTON_R2);
                    break;
            }
        } else if(v > ANALOG_STICK_NEUTRAL_VALUE) {
            switch (button) {
                case Gamepad.AXIS_LEFT_ANALOG_UP:
                    gamepadValues.setLeftAxisY(v);
                    break;
                case Gamepad.AXIS_LEFT_ANALOG_RIGHT:
                    gamepadValues.setLeftAxisX(v);
                    break;
                case Gamepad.AXIS_RIGHT_ANALOG_UP:
                    gamepadValues.setRightAxisY(v);
                    break;
                case Gamepad.AXIS_RIGHT_ANALOG_RIGHT:
                    gamepadValues.setRightAxisX(v);
                    break;
            }
        } else if(v < ANALOG_STICK_NEUTRAL_VALUE) {
            switch(button) {
                case Gamepad.AXIS_LEFT_ANALOG_DOWN:
                    gamepadValues.setLeftAxisY(v);
                    break;

                case Gamepad.AXIS_LEFT_ANALOG_LEFT:
                    gamepadValues.setLeftAxisX(v);
                    break;

                case Gamepad.AXIS_RIGHT_ANALOG_DOWN:
                    gamepadValues.setRightAxisY(v);
                    break;

                case Gamepad.AXIS_RIGHT_ANALOG_LEFT:
                    gamepadValues.setRightAxisX(v);
                    break;

                case Gamepad.AXIS_L2:
                    buttonState &= ~Gamepad.BUTTON_L2;
                    gamepadValues.setButtonState(buttonState);
                    gamepadValues.setChangedButtons(Gamepad.BUTTON_L2);
                    break;

                case Gamepad.AXIS_R2:
                    buttonState &= ~Gamepad.BUTTON_R2;
                    gamepadValues.setButtonState(buttonState & ~Gamepad.BUTTON_R2);
                    gamepadValues.setChangedButtons(Gamepad.BUTTON_R2);
                    break;
            }
        }
    }

    public PSGamepadValues getGamepadValues() {
        return gamepadValues;
    }

    public float getAnalogDeadZone() {
        return analogDeadZone;
    }

    private boolean isSensorChanged() {
        return prevGamepadValues.getLeftAxisX() != gamepadValues.getLeftAxisX()
                || prevGamepadValues.getLeftAxisY() != gamepadValues.getLeftAxisY()
                || prevGamepadValues.getRightAxisX() != gamepadValues.getRightAxisX()
                || prevGamepadValues.getRightAxisY() != gamepadValues.getRightAxisY()
                || prevGamepadValues.getLeftTrigger() != gamepadValues.getLeftTrigger()
                || prevGamepadValues.getRightTrigger() != gamepadValues.getRightTrigger();
    }

    private boolean isButtonChanged() {
        return prevGamepadValues.getButtonState() != gamepadValues.getButtonState()
                || prevGamepadValues.getChangedButtons() != gamepadValues.getChangedButtons();
    }

    private void storePrevGamepadValues(boolean sensor) {
        if(sensor) {
            prevGamepadValues.setLeftAxisX(gamepadValues.getLeftAxisX());
            prevGamepadValues.setLeftAxisY(gamepadValues.getLeftAxisY());
            prevGamepadValues.setRightAxisX(gamepadValues.getRightAxisX());
            prevGamepadValues.setRightAxisY(gamepadValues.getRightAxisY());
            prevGamepadValues.setLeftTrigger(gamepadValues.getLeftTrigger());
            prevGamepadValues.setRightTrigger(gamepadValues.getRightTrigger());
        }

        prevGamepadValues.setButtonState(gamepadValues.getButtonState());
        prevGamepadValues.setChangedButtons(gamepadValues.getChangedButtons());
    }

    @Override
    public void reportControllerState(int controllerId, SparseIntArray buttonFlags, float leftStickX, float leftStickY, float rightStickX, float rightStickY, float leftTrigger, float rightTrigger) {
        for(int i = 0; i < buttonFlags.size(); i++) {
            int keyCode = buttonFlags.keyAt(i);
            int action = buttonFlags.get(keyCode);
            KeyEvent event = new KeyEvent(action, keyCode);

            if(keyCode == combineButtonKeyCode) {
                isCombineButtonPressed = action == KeyEvent.ACTION_DOWN;
                continue;
            }

            List<InputInfo> buttons = isCombineButtonPressed || buttonCombinePressed.contains(keyCode) ? this.buttonCombines : this.buttons;

            for(InputInfo input : buttons) {
                if (input.getKeyCode() != keyCode) continue;

                if (input.getToProfile() != null) {
                    boolean result = init(input.getToProfile(), null);
                    if(context != null && result) {
                        Toast.makeText(context, "[ " + input.getToProfile() + " ]", Toast.LENGTH_SHORT).show();
                    }
                    break;
                }

                int button = input.getOutput();

                if(button == 0) continue;

                setButtonState(action, button, event);
            }
        }
        for(int axis : AXISES) {
            List<InputInfo> axises = isCombineButtonPressed ? this.axiseCombines : this.axises;

            for(InputInfo input : axises) {
                if (input.getKeyCode() != axis) continue;

                switch (axis) {
                    case MotionEvent.AXIS_X:
                        setGamepadAxisState(input, leftStickX);
                        break;
                    case MotionEvent.AXIS_Y:
                        setGamepadAxisState(input, leftStickY);
                        break;
                    case MotionEvent.AXIS_Z:
                        setGamepadAxisState(input, rightStickX);
                        break;
                    case MotionEvent.AXIS_RZ:
                        setGamepadAxisState(input, rightStickY);
                        break;
                    case MotionEvent.AXIS_BRAKE:
                        setGamepadAxisState(input, leftTrigger);
                        break;
                    case MotionEvent.AXIS_GAS:
                        setGamepadAxisState(input, rightTrigger);
                        break;
                    case MotionEvent.AXIS_HAT_X:
                        if (buttonFlags.get(KeyEvent.KEYCODE_DPAD_LEFT) == KeyEvent.ACTION_DOWN) {
                            setGamepadAxisState(input, -1.0f);
                        } else if (buttonFlags.get(KeyEvent.KEYCODE_DPAD_RIGHT) == KeyEvent.ACTION_DOWN) {
                            setGamepadAxisState(input, 1.0f);
                        } else {
                            setGamepadAxisState(input, 0);
                        }
                        break;
                    case MotionEvent.AXIS_HAT_Y:
                        if (buttonFlags.get(KeyEvent.KEYCODE_DPAD_UP) == KeyEvent.ACTION_DOWN) {
                            setGamepadAxisState(input, -1.0f);
                        } else if (buttonFlags.get(KeyEvent.KEYCODE_DPAD_DOWN) == KeyEvent.ACTION_DOWN) {
                            setGamepadAxisState(input, 1.0f);
                        } else {
                            setGamepadAxisState(input, 0);
                        }
                        break;
                }
            }
        }
        boolean sensorChanged = isSensorChanged();

        if(!isButtonChanged() && !sensorChanged) return;

        storePrevGamepadValues(sensorChanged);
        onGamepadStateChangeListener.onGamepadStateChange(sensorChanged);
    }

    @Override
    public void deviceRemoved(AbstractController controller) {
        activeController = null;
    }

    @Override
    public void deviceAdded(AbstractController controller) {
        activeController = controller;
    }

}

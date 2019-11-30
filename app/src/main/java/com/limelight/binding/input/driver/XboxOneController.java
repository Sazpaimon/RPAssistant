package com.limelight.binding.input.driver;

import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.view.KeyEvent;

import com.rAs.android.rpgamepad.PSGamepadHandler;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class XboxOneController extends AbstractXboxController {

    private static final int XB1_IFACE_SUBCLASS = 71;
    private static final int XB1_IFACE_PROTOCOL = 208;

    private static final int[] SUPPORTED_VENDORS = {
            0x045e, // Microsoft
            0x0738, // Mad Catz
            0x0e6f, // Unknown
            0x0f0d, // Hori
            0x1532, // Razer Wildcat
            0x24c6, // PowerA
    };

    private static final byte[] FW2015_INIT = {0x05, 0x20, 0x00, 0x01, 0x00};
    private static final byte[] HORI_INIT = {0x01, 0x20, 0x00, 0x09, 0x00, 0x04, 0x20, 0x3a,
            0x00, 0x00, 0x00, (byte)0x80, 0x00};
    private static final byte[] PDP_INIT1 = {0x0a, 0x20, 0x00, 0x03, 0x00, 0x01, 0x14};
    private static final byte[] PDP_INIT2 = {0x06, 0x20, 0x00, 0x02, 0x01, 0x00};
    private static final byte[] RUMBLE_INIT1 = {0x09, 0x00, 0x00, 0x09, 0x00, 0x0F, 0x00, 0x00,
            0x1D, 0x1D, (byte)0xFF, 0x00, 0x00};
    private static final byte[] RUMBLE_INIT2 = {0x09, 0x00, 0x00, 0x09, 0x00, 0x0F, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00};

    private static InitPacket[] INIT_PKTS = {
            new InitPacket(0x0e6f, 0x0165, HORI_INIT),
            new InitPacket(0x0f0d, 0x0067, HORI_INIT),
            new InitPacket(0x0000, 0x0000, FW2015_INIT),
            new InitPacket(0x0e6f, 0x0000, PDP_INIT1),
            new InitPacket(0x0e6f, 0x0000, PDP_INIT2),
            new InitPacket(0x24c6, 0x541a, RUMBLE_INIT1),
            new InitPacket(0x24c6, 0x542a, RUMBLE_INIT1),
            new InitPacket(0x24c6, 0x543a, RUMBLE_INIT1),
            new InitPacket(0x24c6, 0x541a, RUMBLE_INIT2),
            new InitPacket(0x24c6, 0x542a, RUMBLE_INIT2),
            new InitPacket(0x24c6, 0x543a, RUMBLE_INIT2),
    };

    private byte seqNum = 0;

    public XboxOneController(UsbDevice device, UsbDeviceConnection connection, int deviceId, UsbDriverListener listener) {
        super(device, connection, deviceId, listener);
    }

    private void processButtons(ByteBuffer buffer) {
        byte b = buffer.get();

        setButtonFlag(KeyEvent.KEYCODE_BUTTON_START, b & 0x04);
        setButtonFlag(KeyEvent.KEYCODE_BACK, b & 0x08);

        setButtonFlag(KeyEvent.KEYCODE_BUTTON_A, b & 0x10);
        setButtonFlag(KeyEvent.KEYCODE_BUTTON_B, b & 0x20);
        setButtonFlag(KeyEvent.KEYCODE_BUTTON_X, b & 0x40);
        setButtonFlag(KeyEvent.KEYCODE_BUTTON_Y, b & 0x80);

        b = buffer.get();
        setButtonFlag(KeyEvent.KEYCODE_DPAD_LEFT, b & 0x04);
        setButtonFlag(KeyEvent.KEYCODE_DPAD_RIGHT, b & 0x08);
        setButtonFlag(KeyEvent.KEYCODE_DPAD_UP, b & 0x01);
        setButtonFlag(KeyEvent.KEYCODE_DPAD_DOWN, b & 0x02);

        setButtonFlag(KeyEvent.KEYCODE_BUTTON_L1, b & 0x10);
        setButtonFlag(KeyEvent.KEYCODE_BUTTON_R1, b & 0x20);

        setButtonFlag(KeyEvent.KEYCODE_BUTTON_THUMBL, b & 0x40);
        setButtonFlag(KeyEvent.KEYCODE_BUTTON_THUMBR, b & 0x80);

        leftTrigger = buffer.getShort() / 1023.0f;
        rightTrigger = buffer.getShort() / 1023.0f;

        leftStickX = buffer.getShort() / 32767.0f;
        leftStickY = ~buffer.getShort() / 32767.0f;

        rightStickX = buffer.getShort() / 32767.0f;
        rightStickY = ~buffer.getShort() / 32767.0f;
    }

    private void ackModeReport(byte seqNum) {
        byte[] payload = {0x01, 0x20, seqNum, 0x09, 0x00, 0x07, 0x20, 0x02,
                0x00, 0x00, 0x00, 0x00, 0x00};
        connection.bulkTransfer(outEndpt, payload, payload.length, 3000);
    }

    @Override
    protected boolean handleRead(ByteBuffer buffer) {
        switch (buffer.get())
        {
            case 0x20:
                buffer.position(buffer.position()+3);
                processButtons(buffer);
                return true;

            case 0x07:
                // The Xbox One S controller needs acks for mode reports otherwise
                // it retransmits them forever.
                if (buffer.get() == 0x30) {
                    ackModeReport(buffer.get());
                    buffer.position(buffer.position() + 1);
                }
                else {
                    buffer.position(buffer.position() + 2);
                }
                byte b = buffer.get();
                setButtonFlag(KeyEvent.KEYCODE_HOME, b & 0x01);
                setButtonFlag(KeyEvent.KEYCODE_BUTTON_MODE, b & 0x01);
                return true;
        }

        return false;
    }

    public static boolean canClaimDevice(UsbDevice device) {
        for (int supportedVid : SUPPORTED_VENDORS) {
            if (device.getVendorId() == supportedVid &&
                    device.getInterfaceCount() >= 1 &&
                    device.getInterface(0).getInterfaceClass() == UsbConstants.USB_CLASS_VENDOR_SPEC &&
                    device.getInterface(0).getInterfaceSubclass() == XB1_IFACE_SUBCLASS &&
                    device.getInterface(0).getInterfaceProtocol() == XB1_IFACE_PROTOCOL) {
                return true;
            }
        }

        return false;
    }

    @Override
    protected boolean doInit() {
        // Send all applicable init packets
        for (InitPacket pkt : INIT_PKTS) {
            if (pkt.vendorId != 0 && device.getVendorId() != pkt.vendorId) {
                continue;
            }

            if (pkt.productId != 0 && device.getProductId() != pkt.productId) {
                continue;
            }

            byte[] data = Arrays.copyOf(pkt.data, pkt.data.length);

            // Populate sequence number
            data[2] = seqNum++;

            // Send the initialization packet
            int res = connection.bulkTransfer(outEndpt, data, data.length, 3000);
            if (res != data.length) {
                return false;
            }
        }

        return true;
    }

    @Override
    public void rumble(short lowFreqMotor, short highFreqMotor) {
        byte[] data = {
                0x09, 0x00, seqNum++, 0x09, 0x00,
                0x0F, 0x00, 0x00,
                (byte)lowFreqMotor, (byte)highFreqMotor,
                (byte)0xFF, 0x00, (byte)0xFF
        };
        int res = connection.bulkTransfer(outEndpt, data, data.length, 100);
    }

    private static class InitPacket {
        final int vendorId;
        final int productId;
        final byte[] data;

        InitPacket(int vendorId, int productId, byte[] data) {
            this.vendorId = vendorId;
            this.productId = productId;
            this.data = data;
        }
    }
}
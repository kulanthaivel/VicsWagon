package org.wintrisstech.vicswagon;

import ioio.lib.api.DigitalInput;
import ioio.lib.api.DigitalOutput;
import ioio.lib.api.IOIO;
import ioio.lib.api.PulseInput;
import ioio.lib.api.PwmOutput;
import ioio.lib.api.exception.ConnectionLostException;
import ioio.lib.util.IOIOLooper;
import android.os.SystemClock;
import android.util.Log;

class VicsWagonIOIOLooper implements IOIOLooper {
	private final static String LOGTAG = "IOIOLooper";
    private PwmOutput rightMotorClock;
    private PwmOutput leftMotorClock;
    private int pulseWidth = 10;// microseconds

    private DigitalOutput rightMotorClockPulse;
    private DigitalOutput leftMotorClockPulse;
    private DigitalOutput rightMotorDirection;
    private DigitalOutput leftMotorDirection;
    private DigitalOutput halfFull;
    private DigitalOutput motorEnable; // Must be true for motors to run.
    private DigitalOutput reset; // Must be true for motors to run.
    private DigitalOutput control;// Decay mode selector high = slow, low = fast.
    private DigitalOutput motorControllerControl;// Decay mode selector, high = slow decay, low = fast decay

    private static final int MOTOR_ENABLE_PIN = 3;// Low turns off all power to motors***
    private static final int MOTOR_RIGHT_DIRECTION_OUTPUT_PIN = 20;// High = clockwise, low = counter-clockwise
    private static final int MOTOR_LEFT_DIRECTION_OUTPUT_PIN = 21;
    private static final int MOTOR_CONTROLLER_CONTROL_PIN = 6;// For both motors 

    private static final int MOTOR_HALF_FULL_STEP_PIN = 7;// For both motors
    private static final int MOTOR_RESET = 22;// For both motors
    private static final int MOTOR_CLOCK_LEFT_PIN = 27;
    private static final int MOTOR_CLOCK_RIGHT_PIN = 28;

    private int leftMotorPWMfrequency = 100;
    private int rightMotorPWMfrequency = 100;

    private SensorMonitor sensorMonitor = null;
    private IOIO ioio = null;

    private PulseInput front;
    private DigitalOutput frontStrobe;
    private float m_frontIRPulseDuration;
    private static final int FRONT_STROBE_ULTRASONIC_OUTPUT_PIN = 16;
    private static final int FRONT_ULTRASONIC_INPUT_PIN = 12;
    
    VicsWagonActivity mActivity;
    private Thread mIRMonitorThread = null;
    private boolean mIRSensorConnected = false;;
    
    public VicsWagonIOIOLooper(VicsWagonActivity activity) {
        mActivity = activity;
    }

    public void log(String msg) {
        mActivity.log(msg);
    }
    
    public void setup(IOIO ioio) throws ConnectionLostException, InterruptedException {
        /*
         * When the setup() method is called the IOIO is connected.
         */
        this.ioio = ioio;
        try {
            reset = ioio.openDigitalOutput(MOTOR_RESET);// both motors
            reset.write(false);
            reset.write(true);

            motorControllerControl = ioio.openDigitalOutput(MOTOR_CONTROLLER_CONTROL_PIN);
            motorControllerControl.write(true);// Slow decay

            halfFull = ioio.openDigitalOutput(MOTOR_HALF_FULL_STEP_PIN);// both motors
            halfFull.write(true);// True = half step

            rightMotorDirection = ioio.openDigitalOutput(MOTOR_RIGHT_DIRECTION_OUTPUT_PIN);
            rightMotorDirection.write(false);

            leftMotorDirection = ioio.openDigitalOutput(MOTOR_LEFT_DIRECTION_OUTPUT_PIN);
            leftMotorDirection.write(true);

            motorEnable = ioio.openDigitalOutput(MOTOR_ENABLE_PIN);// both motors
            motorEnable.write(true);

            rightMotorClock = ioio.openPwmOutput(MOTOR_CLOCK_RIGHT_PIN, rightMotorPWMfrequency);
            leftMotorClock = ioio.openPwmOutput(MOTOR_CLOCK_LEFT_PIN, leftMotorPWMfrequency);

            rightMotorClock.setPulseWidth(pulseWidth);
            leftMotorClock.setPulseWidth(pulseWidth);

            openAndMonitorFrontIRSensor();
            //sensorMonitor = new SensorMonitor(ioio, mActivity);
            //sensorMonitor.setupAllSensors(/*hasFrontIRSensor*/true, false, false, false, false);


        } catch (Exception ex) {
            log("Setup hickup");
        }
        log(mActivity.getString(R.string.ioio_connected));
    }

    
    private synchronized void setFrontIRPulseDuration(float inputVal) {
        m_frontIRPulseDuration = inputVal;
    }

    public synchronized float getFrontIRPulseDuration() {
        return m_frontIRPulseDuration;
    }
    
    private void openAndMonitorFrontIRSensor() throws ConnectionLostException {
        if (!mIRSensorConnected) {
            log("FRONT_IR_SENSOR: Opening input & output pins");
            frontStrobe = ioio.openDigitalOutput(FRONT_STROBE_ULTRASONIC_OUTPUT_PIN, false);
            front = ioio.openPulseInput(new DigitalInput.Spec(FRONT_ULTRASONIC_INPUT_PIN), PulseInput.ClockRate.RATE_62KHz, PulseInput.PulseMode.POSITIVE, false);
            mIRSensorConnected  = true;
            setupIRMonitorThread();
        }
    }

    private void setupIRMonitorThread() {
        mIRMonitorThread = new Thread(new Runnable() {
            public void run() {
                while (true) {
                    try {
                        float inputVal = front.waitPulseGetDuration();
                        log("FRONT_IR_SENSOR: duration = " + inputVal);
                        setFrontIRPulseDuration(inputVal);
                    } catch (Exception e) {
                        log("FRONT_IR_SENSOR: waitPulseGetDuration exception : " + e);
                        mIRSensorConnected = false;
                        break;
                    }
                    SystemClock.sleep(10);
                }
            }
        });
        mIRMonitorThread.start();
    }

    public void loop() throws ConnectionLostException, InterruptedException {
        openAndMonitorFrontIRSensor();
        SystemClock.sleep(1000);
        frontStrobe.write(true);
        frontStrobe.write(false);

        accelerateTo(1000);
        
        if (sensorMonitor != null) {
            sensorMonitor.readAllSensors();
            float duration = sensorMonitor.getFrontIRPulseDuration();
            log("Detected IR beam duration: " + duration);
        }
    }

    public void accelerateTo(final int finalPWMfrequency) {
    	SystemClock.sleep(1000);
    	new Thread(new Runnable() {
    		public void run() {
    			while (leftMotorPWMfrequency < finalPWMfrequency) {
    				try {
    					SystemClock.sleep(1000 / leftMotorPWMfrequency);
    					Log.d(LOGTAG, "Setting Motor frequency : " + leftMotorPWMfrequency);
    					rightMotorClock.setFrequency(rightMotorPWMfrequency);
    					leftMotorClock.setFrequency(leftMotorPWMfrequency);
    					leftMotorPWMfrequency++;
    					rightMotorPWMfrequency++;

    				} catch (Exception ex) {
    					log("Motor clock pulsing hiccup");
    				}
    			}
    		}
    	}).start();
    }

    public void disconnected() {
        if (mIRMonitorThread != null) {
            mIRMonitorThread.stop();
        }
        try {
            motorEnable.write(false);
        } catch (ConnectionLostException ex) {
            log("Problem with motor disable on shutdown");
        }
        log(mActivity.getString(R.string.ioio_disconnected));
    }

    public void incompatible() {
        log("Incompatible");
    }
}
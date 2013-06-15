//Copyright 2013 Wintriss Technical Schools

package org.wintrisstech.vicswagon;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.widget.ScrollView;
import android.widget.TextView;
import ioio.lib.util.IOIOLooper;
import ioio.lib.util.android.IOIOActivity;
import java.util.Locale;

import org.wintrisstech.vicswagon.R;

/**
 * This is the main activity of the VicRobot application.
 * 
 * @author Erik Colban, Kvel
 * 
 */
public class VicsWagonActivity extends IOIOActivity implements TextToSpeech.OnInitListener {
    /**
     * Tag used for debugging.
     */
    private static final String LOGTAG = "VicsWagon";
    /**
     * Text view that contains all logged messages
     */
    private TextView mText;
    private ScrollView scroller;
    /**
     * TTS stuff
     */
    protected static final int MY_DATA_CHECK_CODE = 33;
    private TextToSpeech mTts;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        /*
         * Prevent a change of orientation, which would cause the activity to
         * pause.
         */
        this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        setContentView(R.layout.main);

        Intent checkIntent = new Intent();
        checkIntent.setAction(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA);
        startActivityForResult(checkIntent, MY_DATA_CHECK_CODE);

        mText = (TextView) findViewById(R.id.text);
        scroller = (ScrollView) findViewById(R.id.scroller);
        log(getString(R.string.wait_ioio));
    }

    @Override
    public void onPause() {
        log("Pausing");
        log("=================> VicsWagon version 9.00");
        super.onPause();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == MY_DATA_CHECK_CODE) {
            if (resultCode == TextToSpeech.Engine.CHECK_VOICE_DATA_PASS) {
                // success, create the TTS instance
                mTts = new TextToSpeech(this, this);
            } else {
                // missing data, install it
                Intent installIntent = new Intent();
                installIntent.setAction(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
                startActivity(installIntent);
            }
        }
    }

    public void onInit(int arg0) {
    }

    public void speak(String stuffToSay) {
        mTts.setLanguage(Locale.US);
        if (!mTts.isSpeaking()) {
            mTts.speak(stuffToSay, TextToSpeech.QUEUE_FLUSH, null);
        }
    }

    @Override
    public IOIOLooper createIOIOLooper() {
        return new VicsWagonIOIOLooper(this);
    }

    /**
     * Writes a message to the Dashboard instance.
     * 
     * @param msg
     *            the message to write
     */
    public void log(final String msg) {
        runOnUiThread(new Runnable() {
            public void run() {
                mText.append(msg);
                mText.append("\n");
                scroller.smoothScrollTo(0, mText.getBottom());
            }
        });
    }
}

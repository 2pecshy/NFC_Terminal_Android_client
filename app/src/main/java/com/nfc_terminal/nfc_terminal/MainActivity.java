package com.nfc_terminal.nfc_terminal;

import android.app.PendingIntent;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.st.st25sdk.NFCTag;
import com.st.st25sdk.STException;
import com.st.st25sdk.TagHelper;
import com.st.st25sdk.type5.st25dv.ST25DVTag;

import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity implements TagDiscovery.onTagDiscoveryCompletedListener {

    private NfcAdapter mNfcAdapter;
    private ST25DVTag Tag_st25dv;
    private TextView tv_terminal_output;
    private TextView tagNameTextView;
    private Button login_btn, logout_btn, send_input_term_btn;
    private EditText input_term;
    // Last tag taped
    private NFCTag mNfcTag;

    enum ActionStatus {
        ACTION_SUCCESSFUL,
        ACTION_FAILED,
        TAG_NOT_IN_THE_FIELD,
        INPUT_EMPTY
    }

    enum AsyncTaskMode{
        ACTION_LOGIN,
        ACTION_LOGOUT,
        ACTION_WRITE_MAILBOX,
        ACTION_READ_MAILBOX
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Tag_st25dv = null;
        mNfcAdapter = NfcAdapter.getDefaultAdapter(this);
        login_btn = (Button) findViewById(R.id.login_btn);
        logout_btn = (Button) findViewById(R.id.logout_btn);
        send_input_term_btn = (Button) findViewById(R.id.send_input_terminal);
        tv_terminal_output = (TextView) findViewById(R.id.terminal_output);
        tagNameTextView = (TextView) findViewById(R.id.terminal_view);
        input_term = (EditText) findViewById(R.id.input_terminal);

        login_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (Tag_st25dv != null)
                    new asyncTaskLoginLogout(AsyncTaskMode.ACTION_LOGIN).execute();
                else
                    Log.d("ST25DV:", "No tag");
            }
        });
        logout_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (Tag_st25dv != null)
                    new asyncTaskLoginLogout(AsyncTaskMode.ACTION_LOGOUT).execute();
                else
                    Log.d("ST25DV:", "No tag");
            }
        });
        send_input_term_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (Tag_st25dv != null)
                    new asyncTaskLoginLogout(AsyncTaskMode.ACTION_WRITE_MAILBOX).execute();
                else
                    Log.d("ST25DV:", "No tag");
            }
        });
        tv_terminal_output.setMovementMethod(new ScrollingMovementMethod());
        setRepeatingAsyncTask();
        Log.d("ST25DV:", "asyncTask run");
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mNfcAdapter != null) {
            mNfcAdapter.disableForegroundDispatch(this);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Check if if this phone has NFC hardware
        if (mNfcAdapter == null) {

            AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);

            // set title
            alertDialogBuilder.setTitle("Warning!");

            // set dialog message
            alertDialogBuilder
                    .setMessage("This phone doesn't have NFC hardware!")
                    .setCancelable(true)
                    .setPositiveButton("Leave", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            dialog.cancel();
                            finish();
                        }
                    });

            // create alert dialog
            AlertDialog alertDialog = alertDialogBuilder.create();

            // show it
            alertDialog.show();

        } else {
            //Toast.makeText(this, "We are ready to play with NFC!", Toast.LENGTH_SHORT).show();
            // Give priority to the current activity when receiving NFC events (over other actvities)
            PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);
            IntentFilter[] nfcFilters = null;
            String[][] nfcTechLists = null;
            mNfcAdapter.enableForegroundDispatch(this, pendingIntent, nfcFilters, nfcTechLists);
        }

        // The current activity can be resumed for several reasons (NFC tag tapped is one of them).
        // Check what was the reason which triggered the resume of current application
        Intent intent = getIntent();
        String action = intent.getAction();

        if (action.equals(NfcAdapter.ACTION_NDEF_DISCOVERED) ||
                action.equals(NfcAdapter.ACTION_TECH_DISCOVERED) ||
                action.equals(NfcAdapter.ACTION_TAG_DISCOVERED)) {

            // If the resume was triggered by an NFC event, it will contain an EXTRA_TAG providing
            // the handle of the NFC Tag
            Tag androidTag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            if (androidTag != null) {
                Toast.makeText(this, "Starting Tag discovery", Toast.LENGTH_SHORT).show();

                // This action will be done in an Asynchronous task.
                // onTagDiscoveryCompleted() of current activity will be called when the discovery is completed.
                new TagDiscovery(this).execute(androidTag);
            }
        }
        setRepeatingAsyncTask();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // onResume() gets called after this to handle the intent
        setIntent(intent);
    }

    @Override
    public void onTagDiscoveryCompleted(NFCTag nfcTag, TagHelper.ProductID productId, STException error) {

        if (error != null) {
            Toast.makeText(getApplication(), "Error while reading the tag: " + error.toString(), Toast.LENGTH_SHORT).show();
            return;
        }
        if (nfcTag != null && Tag_st25dv == null) {
            mNfcTag = nfcTag;
            try {
                String tagName = nfcTag.getName();
                String uidString = nfcTag.getUidString();
                tagNameTextView.setText(tagName + "\nuid:" + uidString);
                switch (productId) {
                    case PRODUCT_ST_ST25DV64K_I:
                    case PRODUCT_ST_ST25DV64K_J:
                    case PRODUCT_ST_ST25DV16K_I:
                    case PRODUCT_ST_ST25DV16K_J:
                    case PRODUCT_ST_ST25DV04K_I:
                    case PRODUCT_ST_ST25DV04K_J:
                        Tag_st25dv = (ST25DVTag) mNfcTag;
                        break;
                }
            } catch (STException e) {
                e.printStackTrace();
                Toast.makeText(this, "Discovery successful but failed to read the tag!", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "Tag discovery failed!", Toast.LENGTH_SHORT).show();
        }
    }

    private class asyncTaskLoginLogout extends AsyncTask<Void, byte[], ActionStatus> {

        AsyncTaskMode mode;

        public asyncTaskLoginLogout(AsyncTaskMode mode) {
            this.mode = mode;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            switch (mode) {
                case ACTION_LOGIN:
                case ACTION_LOGOUT:
                    break;
                case ACTION_WRITE_MAILBOX:
                    login_btn.setClickable(false);
                    logout_btn.setClickable(false);
                    send_input_term_btn.setClickable(false);
                    break;
                default:
            }
        }

        @Override
        protected ActionStatus doInBackground(Void... arg0) {
            ActionStatus result = ActionStatus.ACTION_FAILED;
            byte[] buffer_cmd;
            try {
                switch (mode) {
                    case ACTION_LOGIN:
                        Tag_st25dv.enableMailbox();
                        break;
                    case ACTION_LOGOUT:
                        Tag_st25dv.disableMailbox();
                        break;
                    case ACTION_WRITE_MAILBOX:
                        if (input_term.getText().length() == 0) {
                            return ActionStatus.INPUT_EMPTY;
                        }
                        if (Tag_st25dv.isMailboxEnabled(true) && !Tag_st25dv.hasRFPutMsg(true)) {
                            if (Tag_st25dv.hasHostPutMsg(true)) {
                                return ActionStatus.ACTION_FAILED;
                            }
                            String cmd = input_term.getText().toString();
                            buffer_cmd = cmd.getBytes();
                            tv_terminal_output.append(">>>>$ " + cmd + "\n");
                            Tag_st25dv.writeMailboxMessage(buffer_cmd);
                        } else
                            return ActionStatus.ACTION_FAILED;
                        break;
                    case ACTION_READ_MAILBOX:
                        byte[] buffer_answer;
                        if(Tag_st25dv != null) {
                            Log.d("ST25DV","getMailBox");
                            try {
                                if (Tag_st25dv.isMailboxEnabled(true)) {
                                    if (Tag_st25dv.hasHostPutMsg(true)) {
                                        buffer_answer = Tag_st25dv.readMailboxMessage(0, Tag_st25dv.readMailboxMessageLength());
                                        final String res = new String(buffer_answer);
                                        runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                Log.d("ST25DV","MailBox txt: " + res );
                                                tv_terminal_output.append(res);
                                            }
                                        });
                                    }
                                }
                            } catch (STException e) {
                            }
                        }
                    default:
                }
                result = ActionStatus.ACTION_SUCCESSFUL;
            } catch (STException e) {
                switch (e.getError()) {
                    case TAG_NOT_IN_THE_FIELD:
                        result = ActionStatus.TAG_NOT_IN_THE_FIELD;
                        break;
                    default:
                        e.printStackTrace();
                        result = ActionStatus.ACTION_FAILED;
                        break;
                }
            }
            return result;
        }

        @Override
        protected void onPostExecute(ActionStatus actionStatus) {
            switch (mode) {
                case ACTION_LOGIN:
                case ACTION_LOGOUT:
                    if (actionStatus == ActionStatus.ACTION_SUCCESSFUL)
                        Toast.makeText(MainActivity.this, this.mode == AsyncTaskMode.ACTION_LOGIN ? "login successful" : "logout successful", Toast.LENGTH_SHORT).show();
                    break;
                case ACTION_WRITE_MAILBOX:
                    if (actionStatus == ActionStatus.ACTION_SUCCESSFUL) {
                        Toast.makeText(MainActivity.this, "command sent", Toast.LENGTH_SHORT).show();
                        input_term.setText("");
                    }
                    break;
                default:
            }
            login_btn.setClickable(true);
            logout_btn.setClickable(true);
            send_input_term_btn.setClickable(true);
            return;
        }
    }

    private void setRepeatingAsyncTask() {

        final Handler handler = new Handler();
        Timer timer = new Timer();

        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                handler.post(new Runnable() {
                    public void run() {
                        new asyncTaskLoginLogout(AsyncTaskMode.ACTION_READ_MAILBOX).execute();
                    }
                });
            }
        };
        timer.schedule(task, 0, 1000);  // interval of one minute
    }
}


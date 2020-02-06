package id.dreamfighter.android.nfcreader;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.nfc.tech.MifareClassic;
import android.nfc.tech.MifareUltralight;
import android.nfc.tech.Ndef;
import android.nfc.tech.NdefFormatable;
import android.nfc.tech.NfcB;
import android.nfc.tech.NfcF;
import android.nfc.tech.NfcV;
import android.os.Bundle;

        import java.io.IOException;

import android.app.PendingIntent;
        import android.content.Intent;
        import android.content.IntentFilter;
        import android.content.IntentFilter.MalformedMimeTypeException;
        import android.nfc.NfcAdapter;
        import android.nfc.Tag;
        import android.nfc.tech.IsoDep;
        import android.nfc.tech.NfcA;
import android.util.Log;
        import android.view.Menu;
        import android.widget.TextView;
        import android.widget.Toast;

/**
 * Attempt to read 14443-3 Smart card via NFC.
 * Features code from John Philip Bigcas:
 *   http://noobstah.blogspot.co.nz/2013/04/mifare-desfire-ev1-and-android.html
 */
public class MainActivity extends AppCompatActivity {

    private IntentFilter[] intentFiltersArray;
    private String[][] techListsArray;
    private NfcAdapter mAdapter;
    private PendingIntent pendingIntent;
    private TextView mTransBytes;
    private TextView mDecId;
    private TextView mHexId;
    private TextView mInfo;
    private NfcA mNfc;
    private NfcB mNfcB;
    private StringBuilder mStringBuilder;

    // Desfire commands
    private static final byte SELECT_COMMAND = (byte) 0x5A;
    private static final byte AUTHENTICATE_COMMAND = (byte) 0x0A;
    private static final byte READ_DATA_COMMAND = (byte) 0xBD;
    private static final byte[] NATIVE_AUTHENTICATION_COMMAND = new byte[] {
            (byte) 0x0A, (byte) 0x00 };
    private static final byte[] NATIVE_SELECT_COMMAND = new byte[] {
            (byte) 0x5A, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF };

    private static final byte[] READ_DATA_COMMAND_B = new byte[] { (byte) 0x94, (byte)0xB2, 0x01, 0x04, 0x00 };

    private static final byte[] SELECT_COMMAND_B = new byte[] { (byte) 0x94, (byte)0xA4, 0x00, 0x00, (byte) 0x02, (byte)0x20, 0x69, 0x00 };

    @Nullable
    @Override
    public CharSequence onCreateDescription() {
        return super.onCreateDescription();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mAdapter = NfcAdapter.getDefaultAdapter(this);

        pendingIntent = PendingIntent.getActivity(this, 0, new Intent(this,
                getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);
        IntentFilter ndef = new IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED);
        try {
            ndef.addDataType("*/*");
        } catch (MalformedMimeTypeException e) {
            throw new RuntimeException();
        }

        IntentFilter ntech = new IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED);
        try {
            ntech.addDataType("*/*");
        } catch (MalformedMimeTypeException e) {
            throw new RuntimeException();
        }

        intentFiltersArray = new IntentFilter[] { ndef,ntech };
        techListsArray = new String[][] {
                new String[] {NfcA.class.getName()},
                new String[] {NfcB.class.getName()},
                new String[] {NfcV.class.getName()},
                new String[] {Ndef.class.getName()},
                new String[] {NfcF.class.getName()},
                new String[] {IsoDep.class.getName()},
                new String[] {NdefFormatable.class.getName()},
                new String[] {MifareClassic.class.getName()},
                new String[] {MifareUltralight.class.getName()}
        };

        // Initialise TextView fields
        mHexId = (TextView) findViewById(R.id.hexId);
        mDecId = (TextView) findViewById(R.id.decId);
        mTransBytes = (TextView) findViewById(R.id.transceivableBytes);
        mInfo = (TextView) findViewById(R.id.infoView);

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    private void processIntent(Intent intent) {
        Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
        if(tag!=null) {
            String[] techList = tag.getTechList();
            Log.d("TAAG",""+techList.length);
            for(String tech:techList){
                Log.d("TECH",tech);
                if(tech.equals(NfcA.class.getName())){
                    nfcA(tag);
                    break;
                }else if(tech.equals(NfcB.class.getName())){
                    nfcB(tag);
                    break;
                }
            }
        }
    }

    private void nfcB(Tag tag){
        mNfcB = NfcB.get(tag);
        try {
            mNfcB.connect();

            Log.v("tag", "connected.");
            byte[] id = mNfcB.getTag().getId();
            Log.v("tag", "Got id from tag:" + id);
            mHexId.setText(getHex(id));
            mDecId.setText(getDec(id));
            mTransBytes.setText("" + mNfcB.getMaxTransceiveLength());

            //byte[] response = mNfcB.transceive(NATIVE_SELECT_COMMAND);
            displayText("Select App", getHex(mNfcB.getApplicationData()));
            authenticateB();
            String read = readCommand(NfcB.class.getName());
            displayText("Read",read);

        } catch (IOException e) {
            // TODO: handle exception
        } finally {
            if (mNfcB != null) {
                try {
                    mNfcB.close();
                } catch (IOException e) {
                    Log.v("tag", "error closing the tag");
                }
            }
        }
    }


    private void nfcA(Tag tag){
        mNfc = NfcA.get(tag);
        try {
            mNfc.connect();
            mNfc.setTimeout(10000);
            Log.v("tag", "connected.");
            byte[] id = mNfc.getTag().getId();
            Log.v("tag", "Got id from tag:" + id);
            mHexId.setText(getHex(id));
            mDecId.setText(getDec(id));
            mTransBytes.setText("" + mNfc.getMaxTransceiveLength());

            byte[] response = mNfc.transceive(NATIVE_SELECT_COMMAND);
            displayText("Select App", getHex(response));
            authenticate();
            String read = readCommand(NfcA.class.getName());
            displayText("Read",read);

        } catch (IOException e) {
            // TODO: handle exception
        } finally {
            if (mNfc != null) {
                try {
                    mNfc.close();
                } catch (IOException e) {
                    Log.v("tag", "error closing the tag");
                }
            }
        }
    }

    private String readCommand(String tech) {
        byte fileNo = (byte) 0x01;
        byte[] offset = new byte[] { (byte) 0x00, (byte) 0x00, (byte) 0x00 };
        byte[] length = new byte[] { (byte) 0x00, (byte) 0x00, (byte) 0x00 };
        byte[] message = new byte[8];
        message[0] = READ_DATA_COMMAND;
        message[1] = fileNo;

        System.arraycopy(offset, 0, message, 2, 3);
        System.arraycopy(length, 0, message, 2, 3);

        byte[] response = new byte[0];
        try {
            if(tech.equals(NfcB.class.getName())) {

                mNfcB.transceive(READ_DATA_COMMAND_B);
                response = mNfcB.transceive(READ_DATA_COMMAND_B);
            }else if(tech.equals(NfcA.class.getName())) {
                response = mNfc.transceive(message);
            }
            Toast.makeText(this, "Response Length = " + response.length, Toast.LENGTH_LONG).show();
            return getHex(response);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return "Read failed";
    }

    private void authenticate() {
        // TODO Auto-generated method stub
        byte[] rndB = new byte[8];
        byte[] response;
        try {
            response = mNfc.transceive(NATIVE_AUTHENTICATION_COMMAND);
            System.arraycopy(response, 1, rndB, 0, 8);

            byte[] command = new byte[17];

            System.arraycopy(DES.gen_sessionKey(rndB), 0, command, 1, 16);
            command[0] = (byte) 0xAF;

            response = mNfc.transceive(command);
            displayText("Authentication Status",getHex(response));
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

    }

    private void authenticateB() {
        // TODO Auto-generated method stub
        byte[] rndB = new byte[8];
        byte[] response;
        try {
            response = mNfcB.transceive(NATIVE_AUTHENTICATION_COMMAND);
            System.arraycopy(response, 1, rndB, 0, 8);

            byte[] command = new byte[17];

            System.arraycopy(DES.gen_sessionKey(rndB), 0, command, 1, 16);
            command[0] = (byte) 0xAF;

            response = mNfcB.transceive(command);
            displayText("Authentication Status",getHex(response));
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

    }

    private String getDec(byte[] bytes) {
        long result = 0;
        long factor = 1;
        for (int i = 0; i < bytes.length; ++i) {
            long value = bytes[i] & 0xffl;
            result += value * factor;
            factor *= 256l;
        }
        return result + "";
    }

    private String getHex(byte[] bytes) {
        Log.v("tag", "Getting hex");
        StringBuilder sb = new StringBuilder();
        for (int i = bytes.length - 1; i >= 0; --i) {
            int b = bytes[i] & 0xff;
            if (b < 0x10)
                sb.append('0');
            sb.append(Integer.toHexString(b));
            if (i > 0) {
                sb.append(" ");
            }
        }
        return sb.toString();
    }

    private void displayText(String label, String text){
        if (mStringBuilder == null){
            mStringBuilder = new StringBuilder();
        }
        if (label != null){
            mStringBuilder.append(label);
            mStringBuilder.append(":");
        }
        mStringBuilder.append(text);
        mStringBuilder.append("\n");

        mInfo.setText(mStringBuilder.toString());
    }

    @Override
    public void onPause() {
        super.onPause();
        mAdapter.disableForegroundDispatch(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        mAdapter.enableForegroundDispatch(this, pendingIntent,
                intentFiltersArray, techListsArray);
    }

    @Override
    public void onNewIntent(Intent intent) {
        Log.v("tag", "In onNewIntent");
        processIntent(intent);
        super.onNewIntent(intent);
    }

}
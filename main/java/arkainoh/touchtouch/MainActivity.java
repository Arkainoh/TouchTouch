package arkainoh.touchtouch;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.IBinder;
import android.os.Bundle;
import android.os.Vibrator;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.widget.Toast;


import android.widget.EditText;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.text.InputType;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.UUID;
import android.widget.ArrayAdapter;

public class MainActivity extends Activity implements BluetoothAdapter.LeScanCallback {
    // State machine
    final private static int STATE_BLUETOOTH_OFF = 1;
    final private static int STATE_DISCONNECTED = 2;
    final private static int STATE_CONNECTING = 3;
    final private static int STATE_CONNECTED = 4;
    final private static int SHORT_TOUCH = 1;
    final private static int MULTIPLE_TOUCH = 2;
    final private static int LONG_TOUCH = 3;
    int SCORE_SHORT_TOUCH = 100;
    int SCORE_MULTIPLE_TOUCH = 200;
    int SCORE_LONG_TOUCH = 500;

    private int state;

    private boolean scanStarted;
    private boolean scanning;

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothDevice bluetoothDevice;

    private RFduinoService rfduinoService;

    private Button enableBluetoothButton;
    //private TextView scanStatusText;
    private Button scanButton;
    private TextView deviceInfoText;
    private TextView connectionStatusText;
    private Button connectButton;
    private EditData valueEdit;
    private Button sendZeroButton;
    private Button sendValueButton;

    private TextView mScore; //현재 점수를 표시
    private int currentScore; //현재 점수
    private SoundPool sound; //음악을 재생하기 위한 인스턴스
    private int currentSound1; //터치 이벤트가 발생했을 때 재생할 음악의 id
    private int currentSound2;
    private int currentSound3;
    private TextView mLeftButton;
    private TextView mCenterButton;
    private TextView mRightButton;
    private Vibrator mVibrator;

    private final BroadcastReceiver bluetoothStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, 0);
            if (state == BluetoothAdapter.STATE_ON) {
                upgradeState(STATE_DISCONNECTED);
            } else if (state == BluetoothAdapter.STATE_OFF) {
                downgradeState(STATE_BLUETOOTH_OFF);
            }
        }
    };

    private final BroadcastReceiver scanModeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            scanning = (bluetoothAdapter.getScanMode() != BluetoothAdapter.SCAN_MODE_NONE);
            scanStarted &= scanning;
            updateUi();
        }
    };

    private final ServiceConnection rfduinoServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            rfduinoService = ((RFduinoService.LocalBinder) service).getService();
            if (rfduinoService.initialize()) {
                if (rfduinoService.connect(bluetoothDevice.getAddress())) {
                    upgradeState(STATE_CONNECTING);
                }
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            rfduinoService = null;
            downgradeState(STATE_DISCONNECTED);
        }
    };

    private final BroadcastReceiver rfduinoReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (RFduinoService.ACTION_CONNECTED.equals(action)) {
                upgradeState(STATE_CONNECTED);
            } else if (RFduinoService.ACTION_DISCONNECTED.equals(action)) {
                downgradeState(STATE_DISCONNECTED);
            } else if (RFduinoService.ACTION_DATA_AVAILABLE.equals(action)) {
                addData(intent.getByteArrayExtra(RFduinoService.EXTRA_DATA));
            }
        }
    };

    private void setupCustomValues() {
        mScore = (TextView) findViewById(R.id.score); //arkainoh
        currentScore = 0; //arkainoh
        sound = new SoundPool(1, AudioManager.STREAM_MUSIC,0); //arkainoh
        currentSound1 = sound.load(getBaseContext(), R.raw.duck, 1); //arkainoh
        currentSound2 = sound.load(getBaseContext(), R.raw.baby, 1); //arkainoh
        currentSound3 = sound.load(getBaseContext(), R.raw.chicken, 1); //arkainoh

        //소리가 나지 않는다면 디바이스 설정 -> 소리 -> 진동 세기 -> 알림 부분을 최대로 해본다.
        mVibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE); //arkainoh
        mLeftButton = (TextView) findViewById(R.id.button_left);
        mCenterButton = (TextView) findViewById(R.id.button_center);
        mRightButton = (TextView) findViewById(R.id.button_right);


        mScore.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                currentScore = 0;
                mScore.setText(""+currentScore);
            }
        });

        mLeftButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                //todo
                //Intent i = new Intent (MainActivity.this, SelectSound.class);
                //startActivityForResult(i, SHORT_TOUCH);
                setSoundFromUser(SHORT_TOUCH);
            }
        });
        mCenterButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                //todo
                //Intent i = new Intent (MainActivity.this, SelectSound.class);
                //startActivityForResult(i, MULTIPLE_TOUCH);
                setSoundFromUser(MULTIPLE_TOUCH);
            }
        });
        mRightButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                //todo
                //Intent i = new Intent (MainActivity.this, SelectSound.class);
                //startActivityForResult(i, LONG_TOUCH);
                setSoundFromUser(LONG_TOUCH);
            }
        });
    }

    public int getResourceId(String pVariableName, String pResourcename, String pPackageName)
    {
        try {
            return getResources().getIdentifier(pVariableName, pResourcename, pPackageName);
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
    }
    /*
    public void onActivityResult(int requestCode, int resultCode, Intent data) { //소리 선택!
        super.onActivityResult(requestCode,resultCode,data);
        switch(requestCode) {
            case 1:
                if(resultCode == Activity.RESULT_OK) {
                    String returnString = data.getStringExtra("sound_title");
                    currentSound1 = sound.load(getBaseContext(), getResourceId(returnString, "raw", getPackageName()), 1);
                }
            break;
            case 2:
                if(resultCode == Activity.RESULT_OK) {
                    String returnString = data.getStringExtra("sound_title");
                    currentSound2 = sound.load(getBaseContext(), getResourceId(returnString, "raw", getPackageName()), 1);
                }
                break;
            case 3:
                if(resultCode == Activity.RESULT_OK) {
                    String returnString = data.getStringExtra("sound_title");
                    currentSound3 = sound.load(getBaseContext(), getResourceId(returnString, "raw", getPackageName()), 1);
                }
                break;
        }
    }
*/
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setupCustomValues();

        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();

        // Bluetooth
        enableBluetoothButton = (Button) findViewById(R.id.enableBluetooth);
        enableBluetoothButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                enableBluetoothButton.setEnabled(false);
                enableBluetoothButton.setText(
                        bluetoothAdapter.enable() ? "Enabling bluetooth..." : "Enable failed!");
            }
        });

        // Find Device
        //scanStatusText = (TextView) findViewById(R.id.scanStatus);

        scanButton = (Button) findViewById(R.id.scan);
        scanButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (state <= STATE_BLUETOOTH_OFF) {
                    Toast.makeText(getApplicationContext(),
                            "Bluetooth is disabled",
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                scanStarted = true;
                bluetoothAdapter.startLeScan(
                        new UUID[]{ RFduinoService.UUID_SERVICE },
                        MainActivity.this);
            }
        });

        // Device Info
        deviceInfoText = (TextView) findViewById(R.id.deviceInfo);

        // Connect Device
        connectionStatusText = (TextView) findViewById(R.id.connectionStatus);

        connectButton = (Button) findViewById(R.id.connect);
        connectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.setEnabled(false);
                //connectionStatusText.setText("Connecting...");
                connectButton.setText("Connecting...");
                Intent rfduinoIntent = new Intent(MainActivity.this, RFduinoService.class);
                bindService(rfduinoIntent, rfduinoServiceConnection, BIND_AUTO_CREATE);
            }
        });

        // Send
        valueEdit = (EditData) findViewById(R.id.value);
        valueEdit.setImeOptions(EditorInfo.IME_ACTION_SEND);
        valueEdit.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEND) {
                    sendValueButton.callOnClick();
                    return true;
                }
                return false;
            }
        });

        sendZeroButton = (Button) findViewById(R.id.sendZero);
        sendZeroButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                rfduinoService.send(new byte[]{0});
            }
        });

        sendValueButton = (Button) findViewById(R.id.sendValue);
        sendValueButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                rfduinoService.send(valueEdit.getData());
            }
        });

        //숨김
        valueEdit.setVisibility(View.GONE);
        sendZeroButton.setVisibility(View.GONE);
        sendValueButton.setVisibility(View.GONE);
        enableBluetoothButton.setVisibility(View.GONE);
        scanButton.setVisibility(View.GONE);
    }

    @Override
    protected void onStart() {
        super.onStart();

        registerReceiver(scanModeReceiver, new IntentFilter(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED));
        registerReceiver(bluetoothStateReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
        registerReceiver(rfduinoReceiver, RFduinoService.getIntentFilter());

        updateState(bluetoothAdapter.isEnabled() ? STATE_DISCONNECTED : STATE_BLUETOOTH_OFF);
    }

    @Override
    protected void onStop() {
        super.onStop();

        bluetoothAdapter.stopLeScan(this);

        unregisterReceiver(scanModeReceiver);
        unregisterReceiver(bluetoothStateReceiver);
        unregisterReceiver(rfduinoReceiver);
    }

    private void upgradeState(int newState) {
        if (newState > state) {
            updateState(newState);
        }
    }

    private void downgradeState(int newState) {
        if (newState < state) {
            updateState(newState);
        }
    }

    private void updateState(int newState) {
        state = newState;
        updateUi();
    }

    private void updateUi() {
        // Enable Bluetooth
        boolean on = state > STATE_BLUETOOTH_OFF;
        enableBluetoothButton.setEnabled(!on);
        enableBluetoothButton.setText(on ? "Bluetooth enabled" : "Enable Bluetooth");
        scanButton.setEnabled(on);

        /*
        // Scan
        if (scanStarted && scanning) {
            //scanStatusText.setText("Scanning...");
            scanButton.setText("Stop Scan");
            scanButton.setEnabled(true);
        } else if (scanStarted) {
            //scanStatusText.setText("Scan started...");
            scanButton.setEnabled(false);
        } else {
            //scanStatusText.setText("");
            scanButton.setText("Scan");
            scanButton.setEnabled(true);
        }
        */
        // Connect
        boolean connected = false;
        String connectionText = "Disconnected";
        if (state == STATE_CONNECTING) {
            connectionText = "Connecting...";
        } else if (state == STATE_CONNECTED) {
            connected = true;
            connectionText = "Connected";
        }
        //connectionStatusText.setText(connectionText);
        connectButton.setText(connectionText);
        connectButton.setEnabled(bluetoothDevice != null && state == STATE_DISCONNECTED);

        // Send
        sendZeroButton.setEnabled(connected);
        sendValueButton.setEnabled(connected);
    }

    private void addData(byte[] data) { // Arkainoh: 실제 데이터 받아서 화면에 띄우기

        // 체온구하기
        float f = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).getFloat();
        if (f == SHORT_TOUCH) {
            currentScore += SCORE_SHORT_TOUCH;
            mScore.setText(""+currentScore);
            mVibrator.vibrate(300);
            sound.play(currentSound1, 1.0F,1.0F,1,0,1.0F);
        } else if (f == MULTIPLE_TOUCH) {

            currentScore += SCORE_MULTIPLE_TOUCH;
            mScore.setText(""+currentScore);
            mVibrator.vibrate(300);
            sound.play(currentSound2, 1.0F,1.0F,1,0,1.0F);

        } else if (f == LONG_TOUCH) {
            currentScore += SCORE_LONG_TOUCH;
            mScore.setText(""+currentScore);
            mVibrator.vibrate(300);
            sound.play(currentSound3, 1.0F,1.0F,1,0,1.0F);
        }

    }

    @Override
    public void onLeScan(BluetoothDevice device, final int rssi, final byte[] scanRecord) {
        bluetoothAdapter.stopLeScan(this);
        bluetoothDevice = device;

        MainActivity.this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                deviceInfoText.setText(
                        BluetoothHelper.getDeviceInfoText(bluetoothDevice, rssi, scanRecord)); //스캔 결과
                updateUi();
            }
        });
    }

    private void setSoundFromUser(final int touch_type) {

        AlertDialog.Builder alertBuilder = new AlertDialog.Builder(this);
        alertBuilder.setIcon(R.drawable.touchtouchlogo);
        switch(touch_type) {
            case SHORT_TOUCH:
                alertBuilder.setTitle("Basic Touch Sound");
                break;
            case MULTIPLE_TOUCH:
                alertBuilder.setTitle("Multiple Touch Sound");
                break;
            case LONG_TOUCH:
                alertBuilder.setTitle("Long Touch Sound");
                break;
        }

        // List Adapter 생성
        final ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.select_dialog_item);

        Field[] fields=R.raw.class.getFields();
        for(int count=0; count < fields.length; count++) {
            final String soundTitle = fields[count].getName();
            if (soundTitle.equals("$change") || soundTitle.equals("serialVersionUID")) continue;
            adapter.add(soundTitle);
            switch(touch_type) {
                case SHORT_TOUCH:

                    break;
                case MULTIPLE_TOUCH:

                    break;
                case LONG_TOUCH:

                    break;
            }
        }

        alertBuilder.setNegativeButton("Cancel",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog,
                                        int which) {
                        dialog.dismiss();
                    }
                });

        // Adapter 셋팅
        alertBuilder.setAdapter(adapter,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog,
                                        int id) {
                        // AlertDialog 안에 있는 AlertDialog
                        String strName = adapter.getItem(id);
                        switch(touch_type) {
                            case SHORT_TOUCH:
                                currentSound1 = sound.load(getBaseContext(), getResourceId(strName, "raw", getPackageName()), 1);
                                break;
                            case MULTIPLE_TOUCH:
                                currentSound2 = sound.load(getBaseContext(), getResourceId(strName, "raw", getPackageName()), 1);
                                break;
                            case LONG_TOUCH:
                                currentSound3 = sound.load(getBaseContext(), getResourceId(strName, "raw", getPackageName()), 1);
                                break;
                        }
                    }
                });
        alertBuilder.show();

    }


    private void setScoresFromUser(final int touch_type) {

        String title = "";
        switch(touch_type) {
            case SHORT_TOUCH:
                title = "Basic Touch";
                break;
            case MULTIPLE_TOUCH:
                title = "Multiple Touch";
                break;
            case LONG_TOUCH:
                title = "Long Touch";
                break;
        }

        AlertDialog.Builder alert = new AlertDialog.Builder(this);
        alert.setTitle(title);
        alert.setMessage("Score per touch");
        // Set an EditText view to get user input
        final EditText input = new EditText(this);
        alert.setView(input);
        input.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_SIGNED);

        alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                //EditText내용을 가져오기
                String value = input.getText().toString().trim();
                if(value.getBytes().length <= 0) return;
                int result = Integer.parseInt(value);;
                // Do something with value!
                switch(touch_type) {
                    case SHORT_TOUCH:
                        SCORE_SHORT_TOUCH = result;
                        Toast.makeText(getApplicationContext(),
                                "Score of Basic Touch is set to "+ result,
                                Toast.LENGTH_SHORT).show();
                        break;
                    case MULTIPLE_TOUCH:
                        SCORE_MULTIPLE_TOUCH = result;
                        Toast.makeText(getApplicationContext(),
                                "Score of Multiple Touch is set to "+ result,
                                Toast.LENGTH_SHORT).show();
                        break;
                    case LONG_TOUCH:
                        SCORE_LONG_TOUCH = result;
                        Toast.makeText(getApplicationContext(),
                                "Score of Long Touch is set to "+ result,
                                Toast.LENGTH_SHORT).show();
                        break;
                }
            }
        });
        alert.setNegativeButton("Cancel",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        // Canceled.
                    }
                });
        alert.show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        if (id == R.id.scan) {
            if (state <= STATE_BLUETOOTH_OFF) {
                Toast.makeText(getApplicationContext(),
                        "Bluetooth is disabled...",
                        Toast.LENGTH_SHORT).show();
                return false;
            }
            scanStarted = true;
            bluetoothAdapter.startLeScan(
                    new UUID[]{ RFduinoService.UUID_SERVICE },
                    MainActivity.this);
            Toast.makeText(getApplicationContext(),
                    "Scanning...",
                    Toast.LENGTH_SHORT).show();

                return true;
        }
        //noinspection SimplifiableIfStatement
        if (id == R.id.touch_short) {
            setScoresFromUser(SHORT_TOUCH);
            return true;
        } else if (id == R.id.touch_twice) {
            setScoresFromUser(MULTIPLE_TOUCH);
            return true;
        } else if (id == R.id.touch_long) {
            setScoresFromUser(LONG_TOUCH);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}

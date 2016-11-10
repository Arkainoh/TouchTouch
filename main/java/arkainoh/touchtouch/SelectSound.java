package arkainoh.touchtouch;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.lang.reflect.Field;

/**
 * Created by arkai on 2016-11-09.
 */

public class SelectSound extends Activity {

    private LinearLayout soundlist;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.selectsound);

        soundlist = (LinearLayout) findViewById(R.id.soundlist);
        soundlist.removeAllViews();

        Field[] fields=R.raw.class.getFields();
        for(int count=0; count < fields.length; count++){
            final String soundtitle = fields[count].getName();
            if(soundtitle.equals("$change") || soundtitle.equals("serialVersionUID")) continue;

            View view = getLayoutInflater().inflate(android.R.layout.simple_list_item_2, soundlist, false);
            TextView text1 = (TextView) view.findViewById(android.R.id.text1);
            text1.setText(soundtitle);

            text1.setOnClickListener(new View.OnClickListener(){
                @Override
                public void onClick(View v) {
                    Uri data = Uri.parse("content://touchtouch/selectsound");
                    Intent result = new Intent(null,data);
                    result.putExtra("sound_title", soundtitle);
                    setResult(RESULT_OK, result);
                    finish();
                }
            });

            soundlist.addView(view, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);



        } // raw에 있는 음악파일들의 목록을 가져옴



        // 체온구하기






        //


    }
}

package com.vgaw.bugcat;

import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        BugCat.getInstance().initial(MainActivity.this);

        TextView tv_show = (TextView) findViewById(R.id.tv_show);
        File path = getExternalFilesDir(Environment.DIRECTORY_MUSIC);
        File path1 = getExternalCacheDir();
        tv_show.setText(path.getAbsolutePath() + "\n" + path1.getAbsolutePath() + "\n" + getExternalFilesDir(null).getAbsolutePath() + "\n");

        Button btn_bug = (Button) findViewById(R.id.btn_bug);
        btn_bug.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //BugCat.getInstance().deliverBug(String.valueOf(System.currentTimeMillis()));
                Log.e("fuck", String.valueOf(1/0));
            }
        });
    }

    @Override
    protected void onDestroy() {
        BugCat.getInstance().release();
        super.onDestroy();
    }
}

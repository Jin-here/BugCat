package com.vgaw.bugcat;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import java.io.IOException;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        BugCat.getInstance().initial(MainActivity.this);

        Button btn_bug = (Button) findViewById(R.id.btn_bug);
        btn_bug.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                BugCat.getInstance().deliverBug(String.valueOf(System.currentTimeMillis()));
            }
        });
    }
}

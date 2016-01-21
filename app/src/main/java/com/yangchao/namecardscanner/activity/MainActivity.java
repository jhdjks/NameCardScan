package com.yangchao.namecardscanner.activity;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;

import com.yangchao.namecardscanner.R;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //拍照模式
        findViewById(R.id.startScanner1).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //        拍照模式扫描
                NameCardScannerActivity.startActivity(MainActivity.this, NameCardScannerActivity.SCAN_MODE_PICTURE);
            }
        });
        //预览模式
        findViewById(R.id.startScanner2).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //        预览模式扫描
                NameCardScannerActivity.startActivity(MainActivity.this, NameCardScannerActivity.SCAN_MODE_PREVIEW);
            }
        });

    }

}

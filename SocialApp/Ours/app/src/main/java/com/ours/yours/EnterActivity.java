package com.ours.yours;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.orhanobut.logger.AndroidLogAdapter;
import com.orhanobut.logger.FormatStrategy;
import com.orhanobut.logger.Logger;
import com.orhanobut.logger.PrettyFormatStrategy;
import com.ours.yours.app.MainActivity;

public class EnterActivity extends AppCompatActivity implements View.OnClickListener {
    private Button btnLogin;
    private FirebaseAuth mFirebaseAuth;
    private FirebaseUser mFirebaseUser;
    private String mFirebaseUserPhotoURL;
    private String mFirebaseUserName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        FormatStrategy formatStrategy = PrettyFormatStrategy.newBuilder()
                .showThreadInfo(false)  // (Optional) Whether to show thread info or not. Default true
                .methodCount(1)         // (Optional) How many method line to show. Default 2
                .methodOffset(0)        // (Optional) Skips some method invokes in stack trace. Default 5
                .tag("OURS")           // (Optional) Custom tag for each log. Default PRETTY_LOGGER
                .build();
        Logger.addLogAdapter(new AndroidLogAdapter(formatStrategy));
        initView();
        /**
         * Initialize Firebase Auth
         */
        mFirebaseAuth = FirebaseAuth.getInstance();
        mFirebaseUser = mFirebaseAuth.getCurrentUser();

        if (mFirebaseUser == null) {
            Logger.d("... mFirebaseUser is null");
            startActivity(new Intent(this, SignInActivity.class));
            finish();
        } else {
            Logger.d("... mFirebaseUser is not null");
            mFirebaseUserName = mFirebaseUser.getDisplayName();
            if (mFirebaseUser.getPhotoUrl() != null) {
                mFirebaseUserPhotoURL =  mFirebaseUser.getPhotoUrl().toString();
            }
            startActivity(new Intent(this, MainActivity.class));
            finish();
        }
    }

    private void initView() {
        btnLogin = findViewById(R.id.btn_login);
        btnLogin.setOnClickListener(this);
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

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onPause() {
        Logger.d(">>>");
        super.onPause();
    }

    @Override
    protected void onStop() {
        Logger.d(">>>");
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        Logger.d(">>>");
        super.onDestroy();

    }

    @Override
    protected void onResume() {
        Logger.d(">>>");
        super.onResume();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()){
            case R.id.btn_login:
                break;
            default:
                break;
        }
    }
}

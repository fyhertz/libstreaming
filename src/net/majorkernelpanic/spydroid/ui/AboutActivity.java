package net.majorkernelpanic.spydroid.ui;

import net.majorkernelpanic.spydroid.R;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

public class AboutActivity extends Activity {

	private Button buttonVisit;
	private Button buttonRate;
	private Button buttonLike;
	
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        setContentView(R.layout.about);

        buttonVisit = (Button)findViewById(R.id.visit);
        buttonRate = (Button)findViewById(R.id.rate);
        buttonLike = (Button)findViewById(R.id.like);
        
        buttonVisit.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				Intent intent = new Intent(Intent.ACTION_VIEW,Uri.parse("https://code.google.com/p/spydroid-ipcamera/"));
				intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY | Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
				startActivity(intent);
			}
		});
        
        buttonRate.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				String appPackageName=getApplicationContext().getPackageName();
				Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id="+appPackageName));
				intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY | Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
				startActivity(intent);
			}
		});
        
        buttonLike.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://www.facebook.com/spydroidipcamera"));
				intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY | Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
				startActivity(intent);
			}
		});        
        
    }
    
    public void onStart() {
    	super.onStart();
    }
    
    public void onStop() {
    	super.onStop();
    }
	
}
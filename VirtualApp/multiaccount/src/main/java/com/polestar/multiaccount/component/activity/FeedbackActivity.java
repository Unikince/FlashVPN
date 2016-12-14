package com.polestar.multiaccount.component.activity;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.polestar.multiaccount.R;
import com.polestar.multiaccount.component.BaseActivity;
import com.polestar.multiaccount.constant.AppConstants;
import com.polestar.multiaccount.utils.Logs;
import com.polestar.multiaccount.utils.ToastUtils;

public class FeedbackActivity extends BaseActivity {

    private Context mContext;
    private EditText mEtFeedback;
    private Button mBtSubmit;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_feedback);
        mContext = this;
        initView();
    }

    private void initView() {
        setTitle(AppConstants.TITLE_FEEDBACK);

        mEtFeedback = (EditText) findViewById(R.id.et_feedback);
        mBtSubmit = (Button) findViewById(R.id.bt_submit);
        mBtSubmit.setEnabled(false);

        mEtFeedback.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                if (s.toString().length() > 0) {
                    mBtSubmit.setEnabled(true);
                } else {
                    mBtSubmit.setEnabled(false);
                }
            }
        });

        mBtSubmit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String content = mEtFeedback.getText().toString();
                if (content == null) {
                    ToastUtils.ToastDefult(mContext, getResources().getString(R.string.feedback_no_description));
                    return;
                }

                Intent data=new Intent(Intent.ACTION_SENDTO);
                data.setData(Uri.parse("mailto:polestar.applab@gmail.com"));
                data.putExtra(Intent.EXTRA_SUBJECT, "Feedback about SuperClone");
                data.putExtra(Intent.EXTRA_TEXT, content);
                try {
                    startActivity(data);
                }catch (Exception e) {
                    Logs.e("Start email activity fail!");
                    ToastUtils.ToastDefult(FeedbackActivity.this, getResources().getString(R.string.submit_success));
                }
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}

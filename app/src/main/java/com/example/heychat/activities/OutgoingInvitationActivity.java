package com.example.heychat.activities;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.Base64;
import android.util.Log;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.example.heychat.R;
import com.example.heychat.models.User;
import com.example.heychat.network.ApiClient;
import com.example.heychat.network.ApiService;
import com.example.heychat.service.SinchService;
import com.example.heychat.ultilities.Constants;
import com.example.heychat.ultilities.PreferenceManager;
import com.google.firebase.iid.FirebaseInstanceId;
import com.sinch.android.rtc.PushPair;
import com.sinch.android.rtc.SinchError;
import com.sinch.android.rtc.calling.CallEndCause;
import com.sinch.android.rtc.video.VideoCallListener;


import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;
import java.util.UUID;

import de.hdodenhof.circleimageview.CircleImageView;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class OutgoingInvitationActivity extends BaseSinchActivity implements SinchService.StartFailedListener {

    private PreferenceManager preferenceManager;
    private String inviterToken = null;
    private String meetingRoom = null;
    private String meetingType = null;
    private int currentProgress = 0;
    private ProgressBar progressBar;
    private CountDownTimer countDownTimer;
    private User receiver;
    private String callId;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_outgoing_invitation);

        preferenceManager = new PreferenceManager(getApplicationContext());

        ImageView imageMeetingType = findViewById(R.id.imageMeetingTypeout);
        meetingType = getIntent().getStringExtra("type");

        if (meetingType != null) {
            if (meetingType.equals("video")) {
                imageMeetingType.setImageResource(R.drawable.ic_video_call);
            } else {
                imageMeetingType.setImageResource(R.drawable.ic_call);
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA, Manifest.permission.ACCESS_NETWORK_STATE, Manifest.permission.READ_PHONE_STATE}, 100);
        }

        progressBar = findViewById(R.id.call_duration);

        setInfo();
        countDownTimer = new CountDownTimer(30 * 1000, 50) {
            @Override
            public void onTick(long l) {
                progressBar.setMax(30000 * 4);
                if (currentProgress == progressBar.getMax()){
                    cancelInvitation(receiver.token);
                }

                currentProgress += 200;
                progressBar.setProgress(currentProgress);

            }

            @Override
            public void onFinish() {
                if (receiver != null) {
                    currentProgress = 0;
                }
            }
        };
        countDownTimer.start();
    }

    private void setInfo(){
        CircleImageView textFirstChar = findViewById(R.id.textFirstChar);
        TextView textUsername = findViewById(R.id.textUsername);
        TextView textEmail = findViewById(R.id.outgoingtextEmail);

        receiver = (User) getIntent().getSerializableExtra("user");
        if (receiver != null) {
            textFirstChar.setImageBitmap(getUserImage(receiver.image));
            textUsername.setText(receiver.name);
            textEmail.setText(receiver.email);
        }

        ImageView imageStopInvitation = findViewById(R.id.imageStopInvatation);
        imageStopInvitation.setOnClickListener(v -> {
            if (receiver != null) {
                cancelInvitation(receiver.token);
            }
        });
    }

    private void initiateMeeting(String meetingType, String receiverToken) {
        try {
            JSONArray tokens = new JSONArray();
            tokens.put(receiverToken);
            JSONObject body = new JSONObject();
            JSONObject data = new JSONObject();

            data.put(Constants.REMOTE_MSG_TYPE, Constants.REMOTE_MSG_INVITATION);
            data.put(Constants.REMOTE_MSG_MEETING_TYPE, meetingType);
//            data.put(Constants.KEY_IMAGE, (preferenceManager.getString(Constants.KEY_IMAGE)));
            data.put(Constants.KEY_NAME, preferenceManager.getString(Constants.KEY_NAME));
            data.put(Constants.KEY_EMAIL, preferenceManager.getString(Constants.KEY_EMAIL));
            data.put(Constants.REMOTE_MSG_INVITER_TOKEN, inviterToken);

            meetingRoom = preferenceManager.getString(Constants.KEY_USER_ID) + "_" +
                    UUID.randomUUID().toString().substring(0, 5);
            data.put(Constants.REMOTE_MSG_MEETING_ROOM, meetingRoom);
            data.put(SinchService.CALL_ID, callId);

            body.put(Constants.REMOTE_MSG_DATA, data);
            body.put(Constants.REMOTE_MSG_REGISTRATION_IDS, tokens);

            sendRemoteMessage(body.toString(), Constants.REMOTE_MSG_INVITATION);

        } catch (Exception exception) {
            Toast.makeText(this, exception.getMessage(), Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void sendRemoteMessage(String remoteMesageBody, String type) {
        ApiClient.getClient().create(ApiService.class).sendMessage(Constants.getRemoteMsgHeaders(), remoteMesageBody)
                .enqueue(new Callback<String>() {
                    @Override
                    public void onResponse(@NonNull Call<String> call, @NonNull Response<String> response) {
                        if (response.isSuccessful()) {
                            if (type.equals(Constants.REMOTE_MSG_INVITATION)) {
                                Toast.makeText(OutgoingInvitationActivity.this, "Invitation send successful", Toast.LENGTH_SHORT).show();
                            } else if (type.equals(Constants.REMOTE_MSG_INVITATION_RESPONSE)) {
                                Toast.makeText(OutgoingInvitationActivity.this, "Invitation Cancelled", Toast.LENGTH_SHORT).show();
                                getSinchServiceInterface().stopClient();
                                finish();
                            }
                        } else {
                            Toast.makeText(OutgoingInvitationActivity.this, response.message(), Toast.LENGTH_SHORT).show();
                            finish();
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<String> call, @NonNull Throwable t) {
                        Toast.makeText(OutgoingInvitationActivity.this, t.getMessage(), Toast.LENGTH_SHORT).show();
                        finish();
                    }
                });
    }

    private void cancelInvitation(String receiverToken) {
        try {
            JSONArray tokens = new JSONArray();
            tokens.put(receiverToken);

            JSONObject body = new JSONObject();
            JSONObject data = new JSONObject();

            data.put(Constants.REMOTE_MSG_TYPE, Constants.REMOTE_MSG_INVITATION_RESPONSE);
            data.put(Constants.REMOTE_MSG_INVITATION_RESPONSE, Constants.REMOTE_MSG_INVITATION_CANCELLED);

            body.put(Constants.REMOTE_MSG_DATA, data);
            body.put(Constants.REMOTE_MSG_REGISTRATION_IDS, tokens);

            sendRemoteMessage(body.toString(), Constants.REMOTE_MSG_INVITATION_RESPONSE);

        } catch (Exception exception) {
            Toast.makeText(OutgoingInvitationActivity.this, exception.getMessage(), Toast.LENGTH_SHORT).show();
            finish();
        }

    }

    private BroadcastReceiver invitationResponseReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String type = intent.getStringExtra(Constants.REMOTE_MSG_INVITATION_RESPONSE);
            if (type != null) {
                if (type.trim().equals(Constants.REMOTE_MSG_INVITATION_ACCEPTED)) {
                    try {
//                        URL serverURL = new URL("https://meet.jit.si");
//
//                        JitsiMeetConferenceOptions.Builder builder = new JitsiMeetConferenceOptions.Builder();
//                        builder.setServerURL(serverURL);
//                        builder.setRoom(meetingRoom);
//
//                        if(meetingType.equals("audio")){
//                            builder.setAudioOnly(true);
//                        }
//
//                        JitsiMeetActivity.launch(OutgoingInvitationActivity.this, builder.build());

//
                        Intent callScreen = new Intent(OutgoingInvitationActivity.this, VideoCallActivity.class);
                        callScreen.putExtra(SinchService.CALL_ID, callId);
                        callScreen.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(callScreen);
                        Toast.makeText(context, "Accepted "+ callId, Toast.LENGTH_SHORT).show();
                        finish();
                    } catch (Exception exception) {
                        Toast.makeText(context, exception.getMessage(), Toast.LENGTH_SHORT).show();
                        finish();
                    }
                } else if (type.trim().equals(Constants.REMOTE_MSG_INVITATION_REJECTED)) {
                    Toast.makeText(context, "Invitation Rejected", Toast.LENGTH_SHORT).show();
                    getSinchServiceInterface().stopClient();
                    finish();
                }
            }
        }
    };

    @Override
    protected void onStart() {
        super.onStart();
        LocalBroadcastManager.getInstance(getApplicationContext()).registerReceiver(
                invitationResponseReceiver,
                new IntentFilter(Constants.REMOTE_MSG_INVITATION_RESPONSE)
        );

    }

    @Override
    protected void onStop() {
        super.onStop();
        LocalBroadcastManager.getInstance(getApplicationContext()).unregisterReceiver(
                invitationResponseReceiver
        );


    }

    private Bitmap getUserImage(String encodedImage) {
        byte[] bytes = Base64.decode(encodedImage, Base64.DEFAULT);
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
    }

    //this method is invoked when the connection is established with the SinchService
    @Override
    protected void onServiceConnected() {
        Log.d("serviceapp", "MainActivity  onServiceConnected");
        getSinchServiceInterface().setStartListener(this);
    }


    @Override
    public void onStartFailed(SinchError error) {
        Log.d("serviceapp", "MainActivity  onStartFailed");
        Toast.makeText(this, error.toString(), Toast.LENGTH_LONG).show();
    }

    @Override
    public void onStarted() {
        com.sinch.android.rtc.calling.Call call = getSinchServiceInterface().callUserVideo(receiver.id);
        callId = call.getCallId();
        call.addCallListener(new SinchCallListener());
        FirebaseInstanceId.getInstance().getInstanceId().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult() != null) {
                inviterToken = task.getResult().getToken();
                if (meetingType != null && receiver != null && callId != null) {
                    initiateMeeting(meetingType, receiver.token);
                }
            }
        });
    }

    private class SinchCallListener implements VideoCallListener {

        @Override
        public void onCallEnded(com.sinch.android.rtc.calling.Call call) {
            CallEndCause cause = call.getDetails().getEndCause();
            Log.d("stopClient", "Call ended outComing, cause: " + cause.toString());
            finish();
        }

        @Override
        public void onCallEstablished(com.sinch.android.rtc.calling.Call call) {
//            Log.d(TAG, "Call established");
        }

        @Override
        public void onCallProgressing(com.sinch.android.rtc.calling.Call call) {
//            Log.d(TAG, "Call progressing");
        }

        @Override
        public void onShouldSendPushNotification(com.sinch.android.rtc.calling.Call call, List<PushPair> pushPairs) {
            // Send a push through your push provider here, e.g. GCM
        }

        @Override
        public void onVideoTrackAdded(com.sinch.android.rtc.calling.Call call) {
            // Display some kind of icon showing it's a video call
        }

        @Override
        public void onVideoTrackPaused(com.sinch.android.rtc.calling.Call call) {

        }

        @Override
        public void onVideoTrackResumed(com.sinch.android.rtc.calling.Call call) {

        }
    }


}
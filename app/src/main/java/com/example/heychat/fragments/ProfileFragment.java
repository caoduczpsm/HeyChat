package com.example.heychat.fragments;

import static android.app.Activity.RESULT_OK;
import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.fragment.app.Fragment;
import com.example.heychat.R;
import com.example.heychat.activities.SignInActivity;
import com.example.heychat.ultilities.Constants;
import com.example.heychat.ultilities.PreferenceManager;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Objects;

import de.hdodenhof.circleimageview.CircleImageView;

public class ProfileFragment extends Fragment {
    private CircleImageView profile_image;
    private Bitmap image;
    private TextView user_name, phone_number;
    private PreferenceManager preferenceManager;
    private String encodedImage;
    private FirebaseFirestore database;
    private String userId;

    public ProfileFragment() {

    }
    @SuppressLint("MissingInflatedId")
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_profile, container, false);

        database = FirebaseFirestore.getInstance();
        profile_image = view.findViewById(R.id.profile_image);
        user_name = view.findViewById(R.id.user_name);
        phone_number = view.findViewById(R.id.phone_number);
        if(getContext() != null){
            preferenceManager = new PreferenceManager(getContext());
        }
        loadUserDetail();

        View log_out_btn = view.findViewById(R.id.log_out_btn);
        View change_language_btn = view.findViewById(R.id.change_language_btn);
        View change_text_size = view.findViewById(R.id.change_text_size);
        View edit_profile = view.findViewById(R.id.edit_profile_btn);
        edit_profile.setOnClickListener(v->editProfile());
        log_out_btn.setOnClickListener(v->logout());
        change_language_btn.setOnClickListener(v-> changeLanguage());
        change_text_size.setOnClickListener(v-> changeTextsize());

        return view;
    }
    CircleImageView change_image;

    private void editProfile() {
        final Dialog dialog = openDialog(R.layout.layout_dialog_edit_profile);
        change_image = dialog.findViewById(R.id.image_edit_profile);
        TextView change_image_tv = dialog.findViewById(R.id.change_image_text_view);
        EditText edit_name = dialog.findViewById(R.id.name_edit_text);
        Button yes_btn = dialog.findViewById(R.id.yes_btn);
        Button no_btn = dialog.findViewById(R.id.no_btn);

        edit_name.setText(user_name.getText());
        change_image.setImageBitmap(image);

        change_image.setOnClickListener(v-> {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            pickImage.launch(intent);
        });

        change_image_tv.setOnClickListener(v->{
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            pickImage.launch(intent);
        });

        yes_btn.setOnClickListener(view -> {
            if(edit_name.getText().toString().trim().isEmpty()){
                showToast("Enter name");
            } else {
                updateProfile(edit_name.getText().toString().trim());
                dialog.dismiss();
            }
        });
        no_btn.setOnClickListener(view -> dialog.dismiss());

        dialog.show();
    }

    private void updateProfile(String name) {
        database.collection(Constants.KEY_COLLECTION_USER).document(userId).update("image", encodedImage);
        database.collection(Constants.KEY_COLLECTION_USER).document(userId).update("name", name);
        preferenceManager.putString(Constants.KEY_NAME, name);
        preferenceManager.putString(Constants.KEY_IMAGE, encodedImage);
        loadUserDetail();
        showToast("Update profile successful");
    }

    private final ActivityResultLauncher pickImage = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK){
                    if(result.getData() != null){
                        Uri imageUri = result.getData().getData();
                        try {
                            InputStream inputStream = getActivity().getContentResolver().openInputStream(imageUri);
                            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                            change_image.setImageBitmap(bitmap);
                            encodedImage = encodeImage(bitmap);
                        } catch (FileNotFoundException e){
                            e.printStackTrace();
                        }
                    }
                }
            }
    );

    private String encodeImage(Bitmap bitmap){
        int previewWidth = 150;
        int previewHeight = bitmap.getHeight() * previewWidth / bitmap.getWidth();
        Bitmap previewBitmap = Bitmap.createScaledBitmap(bitmap, previewWidth, previewHeight, false);
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        previewBitmap.compress(Bitmap.CompressFormat.JPEG, 50, byteArrayOutputStream);
        byte[] bytes = byteArrayOutputStream.toByteArray();
        return Base64.encodeToString(bytes, Base64.DEFAULT);
    }



    private void changeTextsize() {
        final Dialog dialog = openDialog(R.layout.layout_dialog_textsize);
        SeekBar size = dialog.findViewById(R.id.seekBar);
        Button yes_btn = dialog.findViewById(R.id.yes_btn);
        Button no_btn = dialog.findViewById(R.id.no_btn);

        yes_btn.setOnClickListener(view -> showToast(size.getProgress()+""));
        no_btn.setOnClickListener(view -> dialog.dismiss());

        dialog.show();

    }

    private void changeLanguage() {
        final Dialog dialog = openDialog(R.layout.layout_dialog_language);
        Switch vietnamese = dialog.findViewById(R.id.switch_vietnamese);
        Switch english = dialog.findViewById(R.id.switch_english);
        Button yes_btn = dialog.findViewById(R.id.yes_btn);
        Button no_btn = dialog.findViewById(R.id.no_btn);

        if (Objects.equals(preferenceManager.getString(Constants.KEY_LANGUAGE), "VI")){
            vietnamese.setChecked(true);
            english.setChecked(false);
        } else {
            vietnamese.setChecked(false);
            english.setChecked(true);
        }

        vietnamese.setOnCheckedChangeListener((compoundButton, b) -> {
            english.setChecked(!b);
        });

        english.setOnCheckedChangeListener((compoundButton, b) -> {
            vietnamese.setChecked(!b);
        });

        yes_btn.setOnClickListener(view -> {
            if (vietnamese.isChecked()){
                showToast("vietnamese");
                preferenceManager.putString(Constants.KEY_LANGUAGE, "VI");
            } else{
                preferenceManager.putString(Constants.KEY_LANGUAGE, "EN");
                showToast("english");
            }

        });
        no_btn.setOnClickListener(view -> dialog.dismiss());


        dialog.show();

    }

    private void logout(){
        final Dialog dialog = openDialog(R.layout.layout_dialog_logout);
        Button yes_btn = dialog.findViewById(R.id.yes_btn);
        Button no_btn = dialog.findViewById(R.id.no_btn);

        yes_btn.setOnClickListener(v-> signOut());
        no_btn.setOnClickListener(view -> dialog.dismiss());
        dialog.show();
    }

    private Dialog openDialog(int layout) {
        final Dialog dialog = new Dialog(getContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(layout);
        dialog.setCancelable(true);
        Window window = dialog.getWindow();
        if(window == null){
            return null;
        }
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        WindowManager.LayoutParams windowAttributes = window.getAttributes();
        windowAttributes.gravity = Gravity.BOTTOM;
        window.setAttributes(windowAttributes);

        return dialog;
    }


    private void signOut(){
        showToast("Signing out...");

        DocumentReference documentReference = database.collection(Constants.KEY_COLLECTION_USER).document(
                preferenceManager.getString(Constants.KEY_USER_ID)
        );
        HashMap<String, Object> updates = new HashMap<>();
        updates.put(Constants.KEY_FCM_TOKEN, FieldValue.delete());
        documentReference.update(updates)
                .addOnSuccessListener(unused -> {
                    preferenceManager.clear();
                    startActivity(new Intent(getContext(), SignInActivity.class));
                    getActivity().finish();
                })
                .addOnFailureListener(e -> showToast("Unable to sign out"));
    }

    private void showToast(String message){
        Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
    }

    private void loadUserDetail(){
        userId = preferenceManager.getString(Constants.KEY_USER_ID);
        user_name.setText(preferenceManager.getString(Constants.KEY_NAME));
//        Log.d("EEE", preferenceManager.getString(Constants.KEY_EMAIL));
        phone_number.setText(preferenceManager.getString(Constants.KEY_EMAIL));
        byte[] bytes = Base64.decode(preferenceManager.getString(Constants.KEY_IMAGE), Base64.DEFAULT);
        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes,0, bytes.length);
        image = bitmap;
        profile_image.setImageBitmap(bitmap);
    }
}
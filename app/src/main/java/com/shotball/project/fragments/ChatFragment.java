package com.shotball.project.fragments;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.shotball.project.R;
import com.shotball.project.models.Notification;
import com.shotball.project.models.User;
import com.shotball.project.utils.TextUtil;
import com.shotball.project.activities.ProductActivity;
import com.shotball.project.models.Message;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TimeZone;

import static com.shotball.project.utils.FCM.sendPush;

public class ChatFragment extends BaseFragment {

    private static final String TAG = "ChatFragment";

    private View rootView;
    private RecyclerView recyclerView;
    private RecyclerViewAdapter mAdapter;
    private LinearLayoutManager linearLayoutManager;
    private EditText messageInput;
    private ImageView sendButton;

    private DatabaseReference mDatabase;
    private DatabaseReference databaseReference;
    private ValueEventListener valueEventListener;

    private SimpleDateFormat dateFormat;

    private String roomId;
    private String myUid;
    private String toUid;
    private String productKey;
    private String productTitle;

    private ArrayList<User> users;

    public static ChatFragment getInstance(String toUid, String roomId, String productKey, String productTitle) {
        ChatFragment chatFragment = new ChatFragment();
        Bundle bundle = new Bundle();
        bundle.putString("toUid", toUid);
        bundle.putString("roomId", roomId);
        bundle.putString("productKey", productKey);
        bundle.putString("productTitle", productTitle);
        chatFragment.setArguments(bundle);
        return chatFragment;
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.fragment_chat, container, false);
        initComponents();
        users = new ArrayList<>();
        myUid = getUid();
        getUser(myUid);
        getUser(toUid);

        return rootView;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mAdapter = new RecyclerViewAdapter();
        recyclerView.setAdapter(mAdapter);
        Log.d(TAG, roomId);

        if (productKey != null) {
            mDatabase.child("rooms").child(roomId).child("lastproduct").addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    Log.d(TAG, String.valueOf(dataSnapshot.getValue()));
                    if (!String.valueOf(dataSnapshot.getValue()).equals(productKey)) {
                        sendProduct();
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {

                }
            });
        }
    }

    @SuppressLint("SimpleDateFormat")
    private void initComponents() {
        recyclerView = rootView.findViewById(R.id.chat_recycler_view);
        linearLayoutManager = new LinearLayoutManager(rootView.getContext());
        linearLayoutManager.setStackFromEnd(true);
        recyclerView.setLayoutManager(linearLayoutManager);
        messageInput = rootView.findViewById(R.id.message_input);
        sendButton = rootView.findViewById(R.id.button_send);
        sendButton.setOnClickListener(sendButtonClickListener);

        if (getArguments() != null) {
            roomId = getArguments().getString("roomId");
            toUid = getArguments().getString("toUid");
            productKey = getArguments().getString("productKey");
            productTitle = getArguments().getString("productTitle");
        }

        mDatabase = FirebaseDatabase.getInstance().getReference();
        dateFormat = new SimpleDateFormat("dd.MM.yyyy");
        dateFormat.setTimeZone(TimeZone.getDefault());
    }

    private void getUser(String uid) {
        mDatabase.child("users").child(uid).addListenerForSingleValueEvent(new ValueEventListener() {

            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                User user = dataSnapshot.getValue(User.class);

                if (user != null) {
                    user.setUid(dataSnapshot.getKey());
                    users.add(user);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.d(TAG, "onCancelled getUserInfoFromServer: " + databaseError.getMessage());
            }
        });
    }

    private Button.OnClickListener sendButtonClickListener = new View.OnClickListener() {
        public void onClick(View view) {
            String message = messageInput.getText().toString();
            sendMessage(message);
            messageInput.setText("");
        }
    };

    private void sendProduct() {
        Message message = new Message();
        message.uid = productKey;
        message.msg = productTitle;
        message.type = 1;
        message.timestamp = ServerValue.TIMESTAMP;

        mDatabase.child("rooms").child(roomId).child("lastproduct").setValue(productKey);
        sendMessageToDatabase(message);
    }

    private void sendMessage(String msg) {
        if (msg.equals("")) {
            return;
        }

        sendButton.setEnabled(false);

        Message message = new Message();
        message.uid = myUid;
        message.msg = msg;
        message.type = 0;
        message.timestamp = ServerValue.TIMESTAMP;

        sendMessageToDatabase(message);
    }

    private void sendMessageToDatabase(Message message) {
        if (roomId == null) {
            Objects.requireNonNull(getActivity()).finish();
            return;
        }

        mDatabase.child("rooms").child(roomId).child("lastmessage").setValue(message);
        message.readUsers.put(myUid, true);
        mDatabase.child("rooms").child(roomId).child("messages").push().setValue(message).addOnCompleteListener(new OnCompleteListener<Void>() {
            @Override
            public void onComplete(@NonNull Task<Void> task) {
                User me = users.get(0);
                Notification notification = new Notification(getString(R.string.new_message), message.getMsg(), me.getImage(), me.getUsername(), me.getUid(), users.get(1).getFcm());
                sendPush(notification);
                sendButton.setEnabled(true);
            }
        });

        mDatabase.child("rooms").child(roomId).child("users").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                for (final DataSnapshot item : dataSnapshot.getChildren()) {
                    final String uid = item.getKey();

                    if (!myUid.equals(item.getKey())) {
                        mDatabase.child("rooms").child(roomId).child("unread").child(item.getKey()).addListenerForSingleValueEvent(new ValueEventListener() {

                            @Override
                            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                                Integer count = dataSnapshot.getValue(Integer.class);
                                if (count == null) count = 0;
                                mDatabase.child("rooms").child(roomId).child("unread").child(uid).setValue(count + 1);
                            }

                            @Override
                            public void onCancelled(@NonNull DatabaseError databaseError) {
                                Log.d(TAG, "onCancelled sendMessage: " + databaseError.getMessage());
                            }
                        });
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.d(TAG, "onCancelled sendMessage: " + databaseError.getMessage());
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (mAdapter != null) {
            mAdapter.stopListening();
        }
    }

    class RecyclerViewAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private final int VIEW_MESSAGE_ME = 100;
        private final int VIEW_MESSAGE_YOU = 200;
        private final int VIEW_PRODUCT = 2;

        private List<Message> messagesList;
        private String beforeDay;
        private MessageViewHolder beforeViewHolder;

        RecyclerViewAdapter() {
            messagesList = new ArrayList<>();
            startListening();
        }

        private void startListening() {
            beforeDay = null;
            messagesList.clear();

            databaseReference = mDatabase.child("rooms").child(roomId).child("messages");
            valueEventListener = databaseReference.addValueEventListener(new ValueEventListener() {

                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    beforeDay = null;
                    messagesList.clear();

                    mDatabase.child("rooms").child(roomId).child("unread").child(myUid).setValue(0);
                    Map<String, Object> unreadMessages = new HashMap<>();

                    for (DataSnapshot item: dataSnapshot.getChildren()) {
                        final Message message = item.getValue(Message.class);

                        if (!message.readUsers.containsKey(myUid)) {
                            message.readUsers.put(myUid, true);
                            unreadMessages.put(item.getKey(), message);
                        }

                        messagesList.add(message);
                    }

                    if (unreadMessages.size() > 0) {
                        mDatabase.child("rooms").child(roomId).child("messages").updateChildren(unreadMessages).addOnCompleteListener(new OnCompleteListener<Void>() {
                            @Override
                            public void onComplete(@NonNull Task<Void> task) {
                                notifyDataSetChanged();
                                recyclerView.scrollToPosition(messagesList.size() - 1);
                            }
                        });
                    } else {
                        notifyDataSetChanged();
                        recyclerView.scrollToPosition(messagesList.size() - 1);
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {
                    Log.d(TAG, "onCancelled startListening: " + databaseError.getMessage());
                }
            });
        }

        private void stopListening() {
            if (valueEventListener != null) {
                databaseReference.removeEventListener(valueEventListener);
            }

            messagesList.clear();
            notifyDataSetChanged();
        }

        @Override
        public int getItemViewType(int position) {
            Message message = messagesList.get(position);

            if (message.type == 0) {
                if (message.uid.equals(myUid)) {
                    return VIEW_MESSAGE_ME;
                } else {
                    return VIEW_MESSAGE_YOU;
                }
            } else {
                return VIEW_PRODUCT;
            }
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            RecyclerView.ViewHolder vh;
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            if (viewType == VIEW_MESSAGE_ME) {
                View v = inflater.inflate(R.layout.item_chatmsg_me, parent, false);
                vh = new MessageViewHolder(v);
            } else if (viewType == VIEW_MESSAGE_YOU) {
                View v = inflater.inflate(R.layout.item_chatmsg_you, parent, false);
                vh = new MessageViewHolder(v);
            } else {
                View v = inflater.inflate(R.layout.item_chat_product, parent, false);
                vh = new MessageProductViewHolder(v);
            }
            return vh;
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            final Message message = messagesList.get(position);

            if (holder instanceof MessageViewHolder) {
                final MessageViewHolder view = (MessageViewHolder) holder;
                String day = dateFormat.format(new Date((long) message.timestamp));
                setReadCounter(position, view.read_counter);
                view.divider.setVisibility(View.GONE);

                if (position == 0 || position == 1 & getItemViewType(0) == VIEW_PRODUCT) {
                    view.divider_date.setText(day);
                    view.divider.setVisibility(View.VISIBLE);
                }

                if (!day.equals(beforeDay) && beforeDay != null) {
                    beforeViewHolder.divider_date.setText(beforeDay);
                    beforeViewHolder.divider.setVisibility(View.VISIBLE);
                }
                beforeViewHolder = view;
                beforeDay = day;

                view.bind(message);
            } else {
                MessageProductViewHolder view = (MessageProductViewHolder) holder;
                view.bind(message);
            }
        }

        void setReadCounter(int position, ImageView imageView) {
            int cnt = 2 - messagesList.get(position).readUsers.size();
            if (cnt > 0) {
                imageView.setColorFilter(rootView.getContext().getColor(R.color.unread_message));
            } else {
                imageView.setColorFilter(rootView.getContext().getColor(R.color.colorPrimary));
            }
        }

        @Override
        public int getItemCount() {
            return messagesList.size();
        }

        private class MessageViewHolder extends RecyclerView.ViewHolder {
            private TextView msg_item;
            private TextView timestamp;
            private ImageView read_counter;
            private LinearLayout divider;
            private TextView divider_date;

            MessageViewHolder(View view) {
                super(view);
                msg_item = view.findViewById(R.id.message_item);
                timestamp = view.findViewById(R.id.timestamp);
                read_counter = view.findViewById(R.id.read_counter);
                divider = view.findViewById(R.id.divider);
                divider_date = view.findViewById(R.id.divider_date);
            }

            void bind(final Message message) {
                String time = DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date((long) message.timestamp));
                timestamp.setText(time);
                msg_item.setText(message.msg);
            }
        }

        private class MessageProductViewHolder extends RecyclerView.ViewHolder {
            private TextView title;
            private ImageView image;
            private Button openButton;

            MessageProductViewHolder(View view) {
                super(view);
                title = view.findViewById(R.id.product_message_title);
                image = view.findViewById(R.id.product_message_image);
                openButton = view.findViewById(R.id.product_open_button);
            }

            void bind(final Message message) {
                title.setText(message.getMsg());
                mDatabase.child("products").child(message.getUid()).child("images/0").addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        String imageUrl = (String) dataSnapshot.getValue();
                        if (imageUrl != null && TextUtil.isUrl(imageUrl)) {
                            Glide.with(rootView.getContext()).load(imageUrl).centerCrop().into(image);
                        } else {
                            StorageReference storageReference = FirebaseStorage.getInstance().getReference().child("images").child(message.getUid()).child("0");
                            Glide.with(rootView.getContext()).load(storageReference).centerCrop().into(image);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError databaseError) {
                        Log.d(TAG, "onCancelled MessageProductViewHolder: " + databaseError.getMessage());
                    }
                });

                openButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent intent = new Intent(getActivity(), ProductActivity.class);
                        intent.putExtra(ProductActivity.EXTRA_PRODUCT_KEY, message.getUid());
                        startActivity(intent);
                    }
                });
            }
        }
    }

}
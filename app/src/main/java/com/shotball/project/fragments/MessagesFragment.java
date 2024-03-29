package com.shotball.project.fragments;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.request.RequestOptions;
import com.google.android.material.badge.BadgeDrawable;
import com.google.android.material.bottomnavigation.BottomNavigationItemView;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.shotball.project.R;
import com.shotball.project.activities.ChatActivity;
import com.shotball.project.models.ChatRoomModel;
import com.shotball.project.models.Message;
import com.shotball.project.models.User;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.TreeMap;

import static com.google.android.material.bottomnavigation.LabelVisibilityMode.LABEL_VISIBILITY_AUTO;

public class MessagesFragment extends BaseFragment {

    private static final String TAG = "MessagesFragment";

    private SimpleDateFormat simpleDateFormat;

    private DatabaseReference mDatabase;

    private View rootView;
    private RecyclerView recyclerView;
    private ChatsRecyclerViewAdapter mAdapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.fragment_messages, container, false);
        Log.d(TAG, "onCreateView");
        initToolbar();
        initComponents();
        return rootView;
    }

    private void initToolbar() {
        Toolbar toolbar = rootView.findViewById(R.id.toolbar);
        toolbar.setTitle("Messages");
    }

    @SuppressLint("SimpleDateFormat")
    private void initComponents() {
        mDatabase = FirebaseDatabase.getInstance().getReference();

        recyclerView = rootView.findViewById(R.id.chats_recycler_view);
        int verticalPadding = rootView.getContext().getResources().getDimensionPixelSize(R.dimen.item_list_padding_vertical);
        recyclerView.setPadding(0, verticalPadding, 0, verticalPadding);
        recyclerView.setClipToPadding(false);
        recyclerView.setLayoutManager(new LinearLayoutManager(rootView.getContext()));
        recyclerView.addItemDecoration(new DividerItemDecoration(rootView.getContext(), DividerItemDecoration.VERTICAL));
        mAdapter = new ChatsRecyclerViewAdapter();
        recyclerView.setAdapter(mAdapter);

        simpleDateFormat = new SimpleDateFormat("dd.MM.yyyy");
        simpleDateFormat.setTimeZone(TimeZone.getDefault());
    }

    @Override
    public void onStart() {
        super.onStart();
        Log.d(TAG, "onStart");
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        Log.d(TAG, "onDestroyView");
        if (mAdapter != null) {
            mAdapter.stopListening();
        }
    }

    class ChatsRecyclerViewAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder>{
        final private RequestOptions requestOptions = new RequestOptions().transforms(new CenterCrop(), new RoundedCorners(90));
        private List<ChatRoomModel> roomList = new ArrayList<>();
        private Map<String, User> userList = new HashMap<>();
        private String myUid;

        private ValueEventListener listenerUsers;
        private ValueEventListener listenerRegistration;

        ChatsRecyclerViewAdapter() {
            myUid = getUid();
            listenerUsers = new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    for (DataSnapshot item : dataSnapshot.getChildren()) {
                        userList.put(item.getKey(), item.getValue(User.class));
                    }

                    getRoomInfo();
                }
                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {
                    Log.d(TAG, "onCancelled ChatsRecyclerViewAdapter: " + databaseError.getMessage());
                }
            };

            mDatabase.child("users").addListenerForSingleValueEvent(listenerUsers);
        }

        void getRoomInfo() {
            listenerRegistration = mDatabase.child("rooms").orderByChild("users/" + myUid).equalTo("i").addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    int unreadTotal = 0;
                    TreeMap<Long, ChatRoomModel> sortRoomList = new TreeMap<>(Collections.reverseOrder());

                    for (DataSnapshot item : dataSnapshot.getChildren()) {
                        ChatRoomModel chatRoomModel = new ChatRoomModel();
                        chatRoomModel.setRoomID(item.getKey());

                        long sortKey = 0;
                        Message message = item.child("lastmessage").getValue(Message.class);
                        if (message != null) {
                            sortKey = (long) message.timestamp;
                            chatRoomModel.setLastDatetime(simpleDateFormat.format(new Date(sortKey)));

                            switch (message.type) {
                                case 1: chatRoomModel.setLastMsg(getString(R.string.product)); break;
                                default:  chatRoomModel.setLastMsg(message.msg);
                            }
                        }

                        sortRoomList.put(sortKey, chatRoomModel);

                        Map<String, Object> map = (Map<String, Object>)item.child("users").getValue();
                            for (String key : map.keySet()) {
                                if (myUid.equals(key)) continue;
                                User userModel = userList.get(key);
                                chatRoomModel.setTitle(userModel.getUsername());
                                chatRoomModel.setPhoto(userModel.getImage());
                                chatRoomModel.setUserUid(key);
                            }
                        Integer unreadCount = item.child("unread/" + myUid).getValue(Integer.class);
                        if (unreadCount == null)
                            chatRoomModel.setUnreadCount(0);
                        else {
                            chatRoomModel.setUnreadCount(unreadCount);
                            unreadTotal += unreadCount;
                        }
                    }
                    roomList.clear();
                    for(Map.Entry<Long,ChatRoomModel> entry : sortRoomList.entrySet()) {
                        roomList.add(entry.getValue());
                    }
                    setBadge(rootView.getContext(), unreadTotal);
                    notifyDataSetChanged();
                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {
                    Log.d(TAG, "onCancelled getRoomInfo: " + databaseError.getMessage());
                }
            });
        }

        void stopListening() {
            if (listenerUsers != null) {
                mDatabase.removeEventListener(listenerUsers);
            }

            if (listenerRegistration != null) {
                mDatabase.removeEventListener(listenerRegistration);
            }

            roomList.clear();
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chatroom, parent, false);
            return new RoomViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            RoomViewHolder roomViewHolder = (RoomViewHolder) holder;
            final ChatRoomModel chatRoomModel = roomList.get(position);

            roomViewHolder.room_title.setText(chatRoomModel.getTitle());
            roomViewHolder.last_msg.setText(chatRoomModel.getLastMsg());
            roomViewHolder.last_time.setText(chatRoomModel.getLastDatetime());

            if (chatRoomModel.getPhoto().equals("") || chatRoomModel.getPhoto() == null) {
                Glide.with(rootView.getContext()).load(R.drawable.img_user)
                        .apply(requestOptions)
                        .into(roomViewHolder.room_image);
            } else {
                Glide.with(rootView.getContext()).load(chatRoomModel.getPhoto())
                        .apply(requestOptions)
                        .into(roomViewHolder.room_image);
            }

            if (chatRoomModel.getUnreadCount() > 0) {
                roomViewHolder.unread_count.setText(chatRoomModel.getUnreadCount().toString());
                roomViewHolder.unread_count.setVisibility(View.VISIBLE);
            } else {
                roomViewHolder.unread_count.setVisibility(View.INVISIBLE);
            }

            roomViewHolder.itemView.setOnClickListener(new View.OnClickListener(){
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(v.getContext(), ChatActivity.class);
                    intent.putExtra("toUid", chatRoomModel.getUserUid());
                    intent.putExtra("roomID", chatRoomModel.getRoomID());
                    intent.putExtra("roomTitle", chatRoomModel.getTitle());
                    intent.putExtra("roomImage", chatRoomModel.getPhoto());
                    startActivity(intent);
                }
            });
        }

        @Override
        public int getItemCount() {
            return roomList.size();
        }

        private class RoomViewHolder extends RecyclerView.ViewHolder {
            ImageView room_image;
            TextView room_title;
            TextView last_msg;
            TextView last_time;
            TextView unread_count;

            RoomViewHolder(View view) {
                super(view);
                room_image = view.findViewById(R.id.room_image);
                room_title = view.findViewById(R.id.room_title);
                last_msg = view.findViewById(R.id.last_msg);
                last_time = view.findViewById(R.id.last_time);
                unread_count = view.findViewById(R.id.unread_count);
            }
        }
    }

    private static void setBadge(Context context, int count) {
        String launcherClassName = getLauncherClassName(context);
        if (launcherClassName == null) {
            return;
        }
        Intent intent = new Intent("android.intent.action.BADGE_COUNT_UPDATE");
        intent.putExtra("badge_count", count);
        intent.putExtra("badge_count_package_name", context.getPackageName());
        intent.putExtra("badge_count_class_name", launcherClassName);
        context.sendBroadcast(intent);
    }

    private static String getLauncherClassName(Context context) {
        PackageManager pm = context.getPackageManager();

        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);

        List<ResolveInfo> resolveInfos = pm.queryIntentActivities(intent, 0);
        for (ResolveInfo resolveInfo : resolveInfos) {
            String pkgName = resolveInfo.activityInfo.applicationInfo.packageName;
            if (pkgName.equalsIgnoreCase(context.getPackageName())) {
                return resolveInfo.activityInfo.name;
            }
        }
        return null;
    }

}

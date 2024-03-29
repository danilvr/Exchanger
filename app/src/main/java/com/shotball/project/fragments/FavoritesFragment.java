package com.shotball.project.fragments;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Query;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;
import com.shotball.project.R;
import com.shotball.project.activities.ProductActivity;
import com.shotball.project.helpers.RecyclerItemTouchHelper;
import com.shotball.project.interfaces.IsAvailableCallback;
import com.shotball.project.models.Product;
import com.shotball.project.utils.Preferences;
import com.shotball.project.utils.ViewAnimation;
import com.shotball.project.viewHolders.FavoriteProductViewHolder;

import java.util.ArrayList;
import java.util.List;

public class FavoritesFragment extends BaseFragment implements RecyclerItemTouchHelper.RecyclerItemTouchHelperListener {

    private static final String TAG = "FavoritesFragment";

    private View rootView;
    private CoordinatorLayout mainContainer;
    private ConstraintLayout noItemPage;
    private SwipeRefreshLayout swipeContainer;
    private RecyclerView recyclerView;

    private DatabaseReference mDatabase;
    private LinearLayoutManager mManager;
    private FavouriteProductsAdapter mAdapter;
    private ValueEventListener productsListener;
    private IsAvailableCallback isAvailableCallback;
    private boolean onUndoClicked;

    private Location myLocation;

    @Nullable
    @SuppressLint("ClickableViewAccessibility")
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.fragment_favorites, container, false);
        Log.d(TAG, "onCreateView");
        initToolbar();
        initComponents();
        return rootView;
    }

    private void initToolbar() {
        Toolbar toolbar = rootView.findViewById(R.id.toolbar);
        toolbar.setTitle("Favorites");
    }

    private void initComponents() {
        mainContainer = rootView.findViewById(R.id.favourites_container);
        noItemPage = rootView.findViewById(R.id.lyt_no_items);
        swipeContainer = rootView.findViewById(R.id.swipe_container);
        mAdapter = new FavouriteProductsAdapter();
        mManager = new LinearLayoutManager(rootView.getContext());
        myLocation = Preferences.getLocation(rootView.getContext());

        recyclerView = rootView.findViewById(R.id.productGrid);
        recyclerView.setVisibility(View.GONE);
        int verticalPadding = getResources().getDimensionPixelSize(R.dimen.item_list_padding_vertical);
        recyclerView.setPadding(0, verticalPadding, 0, verticalPadding);
        recyclerView.setClipToPadding(false);
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(mManager);
        recyclerView.setItemAnimator(new DefaultItemAnimator());

        swipeContainer.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                resetRecyclerView();
            }
        });

        mDatabase = FirebaseDatabase.getInstance().getReference();

        ItemTouchHelper.SimpleCallback itemTouchHelperCallback = new RecyclerItemTouchHelper(0, ItemTouchHelper.RIGHT, this);
        new ItemTouchHelper(itemTouchHelperCallback).attachToRecyclerView(recyclerView);

        ItemTouchHelper.SimpleCallback itemTouchHelperCallback1 = new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) { }

            @Override
            public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState, boolean isCurrentlyActive) {
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
            }
        };

        new ItemTouchHelper(itemTouchHelperCallback1).attachToRecyclerView(recyclerView);

        recyclerView.setAdapter(mAdapter);

        isAvailableCallback = new IsAvailableCallback() {
            @Override
            public void onAvailableCallback(boolean isAvailable) {
                if (!isAvailable) {
                    recyclerView.setVisibility(View.GONE);
                    ViewAnimation.fadeInAnimation(noItemPage);
                    swipeContainer.setRefreshing(false);
                } else {
                    noItemPage.setVisibility(View.GONE);
                    ViewAnimation.fadeInAnimation(recyclerView);
                    swipeContainer.setRefreshing(false);
                }
            }
        };

        loadData(isAvailableCallback);
    }

    private void loadData(final IsAvailableCallback callback) {
        myLocation = Preferences.getLocation(rootView.getContext());
        final boolean[] isAvailable = {false};
        productsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                for (DataSnapshot item : dataSnapshot.getChildren()) {
                    Product product = item.getValue(Product.class);

                    if (product != null) {
                        Log.d(TAG, String.valueOf(dataSnapshot));
                        isAvailable[0] = true;
                        String key = item.getKey();
                        product.setKey(key);
                        int distance = -1;
                        if (!isLocationNull(myLocation)) {
                            Location locationA = new Location("point A");
                            locationA.setLatitude(myLocation.getLatitude());
                            locationA.setLongitude(myLocation.getLongitude());
                            Location locationB = new Location("point B");
                            locationB.setLatitude(product.getLatitude());
                            locationB.setLongitude(product.getLongitude());
                            distance = (int) locationA.distanceTo(locationB);
                        }
                        product.setDistance(distance);
                        mAdapter.insertItem(product);
                        swipeContainer.setRefreshing(false);
                    }
                }

                callback.onAvailableCallback(isAvailable[0]);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.d(TAG, "onCancelled loadData: " + databaseError.getMessage());
                callback.onAvailableCallback(isAvailable[0]);
            }
        };
        Query productsQuery = mDatabase.child("products").orderByChild("likes/" + getUid()).equalTo(true);
        productsQuery.addListenerForSingleValueEvent(productsListener);
    }

    private void resetRecyclerView() {
        mAdapter.clear();
        noItemPage.setVisibility(View.GONE);
        ViewAnimation.fadeInAnimation(recyclerView);
        loadData(isAvailableCallback);
    }

    private void unLike(String productKey) {
        Log.d(TAG, "setLike");
        DatabaseReference reference = mDatabase.child("products").child(productKey);

        reference.runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData mutableData) {
                Product product = mutableData.getValue(Product.class);
                if (product == null) {
                    return Transaction.success(mutableData);
                }

                if (product.likes.containsKey(getUid())) {
                    product.likeCount = product.likeCount - 1;
                    product.likes.remove(getUid());
                    mutableData.setValue(product);
                }

                return Transaction.success(mutableData);
            }

            @Override
            public void onComplete(DatabaseError databaseError, boolean b, DataSnapshot dataSnapshot) {
                Log.d(TAG, "productTransaction:onComplete: " + databaseError);
                if (mAdapter.getItemCount() == 0) {
                    Handler handler = new Handler();
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (mAdapter.getItemCount() == 0) {
                                swipeContainer.setRefreshing(true);
                                resetRecyclerView();
                            }
                        }
                    }, 3000);
                }
            }
        });
    }

    @Override
    public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction, final int position) {
        if (viewHolder instanceof FavoriteProductViewHolder) {
            final Product deletedItem = mAdapter.getItem(viewHolder.getAdapterPosition());
            final int deletedIndex = viewHolder.getAdapterPosition();
            mAdapter.removeItem(viewHolder.getAdapterPosition());
            
            Snackbar snackbar = Snackbar.make(mainContainer, R.string.favourite_remove, Snackbar.LENGTH_LONG);
            snackbar.setAnchorView(getActivity().findViewById(R.id.bottom_navigation));
            snackbar.addCallback(new Snackbar.Callback() {
                @Override
                public void onDismissed(Snackbar transientBottomBar, int event) {
                    super.onDismissed(transientBottomBar, event);
                    Log.d(TAG, "onDismissed: " + deletedItem.getKey());
                    if (!onUndoClicked) {
                        unLike(deletedItem.getKey());
                    }
                    onUndoClicked = false;
                }
            });
            snackbar.setAction(R.string.undo, new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    onUndoClicked = true;
                    mAdapter.restoreItem(deletedItem, deletedIndex);
                }
            });
            snackbar.show();
        }
    }

    private boolean isLocationNull(Location location) {
        return location == null || location.getLatitude() == 0.0 && location.getLongitude() == 0.0;
    }

    @Override
    public void onStart() {
        super.onStart();
        Log.d(TAG, "onStart");
    }

    @Override
    public void onStop() {
        super.onStop();
        Log.d(TAG, "onStop");
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.d(TAG, "onPause");
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Log.d(TAG, "onConfigurationChanged");

        ImageView noItems = rootView.findViewById(R.id.no_items_image);
        int normalSize = (int) getResources().getDimension(R.dimen.error_image_size);
        int minSize = (int) getResources().getDimension(R.dimen.error_image_size_landscape);

        if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
            noItems.getLayoutParams().width = normalSize;
            noItems.getLayoutParams().height = normalSize;
        } else if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            noItems.getLayoutParams().width = minSize;
            noItems.getLayoutParams().height = minSize;
        }
    }

    class FavouriteProductsAdapter extends RecyclerView.Adapter<FavoriteProductViewHolder> {
        List<Product> items = new ArrayList<>();

        @NonNull
        @Override
        public FavoriteProductViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            View view = inflater.inflate(R.layout.item_favourite_product, parent, false);
            return new FavoriteProductViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull FavoriteProductViewHolder holder, int position) {
            final Product product = items.get(position);
            holder.bind(rootView.getContext(), product, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(getActivity(), ProductActivity.class);
                    intent.putExtra(ProductActivity.EXTRA_PRODUCT_KEY, product.getKey());
                    startActivity(intent);
                }
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        Product getItem(int position) {
            return items.get(position);
        }

        void insertItem(Product item) {
            items.add(item);
            notifyItemInserted(getItemCount());
        }

        void removeItem(int position) {
            items.remove(position);
            notifyItemRemoved(position);
        }

        void restoreItem(Product item, int position) {
            items.add(position, item);
            notifyItemInserted(position);
            recyclerView.smoothScrollToPosition(position);
        }

        void clear() {
            items.clear();
            notifyDataSetChanged();
        }
    }

}
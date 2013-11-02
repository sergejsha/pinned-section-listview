/*
 * Copyright (C) 2013 Sergej Shafarenka, halfbit.de
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hb.examples;

import android.annotation.SuppressLint;
import android.content.Context;
import android.database.DataSetObserver;
import android.graphics.Canvas;
import android.graphics.PointF;
import android.graphics.Rect;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityEvent;
import android.widget.AbsListView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.SectionIndexer;

//import com.hb.views.pinnedsection.BuildConfig;

/**
 * ListView capable to pin views at its top while the rest is still scrolled.
 * Pinned view's height should large than Body's height.
 */
public class PinnedSectionListView extends ListView {

    // -- inner classes

    /**
     * List adapter to be implemented for being used with PinnedSectionListView
     * adapter.
     */
    public static interface PinnedSectionListAdapter extends ListAdapter {
        /**
         * This method shall return 'true' if views of given type has to be
         * pinned.
         */
        boolean isItemViewTypePinned(int viewType);
    }

    /** Wrapper class for pinned section view and its position in the list. */
    static class PinnedViewShadow {
        public View view;
        public int position;
        public long id;
    }

    // -- class fields

    /** Default change observer. */
    private final DataSetObserver mDataSetObserver = new DataSetObserver() {
        @Override
        public void onChanged() {
            destroyPinnedShadow();
        };

        @Override
        public void onInvalidated() {
            destroyPinnedShadow();
        }
    };

    // fields used for handling touch events
    private final Rect mTouchRect = new Rect();
    private final PointF mTouchPoint = new PointF();
    private final int mTouchSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
    private View mTouchTarget;
    private MotionEvent mDownEvent;

    /** Delegating listener, can be null. */
    OnScrollListener mDelegateOnScrollListener;

    /** Shadow for being recycled, can be null. */
    PinnedViewShadow mRecycleShadow;

    /** shadow instance with a pinned view, can be null. */
    PinnedViewShadow mPinnedShadow;

    /**
     * Pinned view Y-translation. We use it to stick pinned view to the next
     * section.
     */
    int mTranslateY;

    /** Scroll listener which does the magic */
    private final OnScrollListener mOnScrollListener = new OnScrollListener() {

        @Override
        public void onScrollStateChanged(AbsListView view, int scrollState) {
            if (mDelegateOnScrollListener != null) { // delegate
                mDelegateOnScrollListener.onScrollStateChanged(view, scrollState);
            }
        }

        @SuppressLint("NewApi")
        @Override
        public void onScroll(AbsListView view, int firstVisibleItemPosition, int visibleItemCount,
                int totalItemCount) {

            if (mDelegateOnScrollListener != null) { // delegate
                mDelegateOnScrollListener.onScroll(view, firstVisibleItemPosition,
                        visibleItemCount, totalItemCount);
            }

            // get expected adapter or fail
            PinnedSectionListAdapter adapter = (PinnedSectionListAdapter) view.getAdapter();
            if (adapter == null || visibleItemCount == 0)
                return; // nothing to do

            int firstSectionPosition = findFirstSectionPositionInScreen(firstVisibleItemPosition,
                    visibleItemCount);

            if (firstSectionPosition == -1) { // there is no visible sections

                // try to find invisible view
                int currentSectionPosition = findCurrentSectionPosition(firstVisibleItemPosition);
                if (currentSectionPosition == -1)
                    return; // exit here, we have no sections
                // else, we have a section to pin

                if (mPinnedShadow != null) {
                    if (mPinnedShadow.position == currentSectionPosition) {
                        // this section is already pinned
                        mTranslateY = 0;
                        return; // exit, as pinned section is the current one
                    } else {
                        // we have a pinned section, which differs from the
                        // current
                        destroyPinnedShadow(); // destroy old pinned view
                    }
                }

                // create new pinned view for candidate
                createPinnedShadow(currentSectionPosition);
                return; // exit, as we have created a pinned candidate already
            }
            View firstSectionView = view
                    .getChildAt(firstSectionPosition - firstVisibleItemPosition);
            int firstSectionTop = firstSectionView.getTop();
            int topBorder = getListPaddingTop();

            if (mPinnedShadow == null) {

                if (firstSectionTop < topBorder) {
                    // only two sections instance > one's height we need
                    // create pinned shadow
                    int nextSectionPosition = findSectionPositionInScreen(firstVisibleItemPosition,
                            visibleItemCount, firstSectionPosition + 1);
                    boolean isCreat = true;
                    if (nextSectionPosition != -1) {
                        int nextSectionTop = view.getChildAt(
                                nextSectionPosition - firstVisibleItemPosition).getTop();
                        if (nextSectionTop - firstSectionView.getTop() <= firstSectionView
                                .getHeight())
                            isCreat = false;
                    }
                    if (isCreat)
                        createPinnedShadow(firstSectionPosition);
                } else if (firstSectionTop > topBorder) {
                    int pinnedSectionBottom = topBorder + firstSectionView.getHeight();
                    if (firstSectionTop < pinnedSectionBottom) {
                        View firstChild = getChildAt(0);
                        if (!adapter.isItemViewTypePinned(adapter
                                .getItemViewType(firstVisibleItemPosition))
                                && firstChild.getHeight() > 0) {
                            int currentSectionPosition = findCurrentSectionPosition(firstVisibleItemPosition);
                            if (currentSectionPosition > -1) {
                                createPinnedShadow(currentSectionPosition,
                                        firstSectionView.getTop() - pinnedSectionBottom);
                            }
                        }
                    }
                }

            } else {

                if (firstSectionPosition == mPinnedShadow.position) {
                    if (firstSectionTop > topBorder) {
                        destroyPinnedShadow();
                        int prevSectionPosition = findCurrentSectionPosition(firstSectionPosition - 1);
                        if (prevSectionPosition > -1) {
                            createPinnedShadow(prevSectionPosition);
                            int translateY = firstSectionTop - topBorder
                                    - mPinnedShadow.view.getHeight();
                            if (translateY > 0)
                                translateY = 0;
                            mTranslateY = translateY;
                        }
                    } else if (firstSectionTop <= topBorder) {
                        // in fast scroll mode to the top, the first section top
                        // is uncollect.
                        mTranslateY = 0;
                    }

                } else {

                    int pinnedSectionBottom = topBorder + firstSectionView.getHeight();
                    if (firstSectionTop < pinnedSectionBottom) {
                        if (firstSectionTop < topBorder) {
                            // only two sections instance > one's height we need
                            // create pinned shadow
                            int nextSectionPosition = findSectionPositionInScreen(
                                    firstVisibleItemPosition, visibleItemCount,
                                    firstSectionPosition + 1);
                            boolean reCreat = true;
                            if (nextSectionPosition != -1) {
                                int nextSectionTop = view.getChildAt(
                                        nextSectionPosition - firstVisibleItemPosition).getTop();
                                if (nextSectionTop - firstSectionView.getTop() <= firstSectionView
                                        .getHeight())
                                    reCreat = false;
                            }
                            destroyPinnedShadow();
                            if (reCreat) {
                                createPinnedShadow(firstSectionPosition);
                            }
                        } else {
                            mTranslateY = firstSectionTop - pinnedSectionBottom;
                            // in fast scroll mode to the bottom, the first
                            // section top is uncollect.
                            int currentSectionPosition = findCurrentSectionPosition(firstSectionPosition - 1);
                            if (mPinnedShadow.position != currentSectionPosition) {
                                destroyPinnedShadow();
                                createPinnedShadow(currentSectionPosition, mTranslateY);
                            }
                        }
                    } else {
                        mTranslateY = 0;
                    }
                }
            }
        }
    };

    // -- class methods

    public PinnedSectionListView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initView();
    }

    public PinnedSectionListView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        initView();
    }

    private void initView() {
        setOnScrollListener(mOnScrollListener);
    }

    /** Create shadow wrapper with a pinned view for a view at given position */
    private void createPinnedShadow(int position) {
        createPinnedShadow(position, 0);
    }

    private void createPinnedShadow(int position, int translateY) {
        if (position < 0 || mPinnedShadow != null) {
            return;
        }

        // try to recycle shadow
        PinnedViewShadow pinnedShadow = mRecycleShadow;
        mRecycleShadow = null;

        // create new shadow, if needed
        if (pinnedShadow == null)
            pinnedShadow = new PinnedViewShadow();
        // request new view using recycled view, if such
        View pinnedView = getAdapter().getView(position, pinnedShadow.view,
                PinnedSectionListView.this);

        // read layout parameters
        LayoutParams layoutParams = (LayoutParams) pinnedView.getLayoutParams();
        if (layoutParams == null) { // create default layout params
            layoutParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            pinnedView.setLayoutParams(layoutParams);
        }

        int heightMode = MeasureSpec.getMode(layoutParams.height);
        int heightSize = MeasureSpec.getSize(layoutParams.height);

        if (heightMode == MeasureSpec.UNSPECIFIED)
            heightMode = MeasureSpec.EXACTLY;

        int maxHeight = getHeight() - getListPaddingTop() - getListPaddingBottom();
        if (heightSize > maxHeight)
            heightSize = maxHeight;

        // measure & layout
        int ws = MeasureSpec.makeMeasureSpec(getWidth() - getListPaddingLeft()
                - getListPaddingRight(), MeasureSpec.EXACTLY);
        int hs = MeasureSpec.makeMeasureSpec(heightSize, heightMode);
        pinnedView.measure(ws, hs);
        pinnedView.layout(0, 0, pinnedView.getMeasuredWidth(), pinnedView.getMeasuredHeight());

        // initialize pinned shadow
        pinnedShadow.view = pinnedView;
        pinnedShadow.position = position;
        pinnedShadow.id = getAdapter().getItemId(position);

        // store pinned shadow
        mPinnedShadow = pinnedShadow;

        mTranslateY = translateY;
    }

    /** Destroy shadow wrapper for currently pinned view */
    private void destroyPinnedShadow() {
        // keep shadow for being recycled later
        // if (mPinnedShadow != null)
        // Log.d("show", "destroyPinnedShadow = " + mPinnedShadow.position);
        mRecycleShadow = mPinnedShadow;
        mPinnedShadow = null;
    }

    /** find first section in screen */
    private int findFirstSectionPositionInScreen(int firstVisibleItemPosition, int visibleItemCount) {
        return findSectionPositionInScreen(firstVisibleItemPosition, visibleItemCount,
                firstVisibleItemPosition);
    }

    /** find section from fromPosition in screen */
    private int findSectionPositionInScreen(int firstVisibleItemPosition, int visibleItemCount,
            int fromPosition) {
        PinnedSectionListAdapter adapter = (PinnedSectionListAdapter) getAdapter();
        if (fromPosition < firstVisibleItemPosition
                || fromPosition >= firstVisibleItemPosition + visibleItemCount)
            return -1;
        for (int position = fromPosition; position < firstVisibleItemPosition + visibleItemCount; position++) {
            int viewType = adapter.getItemViewType(position);
            if (adapter.isItemViewTypePinned(viewType))
                return position;
        }
        return -1;
    }

    /** find current position's section position */
    private int findCurrentSectionPosition(int fromPosition) {
        PinnedSectionListAdapter adapter = (PinnedSectionListAdapter) getAdapter();

        if (adapter instanceof SectionIndexer) {
            // try fast way by asking section indexer
            SectionIndexer indexer = (SectionIndexer) adapter;
            int sectionPosition = indexer.getSectionForPosition(fromPosition);
            int itemPosition = indexer.getPositionForSection(sectionPosition);
            int typeView = adapter.getItemViewType(itemPosition);
            if (adapter.isItemViewTypePinned(typeView)) {
                return itemPosition;
            } // else, no luck
        }

        // try slow way by looking through to the next section item above
        for (int position = fromPosition; position >= 0; position--) {
            int viewType = adapter.getItemViewType(position);
            if (adapter.isItemViewTypePinned(viewType))
                return position;
        }
        return -1; // no candidate found
    }

    @Override
    public void setOnScrollListener(OnScrollListener listener) {
        if (listener == mOnScrollListener) {
            super.setOnScrollListener(listener);
        } else {
            mDelegateOnScrollListener = listener;
        }
    }

    private boolean isPinnedViewTouched(View view, float x, float y) {
        view.getHitRect(mTouchRect);
        mTouchRect.top += mTranslateY;
        mTouchRect.bottom += mTranslateY;
        return mTouchRect.contains((int) x, (int) y);
    }

    @Override
    public void onRestoreInstanceState(Parcelable state) {
        super.onRestoreInstanceState(state);

        // restore pinned view after configuration change
        post(new Runnable() {
            @Override
            public void run() {
                ListAdapter adapter = getAdapter();
                if (adapter != null && adapter.getCount() > 0) {
                    // detect pinned position
                    int firstVisiblePosition = getFirstVisiblePosition();
                    int position = findCurrentSectionPosition(firstVisiblePosition);
                    if (position == -1)
                        return; // no views to pin, exit

                    if (firstVisiblePosition == position) {
                        // create pinned shadow for position
                        createPinnedShadow(firstVisiblePosition);
                        // adjust translation
                        View childView = getChildAt(firstVisiblePosition);
                        mTranslateY = childView == null ? 0 : -childView.getTop();
                    } else {
                        createPinnedShadow(position);
                    }
                }
            }
        });
    }

    @Override
    public void setAdapter(ListAdapter adapter) {

        // assert adapter in debug mode
        if (/* BuildConfig.DEBUG && */adapter != null) {
            if (!(adapter instanceof PinnedSectionListAdapter))
                throw new IllegalArgumentException(
                        "Does your adapter implement PinnedSectionListAdapter?");
            if (adapter.getViewTypeCount() < 2)
                throw new IllegalArgumentException(
                        "Does your adapter handle at least two types of views - items and sections?");
        }

        // unregister observer at old adapter and register on new one
        ListAdapter currentAdapter = getAdapter();
        if (currentAdapter != null)
            currentAdapter.unregisterDataSetObserver(mDataSetObserver);
        if (adapter != null)
            adapter.registerDataSetObserver(mDataSetObserver);

        // destroy pinned shadow, if new adapter is not same as old one
        if (currentAdapter != adapter)
            destroyPinnedShadow();

        super.setAdapter(adapter);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);

        if (mPinnedShadow != null) {

            // prepare variables
            int pLeft = getListPaddingLeft();
            int pTop = getListPaddingTop();
            View view = mPinnedShadow.view;

            // draw child
            canvas.save();
            canvas.clipRect(pLeft, pTop, pLeft + view.getWidth(), pTop + view.getHeight());
            canvas.translate(pLeft, pTop + mTranslateY);
            drawChild(canvas, mPinnedShadow.view, getDrawingTime());
            canvas.restore();
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {

        final float x = ev.getX();
        final float y = ev.getY();
        final int action = ev.getAction();

        if (action == MotionEvent.ACTION_DOWN
                && mTouchTarget == null
                && mPinnedShadow != null
                && isPinnedViewTouched(mPinnedShadow.view, x, y)) {

            // user touched pinned view
            mTouchTarget = mPinnedShadow.view;
            mTouchPoint.x = x;
            mTouchPoint.y = y;

            mDownEvent = MotionEvent.obtain(ev);
        }

        if (mTouchTarget != null) {
            if (isPinnedViewTouched(mTouchTarget, x, y)) { // forward event to
                                                           // pinned view
                mTouchTarget.dispatchTouchEvent(ev);
            }

            if (action == MotionEvent.ACTION_UP) { // perform onClick on pinned
                                                   // view
                super.dispatchTouchEvent(ev);
                performPinnedItemClick();
                clearTouchTarget();

            } else if (action == MotionEvent.ACTION_CANCEL) { // cancel
                clearTouchTarget();

            } else if (action == MotionEvent.ACTION_MOVE) {
                if (Math.abs(y - mTouchPoint.y) > mTouchSlop) {

                    // cancel sequence on touch target
                    MotionEvent event = MotionEvent.obtain(ev);
                    event.setAction(MotionEvent.ACTION_CANCEL);
                    mTouchTarget.dispatchTouchEvent(event);
                    event.recycle();

                    // provide correct sequence to super class for further
                    // handling
                    super.dispatchTouchEvent(mDownEvent);
                    super.dispatchTouchEvent(ev);
                    clearTouchTarget();

                }
            }

            return true;
        }

        // call super if this was not our pinned view
        return super.dispatchTouchEvent(ev);
    }

    private void clearTouchTarget() {
        mTouchTarget = null;
        if (mDownEvent != null) {
            mDownEvent.recycle();
            mDownEvent = null;
        }
    }

    private boolean performPinnedItemClick() {
        if (mPinnedShadow == null)
            return false;

        OnItemClickListener listener = getOnItemClickListener();
        if (listener != null) {
            View view = mPinnedShadow.view;
            playSoundEffect(SoundEffectConstants.CLICK);
            if (view != null) {
                view.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_CLICKED);
            }
            listener.onItemClick(this, view, mPinnedShadow.position, mPinnedShadow.id);
            return true;
        }
        return false;
    }

}

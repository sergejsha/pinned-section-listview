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

package com.hb.views;

import android.content.Context;
import android.database.DataSetObserver;
import android.graphics.Canvas;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.AbsListView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.SectionIndexer;

import com.hb.views.pinnedsection.BuildConfig;

/**
 * ListView capable to pin views at its top while the rest is still scrolled.
 */
public class PinnedSectionListView extends ListView {

    //-- inner classes

	/** List adapter to be implemented for being used with PinnedSectionListView adapter. */
	public static interface PinnedSectionListAdapter {
		/** This method shall return 'true' if views of given type has to be pinned. */
		boolean isItemViewTypePinned(int viewType);
	}

	/** Wrapper class for pinned section view and its position in the list. */
	static class PinnedViewShadow {
		public View view;
		public int position;
	}

	//-- class fields

	/** Default change observer. */
	private final DataSetObserver mDataSetObserver = new DataSetObserver() {
	    @Override public void onChanged() {
	        destroyPinnedShadow();
	    };
	    @Override public void onInvalidated() {
	        destroyPinnedShadow();
	    }
    };

    /** Delegating listener, can be null. */
    OnScrollListener mDelegateOnScrollListener;

    /** Shadow for being recycled, can be null. */
    PinnedViewShadow mRecycleShadow;

    /** shadow instance with a pinned view, can be null. */
    PinnedViewShadow mPinnedShadow;

    /** Pinned view Y-translation. We use it to stick pinned view to the next section. */
    int mTranslateY;

	/** Scroll listener which does the magic */
	private final OnScrollListener mOnScrollListener = new OnScrollListener() {

		@Override public void onScrollStateChanged(AbsListView view, int scrollState) {
			if (mDelegateOnScrollListener != null) { // delegate
				mDelegateOnScrollListener.onScrollStateChanged(view, scrollState);
			}
		}

		@Override public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {

			if (mDelegateOnScrollListener != null) { // delegate
				mDelegateOnScrollListener.onScroll(view, firstVisibleItem, visibleItemCount, totalItemCount);
			}

			// get expected adapter or fail
			PinnedSectionListAdapter adapter = (PinnedSectionListAdapter) view.getAdapter();
			if (adapter == null || visibleItemCount == 0) return; // nothing to do

			int visibleSectionPosition = findFirstVisibleSectionPosition(firstVisibleItem, visibleItemCount);
			if (visibleSectionPosition == -1) { // there is no visible sections

				// try to find invisible view
				int currentSectionPosition = findCurrentSectionPosition(firstVisibleItem);
				if (currentSectionPosition == -1) return; // exit here, we have no sections
				// else, we have a section to pin

				if (mPinnedShadow != null) {
					if (mPinnedShadow.position == currentSectionPosition) {
						// this section is already pinned
						mTranslateY = 0;
						return; // exit, as pinned section is the current one
					} else {
						// we have a pinned section, which differs from the current
						destroyPinnedShadow(); // destroy old pinned view
					}
				}

				// create new pinned view for candidate
				createPinnedShadow(currentSectionPosition);
				return; // exit, as we have created a pinned candidate already
			}

			int visibleSectionTop = view.getChildAt(visibleSectionPosition - firstVisibleItem).getTop();
			int topBorder = getListPaddingTop();

			if (mPinnedShadow == null) {
				if (visibleSectionTop < topBorder) {
					createPinnedShadow(visibleSectionPosition);
				}

			} else {

				if (visibleSectionPosition == mPinnedShadow.position) {
					if (visibleSectionTop > topBorder) {
						destroyPinnedShadow();
						visibleSectionPosition = findCurrentSectionPosition(visibleSectionPosition - 1);
						if (visibleSectionPosition > -1) {
							createPinnedShadow(visibleSectionPosition);
							int translateY = visibleSectionTop - topBorder - mPinnedShadow.view.getHeight();
							if (translateY > 0) translateY = 0;
							mTranslateY = translateY;
						}
					}

				} else {

					int pinnedSectionBottom = topBorder + mPinnedShadow.view.getHeight();
					if (visibleSectionTop < pinnedSectionBottom) {
						if (visibleSectionTop < topBorder) {
							destroyPinnedShadow();
							createPinnedShadow(visibleSectionPosition);
						} else {
							mTranslateY = visibleSectionTop - pinnedSectionBottom;
						}
					} else {
						mTranslateY = 0;
					}
				}
			}
		}
	};

	//-- class methods

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

		// try to recycle shadow
		PinnedViewShadow pinnedShadow = mRecycleShadow;
		mRecycleShadow = null;

		// create new shadow, if needed
		if (pinnedShadow == null) pinnedShadow = new PinnedViewShadow();
		// request new view using recycled view, if such
		View pinnedView = getAdapter().getView(position, pinnedShadow.view, PinnedSectionListView.this);

		// read layout parameters
		LayoutParams layoutParams = (LayoutParams) pinnedView.getLayoutParams();
		if (layoutParams == null) { // create default layout params
		    layoutParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
		}

		int heightMode = MeasureSpec.getMode(layoutParams.height);
		int heightSize = MeasureSpec.getSize(layoutParams.height);

		if (heightMode == MeasureSpec.UNSPECIFIED) heightMode = MeasureSpec.EXACTLY;

		int maxHeight = getHeight() - getListPaddingTop() - getListPaddingBottom();
		if (heightSize > maxHeight) heightSize = maxHeight;

		// measure & layout
		int ws = MeasureSpec.makeMeasureSpec(getWidth() - getListPaddingLeft() - getListPaddingRight(), MeasureSpec.EXACTLY);
		int hs = MeasureSpec.makeMeasureSpec(heightSize, heightMode);
		pinnedView.measure(ws, hs);
		pinnedView.layout(0, 0, pinnedView.getMeasuredWidth(), pinnedView.getMeasuredHeight());
		mTranslateY = 0;

		// initialize pinned shadow
		pinnedShadow.position = position;
		pinnedShadow.view = pinnedView;

		// store pinned shadow
		mPinnedShadow = pinnedShadow;
	}

	/** Destroy shadow wrapper for currently pinned view */
	private void destroyPinnedShadow() {
		// keep shadow for being recycled later
		mRecycleShadow = mPinnedShadow;
		mPinnedShadow = null;
	}

	private int findFirstVisibleSectionPosition(int firstVisibleItem, int visibleItemCount) {
		PinnedSectionListAdapter adapter = (PinnedSectionListAdapter) getAdapter();
		for (int childIndex = 0; childIndex < visibleItemCount; childIndex++) {
			int position = firstVisibleItem + childIndex;
			int viewType = getAdapter().getItemViewType(position);
			if (adapter.isItemViewTypePinned(viewType)) return position;
		}
		return -1;
	}

	private int findCurrentSectionPosition(int fromPosition) {
		PinnedSectionListAdapter adapter = (PinnedSectionListAdapter) getAdapter();

		if (adapter instanceof SectionIndexer) {
			// try fast way by asking section indexer
			SectionIndexer indexer = (SectionIndexer) adapter;
			int sectionPosition = indexer.getSectionForPosition(fromPosition);
			int itemPosition = indexer.getPositionForSection(sectionPosition);
			int typeView = getAdapter().getItemViewType(itemPosition);
			if (adapter.isItemViewTypePinned(typeView)) {
				return itemPosition;
			} // else, no luck
		}

		// try slow way by looking through to the next section item above
		for (int position=fromPosition; position>=0; position--) {
			int viewType = getAdapter().getItemViewType(position);
			if (adapter.isItemViewTypePinned(viewType)) return position;
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

	@Override
	public void onRestoreInstanceState(Parcelable state) {
		super.onRestoreInstanceState(state);

		// restore pinned view after configuration change
		post(new Runnable() {
			@Override public void run() {
				ListAdapter adapter = getAdapter();
                if (adapter != null && adapter.getCount() > 0) {
                    // detect pinned position
                    int firstVisiblePosition = getFirstVisiblePosition();
                    int position = findCurrentSectionPosition(firstVisiblePosition);
                    if (position == -1) return; // no views to pin, exit

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
		if (BuildConfig.DEBUG && adapter != null) {
			if (!(adapter instanceof PinnedSectionListAdapter))
				throw new IllegalArgumentException("Does your adapter implement PinnedSectionListAdapter?");
			if (adapter.getViewTypeCount() < 2)
				throw new IllegalArgumentException("Does your adapter handle at least two types of views - items and sections?");
		}

		// unregister observer at old adapter and register on new one
		ListAdapter currentAdapter = getAdapter();
		if (currentAdapter != null) currentAdapter.unregisterDataSetObserver(mDataSetObserver);
		if (adapter != null) adapter.registerDataSetObserver(mDataSetObserver);

		// destroy pinned shadow, if new adapter is not same as old one
		if (currentAdapter != adapter) destroyPinnedShadow();

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
}

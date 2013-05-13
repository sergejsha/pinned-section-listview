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
import android.graphics.Canvas;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.AbsListView;
import android.widget.ListAdapter;
import android.widget.ListView;

/**
 * ListView capable to pin views at its top while the rest is still scrolled.
 * @author sergej
 */
public class PinnedSectionListView extends ListView {

	/** List adapter to be implemented for being used with PinnedSectionListView adapter. */
	public static interface PinnedSectionListAdapter extends ListAdapter {
		/** This method shall return true if views of given type shall be pinned. */
		boolean isItemViewTypePinned(int viewType);
	} 
	
	/** Wrapper class for pinned view */
	static class PinnedViewShadow {
		public View view;
		public int position;
	}
	
	public PinnedSectionListView(Context context, AttributeSet attrs) {
		super(context, attrs);
		initView();
	}
	
	public PinnedSectionListView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		initView();
	}

	// scroll listener doing the magic
	private OnScrollListener mOnScrollListener = new OnScrollListener() {
		
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
			
			// find position of a pinned view candidate
			int candidatePosition = findNextVisibleCandidatePosition(firstVisibleItem, visibleItemCount);
			if (candidatePosition == -1) {
				if (!isFastScrollEnabled()) return; // exit here, we have no candidates to pin

				// try to find invisible view
				candidatePosition = findPreviousCandidatePosition(firstVisibleItem);
				if (candidatePosition == -1) return; // exit here, nothing to pin
				
				// pin the candidate
				if (mPinnedShadow != null) {
					if (mPinnedShadow.position == candidatePosition) {
						return; // exit, this candidate is already pinned
					} else {
						destroyPinnedShadow(); // destroy old pinned view
					}
				}

				// create new pinned view for candidate
				createPinnedShadow(candidatePosition);
				return; // exit, as we have created pinned candidate already
			} 
			
			// we have a candidate
			int childIndex = candidatePosition - firstVisibleItem;
			View childView = view.getChildAt(childIndex);
			
			if (mPinnedShadow == null) {
				if (childView.getTop() < 0) {
					createPinnedShadow(candidatePosition);
				}
				
			} else {
				int candidateTop = childView.getTop();
				if (candidatePosition == mPinnedShadow.position) { // candidate is already pinned
					if (candidateTop > 0) { // we moved pinned candidate to the bottom
						// destroy old pinned view
						destroyPinnedShadow();
						// create new pinned view by pulling it form the top
						candidatePosition = findPreviousCandidatePosition(candidatePosition - 1);
						if (candidatePosition > -1) {
							// create new pinned view
							createPinnedShadow(candidatePosition);
							// adjust translation
							int translateY = candidateTop - mPinnedShadow.view.getHeight();
							if (translateY > 0) translateY = 0;
							mTranslateY = translateY;
						} // else, no candidates above
					}
					
				} else { // new not yet pinned candidate
					int translateY = candidateTop - mPinnedShadow.view.getHeight();
					if (translateY < 0) { // we need to move pinned view up
						if (translateY <= -mPinnedShadow.view.getHeight()) {
							 // pinned shadow is out of visible area, replace pinned view
							destroyPinnedShadow();
							createPinnedShadow(candidatePosition);
						} else {
							mTranslateY = translateY;
						}
					} else {
						mTranslateY = 0;
					}
				}
			}
		}
	};
	
	// delegating listener, can be null
	private OnScrollListener mDelegateOnScrollListener;
	
	// shadow for being recycled (can be null)
	PinnedViewShadow mRecycleShadow;
	// shadow instance with a pinned view (can be null)
	PinnedViewShadow mPinnedShadow;
	// pinned view translation, used to show how it sticks to the next section
	int mTranslateY;
	
	/** Create shadow wrapper with a pinned view for a view at given position */
	private void createPinnedShadow(int position) {
		
		// try to recycle shadow
		PinnedViewShadow pinnedShadow = mRecycleShadow;
		View recycleView = pinnedShadow == null ? null : pinnedShadow.view;
		mRecycleShadow = null;
		
		// request new view
		View pinnedView = getAdapter().getView(position, recycleView, PinnedSectionListView.this);
		
		// measure & layout
		int ws = MeasureSpec.makeMeasureSpec(getWidth(), MeasureSpec.EXACTLY);
		int hs = MeasureSpec.makeMeasureSpec(getHeight(), MeasureSpec.AT_MOST);
		pinnedView.measure(ws, hs);
		pinnedView.layout(0, 0, pinnedView.getMeasuredWidth(), pinnedView.getMeasuredHeight());
		mTranslateY = 0;

		// create pinned shadow
		if (pinnedShadow == null) pinnedShadow = new PinnedViewShadow();
		pinnedShadow.position = position;
		pinnedShadow.view = pinnedView;
		
		// store pinned shadow
		mPinnedShadow = pinnedShadow;
	}
	
	/** Destroy shadow wrapper for currently pinned view */
	private void destroyPinnedShadow() {
		// store shadow for being recycled later
		mRecycleShadow = mPinnedShadow;
		mPinnedShadow = null;
	}
	
	private int findNextVisibleCandidatePosition(int firstVisibleItem, int visibleItemCount) {
		PinnedSectionListAdapter adapter = (PinnedSectionListAdapter) getAdapter();
		for (int childIndex = 0; childIndex < visibleItemCount; childIndex++) {
			int position = firstVisibleItem + childIndex;
			int viewType = adapter.getItemViewType(position);
			if (adapter.isItemViewTypePinned(viewType)) return position;
		}
		return -1;
	}
	
	private int findPreviousCandidatePosition(int fromPosition) {
		PinnedSectionListAdapter adapter = (PinnedSectionListAdapter) getAdapter();
		for (int position=fromPosition; position>=0; position--) {
			int viewType = adapter.getItemViewType(position);
			if (adapter.isItemViewTypePinned(viewType)) return position;
		}
		return -1;
	}
	
	private void initView() {
		setOnScrollListener(mOnScrollListener);
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
				
				// detect pinned position
				int firstVisiblePosition = getFirstVisiblePosition();
				int position = findPreviousCandidatePosition(firstVisiblePosition);
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
		});
	}
	
	@Override
	protected void dispatchDraw(Canvas canvas) {
		super.dispatchDraw(canvas);
		if (mPinnedShadow != null) {
			boolean shallTranslate = mTranslateY != 0;
			if (shallTranslate) {
				canvas.save();
				canvas.translate(0f, mTranslateY);
			} 
			drawChild(canvas, mPinnedShadow.view, getDrawingTime());
			if (shallTranslate) canvas.restore();
		} 
	}
}
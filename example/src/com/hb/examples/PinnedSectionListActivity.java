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

import java.util.ArrayList;
import java.util.List;

import android.app.ListActivity;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.hb.examples.pinnedsection.R;
import com.hb.views.PinnedSectionListView.PinnedSectionListAdapter;

public class PinnedSectionListActivity extends ListActivity {

	private static class MyPinnedSectionListAdapter extends ArrayAdapter<Item> implements PinnedSectionListAdapter {
		
		private static final int[] COLORS = new int[] {
			android.R.color.holo_green_light, android.R.color.holo_orange_light,
			android.R.color.holo_blue_light, android.R.color.holo_red_light };
		
		public MyPinnedSectionListAdapter(Context context, int resource, int textViewResourceId, List<Item> objects) {
			super(context, resource, textViewResourceId, objects);
		}
		
		@Override public View getView(int position, View convertView, ViewGroup parent) {
			TextView view = (TextView) super.getView(position, convertView, parent);
			view.setTextColor(Color.DKGRAY);
			if (getItem(position).type == Item.SECTION) {
				view.setBackgroundColor(parent.getResources().getColor(COLORS[position % COLORS.length]));
			}
			return view;
		}
		
		@Override public int getViewTypeCount() {
			return 2;
		}
		
		@Override public int getItemViewType(int position) {
			return getItem(position).type;
		}

		@Override public boolean isItemViewTypePinned(int viewType) {
			return viewType == Item.SECTION;
		}
	}
	
	private static class Item {
		public static final int ITEM = 0;
		public static final int SECTION = 1;
		
		public final int type;
		public final String text;
		
		public Item(int type, String text) {
			this.type = type;
			this.text = text;
		}
		
		@Override public String toString() {
			return text;
		}
	}
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		MyPinnedSectionListAdapter adapter = new MyPinnedSectionListAdapter(
				this, android.R.layout.simple_list_item_1, android.R.id.text1, prepareItems());
		setListAdapter(adapter);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	private static ArrayList<Item> prepareItems() {
		ArrayList<Item> result = new ArrayList<Item>();
		for (int i = 0; i < 30; i++) {
			result.add(new Item(Item.SECTION, "Section " + i));
			for (int j=0; j<4; j++) {
				result.add(new Item(Item.ITEM, "Item " + j));
			}
		}
		return result;
	}
	
}

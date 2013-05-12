Introduction
============

Easy to use ListView with pinned sections for Android. 
Watch [this video][1] to see `PinnedSectionListView` in action.

Usage
=====
 1. Replace standard `ListView` with `com.hb.views.PinnedSectionListView` in your layout.xml file.
 2. Extend your `ListAdapter` that it implements `PinnedSectionListAdapter` interface. Basically you need to 
    implement an additional `isItemViewTypePinned(int viewType)` method there. It must return `true` for all
    view types which have to be pinned.

 That's all. You are done! Working example can also be found under `examples` folder. 

License
=======

    Copyright 2013 Sergej Shafarenka, halfbit.de

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.


[1]: http://www.youtube.com/watch?v=eW7f7MDBtUY


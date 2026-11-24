/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.notepad;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Paint;
import android.graphics.drawable.InsetDrawable;
import android.net.Uri;
import android.test.ActivityInstrumentationTestCase2;
import android.text.InputType;
import android.view.Menu;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Make sure that the main launcher activity opens up properly, which will be
 * verified by {@link #testActivityTestCaseSetUpProperly}.
 */
public class NotePadActivityTest extends ActivityInstrumentationTestCase2<NotesList> {

    private static final String TEST_TITLE_PREFIX = "NotePadTest ";
    private static final long TEST_MODIFICATION_DATE = 1327674857000L;
    private static final String SETTINGS_NAME = "notepad_settings";
    private static final String DARK_THEME_KEY = "dark_theme";

    private boolean mHadThemePreference;
    private boolean mOriginalDarkTheme;

    /**
     * Creates an {@link ActivityInstrumentationTestCase2} for the {@link NotesList} activity.
     */
    public NotePadActivityTest() {
        super(NotesList.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        SharedPreferences settings = getSettings();
        mHadThemePreference = settings.contains(DARK_THEME_KEY);
        mOriginalDarkTheme = settings.getBoolean(DARK_THEME_KEY, false);
        settings.edit().putBoolean(DARK_THEME_KEY, false).apply();
        deleteTestNotes();
    }

    @Override
    protected void tearDown() throws Exception {
        SharedPreferences.Editor editor = getSettings().edit();
        if (mHadThemePreference) {
            editor.putBoolean(DARK_THEME_KEY, mOriginalDarkTheme);
        } else {
            editor.remove(DARK_THEME_KEY);
        }
        editor.apply();
        deleteTestNotes();
        super.tearDown();
    }

    /**
     * Verifies that the activity under test can be launched.
     */
    public void testActivityTestCaseSetUpProperly() {
        assertNotNull("activity should be launched successfully", getActivity());
    }

    public void testListItemShowsFullModificationTime() {
        insertNote(TEST_TITLE_PREFIX + "timestamp", "Content", TEST_MODIFICATION_DATE);
        NotesList activity = getActivity();
        getInstrumentation().waitForIdleSync();

        int position = findNotePosition(activity, TEST_TITLE_PREFIX + "timestamp");
        assertTrue("Inserted note should be displayed", position >= 0);

        View item = activity.getListAdapter().getView(position, null, activity.getListView());
        TextView timestamp = (TextView) item.findViewById(android.R.id.text2);
        assertNotNull("List item should contain a timestamp", timestamp);
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",
                Locale.getDefault());
        formatter.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
        String expected = formatter.format(new Date(TEST_MODIFICATION_DATE));
        assertEquals(expected, timestamp.getText().toString());
    }

    public void testListItemFormatsTimeInAsiaShanghaiWhenDeviceIsGmt() {
        TimeZone originalTimeZone = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("GMT"));
            insertNote(TEST_TITLE_PREFIX + "Shanghai timestamp", "Content",
                    TEST_MODIFICATION_DATE);
            NotesList activity = getActivity();
            getInstrumentation().waitForIdleSync();

            int position = findNotePosition(activity,
                    TEST_TITLE_PREFIX + "Shanghai timestamp");
            assertTrue("Inserted note should be displayed", position >= 0);
            View item = activity.getListAdapter().getView(position, null,
                    activity.getListView());
            TextView timestamp = (TextView) item.findViewById(android.R.id.text2);
            SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",
                    Locale.getDefault());
            formatter.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));

            assertEquals(formatter.format(new Date(TEST_MODIFICATION_DATE)),
                    timestamp.getText().toString());
        } finally {
            TimeZone.setDefault(originalTimeZone);
        }
    }

    public void testListLogoHasLeadingSpacing() {
        NotesList activity = getActivity();
        ImageView home = (ImageView) activity.findViewById(android.R.id.home);
        assertNotNull("List should show a home logo", home);
        assertTrue("List logo should include leading spacing",
                home.getDrawable() instanceof InsetDrawable);
    }

    public void testListItemShowsContentSummary() {
        insertNote(TEST_TITLE_PREFIX + "summary",
                "First line\n  Second line\n\nThird line", TEST_MODIFICATION_DATE);
        NotesList activity = getActivity();
        getInstrumentation().waitForIdleSync();

        View item = getNoteView(activity, TEST_TITLE_PREFIX + "summary");
        int summaryId = activity.getResources().getIdentifier(
                "note_summary", "id", activity.getPackageName());
        assertTrue("List item should contain a content summary", summaryId != 0);
        TextView summary = (TextView) item.findViewById(summaryId);
        assertNotNull(summary);
        assertEquals("First line Second line Third line", summary.getText().toString());
    }

    public void testListItemTitleIsLargeAndBold() {
        insertNote(TEST_TITLE_PREFIX + "title style", "Content", TEST_MODIFICATION_DATE);
        NotesList activity = getActivity();
        getInstrumentation().waitForIdleSync();

        TextView title = (TextView) getNoteView(activity,
                TEST_TITLE_PREFIX + "title style").findViewById(android.R.id.text1);
        assertTrue("List title should be bold", title.getTypeface().isBold());
        assertEquals("List title should use 18sp text", 18f,
                title.getTextSize() / activity.getResources().getDisplayMetrics().scaledDensity,
                0.2f);
    }

    public void testSearchMatchesTitle() {
        insertNote(TEST_TITLE_PREFIX + "shopping", "First note", TEST_MODIFICATION_DATE);
        insertNote(TEST_TITLE_PREFIX + "journal", "Second note", TEST_MODIFICATION_DATE + 1);
        NotesList activity = getActivity();
        EditText search = findSearchBox(activity);
        assertNotNull("Notes list should contain a search box", search);

        setSearchText(activity, search, "shopping");

        assertEquals("Only the matching title should remain", 1,
                activity.getListAdapter().getCount());
        assertEquals(TEST_TITLE_PREFIX + "shopping", getNoteTitle(activity, 0));
    }

    public void testSearchMatchesContent() {
        insertNote(TEST_TITLE_PREFIX + "content", "Needle in the note body",
                TEST_MODIFICATION_DATE);
        insertNote(TEST_TITLE_PREFIX + "other", "Unrelated text", TEST_MODIFICATION_DATE + 1);
        NotesList activity = getActivity();
        EditText search = findSearchBox(activity);
        assertNotNull("Notes list should contain a search box", search);

        setSearchText(activity, search, "Needle in the note");

        assertEquals("Only the matching content should remain", 1,
                activity.getListAdapter().getCount());
        assertEquals(TEST_TITLE_PREFIX + "content", getNoteTitle(activity, 0));
    }

    public void testSearchAndCategoryFilterShareOneRow() {
        NotesList activity = getActivity();
        getInstrumentation().waitForIdleSync();
        View searchContainer = activity.findViewById(R.id.search_container);
        Spinner categoryFilter = (Spinner) activity.findViewById(R.id.category_filter);
        int[] searchLocation = new int[2];
        int[] categoryLocation = new int[2];
        searchContainer.getLocationOnScreen(searchLocation);
        categoryFilter.getLocationOnScreen(categoryLocation);

        assertEquals("Search and category filter should share one row",
                searchLocation[1], categoryLocation[1]);
    }

    public void testListItemShowsCategoryLabel() {
        Uri workUri = insertNote(TEST_TITLE_PREFIX + "work category", "Work content",
                TEST_MODIFICATION_DATE);
        updateNoteFlag(workUri, NotePad.Notes.COLUMN_NAME_CATEGORY,
                NotePad.Notes.CATEGORY_WORK);
        insertNote(TEST_TITLE_PREFIX + "no category", "Uncategorized content",
                TEST_MODIFICATION_DATE + 1);
        NotesList activity = getActivity();
        getInstrumentation().waitForIdleSync();

        int categoryId = activity.getResources().getIdentifier(
                "note_category", "id", activity.getPackageName());
        assertTrue("List item should contain a category label", categoryId != 0);
        TextView workCategory = (TextView) getNoteView(activity,
                TEST_TITLE_PREFIX + "work category").findViewById(categoryId);
        assertEquals(View.VISIBLE, workCategory.getVisibility());
        assertEquals("工作", workCategory.getText().toString());
        TextView noCategory = (TextView) getNoteView(activity,
                TEST_TITLE_PREFIX + "no category").findViewById(categoryId);
        assertEquals(View.GONE, noCategory.getVisibility());
    }

    public void testCategoryFilterCombinesWithSearch() {
        Uri workMatch = insertNote(TEST_TITLE_PREFIX + "work match", "shared needle",
                TEST_MODIFICATION_DATE);
        updateNoteFlag(workMatch, NotePad.Notes.COLUMN_NAME_CATEGORY,
                NotePad.Notes.CATEGORY_WORK);
        Uri lifeMatch = insertNote(TEST_TITLE_PREFIX + "life match", "shared needle",
                TEST_MODIFICATION_DATE + 1);
        updateNoteFlag(lifeMatch, NotePad.Notes.COLUMN_NAME_CATEGORY,
                NotePad.Notes.CATEGORY_LIFE);
        Uri workOther = insertNote(TEST_TITLE_PREFIX + "work other", "different",
                TEST_MODIFICATION_DATE + 2);
        updateNoteFlag(workOther, NotePad.Notes.COLUMN_NAME_CATEGORY,
                NotePad.Notes.CATEGORY_WORK);
        NotesList activity = getActivity();
        getInstrumentation().waitForIdleSync();

        setCategoryFilter(activity, NotePad.Notes.CATEGORY_WORK);
        assertEquals("Work filter should show both work notes", 2,
                activity.getListAdapter().getCount());
        setSearchText(activity, findSearchBox(activity), "shared needle");
        assertEquals("Category and search should be combined", 1,
                activity.getListAdapter().getCount());
        assertEquals(TEST_TITLE_PREFIX + "work match", getNoteTitle(activity, 0));
    }

    public void testAllCategoriesRestoresNotes() {
        Uri workUri = insertNote(TEST_TITLE_PREFIX + "all work", "content",
                TEST_MODIFICATION_DATE);
        updateNoteFlag(workUri, NotePad.Notes.COLUMN_NAME_CATEGORY,
                NotePad.Notes.CATEGORY_WORK);
        Uri lifeUri = insertNote(TEST_TITLE_PREFIX + "all life", "content",
                TEST_MODIFICATION_DATE + 1);
        updateNoteFlag(lifeUri, NotePad.Notes.COLUMN_NAME_CATEGORY,
                NotePad.Notes.CATEGORY_LIFE);
        NotesList activity = getActivity();
        getInstrumentation().waitForIdleSync();
        setSearchText(activity, findSearchBox(activity), TEST_TITLE_PREFIX + "all");

        setCategoryFilter(activity, NotePad.Notes.CATEGORY_LIFE);
        assertEquals(1, activity.getListAdapter().getCount());
        setCategoryFilter(activity, -1);
        assertEquals("All categories should restore matching notes", 2,
                activity.getListAdapter().getCount());
    }

    public void testRegularNoteHidesTodoCheckbox() {
        insertNote(TEST_TITLE_PREFIX + "regular", "Regular note", TEST_MODIFICATION_DATE);
        NotesList activity = getActivity();
        getInstrumentation().waitForIdleSync();

        CheckBox completed = getTodoCheckBox(activity, TEST_TITLE_PREFIX + "regular");
        assertNotNull("List item should contain a todo checkbox", completed);
        assertEquals("Regular notes should not show a checkbox", View.GONE,
                completed.getVisibility());
    }

    public void testTodoCompletionUpdatesProviderAndTitle() {
        Uri noteUri = insertNote(TEST_TITLE_PREFIX + "todo", "Finish the work",
                TEST_MODIFICATION_DATE);
        updateNoteFlag(noteUri, "is_todo", 1);
        NotesList activity = getActivity();
        getInstrumentation().waitForIdleSync();

        CheckBox completed = getTodoCheckBox(activity, TEST_TITLE_PREFIX + "todo");
        assertNotNull("Todo note should contain a checkbox", completed);
        assertEquals(View.VISIBLE, completed.getVisibility());
        assertFalse(completed.isChecked());

        clickView(activity, completed);

        assertEquals("Completion should be saved", 1, getNoteFlag(noteUri, "is_completed"));
        View item = getNoteView(activity, TEST_TITLE_PREFIX + "todo");
        TextView title = (TextView) item.findViewById(android.R.id.text1);
        assertTrue("Completed title should have strikethrough",
                (title.getPaintFlags() & Paint.STRIKE_THRU_TEXT_FLAG) != 0);
    }

    public void testTodoCardClickLaunchesEditor() {
        Uri noteUri = insertNote(TEST_TITLE_PREFIX + "todo click", "Editable body",
                TEST_MODIFICATION_DATE);
        updateNoteFlag(noteUri, "is_todo", 1);
        NotesList activity = getActivity();
        getInstrumentation().waitForIdleSync();

        final int position = findNotePosition(activity, TEST_TITLE_PREFIX + "todo click");
        assertTrue("Requested todo note should be displayed", position >= 0);
        final View item = activity.getListView().getChildAt(
                position - activity.getListView().getFirstVisiblePosition());
        assertNotNull("Todo card should be attached to the list", item);
        CheckBox completed = (CheckBox) item.findViewById(R.id.todo_completed);
        assertFalse("Todo checkbox must not block clicks on the rest of the card",
                completed.isFocusable());
        android.app.Instrumentation.ActivityMonitor monitor = getInstrumentation().addMonitor(
                NoteEditor.class.getName(), null, false);

        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                activity.getListView().performItemClick(item, position,
                        activity.getListAdapter().getItemId(position));
            }
        });
        getInstrumentation().waitForIdleSync();

        NoteEditor editor = (NoteEditor) getInstrumentation().waitForMonitorWithTimeout(
                monitor, 5000);
        assertNotNull("Tapping a todo card outside its checkbox should open the editor", editor);
        assertEquals(noteUri, editor.getIntent().getData());
        finishEditor(editor);
    }

    public void testListItemSkipsRepeatedTitleInSummary() {
        insertNote(TEST_TITLE_PREFIX + "repeated", TEST_TITLE_PREFIX + "repeated\nNext details",
                TEST_MODIFICATION_DATE);
        NotesList activity = getActivity();
        getInstrumentation().waitForIdleSync();

        View item = getNoteView(activity, TEST_TITLE_PREFIX + "repeated");
        TextView summary = (TextView) item.findViewById(R.id.note_summary);
        assertEquals("Next details", summary.getText().toString());
    }

    public void testListItemUsesFirstLineWhenTitleEmpty() {
        ContentValues values = new ContentValues();
        values.put(NotePad.Notes.COLUMN_NAME_TITLE, "");
        values.put(NotePad.Notes.COLUMN_NAME_NOTE, "First line\nSecond line");
        values.put(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE, TEST_MODIFICATION_DATE);
        Uri noteUri = getInstrumentation().getTargetContext().getContentResolver().insert(
                NotePad.Notes.CONTENT_URI, values);
        NotesList activity = getActivity();
        getInstrumentation().waitForIdleSync();

        int position = -1;
        for (int index = 0; index < activity.getListAdapter().getCount(); index++) {
            if (activity.getListAdapter().getItemId(index)
                    == Long.parseLong(noteUri.getLastPathSegment())) {
                position = index;
                break;
            }
        }
        assertTrue("Inserted note should be displayed", position >= 0);
        View item = activity.getListAdapter().getView(position, null, activity.getListView());
        TextView title = (TextView) item.findViewById(android.R.id.text1);
        TextView summary = (TextView) item.findViewById(R.id.note_summary);
        assertEquals("First line", title.getText().toString());
        assertEquals("Second line", summary.getText().toString());
    }

    public void testListItemTreatsLegacySystemTitleAsEmpty() {
        ContentValues values = new ContentValues();
        values.put(NotePad.Notes.COLUMN_NAME_TITLE,
                android.content.res.Resources.getSystem().getString(android.R.string.untitled));
        values.put(NotePad.Notes.COLUMN_NAME_NOTE, "First line\nSecond line");
        values.put(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE, TEST_MODIFICATION_DATE);
        Uri noteUri = getInstrumentation().getTargetContext().getContentResolver().insert(
                NotePad.Notes.CONTENT_URI, values);
        NotesList activity = getActivity();
        getInstrumentation().waitForIdleSync();

        int position = -1;
        for (int index = 0; index < activity.getListAdapter().getCount(); index++) {
            if (activity.getListAdapter().getItemId(index)
                    == Long.parseLong(noteUri.getLastPathSegment())) {
                position = index;
                break;
            }
        }
        assertTrue("Inserted note should be displayed", position >= 0);
        View item = activity.getListAdapter().getView(position, null, activity.getListView());
        TextView title = (TextView) item.findViewById(android.R.id.text1);
        TextView summary = (TextView) item.findViewById(R.id.note_summary);
        assertEquals("First line", title.getText().toString());
        assertEquals("Second line", summary.getText().toString());
    }

    public void testSearchClearRestoresNotes() {
        insertNote(TEST_TITLE_PREFIX + "first", "one", TEST_MODIFICATION_DATE);
        insertNote(TEST_TITLE_PREFIX + "second", "two", TEST_MODIFICATION_DATE + 1000);
        NotesList activity = getActivity();
        getInstrumentation().waitForIdleSync();
        EditText search = findSearchBox(activity);
        int noteCount = activity.getListAdapter().getCount();
        setSearchText(activity, search, TEST_TITLE_PREFIX + "first");
        assertEquals(1, activity.getListAdapter().getCount());

        int clearId = activity.getResources().getIdentifier(
                "search_clear", "id", activity.getPackageName());
        assertTrue("Search should contain a clear button", clearId != 0);
        View clear = activity.findViewById(clearId);
        assertEquals(View.VISIBLE, clear.getVisibility());
        clickView(activity, clear);
        assertEquals(noteCount, activity.getListAdapter().getCount());
        assertEquals("", search.getText().toString());
    }

    public void testEmptyStateDescribesNotesAndSearchResults() {
        NotesList activity = getActivity();
        int emptyTitleId = activity.getResources().getIdentifier(
                "empty_title", "id", activity.getPackageName());
        assertTrue("List should contain an empty state", emptyTitleId != 0);
        TextView emptyTitle = (TextView) activity.findViewById(emptyTitleId);
        assertEquals("还没有笔记", emptyTitle.getText().toString());

        insertNote(TEST_TITLE_PREFIX + "present", "content", TEST_MODIFICATION_DATE);
        getInstrumentation().waitForIdleSync();
        setSearchText(activity, findSearchBox(activity), "missing");
        assertEquals("未找到相关笔记", emptyTitle.getText().toString());
    }

    public void testNewNoteFabLaunchesEditor() {
        final NotesList activity = getActivity();
        int fabId = activity.getResources().getIdentifier(
                "fab_new_note", "id", activity.getPackageName());
        assertTrue("List should contain a new note FAB", fabId != 0);
        android.app.Instrumentation.ActivityMonitor monitor = getInstrumentation().addMonitor(
                NoteEditor.class.getName(), null, false);
        clickView(activity, activity.findViewById(fabId));
        NoteEditor editor = (NoteEditor) getInstrumentation().waitForMonitorWithTimeout(
                monitor, 5000);
        assertNotNull("FAB should open the editor", editor);
        finishEditor(editor);
    }

    public void testPinnedNoteShowsIndicator() {
        ContentValues values = new ContentValues();
        values.put(NotePad.Notes.COLUMN_NAME_TITLE, TEST_TITLE_PREFIX + "pinned");
        values.put(NotePad.Notes.COLUMN_NAME_NOTE, "content");
        values.put(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE, TEST_MODIFICATION_DATE);
        values.put(NotePad.Notes.COLUMN_NAME_IS_PINNED, 1);
        getInstrumentation().getTargetContext().getContentResolver().insert(
                NotePad.Notes.CONTENT_URI, values);
        NotesList activity = getActivity();
        getInstrumentation().waitForIdleSync();

        View item = activity.getListView().getChildAt(0);
        int pinnedId = activity.getResources().getIdentifier(
                "note_pinned", "id", activity.getPackageName());
        assertTrue("List item should contain a pinned indicator", pinnedId != 0);
        assertEquals(View.VISIBLE, item.findViewById(pinnedId).getVisibility());
    }

    public void testEditorBottomSheetChangesCategoryAndSaves() {
        Uri noteUri = insertNote(TEST_TITLE_PREFIX + "editor category", "Categorized body",
                TEST_MODIFICATION_DATE);
        NoteEditor editor = openEditor(noteUri);
        NoteEditor reopened = null;
        try {
            AlertDialog picker = openCategoryPicker(editor);
            assertEquals("New notes should start uncategorized",
                    NotePad.Notes.CATEGORY_NONE,
                    picker.getListView().getCheckedItemPosition());
            long beforeSave = System.currentTimeMillis();

            clickListItem(editor, picker.getListView(), NotePad.Notes.CATEGORY_WORK);

            assertEquals("Category selection should be saved immediately",
                    NotePad.Notes.CATEGORY_WORK,
                    getNoteFlag(noteUri, NotePad.Notes.COLUMN_NAME_CATEGORY));
            long modified = Long.parseLong(getNoteValue(noteUri,
                    NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE));
            assertTrue("Category change should update modification time",
                    modified >= beforeSave && modified <= System.currentTimeMillis());

            finishEditor(editor);
            reopened = openEditor(noteUri);
            AlertDialog reopenedPicker = openCategoryPicker(reopened);
            assertEquals("Saved category should be selected after reopening",
                    NotePad.Notes.CATEGORY_WORK,
                    reopenedPicker.getListView().getCheckedItemPosition());
        } finally {
            if (reopened == null) {
                finishEditor(editor);
            } else {
                finishEditor(reopened);
            }
        }
    }

    public void testEditorCanMarkNoteAsTodo() {
        Uri noteUri = insertNote(TEST_TITLE_PREFIX + "editor todo", "Todo from editor",
                TEST_MODIFICATION_DATE);
        final NoteEditor editor = openEditor(noteUri);

        try {
            Dialog sheet = openEditorMoreSheet(editor);
            int optionId = editor.getResources().getIdentifier(
                    "todo_sheet_option", "id", editor.getPackageName());
            assertTrue("Bottom sheet should contain a todo option", optionId != 0);
            View option = sheet.findViewById(optionId);
            assertNotNull(option);
            clickView(editor, option);
            assertEquals(1, getNoteFlag(noteUri, "is_todo"));
        } finally {
            finishEditor(editor);
        }
    }

    public void testEditorBottomSheetCancelsTodoAndCompletion() {
        Uri noteUri = insertNote(TEST_TITLE_PREFIX + "editor completed", "Completed todo",
                TEST_MODIFICATION_DATE);
        updateNoteFlag(noteUri, "is_todo", 1);
        updateNoteFlag(noteUri, "is_completed", 1);
        final NoteEditor editor = openEditor(noteUri);

        try {
            Dialog sheet = openEditorMoreSheet(editor);
            int optionId = editor.getResources().getIdentifier(
                    "todo_sheet_option", "id", editor.getPackageName());
            int checkId = editor.getResources().getIdentifier(
                    "todo_sheet_check", "id", editor.getPackageName());
            View check = sheet.findViewById(checkId);
            assertNotNull("Todo option should show its selected state", check);
            assertEquals(View.VISIBLE, check.getVisibility());

            clickView(editor, sheet.findViewById(optionId));
            assertEquals(0, getNoteFlag(noteUri, "is_todo"));
            assertEquals("Cancelling a todo should clear completion", 0,
                    getNoteFlag(noteUri, "is_completed"));
        } finally {
            finishEditor(editor);
        }
    }

    public void testEditorSavesTitleAndBodySeparately() {
        Uri noteUri = insertNote(TEST_TITLE_PREFIX + "original title", "Original body",
                TEST_MODIFICATION_DATE);
        NoteEditor editor = openEditor(noteUri);
        try {
            EditText title = findEditorTitle(editor);
            EditText body = (EditText) editor.findViewById(R.id.note);
            assertEquals(TEST_TITLE_PREFIX + "original title", title.getText().toString());
            assertEquals("Original body", body.getText().toString());

            setEditorText(editor, title, TEST_TITLE_PREFIX + "updated title");
            setEditorText(editor, body, "Updated body");
            int saveId = editor.getResources().getIdentifier(
                    "menu_save", "id", editor.getPackageName());
            long beforeSave = System.currentTimeMillis();
            getInstrumentation().invokeMenuActionSync(editor, saveId, 0);
            getInstrumentation().waitForIdleSync();
            long afterSave = System.currentTimeMillis();

            assertEquals(TEST_TITLE_PREFIX + "updated title",
                    getNoteValue(noteUri, NotePad.Notes.COLUMN_NAME_TITLE));
            assertEquals("Updated body", getNoteValue(noteUri, NotePad.Notes.COLUMN_NAME_NOTE));
            long modified = Long.parseLong(getNoteValue(noteUri,
                    NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE));
            assertTrue("Modification time should use the current save time",
                    modified >= beforeSave && modified <= afterSave);
        } finally {
            finishEditor(editor);
        }
    }

    public void testEditorAcceptsChineseInput() {
        Uri noteUri = insertNote(TEST_TITLE_PREFIX + "Chinese", "Original body",
                TEST_MODIFICATION_DATE);
        NoteEditor editor = openEditor(noteUri);
        try {
            EditText title = findEditorTitle(editor);
            EditText body = (EditText) editor.findViewById(R.id.note);
            assertEquals("Title should use general Unicode text input",
                    InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_NORMAL,
                    title.getInputType());
            assertEquals("Body should use general multiline Unicode text input",
                    InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE,
                    body.getInputType());

            setEditorText(editor, title, TEST_TITLE_PREFIX + "中文标题");
            setEditorText(editor, body, "你好，这是一段中文笔记。");
            int saveId = editor.getResources().getIdentifier(
                    "menu_save", "id", editor.getPackageName());
            getInstrumentation().invokeMenuActionSync(editor, saveId, 0);
            getInstrumentation().waitForIdleSync();

            assertEquals(TEST_TITLE_PREFIX + "中文标题",
                    getNoteValue(noteUri, NotePad.Notes.COLUMN_NAME_TITLE));
            assertEquals("你好，这是一段中文笔记。",
                    getNoteValue(noteUri, NotePad.Notes.COLUMN_NAME_NOTE));
        } finally {
            finishEditor(editor);
        }
    }

    public void testEditorAcceptsChineseImeComposition() {
        Uri noteUri = insertNote(TEST_TITLE_PREFIX + "Chinese IME", "",
                TEST_MODIFICATION_DATE);
        final NoteEditor editor = openEditor(noteUri);
        try {
            final EditText body = (EditText) editor.findViewById(R.id.note);
            editor.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    body.requestFocus();
                    body.setSelection(body.getText().length());
                    android.view.inputmethod.EditorInfo editorInfo =
                            new android.view.inputmethod.EditorInfo();
                    android.view.inputmethod.InputConnection connection =
                            body.onCreateInputConnection(editorInfo);
                    connection.beginBatchEdit();
                    connection.setComposingText("ni hao", 1);
                    connection.commitText("你好", 1);
                    connection.finishComposingText();
                    connection.endBatchEdit();
                }
            });
            getInstrumentation().waitForIdleSync();

            assertEquals("Chinese text committed by an IME should be preserved", "你好",
                    body.getText().toString());
        } finally {
            finishEditor(editor);
        }
    }

    public void testEditorTreatsLegacySystemTitleAsEmpty() {
        ContentValues values = new ContentValues();
        values.put(NotePad.Notes.COLUMN_NAME_TITLE,
                android.content.res.Resources.getSystem().getString(android.R.string.untitled));
        values.put(NotePad.Notes.COLUMN_NAME_NOTE, "Body");
        values.put(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE, TEST_MODIFICATION_DATE);
        Uri noteUri = getInstrumentation().getTargetContext().getContentResolver().insert(
                NotePad.Notes.CONTENT_URI, values);
        NoteEditor editor = openEditor(noteUri);
        try {
            assertEquals("", findEditorTitle(editor).getText().toString());
        } finally {
            finishEditor(editor);
        }
    }

    public void testEditorBackIconHasLeadingSpacing() {
        Uri noteUri = insertNote(TEST_TITLE_PREFIX + "back spacing", "Body",
                TEST_MODIFICATION_DATE);
        NoteEditor editor = openEditor(noteUri);
        try {
            int upId = android.content.res.Resources.getSystem().getIdentifier(
                    "up", "id", "android");
            ImageView up = (ImageView) editor.findViewById(upId);
            assertNotNull("Editor should show a back icon", up);
            assertTrue("Back icon should include leading spacing",
                    up.getDrawable() instanceof InsetDrawable);
        } finally {
            finishEditor(editor);
        }
    }

    public void testEditorHasHomeAction() {
        Uri noteUri = insertNote(TEST_TITLE_PREFIX + "home", "Body", TEST_MODIFICATION_DATE);
        final NoteEditor editor = openEditor(noteUri);
        try {
            assertTrue("Editor should show a back action",
                    (editor.getActionBar().getDisplayOptions()
                            & android.app.ActionBar.DISPLAY_HOME_AS_UP) != 0);
            assertTrue("Editor should make the home affordance visible",
                    (editor.getActionBar().getDisplayOptions()
                            & android.app.ActionBar.DISPLAY_SHOW_HOME) != 0);
            Menu menu = createEditorOptionsMenu(editor);
            final android.view.MenuItem home = menu.add(0, android.R.id.home, 0, "home");
            final boolean[] handled = new boolean[1];
            editor.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    handled[0] = editor.onOptionsItemSelected(home);
                }
            });
            getInstrumentation().waitForIdleSync();
            assertTrue("Home action should be handled", handled[0]);
        } finally {
            finishEditor(editor);
        }
    }

    public void testEditorDoesNotAddTitleAlternative() {
        Uri noteUri = insertNote(TEST_TITLE_PREFIX + "menu", "Body", TEST_MODIFICATION_DATE);
        final NoteEditor editor = openEditor(noteUri);
        try {
            Menu menu = createEditorOptionsMenu(editor);
            for (int index = 0; index < menu.size(); index++) {
                assertFalse("System Edit title action should not be added",
                        editor.getString(R.string.resolve_title).equals(
                                menu.getItem(index).getTitle().toString()));
            }
        } finally {
            finishEditor(editor);
        }
    }

    public void testEditorDeleteRequiresConfirmation() {
        final Uri noteUri = insertNote(TEST_TITLE_PREFIX + "delete", "Body",
                TEST_MODIFICATION_DATE);
        NoteEditor editor = openEditor(noteUri);
        try {
            int deleteId = editor.getResources().getIdentifier(
                    "menu_delete", "id", editor.getPackageName());
            getInstrumentation().invokeMenuActionSync(editor, deleteId, 0);
            getInstrumentation().waitForIdleSync();

            DialogFragment fragment = (DialogFragment) editor.getFragmentManager()
                    .findFragmentByTag("delete_confirmation");
            assertNotNull("Delete should show a confirmation dialog", fragment);
            final android.app.AlertDialog dialog = (android.app.AlertDialog) fragment.getDialog();
            clickView(editor, dialog.getButton(Dialog.BUTTON_NEGATIVE));
            assertTrue("Cancelling should keep the note", noteExists(noteUri));

            getInstrumentation().invokeMenuActionSync(editor, deleteId, 0);
            getInstrumentation().waitForIdleSync();
            fragment = (DialogFragment) editor.getFragmentManager()
                    .findFragmentByTag("delete_confirmation");
            final android.app.AlertDialog confirmation =
                    (android.app.AlertDialog) fragment.getDialog();
            clickView(editor, confirmation.getButton(Dialog.BUTTON_POSITIVE));
            assertFalse("Confirming should delete the note", noteExists(noteUri));
        } finally {
            finishEditor(editor);
        }
    }

    public void testEditorRevertRestoresTitleAndBody() {
        Uri noteUri = insertNote(TEST_TITLE_PREFIX + "revert title", "Original body",
                TEST_MODIFICATION_DATE);
        NoteEditor editor = openEditor(noteUri);
        try {
            setEditorText(editor, findEditorTitle(editor), "Changed title");
            setEditorText(editor, (EditText) editor.findViewById(R.id.note), "Changed body");
            Dialog sheet = openEditorMoreSheet(editor);
            View revert = sheet.findViewById(R.id.revert_sheet_option);
            assertEquals(View.VISIBLE, revert.getVisibility());
            clickView(editor, revert);

            assertEquals(TEST_TITLE_PREFIX + "revert title",
                    getNoteValue(noteUri, NotePad.Notes.COLUMN_NAME_TITLE));
            assertEquals("Original body", getNoteValue(noteUri, NotePad.Notes.COLUMN_NAME_NOTE));
        } finally {
            finishEditor(editor);
        }
    }

    public void testEditorSaveActionTracksChanges() {
        Uri noteUri = insertNote(TEST_TITLE_PREFIX + "save state", "Body",
                TEST_MODIFICATION_DATE);
        NoteEditor editor = openEditor(noteUri);
        try {
            Menu menu = createEditorOptionsMenu(editor);
            assertFalse("Unchanged note should show a disabled save action",
                    menu.findItem(R.id.menu_save).isEnabled());
            setEditorText(editor, (EditText) editor.findViewById(R.id.note), "Changed body");
            assertTrue("Editing should enable the save action",
                    menu.findItem(R.id.menu_save).isEnabled());
        } finally {
            finishEditor(editor);
        }
    }

    public void testExistingBlankNoteIsNotDeleted() {
        Uri noteUri = insertNote(TEST_TITLE_PREFIX + "blank", "Body", TEST_MODIFICATION_DATE);
        NoteEditor editor = openEditor(noteUri);
        try {
            setEditorText(editor, findEditorTitle(editor), "");
            setEditorText(editor, (EditText) editor.findViewById(R.id.note), "");
            finishEditor(editor);
            assertTrue("Clearing an existing note should not delete it", noteExists(noteUri));
            assertEquals("", getNoteValue(noteUri, NotePad.Notes.COLUMN_NAME_TITLE));
            assertEquals("", getNoteValue(noteUri, NotePad.Notes.COLUMN_NAME_NOTE));
        } finally {
            finishEditor(editor);
            getInstrumentation().getTargetContext().getContentResolver().delete(
                    noteUri, null, null);
        }
    }

    public void testEditorPinUpdatesProviderWithoutChangingTimeAndUndo() {
        Uri noteUri = insertNote(TEST_TITLE_PREFIX + "pin action", "Body",
                TEST_MODIFICATION_DATE);
        NoteEditor editor = openEditor(noteUri);
        try {
            Dialog sheet = openEditorMoreSheet(editor);
            int optionId = editor.getResources().getIdentifier(
                    "pin_sheet_option", "id", editor.getPackageName());
            assertTrue("Bottom sheet should contain a pin option", optionId != 0);
            clickView(editor, sheet.findViewById(optionId));

            assertEquals(1, getNoteFlag(noteUri, NotePad.Notes.COLUMN_NAME_IS_PINNED));
            assertEquals(String.valueOf(TEST_MODIFICATION_DATE),
                    getNoteValue(noteUri, NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE));

            int undoId = editor.getResources().getIdentifier(
                    "editor_status_undo", "id", editor.getPackageName());
            assertTrue("Pin action should offer undo", undoId != 0);
            View undo = editor.findViewById(undoId);
            assertEquals(View.VISIBLE, undo.getVisibility());
            clickView(editor, undo);
            assertEquals(0, getNoteFlag(noteUri, NotePad.Notes.COLUMN_NAME_IS_PINNED));
            assertEquals(String.valueOf(TEST_MODIFICATION_DATE),
                    getNoteValue(noteUri, NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE));
        } finally {
            finishEditor(editor);
        }
    }

    public void testEditorShareUsesPlainTextTitleAndBody() {
        Uri noteUri = insertNote(TEST_TITLE_PREFIX + "share title", "Shared body",
                TEST_MODIFICATION_DATE);
        NoteEditor editor = openEditor(noteUri);
        try {
            Dialog sheet = openEditorMoreSheet(editor);
            int shareId = editor.getResources().getIdentifier(
                    "share_sheet_option", "id", editor.getPackageName());
            assertTrue("Bottom sheet should contain a share option", shareId != 0);
            assertNotNull(sheet.findViewById(shareId));

            Intent chooser = editor.createShareChooser();
            assertEquals(Intent.ACTION_CHOOSER, chooser.getAction());
            Intent send = (Intent) chooser.getParcelableExtra(Intent.EXTRA_INTENT);
            assertNotNull(send);
            assertEquals(Intent.ACTION_SEND, send.getAction());
            assertEquals("text/plain", send.getType());
            assertEquals(TEST_TITLE_PREFIX + "share title",
                    send.getStringExtra(Intent.EXTRA_SUBJECT));
            assertEquals(TEST_TITLE_PREFIX + "share title\n\nShared body",
                    send.getStringExtra(Intent.EXTRA_TEXT));
        } finally {
            finishEditor(editor);
        }
    }

    public void testThemeSwitchIsSaved() {
        NotesList activity = getActivity();
        int themeMenuId = activity.getResources().getIdentifier(
                "menu_theme", "id", activity.getPackageName());
        assertTrue("Notes list should contain a theme menu item", themeMenuId != 0);

        getInstrumentation().invokeMenuActionSync(activity, themeMenuId, 0);
        getInstrumentation().waitForIdleSync();

        assertTrue("Dark theme selection should be saved",
                getSettings().getBoolean(DARK_THEME_KEY, false));
    }

    private SharedPreferences getSettings() {
        return getInstrumentation().getTargetContext().getSharedPreferences(
                SETTINGS_NAME, Context.MODE_PRIVATE);
    }

    private View getNoteView(NotesList activity, String title) {
        int position = findNotePosition(activity, title);
        assertTrue("Requested note should be displayed", position >= 0);
        return activity.getListAdapter().getView(position, null, activity.getListView());
    }

    private CheckBox getTodoCheckBox(NotesList activity, String title) {
        View item = getNoteView(activity, title);
        int checkboxId = activity.getResources().getIdentifier(
                "todo_completed", "id", activity.getPackageName());
        return (CheckBox) item.findViewById(checkboxId);
    }

    private NoteEditor openEditor(Uri noteUri) {
        Intent intent = new Intent(Intent.ACTION_EDIT, noteUri);
        intent.setClass(getInstrumentation().getTargetContext(), NoteEditor.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        NoteEditor editor = (NoteEditor) getInstrumentation().startActivitySync(intent);
        getInstrumentation().waitForIdleSync();
        return editor;
    }

    private Dialog openEditorMoreSheet(NoteEditor editor) {
        int menuMoreId = editor.getResources().getIdentifier(
                "menu_more", "id", editor.getPackageName());
        assertTrue("Editor should contain a more menu item", menuMoreId != 0);
        assertTrue("More menu action should be handled",
                getInstrumentation().invokeMenuActionSync(editor, menuMoreId, 0));
        getInstrumentation().waitForIdleSync();

        DialogFragment fragment = (DialogFragment) editor.getFragmentManager()
                .findFragmentByTag("editor_more_sheet");
        assertNotNull("More action should open a bottom sheet", fragment);
        Dialog dialog = fragment.getDialog();
        assertNotNull(dialog);
        return dialog;
    }

    private AlertDialog openCategoryPicker(NoteEditor editor) {
        Dialog sheet = openEditorMoreSheet(editor);
        int optionId = editor.getResources().getIdentifier(
                "category_sheet_option", "id", editor.getPackageName());
        assertTrue("Bottom sheet should contain a category option", optionId != 0);
        View option = sheet.findViewById(optionId);
        assertNotNull(option);
        clickView(editor, option);

        DialogFragment fragment = (DialogFragment) editor.getFragmentManager()
                .findFragmentByTag("category_picker");
        assertNotNull("Category action should open a picker", fragment);
        AlertDialog dialog = (AlertDialog) fragment.getDialog();
        assertNotNull(dialog);
        return dialog;
    }

    private void finishEditor(final NoteEditor editor) {
        editor.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                getInstrumentation().callActivityOnPause(editor);
                editor.finish();
            }
        });
        getInstrumentation().waitForIdleSync();
    }

    private EditText findEditorTitle(NoteEditor editor) {
        int titleId = editor.getResources().getIdentifier(
                "note_title", "id", editor.getPackageName());
        assertTrue("Editor should contain a separate title field", titleId != 0);
        return (EditText) editor.findViewById(titleId);
    }

    private Menu createEditorOptionsMenu(final NoteEditor editor) {
        final Menu[] result = new Menu[1];
        editor.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                android.widget.PopupMenu popup = new android.widget.PopupMenu(
                        editor, editor.findViewById(R.id.note));
                editor.onCreateOptionsMenu(popup.getMenu());
                result[0] = popup.getMenu();
            }
        });
        getInstrumentation().waitForIdleSync();
        return result[0];
    }

    private void setEditorText(NoteEditor editor, final EditText field, final String text) {
        editor.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                field.setText(text);
            }
        });
        getInstrumentation().waitForIdleSync();
    }

    private String getNoteValue(Uri noteUri, String column) {
        Cursor cursor = getInstrumentation().getTargetContext().getContentResolver().query(
                noteUri, new String[] { column }, null, null, null);
        assertNotNull(cursor);
        assertTrue(cursor.moveToFirst());
        String value = cursor.getString(cursor.getColumnIndex(column));
        cursor.close();
        return value;
    }

    private boolean noteExists(Uri noteUri) {
        Cursor cursor = getInstrumentation().getTargetContext().getContentResolver().query(
                noteUri, new String[] { NotePad.Notes._ID }, null, null, null);
        boolean exists = cursor != null && cursor.moveToFirst();
        if (cursor != null) {
            cursor.close();
        }
        return exists;
    }

    private void updateNoteFlag(Uri noteUri, String column, int value) {
        ContentValues values = new ContentValues();
        values.put(column, value);
        try {
            getInstrumentation().getTargetContext().getContentResolver().update(
                    noteUri, values, null, null);
        } catch (Exception exception) {
            fail("Todo column should be writable: " + exception.getMessage());
        }
    }

    private int getNoteFlag(Uri noteUri, String column) {
        Cursor cursor = getInstrumentation().getTargetContext().getContentResolver().query(
                noteUri, new String[] { column }, null, null, null);
        assertTrue(cursor.moveToFirst());
        int columnIndex = cursor.getColumnIndex(column);
        assertTrue("Requested flag column should exist", columnIndex >= 0);
        int value = cursor.getInt(columnIndex);
        cursor.close();
        return value;
    }

    private void clickView(Activity activity, final View view) {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                view.performClick();
            }
        });
        getInstrumentation().waitForIdleSync();
    }

    private void clickListItem(Activity activity, final ListView list, final int position) {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                list.performItemClick(list.getChildAt(position), position,
                        list.getAdapter().getItemId(position));
            }
        });
        getInstrumentation().waitForIdleSync();
    }

    private Uri insertNote(String title, String content, long modificationDate) {
        ContentValues values = new ContentValues();
        values.put(NotePad.Notes.COLUMN_NAME_TITLE, title);
        values.put(NotePad.Notes.COLUMN_NAME_NOTE, content);
        values.put(NotePad.Notes.COLUMN_NAME_CREATE_DATE, modificationDate);
        values.put(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE, modificationDate);
        return getInstrumentation().getTargetContext().getContentResolver().insert(
                NotePad.Notes.CONTENT_URI, values);
    }

    private void deleteTestNotes() {
        getInstrumentation().getTargetContext().getContentResolver().delete(
                NotePad.Notes.CONTENT_URI,
                NotePad.Notes.COLUMN_NAME_TITLE + " LIKE ?",
                new String[] { TEST_TITLE_PREFIX + "%" });
    }

    private void setCategoryFilter(NotesList activity, final int category) {
        int filterId = activity.getResources().getIdentifier(
                "category_filter", "id", activity.getPackageName());
        assertTrue("Notes list should contain a category filter", filterId != 0);
        final Spinner filter = (Spinner) activity.findViewById(filterId);
        assertNotNull(filter);
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                filter.setSelection(category + 1);
            }
        });
        getInstrumentation().waitForIdleSync();
    }

    private EditText findSearchBox(NotesList activity) {
        int searchId = activity.getResources().getIdentifier(
                "search", "id", activity.getPackageName());
        return (EditText) activity.findViewById(searchId);
    }

    private void setSearchText(NotesList activity, final EditText search, final String text) {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                search.setText(text);
            }
        });
        getInstrumentation().waitForIdleSync();
    }

    private int findNotePosition(NotesList activity, String title) {
        for (int position = 0; position < activity.getListAdapter().getCount(); position++) {
            if (title.equals(getNoteTitle(activity, position))) {
                return position;
            }
        }
        return -1;
    }

    private String getNoteTitle(NotesList activity, int position) {
        Cursor cursor = (Cursor) activity.getListAdapter().getItem(position);
        return cursor.getString(cursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_TITLE));
    }
}

/*
 * Copyright (C) 2007 The Android Open Source Project
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

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.InsetDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

/** Displays and edits one note. */
public class NoteEditor extends Activity {
    private static final String TAG = "NoteEditor";
    private static final String SETTINGS_NAME = "notepad_settings";
    private static final String DARK_THEME_KEY = "dark_theme";
    private static final String ORIGINAL_CONTENT = "origContent";
    private static final String ORIGINAL_TITLE = "origTitle";

    private static final String[] PROJECTION = new String[] {
            NotePad.Notes._ID,
            NotePad.Notes.COLUMN_NAME_TITLE,
            NotePad.Notes.COLUMN_NAME_NOTE,
            NotePad.Notes.COLUMN_NAME_IS_TODO,
            NotePad.Notes.COLUMN_NAME_IS_COMPLETED,
            NotePad.Notes.COLUMN_NAME_IS_PINNED,
            NotePad.Notes.COLUMN_NAME_CATEGORY
    };

    private static final int STATE_EDIT = 0;
    private static final int STATE_INSERT = 1;
    private static final int UNDO_NONE = 0;
    private static final int UNDO_PIN = 1;
    private static final int UNDO_TODO = 2;

    private int mState;
    private Uri mUri;
    private Cursor mCursor;
    private EditText mTitle;
    private EditText mText;
    private String mOriginalTitle;
    private String mOriginalContent;
    private boolean mIsTodo;
    private boolean mIsCompleted;
    private boolean mIsPinned;
    private int mCategory;
    private boolean mSkipSave;
    private int mUndoType = UNDO_NONE;
    private boolean mUndoPinned;
    private boolean mUndoTodo;
    private boolean mUndoCompleted;
    private View mStatusBar;
    private TextView mStatusText;
    private MenuItem mSaveMenuItem;

    public static class EditorMoreSheet extends DialogFragment {
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final NoteEditor editor = (NoteEditor) getActivity();
            Dialog dialog = new Dialog(editor);
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

            final View content = LayoutInflater.from(editor).inflate(R.layout.editor_more_sheet, null);
            content.setBackgroundResource(editor.isDarkTheme()
                    ? R.drawable.bottom_sheet_dark : R.drawable.bottom_sheet_light);
            final int basePaddingBottom = content.getPaddingBottom();
            if (Build.VERSION.SDK_INT >= 20) {
                content.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
                    @Override
                    public WindowInsets onApplyWindowInsets(View view, WindowInsets insets) {
                        view.setPadding(view.getPaddingLeft(), view.getPaddingTop(),
                                view.getPaddingRight(), basePaddingBottom
                                        + insets.getSystemWindowInsetBottom());
                        return insets;
                    }
                });
            }

            int iconColor = editor.getThemeColor(R.attr.noteIconColor);
            int brandColor = editor.getThemeColor(R.attr.noteBrandColor);
            ImageView pinIcon = (ImageView) content.findViewById(R.id.pin_sheet_icon);
            pinIcon.setImageDrawable(new LineIconDrawable(editor, LineIconDrawable.PIN, iconColor));
            ImageView pinCheck = (ImageView) content.findViewById(R.id.pin_sheet_check);
            pinCheck.setImageDrawable(new LineIconDrawable(editor, LineIconDrawable.CHECK,
                    brandColor));
            pinCheck.setVisibility(editor.mIsPinned ? View.VISIBLE : View.INVISIBLE);
            content.findViewById(R.id.pin_sheet_option).setOnClickListener(
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            editor.togglePin();
                            dismiss();
                        }
                    });

            ImageView shareIcon = (ImageView) content.findViewById(R.id.share_sheet_icon);
            shareIcon.setImageDrawable(new LineIconDrawable(editor, LineIconDrawable.SHARE,
                    iconColor));
            content.findViewById(R.id.share_sheet_option).setOnClickListener(
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            dismiss();
                            editor.shareNote();
                        }
                    });

            ImageView categoryIcon = (ImageView) content.findViewById(R.id.category_sheet_icon);
            categoryIcon.setImageDrawable(new LineIconDrawable(editor, LineIconDrawable.NOTE,
                    iconColor));
            content.findViewById(R.id.category_sheet_option).setOnClickListener(
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            dismiss();
                            editor.showCategoryPicker();
                        }
                    });

            ImageView todoIcon = (ImageView) content.findViewById(R.id.todo_sheet_icon);
            todoIcon.setImageDrawable(new LineIconDrawable(editor, LineIconDrawable.TODO,
                    iconColor));
            ImageView todoCheck = (ImageView) content.findViewById(R.id.todo_sheet_check);
            todoCheck.setImageDrawable(new LineIconDrawable(editor, LineIconDrawable.CHECK,
                    brandColor));
            todoCheck.setVisibility(editor.mIsTodo ? View.VISIBLE : View.INVISIBLE);
            content.findViewById(R.id.todo_sheet_option).setOnClickListener(
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            editor.toggleTodo();
                            dismiss();
                        }
                    });

            View revertOption = content.findViewById(R.id.revert_sheet_option);
            ImageView revertIcon = (ImageView) content.findViewById(R.id.revert_sheet_icon);
            revertIcon.setImageDrawable(new LineIconDrawable(editor, LineIconDrawable.REVERT,
                    iconColor));
            revertOption.setVisibility(editor.hasNoteChanged() ? View.VISIBLE : View.GONE);
            revertOption.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    dismiss();
                    editor.cancelNote();
                }
            });

            dialog.setContentView(content);
            dialog.setCanceledOnTouchOutside(true);
            if (Build.VERSION.SDK_INT >= 20) {
                content.requestApplyInsets();
            }
            return dialog;
        }

        @Override
        public void onStart() {
            super.onStart();
            Window window = getDialog().getWindow();
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.BOTTOM);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams attributes = window.getAttributes();
            attributes.dimAmount = 0.4f;
            window.setAttributes(attributes);
        }
    }

    public static class CategoryPickerFragment extends DialogFragment {
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final NoteEditor editor = (NoteEditor) getActivity();
            return new AlertDialog.Builder(editor)
                    .setTitle(R.string.note_category)
                    .setSingleChoiceItems(R.array.note_category_names, editor.mCategory,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    if (editor.mCategory != which) {
                                        editor.mCategory = which;
                                        editor.saveCurrentNote();
                                    }
                                    dismiss();
                                }
                            })
                    .setNegativeButton(android.R.string.cancel, null)
                    .create();
        }
    }

    public static class DeleteConfirmationFragment extends DialogFragment {
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final NoteEditor editor = (NoteEditor) getActivity();
            return new AlertDialog.Builder(editor)
                    .setTitle(R.string.delete_confirm_title)
                    .setMessage(R.string.delete_confirm_message)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.menu_delete,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    editor.confirmDelete();
                                }
                            })
                    .create();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(isDarkTheme() ? R.style.Theme_NotePad_Dark : R.style.Theme_NotePad_Light);
        super.onCreate(savedInstanceState);
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayShowHomeEnabled(true);
            actionBar.setDisplayUseLogoEnabled(false);
            actionBar.setIcon(new ColorDrawable(Color.TRANSPARENT));
            actionBar.setDisplayHomeAsUpEnabled(true);
            if (Build.VERSION.SDK_INT >= 18) {
                actionBar.setHomeAsUpIndicator(new InsetDrawable(new LineIconDrawable(this,
                        LineIconDrawable.BACK, getThemeColor(R.attr.noteIconColor)),
                        getResources().getDimensionPixelSize(R.dimen.action_bar_leading_inset),
                        0, 0, 0));
            }
        }

        Intent intent = getIntent();
        String action = intent.getAction();
        if (Intent.ACTION_EDIT.equals(action)) {
            mState = STATE_EDIT;
            mUri = intent.getData();
        } else if (Intent.ACTION_INSERT.equals(action) || Intent.ACTION_PASTE.equals(action)) {
            mState = STATE_INSERT;
            mUri = getContentResolver().insert(intent.getData(), null);
            if (mUri == null) {
                Log.e(TAG, "Failed to insert new note into " + intent.getData());
                finish();
                return;
            }
            setResult(RESULT_OK, new Intent().setAction(mUri.toString()));
        } else {
            Log.e(TAG, "Unknown action, exiting");
            finish();
            return;
        }

        mCursor = managedQuery(mUri, PROJECTION, null, null, null);
        setContentView(R.layout.note_editor);
        mTitle = (EditText) findViewById(R.id.note_title);
        mText = (EditText) findViewById(R.id.note);
        mStatusBar = findViewById(R.id.editor_status);
        mStatusText = (TextView) findViewById(R.id.editor_status_text);
        findViewById(R.id.editor_status_undo).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                undoStatusAction();
            }
        });
        TextWatcher changeWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence text, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence text, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable text) {
                updateSaveAction();
            }
        };
        mTitle.addTextChangedListener(changeWatcher);
        mText.addTextChangedListener(changeWatcher);

        if (savedInstanceState != null) {
            mOriginalTitle = savedInstanceState.getString(ORIGINAL_TITLE);
            mOriginalContent = savedInstanceState.getString(ORIGINAL_CONTENT);
        }
        if (Intent.ACTION_PASTE.equals(action)) {
            performPaste();
            mState = STATE_EDIT;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mCursor == null) {
            setTitle(getText(R.string.error_title));
            mText.setText(getText(R.string.error_message));
            return;
        }

        mCursor.requery();
        if (!mCursor.moveToFirst()) {
            setTitle(getText(R.string.error_title));
            return;
        }
        mIsTodo = mCursor.getInt(mCursor.getColumnIndex(
                NotePad.Notes.COLUMN_NAME_IS_TODO)) == 1;
        mIsCompleted = mCursor.getInt(mCursor.getColumnIndex(
                NotePad.Notes.COLUMN_NAME_IS_COMPLETED)) == 1;
        mIsPinned = mCursor.getInt(mCursor.getColumnIndex(
                NotePad.Notes.COLUMN_NAME_IS_PINNED)) == 1;
        mCategory = mCursor.getInt(mCursor.getColumnIndex(
                NotePad.Notes.COLUMN_NAME_CATEGORY));
        String title = mCursor.getString(mCursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_TITLE));
        String note = mCursor.getString(mCursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_NOTE));
        String legacyUntitled = android.content.res.Resources.getSystem().getString(
                android.R.string.untitled);
        if (title != null && legacyUntitled.equals(title.trim())) {
            title = "";
        }
        mTitle.setTextKeepState(title);
        mText.setTextKeepState(note);
        setTitle(title == null || title.trim().length() == 0
                ? getString(R.string.title_create) : title);

        if (mOriginalTitle == null) {
            mOriginalTitle = title;
        }
        if (mOriginalContent == null) {
            mOriginalContent = note;
        }
        updateSaveAction();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putString(ORIGINAL_TITLE, mOriginalTitle);
        outState.putString(ORIGINAL_CONTENT, mOriginalContent);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mSkipSave || mCursor == null) {
            return;
        }
        String title = mTitle.getText().toString();
        String text = mText.getText().toString();
        if (isFinishing() && mState == STATE_INSERT
                && title.trim().length() == 0 && text.trim().length() == 0) {
            setResult(RESULT_CANCELED);
            mSkipSave = true;
            deleteNote();
        } else if (hasNoteChanged() || mState == STATE_INSERT) {
            saveCurrentNote();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.editor_options_menu, menu);
        int iconColor = getThemeColor(R.attr.noteIconColor);
        mSaveMenuItem = menu.findItem(R.id.menu_save);
        menu.findItem(R.id.menu_delete).setIcon(new LineIconDrawable(this,
                LineIconDrawable.DELETE, getResources().getColor(R.color.danger)));
        menu.findItem(R.id.menu_more).setIcon(new LineIconDrawable(this,
                LineIconDrawable.MORE, iconColor));
        updateSaveAction();
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            finish();
            return true;
        } else if (id == R.id.menu_save) {
            saveCurrentNote();
            finish();
            return true;
        } else if (id == R.id.menu_delete) {
            new DeleteConfirmationFragment().show(getFragmentManager(), "delete_confirmation");
            return true;
        } else if (id == R.id.menu_more) {
            new EditorMoreSheet().show(getFragmentManager(), "editor_more_sheet");
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void updateSaveAction() {
        if (mSaveMenuItem == null) {
            return;
        }
        boolean changed = hasNoteChanged();
        mSaveMenuItem.setEnabled(changed);
        mSaveMenuItem.setIcon(new LineIconDrawable(this, LineIconDrawable.SAVE,
                changed ? getThemeColor(R.attr.noteBrandColor)
                        : getThemeColor(R.attr.noteTextHint)));
    }

    private void saveCurrentNote() {
        String title = mTitle.getText().toString();
        String text = mText.getText().toString();
        updateNote(title, text);
        mOriginalTitle = title;
        mOriginalContent = text;
        mState = STATE_EDIT;
        updateSaveAction();
    }

    private void updateNote(String title, String text) {
        ContentValues values = new ContentValues();
        values.put(NotePad.Notes.COLUMN_NAME_TITLE, title == null ? "" : title);
        values.put(NotePad.Notes.COLUMN_NAME_NOTE, text == null ? "" : text);
        values.put(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE, System.currentTimeMillis());
        values.put(NotePad.Notes.COLUMN_NAME_IS_TODO, mIsTodo ? 1 : 0);
        values.put(NotePad.Notes.COLUMN_NAME_IS_COMPLETED, mIsCompleted ? 1 : 0);
        values.put(NotePad.Notes.COLUMN_NAME_CATEGORY, mCategory);
        getContentResolver().update(mUri, values, null, null);
    }

    private boolean hasNoteChanged() {
        if (mTitle == null || mText == null || mOriginalTitle == null || mOriginalContent == null) {
            return false;
        }
        return !mOriginalTitle.equals(mTitle.getText().toString())
                || !mOriginalContent.equals(mText.getText().toString());
    }

    private void togglePin() {
        mUndoPinned = mIsPinned;
        mIsPinned = !mIsPinned;
        updatePinnedState();
        showStatus(mIsPinned ? R.string.status_pinned : R.string.status_unpinned, UNDO_PIN);
    }

    private void updatePinnedState() {
        ContentValues values = new ContentValues();
        values.put(NotePad.Notes.COLUMN_NAME_IS_PINNED, mIsPinned ? 1 : 0);
        getContentResolver().update(mUri, values, null, null);
    }

    private void toggleTodo() {
        mUndoTodo = mIsTodo;
        mUndoCompleted = mIsCompleted;
        mIsTodo = !mIsTodo;
        if (!mIsTodo) {
            mIsCompleted = false;
        }
        saveCurrentNote();
        showStatus(mIsTodo ? R.string.status_todo_on : R.string.status_todo_off, UNDO_TODO);
    }

    private void showCategoryPicker() {
        new CategoryPickerFragment().show(getFragmentManager(), "category_picker");
    }

    private void shareNote() {
        try {
            startActivity(createShareChooser());
        } catch (ActivityNotFoundException exception) {
            Toast.makeText(this, R.string.share_unavailable, Toast.LENGTH_SHORT).show();
        }
    }

    Intent createShareChooser() {
        String title = mTitle.getText().toString();
        String body = mText.getText().toString();
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_SUBJECT, title);
        send.putExtra(Intent.EXTRA_TEXT, title + "\n\n" + body);
        return Intent.createChooser(send, getString(R.string.share_note));
    }

    private void showStatus(int message, int undoType) {
        mUndoType = undoType;
        mStatusText.setText(message);
        mStatusBar.setVisibility(View.VISIBLE);
    }

    private void undoStatusAction() {
        if (mUndoType == UNDO_PIN) {
            mIsPinned = mUndoPinned;
            updatePinnedState();
        } else if (mUndoType == UNDO_TODO) {
            mIsTodo = mUndoTodo;
            mIsCompleted = mUndoCompleted;
            ContentValues values = new ContentValues();
            values.put(NotePad.Notes.COLUMN_NAME_IS_TODO, mIsTodo ? 1 : 0);
            values.put(NotePad.Notes.COLUMN_NAME_IS_COMPLETED, mIsCompleted ? 1 : 0);
            getContentResolver().update(mUri, values, null, null);
        }
        mUndoType = UNDO_NONE;
        mStatusBar.setVisibility(View.GONE);
    }

    private void confirmDelete() {
        mSkipSave = true;
        setResult(RESULT_CANCELED);
        deleteNote();
        finish();
    }

    private void cancelNote() {
        mSkipSave = true;
        if (mCursor != null) {
            if (mState == STATE_EDIT) {
                ContentValues values = new ContentValues();
                values.put(NotePad.Notes.COLUMN_NAME_TITLE, mOriginalTitle);
                values.put(NotePad.Notes.COLUMN_NAME_NOTE, mOriginalContent);
                getContentResolver().update(mUri, values, null, null);
            } else {
                deleteNote();
            }
        }
        setResult(RESULT_CANCELED);
        finish();
    }

    private void deleteNote() {
        if (mCursor != null) {
            mCursor.close();
            mCursor = null;
            getContentResolver().delete(mUri, null, null);
        }
    }

    private void performPaste() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ContentResolver resolver = getContentResolver();
        ClipData clip = clipboard.getPrimaryClip();
        if (clip == null) {
            return;
        }

        String text = null;
        String title = null;
        ClipData.Item item = clip.getItemAt(0);
        Uri uri = item.getUri();
        if (uri != null && NotePad.Notes.CONTENT_ITEM_TYPE.equals(resolver.getType(uri))) {
            Cursor original = resolver.query(uri, PROJECTION, null, null, null);
            if (original != null) {
                if (original.moveToFirst()) {
                    text = original.getString(original.getColumnIndex(NotePad.Notes.COLUMN_NAME_NOTE));
                    title = original.getString(original.getColumnIndex(NotePad.Notes.COLUMN_NAME_TITLE));
                }
                original.close();
            }
        }
        if (text == null) {
            text = item.coerceToText(this).toString();
        }
        updateNote(title, text);
    }

    private boolean isDarkTheme() {
        return getSharedPreferences(SETTINGS_NAME, MODE_PRIVATE)
                .getBoolean(DARK_THEME_KEY, false);
    }

    private int getThemeColor(int attribute) {
        TypedValue value = new TypedValue();
        getTheme().resolveAttribute(attribute, value, true);
        return value.data;
    }
}

package org.tinylisp.activity;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v4.app.ShareCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ScrollView;
import android.widget.TextView;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.tinylisp.engine.Engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ReplActivity extends AppCompatActivity implements TextView.OnEditorActionListener, View.OnClickListener, View.OnKeyListener, TextWatcher {

    private static final String TAG = "Repl";

    protected Engine mEngine;
    protected Engine.TLEnvironment mEnv;

    protected ScrollView mScrollView;
    protected TextView mOutput;
    protected EditText mInput;
    protected ImageButton mTabButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_repl);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        mScrollView = findViewById(R.id.scrollview);

        mOutput = findViewById(R.id.output);

        mInput = findViewById(R.id.input);
        mInput.setOnEditorActionListener(this);
        mInput.setOnKeyListener(this);
        mInput.addTextChangedListener(this);

        mTabButton = findViewById(R.id.tab_button);
        mTabButton.setOnClickListener(this);

        mEngine = new Engine();
        mEnv = Engine.defaultEnvironment();
        initRepl();

        try {
            restoreHistory();
        } catch (Exception ex) {
            Log.d(TAG, "Error restoring history", ex);
        }
    }

    /* REPL manipulation methods */

    protected void initRepl() {
        clear();
        print("TinyLisp ", Engine.VERSION, "\n");
    }

    protected void print(String... strings) {
        for (String string : strings) {
            mOutput.append(string);
        }
        mScrollView.post(new Runnable() {
            @Override public void run() {
                mScrollView.fullScroll(View.FOCUS_DOWN);
                mInput.requestFocus();
            }
        });
    }

    protected void clear() {
        mOutput.setText("");
    }

    protected void onCompletionTriggered() {
        int caret = mInput.getSelectionEnd();
        Editable input = mInput.getText();
        String before = input.subSequence(0, caret).toString();
        String after = input.subSequence(caret, input.length()).toString();
        String completion = complete(before);
        if (completion != null) {
            mInput.setText(completion);
            mInput.append(after);
            mInput.setSelection(completion.length());
            mInput.requestFocus();
        }
    }

    protected String complete(String input) {
        if (input.isEmpty() || Character.isWhitespace(input.charAt(input.length() - 1))) {
            return null;
        }
        List<String> tokens = mEngine.tokenize(input);
        if (tokens.isEmpty()) {
            return null;
        }
        String stem = tokens.get(tokens.size() - 1);
        String leading = input.substring(0, input.length() - stem.length());
        List<String> candidates = mEnv.complete(stem);
        if (candidates.isEmpty()) {
            return null;
        } else if (candidates.size() == 1) {
            return leading + candidates.get(0) + " ";
        } else {
            printCompletionHelp(stem, candidates);
            String commonPrefix = StringUtils.getCommonPrefix(candidates.toArray(new String[0]));
            return commonPrefix.isEmpty() ? null : leading + commonPrefix;
        }
    }

    protected void printCompletionHelp(String stem, List<String> candidates) {
        if (stem.isEmpty()) {
            print("All symbols:\n");
        } else {
            print("Symbols starting with ", stem, ":\n");
        }
        for (String candidate : candidates) {
            print("    ", candidate, "\n");
        }
    }

    protected void execute(String input) {
        // echo
        print("\n> ", input, "\n");
        try {
            Engine.TLExpression result = mEngine.execute(input, mEnv);
            onExecutionSucceeded(result);
        } catch (Exception ex) {
            printException(ex);
        }
    }

    protected void onExecutionSucceeded(Engine.TLExpression result) {
        mEnv.put(Engine.TLSymbolExpression.of("_"), result);
        printObject(result);
    }

    protected void printObject(Object object) {
        String repr;
        if (object instanceof int[]) {
            repr = Arrays.toString((int[]) object);
        } else {
            repr = object.toString();
        }
        print(repr, "\n");
    }

    protected void printException(Exception ex) {
        print(findInterestingCause(ex).toString(), "\n");
        ex.printStackTrace();
    }

    protected Throwable findInterestingCause(Throwable throwable) {
        while (true) {
            if (throwable instanceof Engine.TLRuntimeException) {
                return throwable;
            }
            Throwable cause = throwable.getCause();
            if (cause == null) {
                return throwable;
            } else {
                throwable = cause;
            }
        }
    }

    /* REPL history */

    private static final String HISTORY_KEY = "historyKey";
    private List<String> history = new ArrayList<>();
    private Integer index;

    private void appendHistory(String item) {
        history.add(item);
        saveHistory();
    }

    private void saveHistory() {
        SharedPreferences preferences = getPreferences(Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(HISTORY_KEY, new JSONArray(history).toString());
        editor.apply();
    }

    private void restoreHistory() throws JSONException {
        history.clear();
        SharedPreferences preferences = getPreferences(Context.MODE_PRIVATE);
        String json = preferences.getString(HISTORY_KEY, null);
        if (json != null) {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                history.add(array.getString(i));
            }
        }
    }

    private boolean setPreviousHistory() {
        if (index == null) {
            index = history.size();
        }
        index = Math.max(index - 1, 0);
        if (index >= 0 && index < history.size()) {
            String replacement = history.get(index);
            mInput.setText(replacement);
            mInput.setSelection(mInput.length());
            return true;
        }
        return false;
    }

    private boolean setNextHistory() {
        if (index == null) {
            index = history.size();
        }
        index = Math.min(index + 1, history.size());
        if (index == history.size()) {
            mInput.setText(null);
            return true;
        } else if (index >= 0 && index < history.size()) {
            String replacement = history.get(index);
            mInput.setText(replacement);
            mInput.setSelection(mInput.length());
            return true;
        }
        return false;
    }

    @Override public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_repl, menu);
        return true;
    }

    @Override public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_share) {
            shareConsoleLog();
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    private void shareConsoleLog() {
        ShareCompat.IntentBuilder.from(this)
            .setText(mOutput.getText().toString())
            .setType("text/plain")
            .startChooser();
    }

    /* TextView.OnEditorActionListener */

    @Override public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
        Log.d(TAG, "Input: onEditorAction; actionId=" + actionId + "; event=" + event);
        if (actionId == EditorInfo.IME_NULL) {
            if (v.length() > 0) {
                String input = v.getText().toString().trim();
                appendHistory(input);
                index = null;
                execute(input);
                v.setText("");
            }
            // Always consume so as to prevent inputting raw \n
            return true;
        }
        return false;
    }

    /* View.OnClickListener */

    @Override public void onClick(View v) {
        if (v == mTabButton) {
            onCompletionTriggered();
        }
    }

    /* View.OnKeyListener */

    @Override public boolean onKey(View v, int keyCode, KeyEvent event) {
        Log.d(TAG, "Input: onKey; keyCode=" + keyCode + "; event=" + event);
        if (event.getAction() == KeyEvent.ACTION_UP) {
            switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
                if (setPreviousHistory()) {
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (setNextHistory()) {
                    return true;
                }
                break;
            default:
                return false;
            }
        } else if (event.getAction() == KeyEvent.ACTION_DOWN) {
            switch (keyCode) {
            case KeyEvent.KEYCODE_TAB:
                onCompletionTriggered();
                return true;
            default:
                return false;
            }
        }
        return false;
    }

    /* TextWatcher */

    @Override public void beforeTextChanged(CharSequence s, final int start, int count, int after) {
        Log.d(TAG, "Input: beforeTextChanged; s=" + s + "; start=" + start + ", count=" + count + ", after=" + after);
         if (after == 0 && count == 1 && start + count < s.length()) {
            // Delete
            String deleted = s.subSequence(start, start + count).toString();
            final String next = s.subSequence(start + count, start + count + 1).toString();
            if ("(".equals(deleted) && ")".equals(next)
                || "[".equals(deleted) && "]".equals(next)
                || "\"".equals(deleted) && "\"".equals(next)) {
                mInput.post(new Runnable() {
                    @Override public void run() {
                        deleteAtIndex(next, start);
                    }
                });
            }
        }
    }
    @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
        Log.d(TAG, "Input: onTextChanged; s=" + s + "; start=" + start + ", before=" + before + ", count=" + count);
        if (before == 0 && count == 1) {
            // Insert
            int end = start + count;
            String inserted = s.subSequence(start, end).toString();
            final String next;
            if (end + 1 <= s.length()) {
                next = s.subSequence(end, end + 1).toString();
            } else {
                next = null;
            }
            if (inserted.equals(next) &&
                    (next.equals(")") || next.equals("]") || next.equals("\""))) {
                // Skip already-present closing char
                deleteAtIndex(next, end);
            } else if ("(".equals(inserted)) {
                insertAfterCaret(")");
            } else if ("[".equals(inserted)) {
                insertAfterCaret("]");
            } else if ("\"".equals(inserted)) {
                insertAfterCaret("\"");
            }
        }
    }
    @Override public void afterTextChanged(Editable s) {
        Log.d(TAG, "Input: afterTextChanged; s=" + s);
    }

    private void insertAfterCaret(String string) {
        int caret = mInput.getSelectionEnd();
        Editable content = mInput.getText();
        String before = content.subSequence(0, caret).toString();
        String after = content.subSequence(caret, content.length()).toString();
        String result = before + string + after;
        mInput.setText(result);
        mInput.setSelection(before.length());
    }

    private void deleteAtIndex(String toDelete, int start) {
        int end = start + toDelete.length();
        Editable content = mInput.getText();
        if (start < content.length() && end <= content.length()) {
            if (content.subSequence(start, end).toString().equals(toDelete)) {
                StringBuilder builder = new StringBuilder(content);
                builder.delete(start, end);
                mInput.setText(builder);
                mInput.setSelection(start);
            }
        }
    }
}
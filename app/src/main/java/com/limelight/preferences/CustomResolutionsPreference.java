package com.limelight.preferences;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.DialogPreference;
import android.text.Editable;
import android.text.InputType;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import com.limelight.R;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class CustomResolutionsConsts {
    public static final String CUSTOM_RESOLUTIONS_FILE = "custom_resolutions";
    public static final String CUSTOM_RESOLUTIONS_KEY = "custom_resolutions";
}
class Validate {
    static public boolean isValidResolution(String res){
        String regex = "^\\d{3,5}x\\d{3,5}$";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(res);
        return matcher.matches();
    }
}

public class CustomResolutionsPreference extends DialogPreference {
    private final Context context;
    private final CustomResolutionsAdapter adapter;

    public CustomResolutionsPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.context = context;
        this.adapter = new CustomResolutionsAdapter(context);
        adapter.setOnDataChangedListener(new UpdateStorageEventListener());
    }

    void onSubmitResolution(EditText field){
        field.setError(null);

        Editable editable = field.getText();
        String content = editable.toString();


        if(!Validate.isValidResolution(content)) {
            field.setError("Invalid resolution.");
            return;
        }
        if(adapter.exists(content)) {
            field.setError("Item already exists.");
            return;
        }

        adapter.addItem(content);
    }

    @Override
    protected void onBindDialogView(View view) {
        SharedPreferences prefs = context.getSharedPreferences(CustomResolutionsConsts.CUSTOM_RESOLUTIONS_FILE, Context.MODE_PRIVATE);
        Set<String> stored = prefs.getStringSet(CustomResolutionsConsts.CUSTOM_RESOLUTIONS_KEY, null);

        if(stored == null) return;

        ArrayList<String> list = new ArrayList<>(stored);
        Comparator<String> lengthComparator = new Comparator<String>() {
            @Override
            public int compare(String s1, String s2) {
                String[] s1Size = s1.split("x");
                String[] s2Size = s2.split("x");

                int w1 = Integer.parseInt(s1Size[0]);
                int w2 = Integer.parseInt(s2Size[0]);

                int h1 = Integer.parseInt(s1Size[1]);
                int h2 = Integer.parseInt(s2Size[1]);

                if(w1 == w2) {
                    return Integer.compare(h1, h2);
                }
                return Integer.compare(w1, w2);
            }
        };
        Collections.sort(list, lengthComparator);

        adapter.addAll(list);
        super.onBindDialogView(view);
    }

    @Override
    protected View onCreateDialogView() {
       LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, AbsListView.LayoutParams.WRAP_CONTENT);

        LinearLayout body = new LinearLayout(context);
        LayoutInflater inflater = LayoutInflater.from(context);
        ListView list = new ListView(context);

        body.setLayoutParams(layoutParams);
        list.setLayoutParams(layoutParams);

        View inputRow = inflater.inflate(R.layout.custom_resolutions_form, null);
        EditText textEditingField = inputRow.findViewById(R.id.custom_resolution_field);
        ImageButton doneEditingButton = inputRow.findViewById(R.id.done_editing);

        body.addView(list);
        body.addView(inputRow);
        body.setOrientation(LinearLayout.VERTICAL);

        inputRow.setLayoutParams(layoutParams);

        textEditingField.setHint("2400x1080");
        list.setAdapter(adapter);
        doneEditingButton.setOnClickListener(view -> onSubmitResolution(textEditingField));

        return body;
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        StreamSettings settingsActivity = (StreamSettings) getContext();
        settingsActivity.reloadSettings();
    }

    private class UpdateStorageEventListener implements EventListener {
        @Override
        public void onTrigger() {
            SharedPreferences prefs = context.getSharedPreferences(CustomResolutionsConsts.CUSTOM_RESOLUTIONS_FILE, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            Set<String> set = new HashSet<>(adapter.getAll());
            editor.putStringSet(CustomResolutionsConsts.CUSTOM_RESOLUTIONS_KEY, set).apply();
        }
    }
}

interface EventListener {
    void onTrigger();
}

class CustomResolutionsAdapter extends BaseAdapter {
    ArrayList<String> resolutions = new ArrayList<String>();
    private final Context context;
    private EventListener listener;

    public CustomResolutionsAdapter(Context context) {
        this.context = context;
    }

    public void setOnDataChangedListener(EventListener listener) {
        this.listener = listener;
    }

    @Override
    public void notifyDataSetChanged() {
        if (listener != null) {
            listener.onTrigger();
        }
        super.notifyDataSetChanged();
    }

    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);

        LinearLayout row = new LinearLayout(context);
        TextView listItemText = new TextView(context);
        ImageButton deleteButton = new ImageButton(context);

        row.setLayoutParams(layoutParams);
        row.setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4));
        row.setOrientation(LinearLayout.HORIZONTAL);

        row.addView(listItemText);
        row.addView(deleteButton);

        layoutParams.gravity = Gravity.CENTER;
        layoutParams.weight = 1;

        listItemText.setLayoutParams(layoutParams);
        listItemText.setText(resolutions.get(i));

        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(dpToPx(64), dpToPx(64));

        deleteButton.setLayoutParams(buttonParams);
        deleteButton.setImageResource(R.drawable.ic_delete);

        deleteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                resolutions.remove(i);
                notifyDataSetChanged();
            }
        });

        view = row;
        return view;
    }

    @Override
    public int getCount() {
        return resolutions.size();
    }

    @Override
    public Object getItem(int i) {
        return resolutions.get(i);
    }

    public void addItem(String value) {
        if (resolutions.contains(value)) {
            return;
        }
        resolutions.add(value);
        notifyDataSetChanged();
    }

    public ArrayList<String> getAll() {
        return resolutions;
    }
    public void addAll(ArrayList<String> list) {
        this.resolutions.addAll(list);
        notifyDataSetChanged();
    }

    public boolean exists(String item){
        return resolutions.contains(item);
    }

    @Override
    public long getItemId(int i) {
        return 0;
    }

    private int dpToPx(int value) {
        float density = this.context.getResources().getDisplayMetrics().density;
        return (int) (value * density + 0.5f);
    }
}


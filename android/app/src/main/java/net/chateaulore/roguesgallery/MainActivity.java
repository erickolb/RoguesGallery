package net.chateaulore.roguesgallery;

import android.app.*;
import android.animation.*;
import android.os.*;
import android.graphics.*;
import android.net.Uri;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.net.URL;
import java.net.HttpURLConnection;
import java.util.concurrent.*;
import java.util.*;

public class MainActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService images = Executors.newSingleThreadExecutor();
    private WebView league;
    private LinearLayout root, controls, galleryOverlay;
    private ImageView cover;
    private ImageView previousCover;
    private AnimatorSet transition;
    private Button playbackButton;
    private TextView caption, status;
    private final List<Cover> fullCollection = new ArrayList<>();
    private List<Cover> selected = new ArrayList<>(), collection = new ArrayList<>();
    private Set<String> excluded = new HashSet<>();
    private final Set<String> unavailable = new HashSet<>();
    private String account = "";
    private GalleryPreferences preferences;
    private CoverCache cache;
    private boolean managing;
    private int index, polls;
    private volatile int generation;
    private boolean playing, importing, loadingCover;
    private final RefreshSchedule refreshSchedule = new RefreshSchedule();
    private final Runnable pollCollection = this::pollImport;
    private boolean backgroundImport;
    private int importGeneration;
    private String refreshAccount;
    private final Runnable refreshCollection = ()->{if(playing && inGallery())importCollection(true);};
    private Bitmap currentBitmap;
    private final Runnable hideControls = ()->{
        if(inGallery() && !collection.isEmpty() && galleryOverlay.getVisibility()==View.VISIBLE){
            LinearLayout overlay=galleryOverlay;
            overlay.animate().alpha(0f).setDuration(400).withEndAction(()->overlay.setVisibility(View.GONE));
        }
    };
    private void resetControlsTimeout(){
        handler.removeCallbacks(hideControls);
        if(inGallery() && galleryOverlay.getVisibility()==View.VISIBLE){
            galleryOverlay.animate().cancel();galleryOverlay.setAlpha(1f);
            if(!collection.isEmpty())handler.postDelayed(hideControls,30_000L);
        }
    }
    private void setControlsVisible(boolean visible){
        handler.removeCallbacks(hideControls);galleryOverlay.animate().cancel();galleryOverlay.setAlpha(1f);
        galleryOverlay.setVisibility(visible || collection.isEmpty()?View.VISIBLE:View.GONE);
        resetControlsTimeout();
    }
    @Override public void onUserInteraction(){super.onUserInteraction();resetControlsTimeout();}
    private final Runnable slideshow = ()->{if(playing)advance(1);};
    @Override public void onCreate(Bundle state) { super.onCreate(state); preferences=new GalleryPreferences(getSharedPreferences("gallery",MODE_PRIVATE)); cache=new CoverCache(new File(getCacheDir(),"covers")); createWebView(); showLogin(); if(Build.VERSION.SDK_INT>=33) ModernBack.register(this,this::handleBack); }
    @android.annotation.TargetApi(33)
    private static class ModernBack {
        static void register(Activity activity, Runnable action) {
            activity.getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,action::run);
        }
    }
    private void createWebView() {
        league = new WebView(this);
        league.getSettings().setJavaScriptEnabled(true);
        league.getSettings().setDomStorageEnabled(true);
        league.getSettings().setAllowFileAccess(false);
        league.getSettings().setAllowContentAccess(false);
        league.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        CookieManager.getInstance().setAcceptCookie(true);
        league.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                return request.isForMainFrame() && !LeagueOrigin.accepts(uri.toString());
            }
            @Override public void onPageFinished(WebView view, String url) { CookieManager.getInstance().flush(); }
            @Override public void onReceivedSslError(WebView view, android.webkit.SslErrorHandler handler, android.net.http.SslError error) { handler.cancel(); }
        });
    }
    private static byte[] readBytes(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] buffer = new byte[8192]; int n;
        while ((n = in.read(buffer)) != -1) out.write(buffer,0,n);
        return out.toByteArray();
    }
    private void base() {
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.rgb(24,17,25));
        root.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets;}); setContentView(root);
    }
    private Button button(String label, Runnable action) { Button b = new Button(this); b.setText(label); b.setOnClickListener(v -> action.run()); return b; }
    private void text(String value) { TextView t = new TextView(this); t.setText(value); t.setTextColor(Color.WHITE); t.setPadding(20,12,20,12); root.addView(t); }
    private void showLogin() {
        cancelImport();
        stopPlayback(); generation++; managing=false; base();
        text("Legion of Comic Geeks - Rogues Gallery is an unaffiliated app that will allow you to display issues in your collection as a slideshow on your device. To begin, sign into to LCG, then open your own profile collection and tap the Load My Collection button.");
        LinearLayout row = new LinearLayout(this);
        row.addView(button("Load My Collection", this::importCollection),new LinearLayout.LayoutParams(0,-2,1)); row.addView(button("Settings", this::settings),new LinearLayout.LayoutParams(0,-2,1));
        root.addView(row);
        LinearLayout links=new LinearLayout(this); links.addView(button("Data Use / Privacy Policy",this::privacy),new LinearLayout.LayoutParams(0,-2,2)); links.addView(button("Credits",this::credits),new LinearLayout.LayoutParams(0,-2,1)); root.addView(links);
        status = new TextView(this); status.setTextColor(Color.WHITE); status.setPadding(20,0,20,8); root.addView(status);
        if (league.getParent() != null) ((android.view.ViewGroup)league.getParent()).removeView(league);
        root.addView(league, new LinearLayout.LayoutParams(-1,0,1));
        if (league.getUrl() == null) league.loadUrl("https://leagueofcomicgeeks.com/login");
    }
    private void importCollection() {
        importCollection(false);
    }
    private void importCollection(boolean background) {
        if (importing) return;
        backgroundImport=background;refreshAccount=account;
        if (!LeagueOrigin.accepts(league.getUrl())) { importFailed("Open League before loading your collection."); return; }
        try {
            String script;
            try (InputStream in = getAssets().open("collection.js")) { script = new String(readBytes(in), java.nio.charset.StandardCharsets.UTF_8); }
            importing = true; polls=0; int token=++importGeneration;
            if(!background)status.setText("Listing your full collection…");
            league.evaluateJavascript(script, ignored -> {if(token==importGeneration && importing)handler.postDelayed(pollCollection,1000);});
        } catch(Exception e) { importFailed("Could not start import."); }
    }
    private void cancelImport(){if(importing)league.evaluateJavascript("window.__roguesRun=(window.__roguesRun||0)+1",null);importing=false;importGeneration++;handler.removeCallbacks(pollCollection);}
    private void scheduleRefresh(){handler.removeCallbacks(refreshCollection);if(playing && !importing && !account.isEmpty())handler.postDelayed(refreshCollection,refreshSchedule.delay(SystemClock.elapsedRealtime()));}
    private void importFailed(String message){
        importing=false;
        if(backgroundImport){refreshSchedule.failed(SystemClock.elapsedRealtime());scheduleRefresh();}
        else status.setText(message);
    }
    private void pollImport() {
        if (!importing) return;
        int token=importGeneration;
        league.evaluateJavascript("JSON.stringify(window.__roguesResult || {progress:window.__roguesProgress || 'Loading owned issues…'})", value -> {
            if(token!=importGeneration || !importing)return;
            try {
                Object decoded = new JSONTokener(value).nextValue();
                if (!(decoded instanceof String) || "null".equals(decoded)) {
                    if (++polls < 600) handler.postDelayed(pollCollection,1000);
                    else importFailed("Collection loading timed out. Please try again.");
                    return;
                }
                JSONObject result = new JSONObject((String)decoded);
                if (result.has("progress")) {
                    if(!backgroundImport)status.setText(result.getString("progress"));
                    if (++polls < 600) handler.postDelayed(pollCollection,1000);
                    else importFailed("Collection loading timed out. Please try again.");
                    return;
                }
                importing=false;
                if (result.has("error")) { importFailed(result.getString("error")); return; }
                acceptCollection(result);
            } catch(Exception e) { importFailed("Unable to read League's response. Try signing in again."); }
        });
    }
    private void showGallery() {
        stopPlayback(); generation++; managing=false; base(); getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(5894);
        FrameLayout artwork=new FrameLayout(this);
        previousCover=new ImageView(this);previousCover.setScaleType(ImageView.ScaleType.FIT_CENTER);
        previousCover.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        artwork.addView(previousCover,new FrameLayout.LayoutParams(-1,-1));
        cover = new ImageView(this); cover.setScaleType(ImageView.ScaleType.FIT_CENTER);
        cover.setContentDescription("Comic cover");artwork.addView(cover,new FrameLayout.LayoutParams(-1,-1));
        root.addView(artwork,new LinearLayout.LayoutParams(-1,0,1));
        galleryOverlay=new LinearLayout(this);galleryOverlay.setOrientation(LinearLayout.VERTICAL);
        galleryOverlay.setBackgroundColor(Color.argb(190,16,20,28));galleryOverlay.setPadding(dp(12),dp(12),dp(12),dp(8));
        galleryOverlay.setClickable(true);
        caption = new TextView(this); caption.setTextColor(Color.WHITE); caption.setGravity(Gravity.CENTER);galleryOverlay.addView(caption);
        controls = new LinearLayout(this); controls.setGravity(Gravity.CENTER);
        controls.addView(iconButton(R.drawable.ic_skip_previous,"Previous cover",()->advance(-1)),new LinearLayout.LayoutParams(dp(56),dp(56)));
        playbackButton=button("",()->setPlaying(!playing));
        playbackButton.setMinWidth(0);playbackButton.setMinimumWidth(0);
        playbackButton.setGravity(Gravity.CENTER);playbackButton.setPadding(dp(8),0,dp(8),0);playbackButton.setCompoundDrawablePadding(dp(4));
        controls.addView(playbackButton,new LinearLayout.LayoutParams(dp(116),dp(56)));
        controls.addView(iconButton(R.drawable.ic_skip_next,"Next cover",()->advance(1)),new LinearLayout.LayoutParams(dp(56),dp(56)));
        controls.addView(iconButton(R.drawable.ic_more,"Options menu",this::menu),new LinearLayout.LayoutParams(dp(56),dp(56)));
        LinearLayout.LayoutParams controlRow=new LinearLayout.LayoutParams(-2,dp(56));controlRow.gravity=Gravity.CENTER_HORIZONTAL;galleryOverlay.addView(controls,controlRow);
        FrameLayout.LayoutParams overlayPosition=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);
        artwork.addView(galleryOverlay,overlayPosition);
        galleryOverlay.setVisibility(View.GONE);
        cover.setOnClickListener(v->setControlsVisible(galleryOverlay.getVisibility()!=View.VISIBLE));
        display();
        setPlaying(true);
    }
    private Button iconButton(int drawable,String description,Runnable action){
        Button button=button("",action);button.setMinWidth(0);button.setMinimumWidth(0);
        button.setContentDescription(description);button.setTooltipText(description);
        button.setGravity(Gravity.CENTER);button.setPadding(0,0,0,0);
        button.setCompoundDrawablesWithIntrinsicBounds(drawable,0,0,0);
        button.setPadding(dp(16),0,dp(16),0);
        return button;
    }
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private boolean inGallery(){return !managing && cover!=null && cover.getParent()!=null && cover.getParent().getParent()==root;}
    private void scheduleRotation(){handler.removeCallbacks(slideshow);if(playing && !loadingCover)handler.postDelayed(slideshow,preferences.rotationSeconds*1000L);}
    private void setPlaying(boolean requested){
        playing=requested && !collection.isEmpty();handler.removeCallbacks(slideshow);
        refreshSchedule.running(SystemClock.elapsedRealtime(),playing);scheduleRefresh();
        if(playbackButton!=null){
            playbackButton.setText(playing?"Pause":"Play");
            playbackButton.setContentDescription(playing?"Pause slideshow":"Play slideshow");
            playbackButton.setCompoundDrawablesWithIntrinsicBounds(playing?R.drawable.ic_pause:R.drawable.ic_play,0,0,0);
            playbackButton.setEnabled(!collection.isEmpty());
        }
        scheduleRotation();
    }
    private void advance(int delta) { if(collection.size()>0) {index=(index+delta+collection.size())%collection.size();display();} }
    private void display() {
        int token=++generation;
        handler.removeCallbacks(slideshow);loadingCover=!collection.isEmpty();
        String imageAccount=account;
        if(collection.isEmpty()){setPlaying(false);cover.setImageDrawable(null); caption.setText("No enabled covers. Open Menu to manage your collection.");setControlsVisible(true);return;} Cover item=collection.get(index);
        caption.setText(item.title+" · "+(index+1)+" / "+collection.size());
        cover.setContentDescription(item.title);
        images.execute(()->{
            if(token!=generation) return;
            Bitmap bitmap=null; String error=null;
            try {
                String[] candidates = !item.fallbackUrl.isEmpty() ? new String[]{item.url,item.fallbackUrl} : new String[]{item.url};
                for (int candidate=0;candidate<candidates.length;candidate++) {
                  try {
                String imageUrl=candidates[candidate];
                byte[] bytes=cache.get(imageAccount,item.id,imageUrl,()->download(imageUrl));
                BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,bounds);
                BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=1;
                while(bounds.outWidth/options.inSampleSize>2400 || bounds.outHeight/options.inSampleSize>2400) options.inSampleSize*=2;
                bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
                if(bitmap==null){cache.remove(imageAccount,item.id,imageUrl);throw new IOException();}                    break;
                  } catch(Exception failure) { if(candidate==candidates.length-1) throw failure; }
                }
            }catch(Exception e){error="Cover unavailable. Use Next to continue.";}
            Bitmap loaded=bitmap;String failure=error;
            handler.post(()->{if(token!=generation){if(loaded!=null)loaded.recycle();return;}if(failure!=null){unavailable.add(item.id);select(CollectionSelection.ids(selected),true);Toast.makeText(this,"Skipped unavailable cover: "+item.title,Toast.LENGTH_SHORT).show();display();}else{loadingCover=false;showCover(loaded);scheduleRotation();}});
        });
    }
    private void finishTransition(){if(transition!=null){transition.cancel();transition=null;}if(cover!=null)cover.setAlpha(1f);}
    private void showCover(Bitmap loaded){
        finishTransition();Bitmap outgoing=currentBitmap;currentBitmap=loaded;
        cover.setImageBitmap(loaded);
        if(preferences.crossFade && outgoing!=null){
            previousCover.setImageBitmap(outgoing);previousCover.setAlpha(1f);cover.setAlpha(0f);
            AnimatorSet animation=new AnimatorSet();transition=animation;
            animation.playTogether(ObjectAnimator.ofFloat(cover,"alpha",0f,1f),ObjectAnimator.ofFloat(previousCover,"alpha",1f,0f));
            animation.setDuration(600);animation.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
            animation.addListener(new AnimatorListenerAdapter(){@Override public void onAnimationEnd(Animator animator){previousCover.setImageDrawable(null);outgoing.recycle();if(transition==animation)transition=null;}});
            animation.start();
        }else if(outgoing!=null)outgoing.recycle();
    }
    private void stopPlayback(){handler.removeCallbacks(hideControls);if(galleryOverlay!=null){galleryOverlay.animate().cancel();galleryOverlay.setAlpha(1f);}if(backgroundImport && importing){cancelImport();refreshSchedule.failed(SystemClock.elapsedRealtime());}setPlaying(false);finishTransition();getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);getWindow().getDecorView().setSystemUiVisibility(0);}
    private void acceptCollection(JSONObject result) throws JSONException {
        String incomingAccount=result.getString("accountId");
        if(backgroundImport && (!account.equals(refreshAccount) || !account.equals(incomingAccount)))throw new JSONException("Account changed during refresh");
        List<Cover> incoming=new ArrayList<>();
        JSONArray covers=result.getJSONArray("covers");
        for(int i=0;i<covers.length();i++){JSONObject c=covers.getJSONObject(i);incoming.add(new Cover(c.getString("id"),c.optString("title"),c.getString("url"),c.optString("fallbackUrl")));}
        String currentId=collection.isEmpty()?null:collection.get(index).id;
        List<String> previous=backgroundImport?CollectionSelection.ids(selected):preferences.selected(incomingAccount);
        account=incomingAccount;fullCollection.clear();fullCollection.addAll(incoming);unavailable.clear();
        excluded=preferences.exclusions(account);
        select(previous,preferences.rememberSelection);
        refreshSchedule.refreshed(SystemClock.elapsedRealtime());
        if(backgroundImport){
            if(inGallery()){
                int retained=-1;for(int i=0;i<collection.size();i++)if(collection.get(i).id.equals(currentId)){retained=i;break;}
                if(retained>=0){index=retained;caption.setText(collection.get(index).title+" · "+(index+1)+" / "+collection.size());}
                else display();
            }
            scheduleRefresh();
        }else showGallery();
    }
    private void select(List<String> previous,boolean keep) {
        Set<String> ineligible=new HashSet<>(excluded);ineligible.addAll(unavailable);
        selected=CollectionSelection.select(fullCollection,ineligible,preferences.maximum,preferences.randomSelection,previous,keep,new Random());
        preferences.selected(account,selected);
        collection=CollectionSelection.displayOrder(selected,preferences.randomDisplay,new Random());index=0;
    }
    private byte[] download(String address) throws IOException {
        URL url=new URL(address);if(!"https".equals(url.getProtocol()))throw new IOException();
        HttpURLConnection connection=(HttpURLConnection)url.openConnection();
        connection.setConnectTimeout(15000);connection.setReadTimeout(15000);connection.setInstanceFollowRedirects(false);
        try {
            if(connection.getResponseCode()!=200)throw new IOException();
            byte[] bytes;
            try(InputStream in=connection.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] buffer=new byte[8192];int n;
                while((n=in.read(buffer))!=-1){if(out.size()+n>20*1024*1024)throw new IOException();out.write(buffer,0,n);}bytes=out.toByteArray();
            }
            BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,bounds);
            if(bounds.outWidth<=0 || bounds.outHeight<=0)throw new IOException();
            return bytes;
        }finally{connection.disconnect();}
    }
    private void menu(){
        stopPlayback();
        new AlertDialog.Builder(this).setItems(new String[]{"Exclude this cover","Manage collection","Settings","Data Use / Privacy Policy","Refresh / Account","Credits","Sign out"},(d,i)->{
            if(i==0){if(!collection.isEmpty()){excluded.add(collection.get(index).id);preferences.exclusions(account,excluded);select(CollectionSelection.ids(selected),true);showGallery();}}
            else if(i==1)manageCollection();else if(i==2)settings();else if(i==3)privacy();else if(i==4)showLogin();else if(i==5)credits();
            else {generation++;fullCollection.clear();selected.clear();collection.clear();excluded.clear();account="";CookieManager.getInstance().removeAllCookies(ignored->{CookieManager.getInstance().flush();league.clearCache(true);WebStorage.getInstance().deleteAllData();league.loadUrl("https://leagueofcomicgeeks.com/login");showLogin();});}
        }).show();
    }
    private void settings(){
        stopPlayback();
        LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(28,16,28,16);
        TextView explanation=new TextView(this);explanation.setText("List the full collection first, then choose up to this many enabled covers. Images are downloaded as they are displayed.");content.addView(explanation);
        TextView label=new TextView(this);label.setText("Maximum images (default 1000)");content.addView(label);
        EditText maximum=new EditText(this);maximum.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);maximum.setText(String.valueOf(preferences.maximum));content.addView(maximum);
        Switch randomSelection=new Switch(this);randomSelection.setText("Choose covers at random (off: LCG order)");randomSelection.setChecked(preferences.randomSelection);content.addView(randomSelection);
        Switch remember=new Switch(this);remember.setText("Keep the same selection between sessions");remember.setChecked(preferences.rememberSelection);content.addView(remember);
        TextView persistence=new TextView(this);persistence.setText("Default: choose afresh each session. Keeping a selection fills gaps if comics are removed or excluded.");content.addView(persistence);
        Switch randomDisplay=new Switch(this);randomDisplay.setText("Random slideshow order (off: LCG order)");randomDisplay.setChecked(preferences.randomDisplay);content.addView(randomDisplay);
        TextView delayLabel=new TextView(this);delayLabel.setText("Seconds between covers (3–600)");content.addView(delayLabel);
        EditText delay=new EditText(this);delay.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);delay.setText(String.valueOf(preferences.rotationSeconds));content.addView(delay);
        Switch crossFade=new Switch(this);crossFade.setText("Cross-fade between covers (off: smash cut)");crossFade.setChecked(preferences.crossFade);content.addView(crossFade);
        Button manage=button("Manage collection",()->{manageCollection();});manage.setEnabled(!account.isEmpty());content.addView(manage);
        content.addView(button("Clear cached covers",()->images.execute(()->{cache.clear();handler.post(()->Toast.makeText(this,"Cached covers cleared",Toast.LENGTH_SHORT).show());})));
        ScrollView scroll=new ScrollView(this);scroll.addView(content);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Settings").setView(scroll).setNegativeButton("Cancel",null).setPositiveButton("Save",null).create();
        manage.setOnClickListener(v->{dialog.dismiss();manageCollection();});
        dialog.setOnShowListener(ignored->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            int limit;try{limit=Integer.parseInt(maximum.getText().toString());if(limit<1)throw new NumberFormatException();}catch(NumberFormatException e){maximum.setError("Enter a positive whole number");return;}
            int seconds;try{seconds=Integer.parseInt(delay.getText().toString());if(seconds<3 || seconds>600)throw new NumberFormatException();}catch(NumberFormatException e){delay.setError("Enter a whole number from 3 to 600");return;}
            preferences.rotationSeconds=seconds;preferences.crossFade=crossFade.isChecked();
            boolean reselect=limit!=preferences.maximum || randomSelection.isChecked()!=preferences.randomSelection;
            preferences.maximum=limit;preferences.randomSelection=randomSelection.isChecked();preferences.rememberSelection=remember.isChecked();preferences.randomDisplay=randomDisplay.isChecked();preferences.save();
            if(!account.isEmpty()){if(reselect)select(CollectionSelection.ids(selected),preferences.rememberSelection);else{preferences.selected(account,selected);collection=CollectionSelection.displayOrder(selected,preferences.randomDisplay,new Random());index=0;}}
            dialog.dismiss();if(!account.isEmpty() && inGallery())showGallery();
        }));dialog.show();
    }
    private void manageCollection(){
        if(account.isEmpty()){Toast.makeText(this,"Load My Collection first",Toast.LENGTH_LONG).show();return;}
        stopPlayback();generation++;managing=true;base();
        text("Manage collection · "+fullCollection.size()+" issues. Checked covers are eligible for display; the maximum and selection settings still apply.");
        root.addView(button("Done",()->{select(CollectionSelection.ids(selected),true);showGallery();}));
        ListView list=new ListView(this);
        Set<String> chosen=new HashSet<>(CollectionSelection.ids(selected));
        list.setAdapter(new BaseAdapter(){
            public int getCount(){return fullCollection.size();}
            public Object getItem(int position){return fullCollection.get(position);}
            public long getItemId(int position){return position;}
            public View getView(int position,View recycled,ViewGroup parent){
                CheckBox check=recycled instanceof CheckBox?(CheckBox)recycled:new CheckBox(MainActivity.this);
                Cover item=fullCollection.get(position);check.setOnCheckedChangeListener(null);check.setTextColor(Color.WHITE);
                check.setText(item.title+(excluded.contains(item.id)?" · excluded":chosen.contains(item.id)?" · selected":" · eligible"));
                check.setChecked(!excluded.contains(item.id));check.setPadding(20,12,20,12);
                check.setOnCheckedChangeListener((button,enabled)->{if(enabled)excluded.remove(item.id);else excluded.add(item.id);preferences.exclusions(account,excluded);notifyDataSetChanged();});
                return check;
            }
        });root.addView(list,new LinearLayout.LayoutParams(-1,0,1));
    }
    private void privacy(){
        stopPlayback();
        try(InputStream in=getAssets().open("privacy.html")){
            TextView text=new TextView(this);text.setPadding(28,20,28,20);text.setText(android.text.Html.fromHtml(new String(readBytes(in),java.nio.charset.StandardCharsets.UTF_8),0));text.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());
            ScrollView scroll=new ScrollView(this);scroll.addView(text);new AlertDialog.Builder(this).setTitle("Data Use / Privacy Policy").setView(scroll).setPositiveButton("Close",null).show();
        }catch(IOException e){Toast.makeText(this,"Privacy policy unavailable",Toast.LENGTH_LONG).show();}
    }    private void credits(){String license="";try(InputStream in=getAssets().open("leagueofcomicgeeks-LICENSE.txt")){license=new String(readBytes(in),java.nio.charset.StandardCharsets.UTF_8);}catch(IOException ignored){}
        TextView t=new TextView(this);t.setPadding(28,20,28,20);t.setText(android.text.Html.fromHtml("<h2>Credits</h2><p><a href=\"https://www.flaticon.com/free-icons/comic-book\" title=\"comic-book icons\">Comic-book icons created by Magnific - Flaticon</a></p><p>Collection adapter based on <a href=\"https://github.com/alistairjcbrown/leagueofcomicgeeks\">leagueofcomicgeeks by Alistair Brown</a> (MIT).</p><p>Unofficial app. League of Comic Geeks and cover artwork belong to their respective owners.</p>",0));t.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());
        ScrollView scroll=new ScrollView(this);LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.addView(t);TextView l=new TextView(this);l.setPadding(28,0,28,20);l.setText(license);content.addView(l);scroll.addView(content);new AlertDialog.Builder(this).setView(scroll).setPositiveButton("Close",null).show();}
    private void handleBack(){if(managing){select(CollectionSelection.ids(selected),true);showGallery();}else if(inGallery())menu();else if(league.canGoBack())league.goBack();else finish();}
    // API 33+ uses ModernBack; this callback supports Android 8 through 12.
    @android.annotation.SuppressLint("GestureBackNavigation")
    @Override public void onBackPressed(){handleBack();}
    @Override protected void onPause(){super.onPause();stopPlayback();CookieManager.getInstance().flush();}
    @Override protected void onResume(){super.onResume();if(inGallery()){getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);getWindow().getDecorView().setSystemUiVisibility(5894);resetControlsTimeout();}}
    @Override protected void onDestroy(){generation++;importing=false;finishTransition();handler.removeCallbacksAndMessages(null);images.shutdownNow();league.destroy();super.onDestroy();}
}
